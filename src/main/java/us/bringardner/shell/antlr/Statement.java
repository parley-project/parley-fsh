package us.bringardner.shell.antlr;

import java.awt.Point;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.ListContext;
import us.bringardner.filesource.sh.FileSourceShParser.Redirect_oneContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.IRandomAccessStream;
import us.bringardner.shell.Console.FileDiscriptor;
import us.bringardner.shell.Console.Option;
import us.bringardner.shell.ShellContext;

public abstract class Statement {

	/**
	 * Apply the redirects to ctx.
	 * 
	 * @return the streams opened here that the caller must close (with {@link #closeRedirects(List)}) once the
	 * statement is done. Streams given to a file descriptor above 2 stay open until it is closed (exec 3>&-).
	 */
	public List<Closeable> configureRedirect(ShellContext ctx, RerdirectImpl redirect) throws IOException {
		return configureRedirect(ctx, redirect, null);
	}

	/**
	 * @param firstFd the file descriptor of the first redirect, if it has none written (see RerdirectImpl.fdWord)
	 */
	public List<Closeable> configureRedirect(ShellContext ctx, RerdirectImpl redirect, Integer firstFd) throws IOException {
		List<Closeable> opened = new ArrayList<>();
		if( redirect == null ) {
			return opened;
		}
		try {
			for(Redirect_oneContext r : redirect.redirects) {
				redirect(ctx, r, r == redirect.redirects.get(0) ? firstFd : null, opened);
				firstFd = null;
			}
		} catch (IOException | RuntimeException e) {
			closeRedirects(opened);
			throw e;
		}
		return opened;
	}

	public static void closeRedirects(List<Closeable> opened) {
		if( opened != null ) {
			for(Closeable c : opened) {
				try {
					c.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * One redirect, [n]op word. File descriptors 0, 1 and 2 are ctx's stdin, stdout and stderr (so
	 * they follow earlier redirects and subshells); others are the console's (exec 3>file).
	 */
	private void redirect(ShellContext ctx, Redirect_oneContext r, Integer fd, List<Closeable> opened) throws IOException {
		String op = r.redirectionOperator().getText();
		if( r.fd != null ) {
			fd = Integer.parseInt(r.fd.getText());
		}
		String word = ""+new Argument(r.target).getValue(ctx);

		switch (op) {
		case ">":
		case ">|": {
			FileSource file = ctx.getFileSource(word);
			if( op.equals(">") && file.exists() && ctx.console.options.contains(Option.NoClobberRedirect)) {
				throw new IOException(word+": cannot overwrite existing file");
			}
			setOut(ctx, fd == null ? 1 : fd, new PrintStream(file.getOutputStream()), file, opened);
			break;
		}
		case ">>": {
			FileSource file = ctx.getFileSource(word);
			setOut(ctx, fd == null ? 1 : fd, new PrintStream(file.getOutputStream(true)), file, opened);
			break;
		}
		case "&>":
		case "&>>": {
			// both stdout and stderr
			FileSource file = ctx.getFileSource(word);
			PrintStream out = new PrintStream(file.getOutputStream(op.equals("&>>")));
			setOut(ctx, 1, out, file, opened);
			ctx.stderr = out;
			break;
		}
		case "<": {
			FileSource file = ctx.getFileSource(word);
			setIn(ctx, fd == null ? 0 : fd, file.getInputStream(), file, opened);
			break;
		}
		case ">&":
			if( fd == null && !isDescriptor(word)) {
				// >&word is &>word
				FileSource file = ctx.getFileSource(word);
				PrintStream out = new PrintStream(file.getOutputStream());
				setOut(ctx, 1, out, file, opened);
				ctx.stderr = out;
			} else {
				duplicate(ctx, fd == null ? 1 : fd, word, true);
			}
			break;
		case "<&":
			duplicate(ctx, fd == null ? 0 : fd, word, false);
			break;
		case "<>":
			openReadWrite(ctx, fd == null ? 0 : fd, word);
			break;
		default:
			throw new IOException("unknown redirect "+op);
		}
	}

	private static boolean isDescriptor(String word) {
		return word.matches("[0-9]+-?|-");
	}

	/**
	 * n>&m (out) or n<&m: n becomes a copy of m; n>&m- also closes m (moves it); n>&- closes n.
	 */
	private void duplicate(ShellContext ctx, int n, String word, boolean out) throws IOException {
		if( !isDescriptor(word)) {
			throw new IOException(word+": ambiguous redirect");
		}
		if( word.equals("-")) {
			close(ctx, n);
			return;
		}
		boolean move = word.endsWith("-");
		int m = Integer.parseInt(move ? word.substring(0, word.length()-1) : word);
		if( out ) {
			PrintStream ps = getOut(ctx, m);
			if( ps == null ) {
				throw new IOException(m+": Bad file descriptor");
			}
			ps.flush();
			if( n == 1 ) {
				ctx.stdout = ps;
			} else if( n == 2 ) {
				ctx.stderr = ps;
			} else {
				ctx.console.setFileDistcriptor(FileDiscriptor.shared(n, ps));
			}
		} else {
			InputStream in = getIn(ctx, m);
			if( in == null ) {
				throw new IOException(m+": Bad file descriptor");
			}
			if( n == 0 ) {
				ctx.stdin = in;
			} else {
				ctx.console.setFileDistcriptor(FileDiscriptor.shared(n, in));
			}
		}
		if( move && m > 2 ) {
			// the stream lives on as n
			ctx.console.removeFileDistcriptor(m);
		}
	}

	private static PrintStream getOut(ShellContext ctx, int m) {
		if( m == 1 ) {
			return ctx.stdout;
		} else if( m == 2 ) {
			return ctx.stderr;
		}
		FileDiscriptor fd = ctx.console.getFileDistcriptor(m);
		return fd == null ? null : fd.getOut();
	}

	private static InputStream getIn(ShellContext ctx, int m) {
		if( m == 0 ) {
			return ctx.stdin;
		}
		FileDiscriptor fd = ctx.console.getFileDistcriptor(m);
		return fd == null ? null : fd.getIn();
	}

	private static void close(ShellContext ctx, int n) {
		if( n == 0 ) {
			ctx.stdin = InputStream.nullInputStream();
		} else if( n == 1 ) {
			ctx.stdout.flush();
			ctx.stdout = new PrintStream(OutputStream.nullOutputStream());
		} else if( n == 2 ) {
			ctx.stderr.flush();
			ctx.stderr = new PrintStream(OutputStream.nullOutputStream());
		} else {
			ctx.console.closeFileDistcriptor(n);
		}
	}

	private static void setOut(ShellContext ctx, int n, PrintStream out, FileSource file, List<Closeable> opened) {
		if( n == 1 ) {
			ctx.stdout = out;
			opened.add(out);
		} else if( n == 2 ) {
			ctx.stderr = out;
			opened.add(out);
		} else {
			ctx.console.setFileDistcriptor(new FileDiscriptor(n, out, file));
		}
	}

	private static void setIn(ShellContext ctx, int n, InputStream in, FileSource file, List<Closeable> opened) {
		if( n == 0 ) {
			ctx.stdin = in;
			opened.add(in);
		} else {
			ctx.console.setFileDistcriptor(new FileDiscriptor(n, in, file));
		}
	}

	private static void openReadWrite(ShellContext ctx, int n, String path) throws IOException {
		FileSource file = ctx.getFileSource(path);
		if(!file.exists()) {
			if(!file.createNewFile()) {
				throw new IOException("Could not create file "+file);
			}
		}
		IRandomAccessStream rad = file.getRandomAccessStream("rw");
		InputStream in = new InputStream() {
			@Override
			public int read() throws IOException {
				return rad.read();
			}
			@Override
			public int read(byte[] b) throws IOException {
				return rad.read(b);
			}
			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return rad.read(b, off, len);
			}
		};
		OutputStream out = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				rad.write(b);
			}
			@Override
			public void write(byte[] b) throws IOException {
				rad.write(b);
			}
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				rad.write(b, off, len);
			}
		};
		FileDiscriptor fd = new FileDiscriptor(n, in, rad);
		fd.setOut(new PrintStream(out));
		ctx.console.setFileDistcriptor(fd);
		if( n == 0 ) {
			ctx.stdin = in;
		}
	}

	public static boolean testEq(Object a, Object b) {
		if( a == null && b == null) {
			return true;
		}

		if( a != null ) {
			return a.equals(b);
		}

		if( b != null) {
			return b.equals(a);
		}
		return false;
	}

	protected ParserRuleContext context;
	protected Argument [] args=new Argument[0];
	private List<ArgumentContext> argCtx;


	public List<ArgumentContext> getArgCtx() {
		return argCtx;
	}

	public void setArgCtx(List<ArgumentContext> argCtx) {
		this.argCtx = argCtx;
	}

	public Statement(ParserRuleContext context) {
		this.context = context;
		if(context==null) {
			throw new RuntimeException("Null context not allowed in "+getClass());
		}
	}

	public ParserRuleContext getContext() {
		return context;
	}

	public void setContext(ParserRuleContext context) {
		this.context = context;
	}

	public Argument[] getArgs() {
		return args;
	}

	public void setArgs(Argument[] args, List<ArgumentContext> argCtx) {
		this.args = args;
		this.argCtx = argCtx;
	}

	public final int process(ShellContext ctx) throws IOException{
		int ret = 0;
		ctx.waitWhilePaused();

		// brace expansion and word splitting replace args for this run only; the tree (and these
		// args) are shared by every run of the statement
		Argument [] savedArgs = args;
		expandWords(ctx);

		ctx.enterStatement(this);

		try {
			ret = execute(ctx);
		} finally {
			args = savedArgs;
			ctx.exitStatement(ret,this);
		}
		return ret;
	}

	/**
	 * Whether the unquoted expansions in an argument are split into words (see Argument.expandWord).
	 */
	protected boolean splitWords(ArgumentContext word) {
		return true;
	}

	private void expandWords(ShellContext ctx) throws IOException {
		List<ParseTree> kids = context.children;
		if( kids == null ) {
			return;
		}
		List<Argument> newArgs = new ArrayList<Argument>();
		boolean changed = false;
		int aidx=0;

		for(ParseTree kid : kids) {
			if (kid instanceof ListContext) {
				// for and select lists
				ListContext lc = (ListContext)kid;
				if(lc.argument()!=null && !lc.argument().isEmpty()) {
					changed = true;
					for(ArgumentContext ac : lc.argument()) {
						List<Argument> words = Argument.expandWord(ac, ctx, true);
						if( words == null ) {
							newArgs.add(new Argument(ac));
						} else {
							newArgs.addAll(words);
						}
					}
				}
			} else if (kid instanceof ArgumentContext && aidx < args.length) {
				Argument a = args[aidx++];
				List<Argument> words = a.context == null ? null : Argument.expandWord(a.context, ctx, splitWords(a.context));
				if( words != null ) {
					changed = true;
					newArgs.addAll(words);
				} else {
					newArgs.add(a);
				}				
			}
		}

		if( changed ) {
			args = newArgs.toArray(new Argument[newArgs.size()]);
		}
	}

	public String toString(ShellContext ctx) {
		StringBuilder ret = new StringBuilder();
		for(Argument a : getArgs()) {
			try {
				ret.append(""+a.getValue(ctx));
			} catch (Exception e) {
				ret.append(e.toString());
			}
		}
		return ret.toString();
	}

	public Point getLine() {
		return new Point(context.getStart().getLine(),context.getStart().getCharPositionInLine());
	}

	protected abstract int execute(ShellContext ctx) throws IOException;
}
