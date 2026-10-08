package us.bringardner.fsh.commands;

import us.bringardner.fsh.signal.ExitException;
import us.bringardner.fsh.signal.ReturnException;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Source extends ShellCommand{
	static String name = "source";
	static String help = " .  filename [arguments]\n"
			+ "source filename [arguments]\n"
			+ "\tRead  and  execute  commands  from  filename  in the current shell environment and return the exit status of the last command executed from filename."
			+ "  If filename does not contain a slash, file names in PATH are used to find the directory containing filename."
			+ "  The file searched for in PATH need not be executable.  When bash is not in  posix  mode,  the  current\n"
			+ "\tdirectory  is  searched  if no file is found in PATH.  If the sourcepath option to the shopt builtin command is turned off, the PATH is not searched."
			+ "  If any arguments are supplied,\n"
			+ "\tthey become the positional parameters when filename is executed.  Otherwise the positional parameters are unchanged."
			+ "  The return status is the status  of  the  last  command  exited\n"
			+ "\twithin the script (0 if no commands are executed), and false if filename is not found or cannot be read.\n"
			;

	public Source() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx)  {
		if(args.length==0) {
			ctx.error("source: usage: source filename [arguments]");
			return 1;
		}

		
		try {
			String path = ""+args[0].getValue(ctx);
			path = expandTilde(ctx, path);
			FileSource file = null;
			if( path.indexOf('/') < 0 && us.bringardner.fsh.Glob.option(ctx, "sourcepath")) {
				// a name with no / is looked for on $PATH first (shopt sourcepath), as in bash
				Object dirs = ctx.getVariable("PATH");
				if( dirs != null ) {
					for(String dir : dirs.toString().split(":")) {
						if( dir.isEmpty()) {
							continue;
						}
						FileSource f = ctx.getFileSource(dir+"/"+path);
						if( f.exists() && f.isFile()) {
							file = f;
							break;
						}
					}
				}
			}
			if( file == null ) {
				file = ctx.getFileSource(path);
			}
			
			if( !file.exists()) {
				// as bash says it
				ctx.error(path+": No such file or directory");
				return 1;

			}
			try (InputStream in = file.getInputStream()) {
				String code = new String(in.readAllBytes());
				// in the caller's context (its functions and locals), and return ends the file
				List<Object> saved = null;
				if( args.length>1) {
					saved = ctx.getPositionalParameterValues();
					List<Object> params = new ArrayList<>();
					for (int idx = 1; idx < args.length; idx++) {
						params.add(""+args[idx].getValue(ctx));
					}
					ctx.setPositionalParameterValues(params);
				}
				int ret = 0;
				ctx.sourceDepth++;
				ctx.sourceFiles.addLast(path);
				try {
					ret = ctx.console.runCode(ctx, code);
				} catch (ReturnException e) {
					ret = e.exitCode;
				} finally {
					ctx.sourceFiles.pollLast();
					ctx.sourceDepth--;
					if( saved != null ) {
						ctx.setPositionalParameterValues(saved);
					}
				}
				return ret;
			}
		} catch (ExitException e) {
			throw e;
		} catch (Exception e) {
			ctx.error("source: "+(e.getMessage() != null ? e.getMessage() : e));
			return 1;
		}
				
	}

}
