package us.bringardner.fsh.exec;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import us.bringardner.fsh.Console.CommandThread;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellTask;
import us.bringardner.fsh.signal.ExitException;
import us.bringardner.fsh.syntax.Ast;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * <(cmd) and >(cmd), as bash runs them: cmd runs at the same time as the command that uses it,
 * on a named pipe (mkfifo) whose path is the word's value. So while read l; do ...; done <
 * <(tail -f log) reads lines as they come. Where there is no mkfifo (Windows), <(cmd) runs to
 * the end into a temporary file first and >(cmd) reads one after the command.
 */
final class ProcessSubstitutions {

	private ProcessSubstitutions() {
	}

	private static final AtomicInteger count = new AtomicInteger();
	private static volatile Boolean haveMkfifo;

	/** how long a command waits for its >(cmd) to finish before it goes on */
	private static final long OUTPUT_WAIT_MILLIS = 10_000;

	/**
	 * Start seq (the commands of <( ) or >( ), which ex read) and return the path the command
	 * reads (<) or writes (>). What is left to do when the command is done is added to
	 * sc.afterCommand.
	 */
	static String start(char direction, Ast.Sequence seq, Executor ex, String text, ShellContext sc) throws IOException {
		File fifo = makeFifo();
		if( fifo == null ) {
			return withFile(direction, seq, ex, text, sc);
		}
		// a stage of its own, as in a pipeline: it runs at the same time as this shell
		ShellContext ctx = sc.isolatedSubShell();
		if( direction == '>' ) {
			// cmd writes where the shell writes; the thread must not close that when it ends
			ctx.stdout = new KeptOpen(sc.stdout);
		}
		AtomicBoolean opened = new AtomicBoolean();
		CommandThread thread = new CommandThread(ctx, new ShellTask() {
			@Override
			public int run(ShellContext c) throws IOException {
				if( direction == '<' ) {
					// (opening blocks until the command opens the other end)
					try (OutputStream out = new FileOutputStream(fifo)) {
						opened.set(true);
						c.stdout = new PrintStream(new PipeOutput(out, c), true);
						return ex.list(seq, c);
					}
				}
				try (InputStream in = new FileInputStream(fifo)) {
					opened.set(true);
					c.stdin = in;
					return ex.list(seq, c);
				}
			}

			@Override
			public String text() {
				return direction+"("+text+")";
			}
		});
		thread.setDaemon(true);
		thread.start();
		sc.afterCommand.add(() -> finish(direction, fifo, thread, opened));
		return fifo.getAbsolutePath();
	}

	/**
	 * The command is done. If it never opened the pipe, open the other end so cmd is not left
	 * waiting. Wait for >(cmd) to read the rest, so what it writes comes before what follows
	 * (bash does not wait, but it is quick). The pipe's name is removed.
	 */
	private static void finish(char direction, File fifo, CommandThread thread, AtomicBoolean opened) {
		if( !opened.get()) {
			Thread unblock = new Thread(() -> {
				try (Closeable c = direction == '<' ? new FileInputStream(fifo) : new FileOutputStream(fifo)) {
					// opened and closed: cmd sees the end
				} catch (IOException e) {
				}
			}, "process substitution end");
			unblock.setDaemon(true);
			unblock.start();
		}
		if( direction == '>' ) {
			long until = System.currentTimeMillis()+OUTPUT_WAIT_MILLIS;
			while( thread.isAlive() && System.currentTimeMillis() < until ) {
				try {
					thread.join(50);
				} catch (InterruptedException e) {
				}
			}
		}
		fifo.delete();
	}

	/**
	 * Writes to the pipe of <(cmd). When the reader is gone (it read what it wanted and
	 * closed), cmd stops, as a program does on SIGPIPE: status 141.
	 */
	private static final class PipeOutput extends OutputStream {
		private final OutputStream out;
		private final ShellContext ctx;

		PipeOutput(OutputStream out, ShellContext ctx) {
			this.out = out;
			this.ctx = ctx;
		}

		@Override
		public void write(int b) throws IOException {
			try {
				out.write(b);
			} catch (IOException e) {
				broken();
				throw e;
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			try {
				out.write(b, off, len);
			} catch (IOException e) {
				broken();
				throw e;
			}
		}

		@Override
		public void flush() throws IOException {
			try {
				out.flush();
			} catch (IOException e) {
				broken();
				throw e;
			}
		}

		private void broken() {
			if( ctx.getException() == null ) {
				ctx.setExecption(new ExitException(ctx, 141));
			}
		}
	}

	/** a new named pipe in the temporary directory, or null if there is no mkfifo */
	static File makeFifo() {
		if( Boolean.FALSE.equals(haveMkfifo) || FileSourceFactory.isWindows()) {
			return null;
		}
		File fifo = new File(System.getProperty("java.io.tmpdir"), "fsh-"+ProcessHandle.current().pid()+"-"+count.incrementAndGet()+".fifo");
		try {
			Process p = new ProcessBuilder("mkfifo", fifo.getAbsolutePath()).redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			if( p.waitFor() == 0 ) {
				haveMkfifo = true;
				fifo.deleteOnExit();
				return fifo;
			}
		} catch (IOException e) {
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		haveMkfifo = false;
		return null;
	}

	/** without a named pipe: <(cmd) runs first into a file; >(cmd) reads the file afterward */
	private static String withFile(char direction, Ast.Sequence seq, Executor ex, String text, ShellContext sc) throws IOException {
		File file = File.createTempFile("fsh-", ".fifo");
		file.deleteOnExit();
		if( direction == '>' ) {
			sc.afterCommand.add(() -> Executor.outputSubstitution(text, file.getAbsolutePath(), sc));
			return file.getAbsolutePath();
		}
		java.nio.file.Files.writeString(file.toPath(), ex.capture(seq, sc));
		return file.getAbsolutePath();
	}
}
