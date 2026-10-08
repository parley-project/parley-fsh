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
	static int run(String name, List<String> args, ShellContext sc) throws IOException {
		FileSource exec = which(name, sc);
		if( exec == null && !name.contains("/")) {
			// fsh runs a program in the current directory without ./ (bash does not)
			FileSource here = sc.getFileSource(name);
			if( here.exists() && here.isFile()) {
				exec = here;
			}
		}
		if( exec != null ) {
			if( !exec.canExecute()) {
				throw new NotExecutable(name);
			}
			if( isFshScript(exec)) {
				Argument [] all = new Argument[args.size()+1];
				all[0] = new Argument(exec.getName());
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

	/** #!fsh (or #!fssh, the name before): a script for this shell */
	private static boolean isFshScript(FileSource exec) throws IOException {
		byte [] data = exec.head(20);
		if( data.length < 2 || data[0] != '#' || data[1] != '!' ) {
			return false;
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
					// only read what was already typed, so nothing is taken after the process ends
					if( in.available() <= 0 ) {
						Thread.sleep(10);
						continue;
					}
					int key = in.read();
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

			try {
				String name = cmd.get(0);
				// with umask or ulimit set, through sh so the program gets them
				ProcessBuilder builder = new ProcessBuilder(us.bringardner.fsh.commands.ProcessSettings.wrap(ctx, cmd));
				// the shell's exported variables, not the JVM's (export X=1 and X=1 cmd reach the program)
				Map<String,String> env = builder.environment();
				env.clear();
				for(Map.Entry<String,Object> e : ctx.getEnvironmentVariables().entrySet()) {
					if( e.getValue() != null ) {
						env.put(e.getKey(), ""+e.getValue());
					}
				}

				FileSource dir = ctx.console.getCurrentDirectory();
				if (dir instanceof FileProxy) {
					FileProxy cwd = (FileProxy) dir;
					builder.directory(cwd.getTarget());
				}

				Process p = builder.start();
				if (ctx.stdin instanceof NativeKeyboard) {
					boolean echo = ctx.console.isOptionEnabled(Option.KeyboardEcho);
					sc1 = new NativeStreamCopier(ctx,(NativeKeyboard)ctx.stdin,p.getOutputStream(),name+" native",echo);
				} else {
					// stdin belongs to the shell (or to the redirect / pipe that opened it); only the process side is closed
					sc1 = new StreamCopier(ctx,ctx.stdin,p.getOutputStream(),name+" stdin",false,true);
				}

				// stdout and stderr belong to the shell; the copiers only flush them
				sc2 = new StreamCopier(ctx,p.getInputStream(),ctx.stdout,name+" stdout",true,false);
				sc3 = new StreamCopier(ctx,p.getErrorStream(),ctx.stderr,name+" stderr",true,false);

				sc1.start();
				sc2.start();
				sc3.start();

				boolean killed = false;
				while(!waitFor(p, 100)) {
					if( !killed && ctx.getException() != null) {
						// the job was interrupted, terminated or killed
						killed = true;
						terminate(p);
					}
				}
				exitCode = p.exitValue();

				// the process has exited, wait for the rest of its output
				drain(sc2);
				drain(sc3);

			} catch (Throwable e) {
				exitCode = 1;
				error = e;
			} finally {
				try {sc1.stop();}catch (Throwable e) {}
				try {sc2.stop();}catch (Throwable e) {}
				try {sc3.stop();}catch (Throwable e) {}
			}

			running = false;

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
			while(copier.isAlive() && ctx.getException() == null) {
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
		for(String path : (""+ctx.getEvironmentVariable("PATH")).split(""+factory.getPathSeperatorChar())) {
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
