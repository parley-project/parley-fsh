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

	protected Source(String name) {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx)  {
		int first = 0;
		// -p path: where a name with no / is looked for (in place of PATH)
		String searchPath = null;
		while( first < args.length ) {
			String a = ""+args[first].getValue(ctx);
			if( a.equals("--")) {
				first++;
				break;
			}
			if( a.equals("-p") && first+1 < args.length ) {
				searchPath = ""+args[first+1].getValue(ctx);
				first += 2;
				continue;
			}
			break;
		}
		if( first >= args.length ) {
			ctx.error(getName()+": filename argument required");
			ctx.stderr.println(getName()+": usage: "+getName()+" [-p path] filename [arguments]");
			return 2;
		}
		args = java.util.Arrays.copyOfRange(args, first, args.length);

		try {
			String path = ""+args[0].getValue(ctx);
			path = expandTilde(ctx, path);
			FileSource file = null;
			boolean posix = ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix);
			// (command . file: not fatal)
			boolean fatal = posix && ctx.viaCommand == 0;
			if( path.indexOf('/') < 0 && (searchPath != null || us.bringardner.fsh.Glob.option(ctx, "sourcepath"))) {
				// a name with no / is looked for on -p's path, or $PATH (shopt sourcepath), as in bash
				Object dirs = searchPath != null ? searchPath : ctx.getVariable("PATH");
				if( dirs != null ) {
					for(String dir : dirs.toString().split(":", -1)) {
						FileSource f = ctx.getFileSource((dir.isEmpty() ? "." : dir)+"/"+path);
						if( f.exists() && f.isFile()) {
							file = f;
							break;
						}
					}
				}
				if( file == null && (searchPath != null || posix)) {
					// (-p, or posix mode: not the current directory)
					ctx.error(getName()+": "+path+": file not found");
					if( fatal ) {
						throw new ExitException(ctx, 1);
					}
					return 1;
				}
			}
			if( file == null ) {
				file = ctx.getFileSource(path);
			}

			boolean stdin = path.equals("/dev/stdin") || path.equals("/dev/fd/0");
			if( !stdin && !file.exists()) {
				// as bash says it (posix mode: the shell ends, . being a special builtin)
				ctx.error(path+": No such file or directory");
				if( fatal ) {
					throw new ExitException(ctx, 1);
				}
				return 1;

			}
			// (/dev/stdin: this command's standard input, a pipe too)
			try (InputStream in = stdin ? new java.io.FilterInputStream(ctx.stdin) {
					@Override
					public void close() {
					}
				} : file.getInputStream()) {
				String code = new String(in.readAllBytes());
				// in the caller's context (its functions and locals), and return ends the file
				List<Object> saved = null;
				List<Object> params = null;
				if( args.length>1) {
					saved = ctx.getPositionalParameterValues();
					params = new ArrayList<>();
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
					if( saved != null && params.equals(ctx.getPositionalParameterValues())) {
						// (unless the file set them itself: set -- in it stays, as in bash)
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
