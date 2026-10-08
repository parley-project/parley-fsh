package us.bringardner.fsh.exec;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.Console.FileDiscriptor;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.expand.Expander;
import us.bringardner.fsh.syntax.Ast;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.IRandomAccessStream;

/**
 * Redirects (n>file, n>&m, <<EOF ...) applied to a context. Descriptors 0, 1 and 2 are the
 * context's stdin, stdout and stderr (so they follow earlier redirects and subshells); others are
 * the console's (exec 3>file).
 */
final class Redirects {

	private Redirects() {
	}

	/** the streams of a context, to put back after a command */
	record Saved(InputStream in, PrintStream out, PrintStream err) {
		static Saved of(ShellContext sc) {
			return new Saved(sc.stdin, sc.stdout, sc.stderr);
		}

		void restore(ShellContext sc) {
			sc.stdin = in;
			sc.stdout = out;
			sc.stderr = err;
		}
	}

	/**
	 * Apply the redirects in order.
	 * @return the streams opened here, to close (with {@link #close}) when the command is done.
	 * Streams given to a descriptor above 2 stay open until it is closed (exec 3>&-).
	 */
	static List<Closeable> apply(List<Ast.Redirect> redirects, ShellContext sc, Expander ex) throws IOException {
		List<Closeable> opened = new ArrayList<>();
		try {
			for(Ast.Redirect r : redirects) {
				redirect(r, sc, ex, opened);
			}
		} catch (IOException | RuntimeException e) {
			close(opened);
			throw e;
		}
		return opened;
	}

	static void close(List<Closeable> opened) {
		if( opened != null ) {
			for(Closeable c : opened) {
				try {
					c.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/** the file name of a redirect: one word after expansion, or an ambiguous redirect */
	private static String target(Ast.Redirect r, Expander ex) throws IOException {
		List<String> words = ex.expand(r.target);
		if( words.size() != 1 ) {
			throw new IOException(r.target.raw+": ambiguous redirect");
		}
		return words.get(0);
	}

	private static void redirect(Ast.Redirect r, ShellContext sc, Expander ex, List<Closeable> opened) throws IOException {
		Integer fd = r.fd;
		if( r.fdVariable != null ) {
			fd = variableFd(sc, r);
		}
		if( r.hereDoc != null ) {
			String body = r.hereDoc.body == null ? "" : r.hereDoc.body;
			if( !r.hereDoc.quoted ) {
				body = ex.hereDocument(body);
			}
			setIn(sc, fd == null ? 0 : fd, new ByteArrayInputStream(body.getBytes()), opened);
			return;
		}
		switch (r.op) {
		case ">":
		case ">|": {
			String word = target(r, ex);
			FileSource file = sc.getFileSource(word);
			if( r.op.equals(">") && file.exists() && !file.isDirectory() && sc.console.isOptionEnabled(Option.NoClobberRedirect) && !word.equals("/dev/null")) {
				throw new IOException(word+": cannot overwrite existing file");
			}
			setOut(sc, fd == null ? 1 : fd, new PrintStream(open(file, word, false)), file, opened);
			break;
		}
		case ">>": {
			String word = target(r, ex);
			FileSource file = sc.getFileSource(word);
			setOut(sc, fd == null ? 1 : fd, new PrintStream(open(file, word, true)), file, opened);
			break;
		}
		case "&>":
		case "&>>": {
			String word = target(r, ex);
			FileSource file = sc.getFileSource(word);
			PrintStream out = new PrintStream(open(file, word, r.op.equals("&>>")));
			setOut(sc, 1, out, file, opened);
			sc.stderr = out;
			break;
		}
		case "<": {
			String word = target(r, ex);
			FileSource file = sc.getFileSource(word);
			if( !file.exists()) {
				throw new IOException(word+": No such file or directory");
			}
			setIn(sc, fd == null ? 0 : fd, file.getInputStream(), opened);
			break;
		}
		case "<<<":
			// a here-string: the word and a newline
			setIn(sc, fd == null ? 0 : fd, new ByteArrayInputStream((ex.string(r.target)+"\n").getBytes()), opened);
			break;
		case ">&": {
			String word = target(r, ex);
			if( fd == null && !isDescriptor(word)) {
				// >&word is &>word
				FileSource file = sc.getFileSource(word);
				PrintStream out = new PrintStream(open(file, word, false));
				setOut(sc, 1, out, file, opened);
				sc.stderr = out;
			} else {
				duplicate(sc, fd == null ? 1 : fd, word, true);
			}
			break;
		}
		case "<&":
			duplicate(sc, fd == null ? 0 : fd, target(r, ex), false);
			break;
		case "<>":
			openReadWrite(sc, fd == null ? 0 : fd, target(r, ex));
			break;
		default:
			throw new IOException("unknown redirect "+r.op);
		}
	}

	/** a file to write, or bash's message if it cannot be */
	private static OutputStream open(FileSource file, String word, boolean append) throws IOException {
		if( file.isDirectory()) {
			throw new IOException(word+": Is a directory");
		}
		try {
			return file.getOutputStream(append);
		} catch (IOException e) {
			FileSource parent = file.getParentFile();
			if( parent != null && !parent.exists()) {
				throw new IOException(word+": No such file or directory");
			}
			throw new IOException(word+": Permission denied");
		}
	}

	/**
	 * {name}>file: a new descriptor (10 or more, the first free one) whose number is put in name;
	 * {name}>&- closes the one name holds.
	 */
	private static int variableFd(ShellContext sc, Ast.Redirect r) {
		String name = r.fdVariable;
		if( r.target != null && "-".equals(r.target.raw)) {
			try {
				return Integer.parseInt((""+sc.getVariable(name)).trim());
			} catch (NumberFormatException e) {
				throw new RuntimeException(name+": not a file descriptor");
			}
		}
		int fd = 10;
		while( sc.console.getFileDistcriptor(fd) != null ) {
			fd++;
		}
		sc.setVariable(name, String.valueOf(fd));
		return fd;
	}

	private static boolean isDescriptor(String word) {
		return word.matches("[0-9]+-?|-");
	}

	/**
	 * n>&m (out) or n<&m: n becomes a copy of m; n>&m- also closes m (moves it); n>&- closes n.
	 */
	private static void duplicate(ShellContext sc, int n, String word, boolean out) throws IOException {
		if( !isDescriptor(word)) {
			throw new IOException(word+": ambiguous redirect");
		}
		if( word.equals("-")) {
			close(sc, n);
			return;
		}
		boolean move = word.endsWith("-");
		int m = Integer.parseInt(move ? word.substring(0, word.length()-1) : word);
		if( out ) {
			PrintStream ps = getOut(sc, m);
			if( ps == null ) {
				throw new IOException(m+": Bad file descriptor");
			}
			ps.flush();
			if( n == 1 ) {
				sc.stdout = ps;
			} else if( n == 2 ) {
				sc.stderr = ps;
			} else {
				sc.console.setFileDistcriptor(FileDiscriptor.shared(n, ps));
			}
		} else {
			InputStream in = getIn(sc, m);
			if( in == null ) {
				throw new IOException(m+": Bad file descriptor");
			}
			if( n == 0 ) {
				sc.stdin = in;
			} else {
				sc.console.setFileDistcriptor(FileDiscriptor.shared(n, in));
			}
		}
		if( move && m > 2 ) {
			// the stream lives on as n
			sc.console.removeFileDistcriptor(m);
		}
	}

	private static PrintStream getOut(ShellContext sc, int m) {
		if( m == 1 ) {
			return sc.stdout;
		} else if( m == 2 ) {
			return sc.stderr;
		}
		FileDiscriptor fd = sc.console.getFileDistcriptor(m);
		return fd == null ? null : fd.getOut();
	}

	private static InputStream getIn(ShellContext sc, int m) {
		if( m == 0 ) {
			return sc.stdin;
		}
		FileDiscriptor fd = sc.console.getFileDistcriptor(m);
		return fd == null ? null : fd.getIn();
	}

	private static void close(ShellContext sc, int n) {
		if( n == 0 ) {
			// <&-: reading is an error (cat <&- fails), as in bash
			sc.stdin = new InputStream() {
				@Override
				public int read() throws IOException {
					throw new IOException("Bad file descriptor");
				}
			};
		} else if( n == 1 ) {
			sc.stdout.flush();
			sc.stdout = new us.bringardner.fsh.antlr.Statement.ClosedStream();
		} else if( n == 2 ) {
			sc.stderr.flush();
			sc.stderr = new PrintStream(OutputStream.nullOutputStream());
		} else {
			sc.console.closeFileDistcriptor(n);
		}
	}

	private static void setOut(ShellContext sc, int n, PrintStream out, FileSource file, List<Closeable> opened) {
		if( n == 1 ) {
			sc.stdout = out;
			opened.add(out);
		} else if( n == 2 ) {
			sc.stderr = out;
			opened.add(out);
		} else {
			sc.console.setFileDistcriptor(new FileDiscriptor(n, out, file));
		}
	}

	private static void setIn(ShellContext sc, int n, InputStream in, List<Closeable> opened) {
		if( n == 0 ) {
			sc.stdin = in;
			opened.add(in);
		} else {
			sc.console.setFileDistcriptor(new FileDiscriptor(n, in, null));
		}
	}

	private static void openReadWrite(ShellContext sc, int n, String path) throws IOException {
		FileSource file = sc.getFileSource(path);
		if( !file.exists() && !file.createNewFile()) {
			throw new IOException(path+": cannot create file");
		}
		IRandomAccessStream rad = file.getRandomAccessStream("rw");
		InputStream in = new InputStream() {
			@Override
			public int read() throws IOException {
				return rad.read();
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
			public void write(byte[] b, int off, int len) throws IOException {
				rad.write(b, off, len);
			}
		};
		FileDiscriptor fd = new FileDiscriptor(n, in, rad);
		fd.setOut(new PrintStream(out));
		sc.console.setFileDistcriptor(fd);
		if( n == 0 ) {
			sc.stdin = in;
		}
	}
}
