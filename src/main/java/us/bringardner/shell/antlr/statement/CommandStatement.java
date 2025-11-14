package us.bringardner.shell.antlr.statement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.core.BaseThread;
import us.bringardner.filesource.sh.FileSourceShParser.HereDocumentContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.shell.Console;
import us.bringardner.shell.Console.Option;
import us.bringardner.shell.NativeKeyboard;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl;
import us.bringardner.shell.antlr.FileSourceShVisitorImpl;
import us.bringardner.shell.antlr.RerdirectImpl;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.ReturnException;

public class CommandStatement extends Statement{
	Map<String,FileSource> executables = new TreeMap<>();

	public static class StreamCopier extends BaseThread{
		InputStream in;
		OutputStream out;
		Throwable error;
		byte [] buffer = new byte[1024*10];
		StringBuilder debug = new StringBuilder();
		boolean isStdin = false;
		boolean isNative= false;
		ShellContext ctx;

		public StreamCopier(ShellContext ctx, InputStream in, OutputStream out,String name) {
			this.ctx = ctx;
			this.in = in;
			this.out = out;
			setName(name);
			isStdin = in == Console.System_in;
			if (in instanceof NativeKeyboard) {
				isNative = true;				
			}
		}

		@Override
		public void run() {
			started = running = true;
			try {
				int cnt = 0;
				while(running && !stopping && (cnt = in.read(buffer))>=0) {
					if( cnt > 0 ) {
						debug.append(new String(buffer,0,cnt));
						out.write(buffer, 0, cnt);						
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

	public static class NativeStreamCopier extends BaseThread{
		NativeKeyboard in;
		OutputStream out;
		Throwable error;
		byte [] buffer = new byte[1024*10];
		StringBuilder debug = new StringBuilder();
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
					int key = in.read();
					if( key > 0 ) {
						debug.append((char)key);
						out.write(key);
						out.flush();
						// echo key to stdout
						if(!isSystemOut && echo ) {
							System.out.print((char)key);
						}
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
				ProcessBuilder builder = new ProcessBuilder(cmd);

				FileSource dir = ctx.console.getCurrentDirectory();
				if (dir instanceof FileProxy) {
					FileProxy cwd = (FileProxy) dir;
					builder.directory(cwd.getTarget());
				}

				//builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
				//builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
				//builder.redirectError(ProcessBuilder.Redirect.PIPE);
				Process p = builder.start();
				if (ctx.stdin instanceof NativeKeyboard) {
					boolean echo = ctx.console.isOptionEnabled(Option.KeyboardEcho);
					sc1 = new NativeStreamCopier(ctx,(NativeKeyboard)ctx.stdin,p.getOutputStream(),name+" native",echo);
				} else {
					sc1 = new StreamCopier(ctx,ctx.stdin,p.getOutputStream(),name+" stdin");

				}

				sc2 = new StreamCopier(ctx,p.getInputStream(),ctx.stdout,name+" stdout");
				sc3 = new StreamCopier(ctx,p.getErrorStream(),ctx.stderr,name+" stderr");

				sc1.start();
				sc2.start();
				sc3.start();

				@SuppressWarnings("unused")
				int time = 0;
				while(p.isAlive()) {
					try {
						p.waitFor(1000, TimeUnit.MILLISECONDS);
						if( ++time > 3) { 
							//Console.System_out.println("ExternalProcess Waiting for "+(time*1000));
						}
					} catch (InterruptedException e) {
					}
				}
				exitCode = p.exitValue();

				/*
				Map<String, Object> env1 = ctx.getEnvironmentVariable();
				Map<String, String> env = builder.environment();
				env.clear();
				for(String name: env1.keySet()) {
					env.put(name, ""+env1.get(name));
				}
				 */



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



	}

	String name ;
	RerdirectImpl redirect;
	String hereId;



	private void argsToString(List<String> cmd, ShellContext ctx) throws IOException {
		for (int idx = 0; idx < args.length; idx++) {

			Argument a = args[idx];
			String val = ""+a.getValue(ctx);

			//TODO: probably need a more comprehensive way to generate file lists
			if(val.contains("*")  ) {

				List<FileSource> list = ShellCommand.getFiles(ctx, val);
				if( list.size()>0) {
					for(FileSource file : list) {
						if( file.exists()) {
							if (!(file instanceof FileProxy)) {
								// download to temp
								throw new RuntimeException("download to temp Not implemented");
							} else {
								cmd.add(file.getAbsolutePath());
								continue;
							}
						}
					}		
				} else {
					cmd.add(val);
				}
			} else {
				cmd.add(val);
			}
		}


	}

	@Override
	public boolean equals(Object obj) {
		boolean ret = false;
		if (obj instanceof CommandStatement) {
			CommandStatement cs = (CommandStatement) obj;
			if( !testEq(name, cs.name)) {
				return false;
			}
			if(args.length==cs.args.length) {
				for (int idx = 0; idx < args.length; idx++) {
					if(!args[idx].equals(cs.args[idx])) {
						return false;
					}
				}
			} else {
				return false;
			}
			if( !testEq(redirect, cs.redirect)) {
				return false;
			}

			ret = true;
		}

		return ret;
	}


	public CommandStatement(ParserRuleContext context) {
		super(context);
	}


	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(name);
		for(Object a : args) {
			ret.append(' ');
			ret.append(a.toString());
		}
		return ret.toString(); 
	}

	public String getName() {
		return name;
	}


	public void setName(String name) {
		if( name.equals(".")) {
			name ="source";
		}

		this.name = name;
	}



	public RerdirectImpl getRedirect() {
		return redirect;
	}

	public void setRedirect(RerdirectImpl redirectionOperatorContext) {
		this.redirect = redirectionOperatorContext;
	}


	@Override
	protected int execute(ShellContext ctx) throws IOException {
		int ret = 0;
		//ctx.enterCommand(this);
		Integer returnStatus = null;
		InputStream in = ctx.stdin;
		PrintStream out = ctx.stdout;
		PrintStream err = ctx.stderr;

		try {
			configureRedirect(ctx,redirect);				

			if( hereId !=null ) {
				Object obj = ctx.getValue(hereId);
				String val = (""+obj);
				String val2 = FileSourceShPreProcessorVisitorImpl.processString(val, ctx);
				ctx.stdin = new ByteArrayInputStream(val2.getBytes());				
			}

			/*
			 * 1)  If the command name contains no slashes, the shell attempts to locate it. 
			 * 		If there exists a shell function by that name, that function is invoked as described in Shell Functions.
			 */
			Object alias = ctx.getAlias(name);
			if( alias !=null ) {
				ctx.addActiveAlias(name);
				try {
					ret = invokeAlias(ctx,alias);
				} finally {
					ctx.removeActiveAlias(name);
				}
			} else {
				FunctionDefStatement function = ctx.getFunction(name);
				if( function != null ) {
					ret = function.invoke(args,ctx);
				} else {
					/*
					 * 2) If the name does not match a function, the shell searches for it in the list of shell built-ins. 
					 * If a match is found, that built-in is invoked.
					 */
					Constructor<? extends ShellCommand> con = Console.commands.get(name);
					if( con != null ) {

						ShellCommand cmd;
						try {
							cmd = con.newInstance();
							cmd.setArgs(args);
							cmd.setContext(context);
							ret = cmd.process(ctx);
						} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							throw new IOException(e);
						}
						
						
					} else {
						String tmp = "xx"+ctx.getVariable("$0");
						boolean ok = name.equals(tmp);
						FileSource exec = findExecutable(name,ctx);
						if( exec != null ) {
							if( ok ) {
								if(!exec.exists()) {
									throw new IOException(name+" does not exists");
								}
								try(InputStream inp = exec.getInputStream()){
									String code = new String(inp.readAllBytes());
									Console tc = new Console();
									tc.setStdIn(ctx.stdin);
									tc.setStdOut(ctx.stdout);
									tc.setStdErr(ctx.stderr);
									List<Object> list = new ArrayList<>();
									for(Argument sa : args) {
										list.add(sa);
									}
									tc.setPositionalParameters(true, list);
									ret = tc.executeUsingAntlr(code);
								} catch (Exception e) {
									throw new IOException(e);
								}
							} else {
								ret = execute(exec,ctx);
							}
						} else {
							throw new IOException("No function or builtin command named '"+name+"'");
						}
					}



				}
			}
		} catch (ReturnException e) {
			returnStatus = e.exitCode;
		} finally {
			if( name.equals("exec")) {
				// special case: any redirect stays and is pushed to the console object

			} else {
				ctx.stdin = in;
				ctx.stdout = out;
				ctx.stderr = err;
			}
			//ctx.exitCommand();
			if( returnStatus ==null ) {
				ctx.console.setLastExitCode(ret);
			} else {
				ctx.console.setLastExitCode(returnStatus);
				ret = returnStatus;
			}
		}

		return ret;
	}



	public int invokeAlias(ShellContext ctx, Object code) throws IOException {
		int ret = 0;
		StringBuilder tmp = new StringBuilder(code.toString());
		for (int idx = 0; idx < args.length; idx++) {
			tmp.append(' ');
			tmp.append(args[idx]);
		}


		try {
			List<Statement> stmts = FileSourceShVisitorImpl.parse(tmp.toString());
			for(Statement s : stmts) {
				if( (ret=s.process(ctx)) != 0) {
					break;
				}
			}			
		} catch (Exception e) {
			if (e instanceof IOException) {
				throw (IOException) e;				
			} else {
				throw new IOException(e);
			}			
		}

		return ret;
	}

	public static int execute(List<String> cmd, ShellContext ctx) throws IOException {
		int ret = 0;
		if(cmd!=null && cmd.size()>0) {
			ExternalProcess ep = new ExternalProcess(cmd, ctx);
			ep.setName(cmd.get(0));
			ep.start();

			while(!ep.hasStarted()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			};
			while(ep.isRunning()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			};

			ret =  ep.exitCode;
		} 

		return ret;

	}

	private FileSource findExecutable(String execName, ShellContext ctx) throws IOException {

		FileSource file = executables.get(execName);
		if( file == null) {
			// do we have absolute path
			file = ctx.getFileSource(execName);	
			if( !file.exists()) {
				//command not found: bassh
				FileSourceFactory factory = file.getFileSourceFactory();

				for(String path : (""+ctx.getEvironmentVariable("PATH")).split(""+factory.getPathSeperatorChar())) {
					FileSource dir = ctx.getFileSource(path);
					if( dir.exists()) {
						file = dir.getChild(execName);
						if( file.exists()) {
							break;
						}
					}
				}
			}
		}

		if( file == null || !file.exists()) {
			throw new IOException("command not found: "+execName);
		}

		if( !file.canExecute()) {
			throw new IOException("execute permission denied: "+execName);
		}


		executables.put(execName, file);

		return file;
	}

	
	public void setHereDocument(HereDocumentContext hereDocument) {
		hereId = hereDocument.ID().getText();		
	}

	public static class StringArgument extends Argument {
		String value;
		public StringArgument(String value) {
			super(null);
			this.value = value;
		}
		
		@Override
		public Object getValue(ShellContext ctx) throws IOException {
			return value;
		}
		
		@Override
		public String toString() {
			return value;
		}
		
	}
	private int execute(FileSource exec, ShellContext ctx) throws IOException {
		List<String> cmd =  new ArrayList<>();
		cmd.add(exec.getAbsolutePath());
		byte [] data = exec.head(20);
		if(data.length>=2 && data[0] == '#' && data[1] == '!') {
			String tmp = new String(data).substring(2);
			int idx1=tmp.lastIndexOf('\n');
			if( idx1 > 0 ) {
				tmp = tmp.substring(0,idx1).trim();
				if(tmp.equals("fssh")) {
					Argument[] args2 = new Argument[args.length+1];
					args2[0] = new StringArgument(exec.getName());
					for(int idx = 0; idx < args.length; idx++ ) {
						args2[idx+1] = args[idx];
					}
					return ctx.executeSubShell(exec,args2);
				}
			}
		} 


		argsToString(cmd, ctx);

		return execute(cmd, ctx);
	}


}
