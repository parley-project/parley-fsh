package us.bringardner.fsh.commands;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * builtin name [args]: run the builtin name, even if a function has that name.
 */
public class Builtin extends ShellCommand{
	static String name = "builtin";
	static String help = "builtin name [args]\n"
			+ "	Run the shell builtin name (not a function or program with that name)."
			;

	public Builtin() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( args.length == 0 ) {
			return 0;
		}
		int first = 0;
		String n = ""+args[0].getValue(ctx);
		if( n.equals("--")) {
			if( args.length == 1 ) {
				return 0;
			}
			first = 1;
			n = ""+args[1].getValue(ctx);
		} else if( n.startsWith("-") && n.length() > 1 ) {
			ctx.error("builtin: "+n.substring(0, 2)+": invalid option");
			ctx.stderr.println("builtin: usage: builtin [shell-builtin [arg ...]]");
			return 2;
		}
		Constructor<? extends ShellCommand> con = ctx.console.builtin(n);
		if( con == null ) {
			ctx.error("builtin: "+n+": not a shell builtin");
			return 1;
		}
		return run(con, Arrays.copyOfRange(args, first+1, args.length), ctx);
	}

	static int run(Constructor<? extends ShellCommand> con, us.bringardner.fsh.Argument[] args, ShellContext ctx) throws IOException {
		try {
			ShellCommand cmd = con.newInstance();
			cmd.setArgs(args);
			return cmd.process(ctx);
		} catch (ReflectiveOperationException e) {
			throw new IOException(e);
		}
	}
}
