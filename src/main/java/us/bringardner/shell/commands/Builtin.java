package us.bringardner.shell.commands;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

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
		String n = ""+args[0].getValue(ctx);
		Constructor<? extends ShellCommand> con = Console.commands.get(n);
		if( con == null ) {
			ctx.stderr.println("builtin: "+n+": not a shell builtin");
			return 1;
		}
		return run(con, Arrays.copyOfRange(args, 1, args.length), ctx);
	}

	static int run(Constructor<? extends ShellCommand> con, us.bringardner.shell.antlr.Argument[] args, ShellContext ctx) throws IOException {
		try {
			ShellCommand cmd = con.newInstance();
			cmd.setArgs(args);
			return cmd.process(ctx);
		} catch (ReflectiveOperationException e) {
			throw new IOException(e);
		}
	}
}
