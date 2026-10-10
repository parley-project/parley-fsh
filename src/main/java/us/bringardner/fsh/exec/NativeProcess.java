package us.bringardner.fsh.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import us.bringardner.fsh.NativeKeyboard;

/**
 * A program started with posix_spawn by the shell's library, as a ProcessBuilder would start it
 * (its command, environment, directory and redirects). Java starts each program through
 * jspawnhelper, a program more to start every time; this is about as quick as bash's fork and
 * exec. Where the library cannot (no library, Windows), the ProcessBuilder starts it.
 */
public final class NativeProcess extends Process {

	private static volatile Boolean usable;

	/** programs are started by the library (unless the fsh.spawn property is "java") */
	public static boolean usable() {
		Boolean u = usable;
		if( u == null ) {
			// (Unix descriptors, process groups and paths: Windows starts programs with ProcessBuilder)
			u = NativeKeyboard.canSpawn() && !"java".equals(System.getProperty("fsh.spawn"));
			usable = u;
		}
		return u;
	}

	/** builder's program started: by the library if it can, else by the builder */
	public static Process start(ProcessBuilder builder) throws IOException {
		return usable() ? new NativeProcess(builder, DIRECT, null) : builder.start();
	}

	/** start, by spawner (a subshell's stand-in, whose child it is) */
	public static Process start(ProcessBuilder builder, Spawner spawner) throws IOException {
		return usable() ? new NativeProcess(builder, spawner, null) : builder.start();
	}

	/** start (by the library), with the shell's descriptor extra[i+1] as its extra[i] too */
	public static Process start(ProcessBuilder builder, int [] extra) throws IOException {
		return new NativeProcess(builder, DIRECT, extra);
	}

	/** what starts a program, and tells it when it ends (see exited) */
	public interface Spawner {
		/**
		 * Start path (see NativeKeyboard.spawn0) for process.
		 * @return its process id, or -errno
		 */
		int spawn(byte [] path, byte [][] argv, byte [][] envp, byte [] dir, int [] fds, NativeProcess process);
	}

	/** the shell starts it and waits for it itself */
	public static final Spawner DIRECT = (path, argv, envp, dir, fds, process) -> {
		int pid = NativeKeyboard.spawn(path, argv, envp, dir, fds, -1, false);
		waitFor(pid, process);
		return pid;
	};

	private static void waitFor(int pid, NativeProcess process) {
		if( pid > 0 ) {
			Thread.ofPlatform().daemon().name("fsh wait "+pid).stackSize(64*1024).start(() -> process.exited(NativeKeyboard.waitExit(pid)));
		}
	}

	/**
	 * start, in process group group (0: a new one, its own; it is new too if that one is gone), as
	 * bash runs a job's programs; foreground: the group gets the terminal before it runs (so that
	 * reading it does not stop it).
	 */
	public static Process startInGroup(ProcessBuilder builder, long group, boolean foreground) throws IOException {
		return new NativeProcess(builder, (path, argv, envp, dir, fds, process) -> {
			int pid = NativeKeyboard.spawn(path, argv, envp, dir, fds, (int) group, foreground && group == 0);
			process.group = group == 0 ? pid : group;
			if( pid == -1 || pid == -3 ) {
				// (EPERM, ESRCH: its programs have all ended)
				pid = NativeKeyboard.spawn(path, argv, envp, dir, fds, 0, foreground);
				process.group = pid;
				if( foreground && pid > 0 ) {
					NativeKeyboard.ttyTo(pid);
					NativeKeyboard.kill(pid, CONT);
				}
			} else if( foreground && pid > 0 ) {
				NativeKeyboard.ttyTo(group == 0 ? pid : (int) group);
				if( group == 0 ) {
					NativeKeyboard.kill(pid, CONT);
				}
			}
			waitFor(pid, process);
			return pid;
		}, null);
	}

	private static final int CONT = Math.max(1, us.bringardner.fsh.job.ProcessSignals.number("CONT"));

	private final int pid;
	private final OutputStream stdin;
	private final InputStream stdout;
	private final InputStream stderr;
	private final CompletableFuture<Process> exit = new CompletableFuture<>();
	/** the process group it was started in (see startInGroup), else 0 */
	private long group;

	/** the process group it was started in (see startInGroup), else 0 */
	public long group() {
		return group;
	}

	/** its wait status once it has ended (-1: not known) */
	private volatile int waitStatus = -1;

	/** the signal that ended it (0: it exited, or it is not known) */
	public int termSignal() {
		int s = waitStatus;
		int sig = s & 0x7f;
		return s < 0 || sig == 0x7f ? 0 : sig;
	}

	/** a signal that ended it dumped core */
	public boolean coreDumped() {
		int s = waitStatus;
		return s >= 0 && termSignal() != 0 && (s & 0x80) != 0;
	}

	/** its exit value once it has ended */
	private volatile int exitValue;

	private NativeProcess(ProcessBuilder builder, Spawner spawner, int [] extra) throws IOException {
		List<String> command = builder.command();
		if( command.isEmpty()) {
			throw new IndexOutOfBoundsException();
		}
		String program = command.get(0);
		File dir = builder.directory();
		String path = program.contains("/") ? program : onPath(program);

		// the program's descriptors 0-2 (pairs of its number and the shell's), and those of the
		// shell's to close once it has started; parent: the shell's ends of its pipes
		List<Integer> close = new ArrayList<>();
		int [] parent = {-1, -1, -1};
		int [] fds = new int[6+(extra == null ? 0 : extra.length)];
		if( extra != null ) {
			System.arraycopy(extra, 0, fds, 6, extra.length);
		}
		try {
			Redirect in = builder.redirectInput();
			fds[0] = 0;
			fds[1] = descriptor(in, 0, close, parent);
			Redirect out = builder.redirectOutput();
			fds[2] = 1;
			fds[3] = descriptor(out, 1, close, parent);
			fds[4] = 2;
			fds[5] = builder.redirectErrorStream() ? fds[3] : descriptor(builder.redirectError(), 2, close, parent);

			byte [][] argv = new byte[command.size()][];
			for(int i = 0; i < argv.length; i++) {
				argv[i] = bytes(command.get(i));
			}
			Map<String,String> environment = builder.environment();
			byte [][] envp = new byte[environment.size()][];
			int i = 0;
			for(Map.Entry<String,String> e : environment.entrySet()) {
				envp[i++] = bytes(e.getKey()+"="+e.getValue());
			}
			int ret = path == null ? -2 : spawner.spawn(bytes(path), argv, envp, dir == null ? null : bytes(dir.getPath()), fds, this);
			if( ret < 0 ) {
				// (as the ProcessBuilder says it: error=2 is "command not found")
				throw new IOException("Cannot run program \""+program+"\""+(dir == null ? "" : " (in directory \""+dir+"\")")
						+": error="+(-ret)+", "+NativeKeyboard.strerror(-ret));
			}
			pid = ret;
		} catch (IOException | RuntimeException e) {
			for(int fd : parent) {
				if( fd >= 0 ) {
					NativeKeyboard.close(fd);
				}
			}
			throw e;
		} finally {
			for(int fd : close) {
				NativeKeyboard.close(fd);
			}
		}
		stdin = parent[0] < 0 ? OutputStream.nullOutputStream() : NativeKeyboard.outputStream(parent[0]);
		stdout = parent[1] < 0 ? InputStream.nullInputStream() : NativeKeyboard.inputStream(parent[1]);
		stderr = parent[2] < 0 ? InputStream.nullInputStream() : NativeKeyboard.inputStream(parent[2]);
	}

	/** it ended with wait status status (-1: not known) */
	public void exited(int status) {
		waitStatus = status;
		int signal = status & 0x7f;
		// (as Java's: 128 + the signal that ended it)
		exitValue = status < 0 ? 127 : signal == 0 ? (status >> 8) & 0xff : 0x80+signal;
		exit.complete(this);
	}

	/**
	 * The shell's descriptor for the program's descriptor n as redirect says; for a pipe, the
	 * shell's end is in parent[n]. Those it opens for the program are added to close.
	 */
	private static int descriptor(Redirect redirect, int n, List<Integer> close, int [] parent) throws IOException {
		switch (redirect.type()) {
		case INHERIT:
			return n;
		case PIPE: {
			int [] p = NativeKeyboard.pipe();
			if( p == null ) {
				throw new IOException("pipe failed");
			}
			int mine = n == 0 ? p[0] : p[1];
			parent[n] = n == 0 ? p[1] : p[0];
			close.add(mine);
			return mine;
		}
		default: {
			File f = redirect.file();
			int fd = NativeKeyboard.open(bytes(f.getPath()), switch (redirect.type()) {
			case READ -> 'r';
			case APPEND -> 'a';
			default -> 'w';
			});
			if( fd < 0 ) {
				throw new IOException(f+": "+NativeKeyboard.strerror(-fd));
			}
			close.add(fd);
			return fd;
		}
		}
	}

	/** name in the JVM's PATH, as the ProcessBuilder finds it; null if it is not there */
	private static String onPath(String name) {
		String path = System.getenv("PATH");
		for(String dir : (path == null ? "/bin:/usr/bin" : path).split(":", -1)) {
			File f = new File(dir.isEmpty() ? "." : dir, name);
			if( f.isFile() && f.canExecute()) {
				return f.getPath();
			}
		}
		return null;
	}

	/** (as the shell has them: bytes that are not UTF-8 are as they were, see RawUtf8) */
	private static byte [] bytes(String s) {
		return s.getBytes(us.bringardner.fsh.RawUtf8.CHARSET);
	}

	@Override
	public OutputStream getOutputStream() {
		return stdin;
	}

	@Override
	public InputStream getInputStream() {
		return stdout;
	}

	@Override
	public InputStream getErrorStream() {
		return stderr;
	}

	@Override
	public int waitFor() throws InterruptedException {
		try {
			exit.get();
		} catch (java.util.concurrent.ExecutionException e) {
			throw new IllegalStateException(e);
		}
		return exitValue;
	}

	@Override
	public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
		try {
			exit.get(timeout, unit);
			return true;
		} catch (java.util.concurrent.TimeoutException e) {
			return false;
		} catch (java.util.concurrent.ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public int exitValue() {
		if( !exit.isDone()) {
			throw new IllegalThreadStateException("process hasn't exited");
		}
		return exitValue;
	}

	@Override
	public boolean isAlive() {
		return !exit.isDone();
	}

	@Override
	public long pid() {
		return pid;
	}

	@Override
	public void destroy() {
		if( isAlive()) {
			NativeKeyboard.kill(pid, 15);
		}
	}

	@Override
	public Process destroyForcibly() {
		if( isAlive()) {
			NativeKeyboard.kill(pid, 9);
		}
		return this;
	}

	@Override
	public boolean supportsNormalTermination() {
		return true;
	}

	@Override
	public CompletableFuture<Process> onExit() {
		return exit.copy();
	}

	@Override
	public ProcessHandle toHandle() {
		return ProcessHandle.of(pid).orElseThrow(() -> new IllegalStateException("process "+pid+" has ended"));
	}

	@Override
	public Stream<ProcessHandle> children() {
		return isAlive() ? ProcessHandle.of(pid).map(ProcessHandle::children).orElse(Stream.empty()) : Stream.empty();
	}

	@Override
	public Stream<ProcessHandle> descendants() {
		return isAlive() ? ProcessHandle.of(pid).map(ProcessHandle::descendants).orElse(Stream.empty()) : Stream.empty();
	}

	@Override
	public String toString() {
		return "NativeProcess[pid="+pid+", exitValue="+(isAlive() ? "\"not exited\"" : exitValue)+"]";
	}
}
