package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** whoami, modeled on the GNU whoami: the name of the user running the shell. */
public class Whoami extends ShellCommand{
	static String name = "whoami";
	static String help = "whoami\n"
			+ "\tPrint the name of the user who is running the shell.";

	public Whoami() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "", "", false);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( !a.operands.isEmpty()) {
			ctx.stderr.println("whoami: extra operand '"+a.operands.get(0)+"'");
			return 1;
		}
		ctx.stdout.println(System.getProperty("user.name"));
		ctx.stdout.flush();
		return 0;
	}
}
