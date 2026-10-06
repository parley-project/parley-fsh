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

	private enum RedirectOperator {

		Input("<"),
		Output(">"),
		OutoutNoCLobber(">|"),
		Append(">>"),
		Duplicate(">&"),
		StdinToStdOut2("&&"),
		AppendStdinAndStdout("&>>"),
		Move("<&"),
		OpenReadWrite("<>"),

		UnKnown("UnKnown");

		public final String label;
		private RedirectOperator(String label) {
			this.label = label;			
		}

		public static RedirectOperator find(String name) {
			for(RedirectOperator o : values()) {
				if( o.label.equals(name)) {
					return o;
				}
			}
			return UnKnown;
		}

	};

	/**
	 * Apply the redirects to ctx.
	 * 
	 * @return the streams opened here that the caller must close (with {@link #closeRedirects(List)}) once the
	 * statement is done. Streams registered as a file descriptor are not included.
	 */
	public List<Closeable> configureRedirect(ShellContext ctx, RerdirectImpl redirectStart) throws IOException {
		List<Closeable> opened = new ArrayList<>();
		try {
			configureRedirect(ctx, redirectStart, opened);
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

	private void configureRedirect(ShellContext ctx, RerdirectImpl redirectStart, List<Closeable> opened) throws IOException {

		if(redirectStart!=null) {
			Integer fid1 = null;
			Integer fid2 = null;

			if( redirectStart.fid!=null) {
				fid1 = Integer.parseInt(""+redirectStart.fid.getValue(ctx));
			}


			for(Redirect_oneContext redirect : redirectStart.context.redirect_one()) {
				String text = redirect.getText();
				boolean closeAfterDup = false;
				if( redirect.redirectionOperator() ==null) {
					throw new IOException("No redirect operator");
				} else {
					String pathText = null;
					if( redirect.args !=null) {
						ArgumentContext tmp = redirect.args;
						Argument a = new Argument(tmp);
						pathText=""+a.getValue(ctx);
						if( pathText.startsWith(">")) {
							pathText = pathText.substring(1).trim();
						}
						if( pathText.endsWith("-")) {
							closeAfterDup = true;
							pathText = pathText.substring(0,pathText.length()-1);
						}
						try {
							fid2 = Integer.parseInt(pathText);
						} catch (Exception e) {
						}
					} else {
						throw new IOException("configureRedirect no argument for path or toid");
					}


					String op = redirect.redirectionOperator().getText();
					RedirectOperator rdop = RedirectOperator.find(op);
					switch (rdop) {
					case Input: 
						FileSource file = ctx.getFileSource(pathText);
						ctx.stdin = file.getInputStream();
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdin,file);
							ctx.console.setFileDistcriptor(fd);								
						} else {
							opened.add(ctx.stdin);
						}
						break;
					case Output:
						file = ctx.getFileSource(pathText);
						if(file.exists() && ctx.console.options.contains(Option.NoClobberRedirect)) {
							throw new IOException(file.getAbsolutePath()+" exists and no clobber is set");
						}
						ctx.stdout = new PrintStream( file.getOutputStream());
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdout,file);
							ctx.console.setFileDistcriptor(fd);								
						} else {
							opened.add(ctx.stdout);
						}
						break;
					case Append:
						file = ctx.getFileSource(pathText);
						ctx.stdout = new PrintStream( file.getOutputStream(true));
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdout,file);
							ctx.console.setFileDistcriptor(fd);								
						} else {
							opened.add(ctx.stdout);
						}

						break;

					case Duplicate:
						Integer fid = fid1!=null?fid1:fid2;
						if( fid == null) {
							throw new IOException("No file descriptor " );
						}

						FileDiscriptor tmp = ctx.console.getFileDistcriptor(fid);
						if( tmp == null) {
							throw new IOException("bad fid="+fid);
						}

						ctx.stdout = tmp.getOut();	
						if(closeAfterDup) {
							ctx.console.closeFileDistcriptor(fid);
						}


						break;
					case AppendStdinAndStdout:
						/*
						 * 	&>>word
						 *	This is semantically equivalent to
						 *	>>word 2>&1
						 */
						//throw new IOException("RedirectOperator Unexpected value: " + rdop);
						file = ctx.getFileSource(pathText);
						ctx.stderr = ctx.stdout = new PrintStream( file.getOutputStream(true));
						if( fid1 != null) {
							FileDiscriptor fd = new FileDiscriptor(fid1, ctx.stdout,file);
							ctx.console.setFileDistcriptor(fd);								
						} else {
							opened.add(ctx.stdout);
						}

						break;
					case Move: 
						if( fid2 == null) {
							throw new IOException("RedirectOperator Unexpected value: fid2= null " + text);	
						}
						FileDiscriptor fd = ctx.console.getFileDistcriptor(fid2);
						if( fd == null) {
							throw new IOException("bad file descriptor = "+fid2);
						}
						if( fd.getIn() == null) {
							throw new IOException("bad file descriptor (not input) = "+fid2);
						}
						ctx.stdin = fd.getIn();
						break;
					case OpenReadWrite:
						file = ctx.getFileSource(pathText);

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
						fd = new FileDiscriptor(fid1, in,rad);
						fd.setOut(new PrintStream(out));
						ctx.console.setFileDistcriptor(fd);

						break;

					default:
						throw new IOException("RedirectOperator Unexpected value: " + rdop);
					}
				}
			}
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
