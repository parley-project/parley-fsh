package us.bringardner.fsh.exec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import us.bringardner.fsh.Argument;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.InteractiveInput;
import us.bringardner.fsh.NativeKeyboard;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.core.BaseThread;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.fileproxy.FileProxy;

/**
 * Programs: a command that is not a function or a builtin, found by its path or on $PATH.
 */
public final class Programs {

	private Programs() {
	}

	/** a program file that exists but may not be run (status 126) */
	static final class NotExecutable extends IOException {
		private static final long serialVersionUID = 1L;

		NotExecutable(String name) {
			super(name+": Permission denied");
		}
	}

	/** run name with args; status 127 (and bash's message) if there is no such program */
	/** a child of the shell itself ended: its SIGCHLD trap runs after this command */
	static void childEnded(ShellContext ctx) {
		if( !ctx.isIsolated() && !Console.IN_COMMAND_THREAD.get()) {
			int chld = us.bringardner.fsh.job.ProcessSignals.number("CHLD");
			if( chld > 0 && ctx.console.hasTrap(chld)) {
				ctx.console.queueSignal(chld);
			}
		}
	}

	static int run(String name, List<String> args, ShellContext sc) throws IOException {
		FileSource exec = hashed(name, sc);
		if( exec == null && !name.contains("/")) {
			// fsh runs a program in the current directory without ./ (bash does not)
			FileSource here = sc.getFileSource(name);
			if( here.exists() && here.isFile()) {
				exec = here;
			}
		}
		if( exec != null ) {
			if( !exec.exists()) {
				// a remembered path (hash -p) that is not there
				sc.error(exec.getAbsolutePath()+": No such file or directory");
				return 127;
			}
			if( !exec.canExecute()) {
				throw new NotExecutable(name);
			}
			if( isFshScript(exec)) {
				Argument [] all = new Argument[args.size()+1];
				// ($0: the name it was run by, as bash)
				all[0] = new Argument(name);
				for (int i = 0; i < args.size(); i++) {
					all[i+1] = new Argument(args.get(i));
				}
				return sc.executeSubShell(exec, all);
			}
			List<String> cmd = new ArrayList<>();
			cmd.add(exec.getAbsolutePath());
			cmd.addAll(args);
			return execute(cmd, sc);
		}
		if( name.contains("/")) {
			sc.error(name+": No such file or directory");
			return 127;
		}

		List<String> cmd = new ArrayList<>();
		cmd.add(name);
		cmd.addAll(args);
		if( FileSourceFactory.isWindows()) {
			// cmd /r name ... (dir, type and the like are cmd's own)
			FileSource shell = which("cmd", sc);
			cmd.add(0, "/r");
			cmd.add(0, shell != null ? shell.getAbsolutePath() : "cmd");
		}
		return execute(cmd, sc);
	}

	/** a program's (not text): ELF, Mach-O, a NUL in its first bytes */
	private static boolean binary(byte [] data) {
		if( data.length >= 4 ) {
			int magic = (data[0] & 0xff) << 24 | (data[1] & 0xff) << 16 | (data[2] & 0xff) << 8 | (data[3] & 0xff);
			if( magic == 0x7f454c46 || magic == 0xfeedface || magic == 0xfeedfacf || magic == 0xcefaedfe
					|| magic == 0xcffaedfe || magic == 0xcafebabe || magic == 0xbebafeca ) {
				return true;
			}
		}
		for(byte b : data) {
			if( b == 0 ) {
				return true;
			}
		}
		return false;
	}

	/** #!fsh (or #!fssh, the name before): a script for this shell */
	private static boolean isFshScript(FileSource exec) throws IOException {
		byte [] data = exec.head(80);
		if( data.length < 2 || data[0] != '#' || data[1] != '!' ) {
			// no #!: a script for the shell that runs it (bash runs it itself), unless it is a program
			return !binary(data);
		}
		String first = new String(data).substring(2);
		int nl = first.indexOf('\n');
		if( nl < 0 ) {
			return false;
		}
		first = first.substring(0, nl).trim();
		return first.equals("fsh") || first.equals("fssh");
	}

	public static class StreamCopier extends BaseThread{
		InputStream in;
		OutputStream out;
		Throwable error;
		byte [] buffer = new byte[1024*10];
		ShellContext ctx;
		private final boolean closeIn;
		private final boolean closeOut;

		public StreamCopier(ShellContext ctx, InputStream in, OutputStream out,String name) {
			this(ctx, in, out, name, in != Console.System_in, true);
		}

		/**
		 * @param closeIn close the input when done. False when the input belongs to the shell.
		 * @param closeOut close the output when done (it is only flushed otherwise). False when the output belongs to the shell.
		 */
		public StreamCopier(ShellContext ctx, InputStream in, OutputStream out,String name, boolean closeIn, boolean closeOut) {
			this.ctx = ctx;
			this.in = in;
			this.out = out;
			this.closeIn = closeIn;
			this.closeOut = closeOut;
			setName(name);
		}

		@Override
		public void run() {
			started = running = true;
			try {
				int cnt = 0;
				if (in instanceof InteractiveInput) {
					// never block on typed input, so nothing is taken after the consumer is gone
					while(!stopping) {
						int avail = in.available();
						if( avail > 0 ) {
							cnt = in.read(buffer, 0, Math.min(avail, buffer.length));
							if( cnt < 0 ) {
								break;
							}
							out.write(buffer, 0, cnt);
							out.flush();
						} else {
							Thread.sleep(10);
						}
					}
				} else {
					while(!stopping && (cnt = in.read(buffer))>=0) {
						if( cnt > 0 ) {
							out.write(buffer, 0, cnt);
							out.flush();
						}
					}
				}
			} catch (InterruptedException e) {
				// stopped
			} catch (Throwable e) {
				error = e;
			} finally {
				if( closeIn ) {
					try {
						in.close();
					} catch (Exception e2) {}
				}
				try {
					if( closeOut ) {
						out.close();
					} else {
						out.flush();
					}
				} catch (Exception e2) {}
			}

			running = false;
		}
	}

	public static class NativeStreamCopier extends BaseThread{
		NativeKeyboard in;
		OutputStream out;
		Throwable error;
		byte [] buffer = new byte[1024*10];
		private boolean echo;
		ShellContext ctx;

		public NativeStreamCopier(ShellContext ctx, NativeKeyboard in, OutputStream out,String name, boolean echo) {
			this.ctx = ctx;
			this.in = in;
			this.out = out;
			this.echo = echo;
			setName(name);
		}

		@Override
		public void run() {
			started = running = true;
			try {
				boolean isSystemOut = (out == Console.System_out) ;

				while(running && !stopping ) {
					// only read what was already typed, so nothing is taken after the process ends;
					// a stopped or background job does not read the keyboard
					int key = ctx.console.readsKeyboard(ctx) ? in.readTyped() : NativeKeyboard.KEY_NONE;
					if( key == NativeKeyboard.KEY_NONE ) {
						Thread.sleep(10);
						continue;
					}
					if( key < 0 || key == 4 ) {
						// end of input or Ctrl-D: the finally block closes the process's stdin
						break;
					}
					out.write(key);
					out.flush();
					// echo key to stdout
					if(!isSystemOut && echo ) {
						System.out.print((char)key);
					}
				}
			} catch (Throwable e) {
				error = e;
				stop();
			} finally {
				try {
					if( in == Console.System_in) {
						//Console.debugFrame.append("Can't close stdin. "+getName()+"\n");
					} else {
						//Console.debugFrame.append("Closing 01 in."+getName()+"\n");
						in.close();
						//Console.debugFrame.append("Closing 02 in."+getName()+"\n");
						//thread.interrupt();
						//Console.debugFrame.append("Closing 03 in."+getName()+"\n");
					}
				} catch (Exception e2) {}
				try {
					Console.close(ctx.console,out);
				} catch (Exception e2) {}

			}

			running = false;

		}

		@Override
		public void stop() {
			super.stop();
			try {
				//boolean isStdin = in == Console.System_in;
				//thread.interrupt();

				//Console.debugFrame.append("Stop closed stdin. "+getName()+"\n");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}


	}

	public static class ExternalProcess extends BaseThread{

		List<String> cmd;
		ShellContext ctx;
		int exitCode;
		Throwable error;
		private Process process;

		public ExternalProcess(List<String> cmd, ShellContext ctx) {
			this.cmd  = cmd;
			this.ctx = ctx;	

		}

		@Override
		public void run() {
			running = started = true;
			BaseThread sc1 = null;
			StreamCopier sc2 = null;
			StreamCopier sc3 = null;

			Object terminalUser = null;
			StdinFeeder feeder = null;
			ReadBack onFile = null;
			java.io.File report = null;
			us.bringardner.fsh.job.IJob job = ctx.job;

			try {
				String name = cmd.get(0);
				List<String> program = cmd;
				if( ctx.programArgv0 != null && NativeKeyboard.helper() != null ) {
					// exec -a name: through the helper, which sets argv[0]
					program = new java.util.ArrayList<>(List.of(NativeKeyboard.helper(), "-0", ctx.programArgv0, "--"));
					program.addAll(cmd);
				}
				// with umask or ulimit set, through sh so the program gets them
				List<String> command = us.bringardner.fsh.commands.ProcessSettings.wrap(ctx, program);

				// as under bash, a program in the foreground uses the shell's terminal (and its
				// output and error files) itself, not through a pipe: vi, less and top work
				boolean tty = NativeKeyboard.terminal();
				boolean foreground = ctx.console.readsKeyboard(ctx);
				boolean inheritIn = tty && foreground && isKeyboard(ctx.stdin);
				boolean inheritOut = isShellStream(ctx.stdout, Console.System_out);
				boolean inheritErr = isShellStream(ctx.stderr, Console.System_err);
				// with job control, as bash: the job's programs are a process group of their own,
				// which has the terminal while the job is in the foreground
				boolean absolute = new java.io.File(cmd.get(0)).isAbsolute();
				boolean grouped = ctx.console.isInteractive && job != null && absolute && NativeKeyboard.groupHelper() != null;
				// a local file as input: the program reads it itself, from where the shell is, and the
				// shell goes on from where the program left it (head -n 1 seeks back after its line)
				onFile = !inheritIn && absolute && ctx.stdin instanceof ReadBack rb && rb.file() != null
						&& NativeKeyboard.helper() != null ? (ReadBack) ctx.stdin : null;
				if( grouped || onFile != null ) {
					List<String> run = new java.util.ArrayList<>();
					run.add(NativeKeyboard.helper());
					if( grouped ) {
						run.add("-g");
						// (the group is set when it starts)
						run.add("0");
						run.add(foreground ? "1" : "0");
					}
					if( onFile != null ) {
						report = java.io.File.createTempFile("fsh-", ".pos");
						run.add("-i");
						run.add(onFile.file().getAbsolutePath());
						run.add(String.valueOf(onFile.position()));
						run.add(report.getAbsolutePath());
					}
					run.add("--");
					run.addAll(command);
					command = run;
				} else if( tty && !foreground && ctx.console.isInteractive ) {
					// a job in the background: Ctrl-C and Ctrl-Z typed for the foreground are not for it
					command = ignoringTerminalSignals(command);
				}

				ProcessBuilder builder = new ProcessBuilder(command);
				// the shell's exported variables, not the JVM's (export X=1 and X=1 cmd reach the program)
				Map<String,String> env = builder.environment();
				env.clear();
				if( !ctx.programCleanEnvironment ) {
					env.putAll(ctx.console.programEnvironment(ctx));
				}

				FileSource dir = ctx.console.getCurrentDirectory();
				if (dir instanceof FileProxy) {
					FileProxy cwd = (FileProxy) dir;
					builder.directory(cwd.getTarget());
				}
				if( inheritIn ) {
					builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
				} else if( ctx.stdin instanceof us.bringardner.fsh.ProcessStdin ) {
					// the shell's own input (a pipe or a file): it reads it itself, as under bash
					builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
				} else if( onFile != null ) {
					// the helper opens it
				} else {
					// input the shell shares with it: what it does not read stays for the shell
					feeder = StdinFeeder.of(ctx.stdin);
					if( feeder != null ) {
						builder.redirectInput(feeder.fifo);
					}
				}
				if( inheritOut ) {
					ctx.stdout.flush();
					builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
				}
				if( inheritErr ) {
					ctx.stderr.flush();
					builder.redirectError(ProcessBuilder.Redirect.INHERIT);
				}
				if( tty && foreground && (inheritIn || inheritOut || inheritErr)) {
					terminalUser = new Object();
					if( job != null ) {
						job.addTerminalUser(terminalUser);
					}
					NativeKeyboard.lendTerminal(terminalUser);
				}
				long started = System.nanoTime();

				Process p;
				if( grouped ) {
					synchronized (job) {
						// the first program makes the group; the others of the job (a pipeline) join it
						List<String> args = new java.util.ArrayList<>(builder.command());
						args.set(2, String.valueOf(job.getProcessGroup()));
						builder.command(args);
						p = builder.start();
						long group = job.getProcessGroup();
						if( group == 0 ) {
							group = p.pid();
							job.setProcessGroup(group);
						}
						NativeKeyboard.processGroup(p.pid(), group);
					}
				} else {
					p = builder.start();
				}
				// stopped (Ctrl-Z), continued and signalled with its job
				if( job != null ) {
					job.addProcess(p);
				}
				process = p;
				if( inheritIn || ctx.stdin instanceof us.bringardner.fsh.ProcessStdin ) {
					// the program reads the shell's input itself
				} else if( feeder != null ) {
					feeder.start();
				} else if( onFile != null ) {
					// the helper gives it the file itself (nothing is copied to it: a write to the pipe
					// it does not read would end the shell with SIGPIPE once it is gone)
				} else if (ctx.stdin instanceof NativeKeyboard) {
					boolean echo = ctx.console.isOptionEnabled(Option.KeyboardEcho);
					sc1 = new NativeStreamCopier(ctx,(NativeKeyboard)ctx.stdin,p.getOutputStream(),name+" native",echo);
				} else {
					// stdin belongs to the shell (or to the redirect / pipe that opened it); only the process side is closed
					sc1 = new StreamCopier(ctx,ctx.stdin,p.getOutputStream(),name+" stdin",false,true);
				}

				// stdout and stderr belong to the shell; the copiers only flush them
				if( !inheritOut ) {
					sc2 = new StreamCopier(ctx,p.getInputStream(),ctx.stdout,name+" stdout",true,false);
				}
				if( !inheritErr ) {
					sc3 = new StreamCopier(ctx,p.getErrorStream(),ctx.stderr,name+" stderr",true,false);
				}

				if( sc1 != null ) {
					sc1.start();
				}
				if( sc2 != null ) {
					sc2.start();
				}
				if( sc3 != null ) {
					sc3.start();
				}

				boolean killed = false;
				while(!waitFor(p, grouped ? 50 : 100)) {
					if( !killed && ctx.getException() != null) {
						// the job was interrupted, terminated or killed
						killed = true;
						terminate(p);
					}
					int stop = grouped ? NativeKeyboard.stopSignal(p.pid()) : 0;
					if( stop != 0 && job.getState() == us.bringardner.fsh.job.JobState.Running ) {
						// Ctrl-Z (or reading the terminal in the background): the job stops, as in bash
						String how = switch (String.valueOf(us.bringardner.fsh.job.ProcessSignals.name(stop))) {
						case "TTIN" -> "Stopped (tty input)";
						case "TTOU" -> "Stopped (tty output)";
						case "STOP" -> "Stopped (signal)";
						default -> "Stopped";
						};
						if( terminalUser != null ) {
							// after the terminal's ^Z
							Console.System_out.println();
							Console.System_out.flush();
						}
						job.stopJob(how);
					}
				}
				exitCode = p.exitValue();
				childEnded(ctx);

				// the process has exited, wait for the rest of its output
				drain(sc2);
				drain(sc3);
				if( feeder != null ) {
					feeder.finish();
					feeder = null;
				}
				if( onFile != null ) {
					// where the program left the file
					String pos = java.nio.file.Files.readString(report.toPath()).trim();
					if( !pos.isEmpty()) {
						onFile.seek(Long.parseLong(pos));
					}
				}

				if( terminalUser != null && job != null && exitCode == 128+INT && (grouped || Console.interruptTypedSince(started))) {
					// Ctrl-C ended it: as bash, the rest of the command line does not run
					NativeKeyboard.reclaimTerminal(terminalUser);
					Console.System_out.println();
					Console.System_out.flush();
					job.signalJob(INT);
				}

			} catch (Throwable e) {
				exitCode = 1;
				error = e;
			} finally {
				if( report != null ) {
					report.delete();
				}
				if( feeder != null ) {
					// it did not start, or failed
					feeder.finish();
				}
				if( terminalUser != null ) {
					NativeKeyboard.reclaimTerminal(terminalUser);
					if( job != null ) {
						job.removeTerminalUser(terminalUser);
					}
				}
				if( process != null && job != null ) {
					job.removeProcess(process);
				}
				try {sc1.stop();}catch (Throwable e) {}
				try {sc2.stop();}catch (Throwable e) {}
				try {sc3.stop();}catch (Throwable e) {}
			}

			running = false;

		}

		private static final int INT = Math.max(2, us.bringardner.fsh.job.ProcessSignals.number("INT"));

		/** the shell's own keyboard */
		private static boolean isKeyboard(java.io.InputStream in) {
			return in instanceof NativeKeyboard || Console.isKeyboard(in);
		}

		/** the stream is the shell's own standard output (or error), not a pipe or a file it opened */
		private static boolean isShellStream(java.io.OutputStream out, java.io.PrintStream shells) {
			out = KeptOpen.unwrap(out);
			return out != null && (out == shells || out == System.out && shells == Console.System_out || out == System.err && shells == Console.System_err);
		}

		/** cmd with SIGINT, SIGQUIT and SIGTSTP ignored (as bash's other process groups do not get them) */
		private static List<String> ignoringTerminalSignals(List<String> cmd) {
			if( FileSourceFactory.isWindows()) {
				return cmd;
			}
			List<String> ret = new java.util.ArrayList<>();
			ret.add("/bin/sh");
			ret.add("-c");
			ret.add("trap '' INT QUIT TSTP; exec \"$@\"");
			ret.add("sh");
			ret.addAll(cmd);
			return ret;
		}

		private static boolean waitFor(Process p, long millis) {
			try {
				return p.waitFor(millis, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// requests to stop arrive through the ShellContext
				return !p.isAlive();
			}
		}

		private static void terminate(Process p) {
			p.descendants().forEach(ProcessHandle::destroy);
			p.destroy();
			if( !waitFor(p, 2000)) {
				p.descendants().forEach(ProcessHandle::destroyForcibly);
				p.destroyForcibly();
			}
		}

		/**
		 * Wait until a copier reaches the end of the process output, unless the job is being stopped.
		 */
		private void drain(StreamCopier copier) {
			while(copier != null && copier.isAlive() && ctx.getException() == null) {
				try {
					copier.join(100);
				} catch (InterruptedException e) {
				}
			}
		}

	}

	public static int execute(List<String> cmd, ShellContext ctx) throws IOException {
		int ret = 0;
		if(cmd!=null && cmd.size()>0) {
			ExternalProcess ep = new ExternalProcess(cmd, ctx);
			ep.setName(cmd.get(0));
			ep.start();

			// ExternalProcess ends the process itself when the job is stopped, so always wait for it
			while(ep.isAlive()) {
				try {
					ep.join(100);
				} catch (InterruptedException e) {
				}
			}

			ret =  ep.exitCode;
			// a command that ran and failed has said why itself; one that could not run has not
			if( ep.error!=null) {
				String msg = ""+ep.error.getMessage();
				if( msg.contains("error=2,") || msg.contains("error: 2 ")) {
					// as in bash
					ctx.error(cmd.get(0)+": command not found");
					ret = 127;
				} else {
					ctx.error(cmd.get(0)+": "+msg);
				}
			}
		} 

		return ret;

	}

	/**
	 * @return the program name runs (a path, or found in PATH), or null
	 */
	/**
	 * which, through the shell's table of the programs it has found (hash): a remembered one that
	 * is still there is used (and counted); one found on PATH is remembered.
	 */
	public static FileSource hashed(String name, ShellContext ctx) throws IOException {
		if( name.contains("/")) {
			return which(name, ctx);
		}
		Object [] known = ctx.console.hashTable.get(name);
		if( known != null ) {
			FileSource f = ctx.getFileSource(""+known[0]);
			// (as bash: the remembered path is used, unless shopt checkhash finds it gone)
			if( f.exists() || !us.bringardner.fsh.Glob.option(ctx, "checkhash")) {
				((int []) known[1])[0]++;
				return f;
			}
		}
		FileSource ret = which(name, ctx);
		if( ret != null ) {
			ctx.console.hashTable.put(name, new Object[] {ret.getAbsolutePath(), new int[] {1}});
		}
		return ret;
	}

	public static FileSource which(String execName, ShellContext ctx) throws IOException {
		if( execName.contains("/")) {
			FileSource file = ctx.getFileSource(execName);
			return file.exists() ? file : null;
		}
		FileSourceFactory factory = ctx.getFileSource(".").getFileSourceFactory();
		String [] exts = new String[0];
		Object tmpExt = ctx.getEvironmentVariable("PATHEXT");
		if( tmpExt!=null) {
			exts = tmpExt.toString().split(""+factory.getPathSeperatorChar());
		}
		Object pathVar = ctx.getVariable("PATH");
		// (no PATH, or an empty part of it: the current directory, as bash's)
		for(String path : (pathVar == null ? "" : ""+pathVar).split(""+factory.getPathSeperatorChar(), -1)) {
			if( path.isEmpty()) {
				path = ".";
			}
			FileSource file = findExecutable(execName,path,exts,ctx);
			if( file !=null && file.isFile()) {
				return file;
			}
		}
		return null;
	}

	private static FileSource findExecutable(String execName,String path, String[] exts,ShellContext ctx) throws IOException {
		FileSource file = null;
		FileSource dir = ctx.getFileSource(path);
		if( dir.exists()) {
			file = dir.getChild(execName);
			if( file.exists()) {
				return file;
			}
			for(String ext : exts) {
				file = dir.getChild(execName+ext);
				if( file.exists()) {
					return file;
				}
			}
		}
		return null;
	}

}
