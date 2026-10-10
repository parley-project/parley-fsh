package us.bringardner.fsh;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.IntConsumer;

import us.bringardner.fsh.exec.NativeProcess;

/**
 * A process that stands in for a subshell (fshexec -s). fsh's subshells run in its own process,
 * but in bash each is a process of its own: this one's process id is the subshell's ($BASHPID,
 * $!), so kill, ps and wait see it, and the signals it gets are the subshell's. Where the library
 * can, it also starts the subshell's programs (see NativeProcess.Spawner), so they are its
 * children as in bash ($PPID, ps): it is then given a socket as its input and output (and has no
 * other descriptors, as bash's subshell has only its own, for lsof).
 */
public final class StandIn implements NativeProcess.Spawner {

	/** the stand-ins running, by process id */
	private static final Map<Long,StandIn> running = new ConcurrentHashMap<>();

	private static final int EPIPE = 32;

	private final Process process;
	private final IntConsumer signals;
	private volatile boolean ended;
	/** its input, where there is no socket: the status that ends it */
	private final OutputStream status;
	/** the socket to it, -1 if none (or no more) */
	private int sock = -1;
	/** for one message at a time on the socket */
	private final Object sending = new Object();
	/** the answer to the request being made: a process id or -errno */
	private volatile CompletableFuture<Integer> answer;
	/** the program being started */
	private volatile NativeProcess starting;
	/** the programs it started that have not ended, by process id */
	private final Map<Integer,NativeProcess> children = new ConcurrentHashMap<>();

	private StandIn(Process process, IntConsumer signals, int sock) {
		this.process = process;
		this.signals = signals;
		this.sock = sock;
		this.status = sock >= 0 ? null : process.getOutputStream();
		Thread reader = new Thread(sock >= 0 ? this::readSocket : this::readSignals, "stand-in "+process.pid());
		reader.setDaemon(true);
		reader.start();
	}

	/** the signals it gets, a byte each (without a socket) */
	private void readSignals() {
		try (InputStream in = process.getInputStream()) {
			int sig;
			while( (sig = in.read()) >= 0 ) {
				signals.accept(sig);
			}
		} catch (IOException e) {
		}
		gone();
	}

	/**
	 * What it says on the socket (a kind, then two numbers): 'G' a signal it got; 'P' the program
	 * asked for started (its process id or -errno); 'X' one ended (its process id, its wait status).
	 */
	private void readSocket() {
		DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(
				NativeKeyboard.inputStream(sock), 256));
		try {
			int kind;
			while( (kind = in.read()) >= 0 ) {
				int a = in.readInt();
				int b = in.readInt();
				if( kind == 'G' ) {
					signals.accept(a);
				} else if( kind == 'P' ) {
					NativeProcess p = starting;
					if( a > 0 && p != null ) {
						children.put(a, p);
					}
					CompletableFuture<Integer> f = answer;
					if( f != null ) {
						f.complete(a);
					}
				} else if( kind == 'X' ) {
					NativeProcess p = children.remove(a);
					if( p != null ) {
						p.exited(b);
					}
				}
			}
		} catch (IOException e) {
		}
		CompletableFuture<Integer> f = answer;
		if( f != null ) {
			f.complete(-EPIPE);
		}
		synchronized (sending) {
			// (not while a message may be sent on it)
			sock = -1;
			try {
				in.close();
			} catch (IOException e) {
			}
		}
		// its programs that are still running: no longer its children (their status is not known)
		for(NativeProcess p : children.values()) {
			ProcessHandle.of(p.pid()).ifPresentOrElse(h -> h.onExit().thenRun(() -> p.exited(0)), () -> p.exited(0));
		}
		children.clear();
		gone();
	}

	private void gone() {
		if( !ended ) {
			// gone before the subshell ended: SIGKILL (the one it cannot pass on)
			signals.accept(9);
		}
	}

	/**
	 * A stand-in whose signals go to signals (on a thread of its own), or null where there is no
	 * helper.
	 */
	public static StandIn start(IntConsumer signals) {
		return start(signals, false);
	}

	/** start, the stand-in leading a process group of its own (a job's, with job control) when group */
	public static StandIn start(IntConsumer signals, boolean group) {
		String helper = NativeKeyboard.helper();
		if( helper == null ) {
			return null;
		}
		int [] pair = null;
		try {
			ProcessBuilder builder = group ? new ProcessBuilder(helper, "-s", "-g") : new ProcessBuilder(helper, "-s");
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process p;
			if( NativeProcess.usable() && (pair = NativeKeyboard.socketPair()) != null ) {
				// (the socket is its input and output)
				builder.redirectInput(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.INHERIT);
				p = NativeProcess.start(builder, new int[] {0, pair[1], 1, pair[1]});
				NativeKeyboard.close(pair[1]);
			} else {
				p = NativeProcess.start(builder);
			}
			StandIn ret = new StandIn(p, signals, pair == null ? -1 : pair[0]);
			running.put(ret.pid(), ret);
			return ret;
		} catch (IOException e) {
			if( pair != null ) {
				NativeKeyboard.close(pair[0]);
				NativeKeyboard.close(pair[1]);
			}
			return null;
		}
	}

	/** it starts programs (and is running) */
	public boolean startsPrograms() {
		return sock >= 0 && !ended;
	}

	/** start a program of the subshell as its child (by the shell if it is gone) */
	@Override
	public int spawn(byte [] path, byte [][] argv, byte [][] envp, byte [] dir, int [] fds, NativeProcess p) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write('S');
		field(out, path);
		field(out, dir == null ? new byte[0] : dir);
		field(out, "-1");
		int [] mine = new int[fds.length/2];
		field(out, String.valueOf(mine.length));
		for(int i = 0; i < mine.length; i++) {
			field(out, String.valueOf(fds[2*i]));
			mine[i] = fds[2*i+1];
		}
		field(out, String.valueOf(argv.length));
		for(byte [] a : argv) {
			field(out, a);
		}
		field(out, String.valueOf(envp.length));
		for(byte [] e : envp) {
			field(out, e);
		}
		synchronized (sending) {
			if( sock >= 0 ) {
				CompletableFuture<Integer> a = new CompletableFuture<>();
				answer = a;
				starting = p;
				int ret = NativeKeyboard.sendWithDescriptors(sock, out.toByteArray(), mine);
				if( ret == 0 ) {
					while( true ) {
						try {
							ret = a.get();
							break;
						} catch (InterruptedException e) {
							// (it answers at once)
						} catch (ExecutionException e) {
							ret = -EPIPE;
							break;
						}
					}
				}
				answer = null;
				starting = null;
				if( ret != -EPIPE ) {
					return ret;
				}
			}
		}
		// (it is gone)
		return NativeProcess.DIRECT.spawn(path, argv, envp, dir, fds, p);
	}

	private static void field(ByteArrayOutputStream out, String s) {
		field(out, s.getBytes(StandardCharsets.UTF_8));
	}

	private static void field(ByteArrayOutputStream out, byte [] b) {
		out.write(b, 0, b.length);
		out.write(0);
	}

	/** the stand-in with process id pid, or null */
	public static StandIn of(long pid) {
		return running.get(pid);
	}

	public long pid() {
		return process.pid();
	}

	/** the signal for the subshell from fsh itself (kill $!): given to it directly */
	public void deliver(int sig) {
		signals.accept(sig);
	}

	/** the subshell ended with status: so does this */
	public void end(int exitStatus) {
		if( ended ) {
			return;
		}
		ended = true;
		running.remove(pid());
		boolean told = false;
		if( status != null ) {
			try {
				status.write((exitStatus+"\n").getBytes());
				status.close();
				told = true;
			} catch (IOException e) {
			}
		} else {
			synchronized (sending) {
				told = sock >= 0 && NativeKeyboard.sendWithDescriptors(sock, ("E"+exitStatus).getBytes(), null) == 0;
			}
		}
		if( !told ) {
			process.destroy();
		}
		// (reaped when it is gone)
		process.onExit();
	}

	public boolean isEnded() {
		return ended;
	}
}
