package us.bringardner.shell.commands;

import java.io.InputStream;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.FsshList;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

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
			ctx.stderr.println("source: usage: source filename [arguments]");
			return 1;
		}

		
		try {
			String path = ""+args[0].getValue(ctx);
			path = expandTilde(ctx, path);
			FileSource file = ctx.getFileSource(path);
			
			if( !file.exists()) {
				ctx.stderr.println("source: "+path+"  not found");
				ctx.stderr.println("source: usage: source filename [arguments]");
				return 1;

			}
			try (InputStream in = file.getInputStream()) {
				String code = new String(in.readAllBytes());
				FsshList tmp = ctx.console.getPositionalParameters();
				FsshList tmp2 = null;
				if( args.length>1) {
					tmp2 = new FsshList();
					for (int idx = 1; idx < args.length; idx++) {
						String val = args[idx].getValue(ctx).toString();
						tmp2.add(val);
					}
					ctx.console.setPositionalParameters(false, tmp2);
				}
				
				int ret = ctx.console.executeUsingAntlr(code);
				ctx.console.setPositionalParameters(false, tmp);
				
				return ret;				
			}
		} catch (Exception e) {
			ctx.stderr.println("source: "+e);
			return 1;
		}
				
	}

}
