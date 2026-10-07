package us.bringardner.fsh.antlr.statement;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
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

import us.bringardner.parley.core.BaseThread;
import us.bringardner.fsh.parser.FileSourceShParser.ArgumentContext;
import us.bringardner.fsh.parser.FileSourceShParser.AssignmentContext;
import us.bringardner.fsh.parser.FileSourceShParser.HereDocumentContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.fileproxy.FileProxy;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.InteractiveInput;
import us.bringardner.fsh.NativeKeyboard;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Argument;
import us.bringardner.fsh.antlr.FileSourceShPreProcessorVisitorImpl;
import us.bringardner.fsh.antlr.FileSourceShVisitorImpl;
import us.bringardner.fsh.antlr.RerdirectImpl;
import us.bringardner.fsh.antlr.Statement;
import us.bringardner.fsh.antlr.Statement.ClosedStream;
import us.bringardner.fsh.antlr.signal.ExitException;
import us.bringardner.fsh.antlr.signal.ReturnException;

public class CommandStatement extends Statement{
	Map<String,FileSource> executables = new TreeMap<>();

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
				ProcessBuilder builder = new ProcessBuilder(cmd);
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

	String name ;
	/** the command name as a word to expand when it runs ($cmd, "$(...)"), or null if name is fixed */
	ArgumentContext commandWord;
	RerdirectImpl redirect;
	String hereId;



	private void argsToString(List<String> cmd, ShellContext ctx) throws IOException {
		// the words are already expanded (*.txt was matched before the command ran)
		for(Argument a : args) {
			cmd.add(""+a.getValue(ctx));
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

	/** commands whose name=value arguments are not word-split, as in bash: export X=$y keeps "a b" */
	private static final java.util.Set<String> DECLARATIONS = java.util.Set.of("export", "local", "declare", "readonly", "typeset", "alias");

	@Override
	protected boolean splitWords(ArgumentContext word) {
		if( name != null && DECLARATIONS.contains(name) && word.getText().matches("[a-zA-Z_][a-zA-Z_0-9]*=.*")) {
			return false;
		}
		return true;
	}


	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(name != null ? name : commandWord.getText());
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

	public void setCommandWord(ArgumentContext commandWord) {
		this.commandWord = commandWord;
	}



	public RerdirectImpl getRedirect() {
		return redirect;
	}

	public void setRedirect(RerdirectImpl redirectionOperatorContext) {
		this.redirect = redirectionOperatorContext;
	}


	/** VAR=value before the command: set (and exported) for this command only */
	List<AssignmentContext> prefixAssignments;

	public void setPrefixAssignments(List<AssignmentContext> prefix) {
		prefixAssignments = prefix == null || prefix.isEmpty() ? null : prefix;
	}

	@Override
	protected int execute(ShellContext ctx) throws IOException {
		if( prefixAssignments == null ) {
			return expandAndRun(ctx);
		}
		// IFS=: read a b: set them for the command, then put back what was there
		List<Object[]> saved = new ArrayList<>();
		try {
			for(AssignmentContext a : prefixAssignments) {
				String var = a.id1.getText();
				Object val = AssignStatement.valueOf(a, ctx);
				saved.add(new Object[] {var, ctx.console.getVariable(var), ctx.getEvironmentVariable(var)});
				ctx.setVariable(var, val);
				ctx.setEnvironmentVariable(var, ""+val);
			}
			return expandAndRun(ctx);
		} finally {
			for (int idx = saved.size()-1; idx >= 0; idx--) {
				Object [] s = saved.get(idx);
				ctx.setVariable((String) s[0], s[1]);
				ctx.setEnvironmentVariable((String) s[0], s[2]);
			}
		}
	}

	private int expandAndRun(ShellContext ctx) throws IOException {
		if( commandWord == null ) {
			return runCommand(ctx);
		}
		// as in bash, the first field of the expanded word is the command and the others are its
		// first arguments: c="ls -l"; $c dir runs ls -l dir
		List<Argument> words = Argument.expandWord(commandWord, ctx, true);
		if( words == null ) {
			words = List.of(new Argument(""+new Argument(commandWord).getValue(ctx)));
		}
		if( words.isEmpty()) {
			// an empty expansion and no arguments: nothing to run
			if( args.length == 0 ) {
				ctx.console.setLastExitCode(0);
				return 0;
			}
			words = new ArrayList<>(List.of(args));
			args = new Argument[0];
		}
		List<Argument> all = new ArrayList<>(words.subList(1, words.size()));
		all.addAll(List.of(args));
		args = all.toArray(new Argument[all.size()]);
		// (put back after: a run of this statement may be going on outside this one, in recursion)
		String outerName = name;
		try {
			setName(""+words.get(0).getValue(ctx));
			return runCommand(ctx);
		} finally {
			name = outerName;
		}
	}

	private int runCommand(ShellContext ctx) throws IOException {
		int ret = 0;
		//ctx.enterCommand(this);
		Integer returnStatus = null;
		InputStream in = ctx.stdin;
		PrintStream out = ctx.stdout;
		PrintStream err = ctx.stderr;
		List<Closeable> redirected = null;

		try {
			Integer firstFd = null;
			if( redirect != null && redirect.fdWord != null && args.length > 0 ) {
				// $fid<> file: the word (now the last argument) is the file descriptor, not an argument
				String val = ""+new Argument(redirect.fdWord).getValue(ctx);
				if( val.matches("[0-9]+") && val.equals(""+args[args.length-1].getValue(ctx))) {
					firstFd = Integer.parseInt(val);
					args = java.util.Arrays.copyOf(args, args.length-1);
				}
			}
			redirected = configureRedirect(ctx,redirect,firstFd);				
		
			if( hereId !=null ) {
				String val = ctx.console.getHereDocument(hereId);
				if( val == null ) {
					throw new IOException("here-document "+hereId+" not found");
				}
				// <<'EOF': the body as written; <<EOF: expanded as in double quotes
				if( !ctx.console.isHereDocumentQuoted(hereId)) {
					val = FileSourceShPreProcessorVisitorImpl.processString(val, ctx, FileSourceShPreProcessorVisitorImpl.Quoting.HERE_DOC);
				}
				ctx.stdin = new ByteArrayInputStream(val.getBytes());				
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
							if( ret == 0 && ctx.stdout instanceof ClosedStream && ctx.stdout.checkError()) {
								// echo hi >&-
								ctx.stderr.println(name+": write error: Bad file descriptor");
								ret = 1;
							}
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
							//  No executable was found but try to execute anyway
							List<String> cmd = new ArrayList<String>();
							cmd.add(name);
							argsToString(cmd, ctx);
							
							if(FileSourceFactory.isWindows()) {
								exec = findExecutable("cmd",ctx);
								if( exec !=null) {
									cmd.add(0, "/r");
									cmd.add(0, exec.getAbsolutePath());
									ret = execute(cmd,ctx);
								} else {
									cmd.add(0, "/r");
									cmd.add(0, "cmd");									
									ret = execute(cmd, ctx);
								}
							} else {						
								ret = execute(cmd, ctx);
							}
							//throw new IOException("No function or builtin command named '"+name+"'");
						}
					}



				}
			}
		} catch (ReturnException e) {
			returnStatus = e.exitCode;
			if( ctx.isInFunction() || ctx.sourceDepth > 0 ) {
				// let FunctionDefStatement.invoke (or source) end the function
				throw e;
			}
		} catch (ExitException e) {
			returnStatus = e.exitCode;
			
			throw e;
		} catch (Exception e) {
			//e.printStackTrace();
			returnStatus = 1;
			String msg = e.getMessage();
			if( msg== null) {
				msg = e.toString();
				int idx = msg.lastIndexOf('.');
				if(idx > 0 ) {
					msg = msg.substring(idx+1);
				}
			}
			ctx.stderr.println(msg);
		} finally {
			if( name.equals("exec")) {
				// special case: any redirect stays and is pushed to the console object

			} else {
				ctx.stdin = in;
				ctx.stdout = out;
				ctx.stderr = err;
				closeRedirects(redirected);
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
			// as in bash, a failed command does not stop the rest (alias x='false; echo hi')
			for(Statement s : stmts) {
				ret = s.process(ctx);
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
					ctx.stderr.println(cmd.get(0)+": command not found");
					ret = 127;
				} else {
					ctx.stderr.println(cmd.get(0)+": "+msg);
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

	private FileSource findExecutable(String execName, ShellContext ctx) throws IOException {

		FileSource file = executables.get(execName);
		if( file == null) {
			// do we have absolute path
			file = ctx.getFileSource(execName);	
			if( !file.exists()) {
				//command not found: bash
				FileSourceFactory factory = file.getFileSourceFactory();
				String [] exts =new String[0];
				Object tmpExt = ctx.getEvironmentVariable("PATHEXT");
				if( tmpExt!=null) {
					exts = tmpExt.toString().split(""+factory.getPathSeperatorChar());
				}
				
				for(String path : (""+ctx.getEvironmentVariable("PATH")).split(""+factory.getPathSeperatorChar())) {
					file = findExecutable(execName,path,exts,ctx);
					if( file !=null) {
						break;
					}
				}
			}
		}

		if( file == null || !file.exists()) {
			//throw new IOException("command not found: "+execName);
			return null;
		}

		if( !file.canExecute()) {
			throw new IOException("execute permission denied: "+execName);
		}


		executables.put(execName, file);

		return file;
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

	public void setHereDocument(HereDocumentContext hereDocument) {
		hereId = hereDocument.ID().getText();		
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
				if(tmp.equals("fsh") || tmp.equals("fssh")) {   // fssh: the name before fsh
					Argument[] args2 = new Argument[args.length+1];
					args2[0] = new Argument(exec.getName());
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


	@Override
	protected boolean globWords() {
		return true;
	}
	@Override
	protected boolean errexitApplies() {
		return true;
	}
}
