package us.bringardner.fsh.exec;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.NativeKeyboard;
import us.bringardner.fsh.ShellContext;

/**
 * The shell's descriptors above 2 for a program (exec 3>file; prog, prog 3>&1), which Java does
 * not give it: the helper's -f options make them, as copies of the program's 0, 1 and 2 or of each
 * other, or as named pipes to and from the shell's streams.
 */
final class ProgramDescriptors {

	/** the helper's options (-f N how arg ...) */
	final List<String> options = new ArrayList<>();
	private final List<OutputFeeder> outputs = new ArrayList<>();
	private final List<StdinFeeder> inputs = new ArrayList<>();

	/**
	 * The shell's descriptors above 2 for a program whose 0, 1 and 2 are stdin, stdout and stderr;
	 * none where there is no helper.
	 */
	static ProgramDescriptors of(ShellContext ctx, InputStream stdin, PrintStream stdout, PrintStream stderr) {
		ProgramDescriptors ret = new ProgramDescriptors();
		if( NativeKeyboard.helper() == null ) {
			return ret;
		}
		Map<Integer,Console.FileDiscriptor> files = ctx.console.getFiles();
		List<Integer> fds;
		synchronized (files) {
			fds = new ArrayList<>(files.keySet());
		}
		// (each stream is made once; another descriptor with it is a copy)
		Map<Object,Integer> made = new java.util.IdentityHashMap<>();
		made.put(stream(stdin), 0);
		made.putIfAbsent(stream(stdout), 1);
		made.putIfAbsent(stream(stderr), 2);
		for(int fd : fds) {
			Console.FileDiscriptor f = files.get(fd);
			if( fd < 3 || f == null || ctx.console.coprocFds.containsKey(fd)) {
				// (a coproc's descriptors are the shell's only, as in bash)
				continue;
			}
			if( f.getIn() != null && f.getOut() != null ) {
				// (read-write, n<>file: not through a pipe)
				continue;
			}
			Object s = f.getOut() != null ? stream(f.getOut()) : stream(f.getIn());
			Integer copy = made.get(s);
			if( copy != null ) {
				ret.add(fd, "d", String.valueOf(copy));
			} else if( f.getOut() != null ) {
				f.getOut().flush();
				OutputFeeder feeder = OutputFeeder.of(f.getOut());
				if( feeder != null ) {
					ret.outputs.add(feeder);
					ret.add(fd, "w", feeder.fifo.getAbsolutePath());
					made.put(s, fd);
				}
			} else {
				StdinFeeder feeder = StdinFeeder.of(f.getIn());
				if( feeder != null ) {
					ret.inputs.add(feeder);
					ret.add(fd, "r", feeder.fifo.getAbsolutePath());
					made.put(s, fd);
				}
			}
		}
		return ret;
	}

	private void add(int fd, String how, String arg) {
		options.add("-f");
		options.add(String.valueOf(fd));
		options.add(how);
		options.add(arg);
	}

	/** the stream itself, under what keeps it open */
	private static Object stream(Object s) {
		return s instanceof OutputStream o ? KeptOpen.unwrap(o) : s;
	}

	/** the program has started */
	void start() {
		outputs.forEach(Thread::start);
		inputs.forEach(Thread::start);
	}

	/** the program has ended (or did not start) */
	void finish() {
		for(OutputFeeder f : outputs) {
			if( f.isAlive() || f.getState() == Thread.State.NEW ) {
				if( f.getState() == Thread.State.NEW ) {
					f.start();
				}
				f.finish();
			}
		}
		inputs.forEach(StdinFeeder::finish);
		outputs.clear();
		inputs.clear();
	}
}
