package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.signal.ExitException;

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
			if( val.equals("-a")) {
				cmdName = ""+args[++idx].getValue(ctx);
			} else if( val.startsWith("-")) {
				c = val.contains("c");
				l = val.contains("l");
			} else {
				break;
			}
		}

		if( idx < args.length) {
			String command = ""+args[++idx].getValue(ctx);
			StringBuilder buf = new StringBuilder(command);
			for(; idx < args.length; idx++ ) {
				buf.append(' ');
				if( l ) {
					buf.append("-");
					l = false;
				}
				buf.append(args[idx].getValue(ctx));
			}
			
			String code = buf.toString().trim();
			if( !code.isEmpty()) {
				if( cmdName !=null ) {
					ctx.setVariable("$0", cmdName);
				} else {
					ctx.setVariable("$0", command);
				}
				if( c ) {
					// clear env
				}
				ret = ctx.console.executeUsingAntlr(code);
				throw new ExitException(ctx, ret);
			} else {
				ret = 1;
			}
		} else {
			//  no command 
			//  redirect current shell???
		}
		return ret;
	}

}
