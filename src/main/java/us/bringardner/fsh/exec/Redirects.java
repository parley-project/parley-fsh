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
import us.bringardner.parley.files.fileproxy.FileProxy;
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
	static String target(Ast.Redirect r, Expander ex) throws IOException {
		if( ex.posixScript()) {
			// as bash: a script in posix mode does not glob a redirect's word
			return ex.string(r.target);
		}
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
			setIn(sc, fd == null ? 0 : fd, new ReadBack(new ByteArrayInputStream(body.getBytes())), opened);
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
			// a file (not a device or a named pipe, where a read can wait) is shared with the programs
			InputStream in;
			if( file instanceof FileProxy proxy && proxy.getTarget().isFile()) {
				// a local file: a program reads it itself, from where the shell is
				in = new ReadBack(proxy.getTarget());
			} else {
				in = file.getInputStream();
				if( file.isFile()) {
					in = new ReadBack(in);
				}
			}
			setIn(sc, fd == null ? 0 : fd, in, opened);
			break;
		}
		case "<<<":
			// a here-string: the word and a newline
			setIn(sc, fd == null ? 0 : fd, new ReadBack(new ByteArrayInputStream((ex.string(r.target)+"\n").getBytes())), opened);
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
		Expander ex = Executor.expanderFor(sc);
		if( r.target != null && "-".equals(r.target.raw)) {
			// {name}>&-, {a[1]}>&-: the descriptor the variable holds
			String value = ex.string(us.bringardner.fsh.syntax.Parser.fragment("${"+name+"}", us.bringardner.fsh.syntax.Parser.Fragment.WORD));
			try {
				return Integer.parseInt(value.trim());
			} catch (NumberFormatException e) {
				throw new RuntimeException(name+": not a file descriptor");
			}
		}
		int fd = 10;
		while( sc.console.getFileDistcriptor(fd) != null ) {
			fd++;
		}
		int bracket = name.indexOf('[');
		if( bracket < 0 && sc.rawVariable(name) instanceof ShellContext.NameRef nr && nr.target().isEmpty()) {
			// a nameref with no value: a number is no name for it (said, and the redirect fails)
			sc.error("`"+fd+"': not a valid identifier");
			throw new RuntimeException(name+": cannot assign fd to variable");
		}
		if( bracket < 0 ) {
			sc.setVariable(name, String.valueOf(fd));
		} else {
			String array = name.substring(0, bracket);
			us.bringardner.fsh.syntax.Word sub = us.bringardner.fsh.syntax.Parser.fragment(name.substring(bracket+1, name.length()-1), us.bringardner.fsh.syntax.Parser.Fragment.WORD);
			if( sc.getVariable(array) instanceof java.util.Map<?,?> ) {
				sc.setVariable(array, ex.string(sub), String.valueOf(fd));
			} else {
				sc.setVariable(array, (int) ex.arithmetic(sub).longValue(), String.valueOf(fd));
			}
		}
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
			sc.stdout = new us.bringardner.fsh.ClosedStream();
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
		if( file instanceof FileProxy proxy && proxy.getTarget().exists() && !proxy.getTarget().isFile() && !proxy.getTarget().isDirectory()) {
			// a named pipe or a device (exec 9<> fifo): opened for reading and writing at once
			java.io.RandomAccessFile raf = new java.io.RandomAccessFile(proxy.getTarget(), "rw");
			InputStream in = new FifoInput(raf);
			OutputStream out = new OutputStream() {
				@Override
				public void write(int b) throws IOException {
					raf.write(b);
				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					raf.write(b, off, len);
				}
			};
			FileDiscriptor fd = new FileDiscriptor(n, in, null);
			fd.setOut(new PrintStream(out, true));
			sc.console.setFileDistcriptor(fd);
			if( n == 0 ) {
				sc.stdin = in;
			}
			return;
		}
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

	/**
	 * Input from a named pipe opened read-write. What is waiting in a pipe is not known (Java's
	 * available is 0), so available reads one byte in the background: read -t sees it when it
	 * comes, and the byte is the next one read.
	 */
	private static final class FifoInput extends InputStream {
		private final java.io.RandomAccessFile raf;
		/** the byte read ahead, -2 if none (-1 is the end) */
		private int ahead = -2;
		private Thread reading;

		FifoInput(java.io.RandomAccessFile raf) {
			this.raf = raf;
		}

		@Override
		public synchronized int available() throws IOException {
			if( ahead != -2 ) {
				return ahead < 0 ? 0 : 1;
			}
			if( reading == null ) {
				reading = new Thread(() -> {
					int b;
					try {
						b = raf.read();
					} catch (IOException e) {
						b = -1;
					}
					synchronized (FifoInput.this) {
						ahead = b;
						reading = null;
						FifoInput.this.notifyAll();
					}
				}, "named pipe read-ahead");
				reading.setDaemon(true);
				reading.start();
			}
			return 0;
		}

		@Override
		public synchronized int read() throws IOException {
			while( reading != null ) {
				try {
					wait();
				} catch (InterruptedException e) {
					throw new java.io.InterruptedIOException();
				}
			}
			if( ahead != -2 ) {
				int b = ahead;
				ahead = -2;
				return b;
			}
			return raf.read();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if( len == 0 ) {
				return 0;
			}
			int first = read();
			if( first < 0 ) {
				return -1;
			}
			b[off] = (byte) first;
			return 1;
		}

		@Override
		public void close() throws IOException {
			raf.close();
		}
	}
}
