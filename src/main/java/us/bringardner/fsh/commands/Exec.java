package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.signal.ExitException;

public class Exec extends ShellCommand{
	static String name = "exec";
	static String help = "exec [-cl] [-a name] [command [arguments]]\n"
			+ "If command is supplied, it replaces the shell without creating a new process. "
			+ "command cannot be a shell builtin or function. The arguments become the arguments "
			+ "to command If the -l option is supplied, the shell places a dash at the beginning of "
			+ "the zeroth argument passed to command. This is what the login program does. "
			+ ""
			+ "The -c option causes command to be executed with an empty environment. "
			+ "If -a is supplied, the shell passes name as the zeroth argument to command.\n"
			+ "\n"
			+ "If command cannot be executed for some reason, a non-interactive shell exits, "
			+ "unless the execfail shell option is enabled. In that case, it returns a non-zero status. "
			+ "An interactive shell returns a non-zero status if the file cannot be executed. "
			+ "A subshell exits unconditionally if exec fails.\n"
			+ "\n"
			+ "If command is not specified, redirections may be used to affect the current shell environment. "
			+ "If there are no redirection errors, the return status is zero; otherwise the return status is non-zero."
			;

	public Exec() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		int idx=0;
		boolean c = false;
		boolean l = false;
		String cmdName = null;

		for(;idx< args.length; idx++ ) {
			Argument a = args[idx];
			String val = ""+a.getValue(ctx);
			if( val.equals("--")) {
				break;
			} else if( val.equals("-a")) {
				if( idx+1 >= args.length ) {
					ctx.error("exec: -a: option requires an argument");
					ctx.stderr.println("exec: usage: exec [-cl] [-a name] [command [argument ...]] [redirection ...]");
					return 2;
				}
				cmdName = ""+args[++idx].getValue(ctx);
			} else if( val.startsWith("-") && val.length() > 1 ) {
				for(char o : val.substring(1).toCharArray()) {
					if( o == 'c' ) {
						c = true;
					} else if( o == 'l' ) {
						l = true;
					} else {
						// as bash's
						ctx.error("exec: -"+o+": invalid option");
						ctx.stderr.println("exec: usage: exec [-cl] [-a name] [command [argument ...]] [redirection ...]");
						return 2;
					}
				}
			} else {
				idx--;
				break;
			}
		}

		if( idx < args.length && idx+1 < args.length ) {
			// as bash: the program (not a builtin or function) replaces the shell
			String command = ""+args[++idx].getValue(ctx);
			us.bringardner.parley.files.FileSource file = us.bringardner.fsh.exec.Programs.which(command, ctx);
			if( file == null || file.isDirectory()) {
				ctx.error("exec: "+command+": "+(file == null ? "not found" : "Is a directory"));
				throw new ExitException(ctx, file == null ? 127 : 126);
			}
			List<String> cmd = new ArrayList<>();
			cmd.add(file.getAbsolutePath());
			for(idx++; idx < args.length; idx++ ) {
				cmd.add(""+args[idx].getValue(ctx));
			}
			if( l ) {
				// a login shell's argv[0]: -name
				String base = cmdName != null ? cmdName : command.substring(command.lastIndexOf('/')+1);
				cmdName = "-"+base;
			}
			ctx.programArgv0 = cmdName;
			ctx.programCleanEnvironment = c;
			try {
				ret = us.bringardner.fsh.exec.Programs.execute(cmd, ctx);
			} finally {
				ctx.programArgv0 = null;
				ctx.programCleanEnvironment = false;
			}
			throw new ExitException(ctx, ret);
		}
		return ret;
	}

}
