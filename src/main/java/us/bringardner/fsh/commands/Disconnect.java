package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** Undoes connect: removes the file system connected at a path and closes its connection. */
public class Disconnect extends ShellCommand {
	static String name = "disconnect";
	static String help = "disconnect PATH\n"
			+ "\tRemove the file system connected at PATH, and close its connection (unless\n"
			+ "\tanother path uses the same connection). The status is 1 if nothing is connected there.\n"
			+ "\tExample:\n"
			+ "\tdisconnect /prod";

	public Disconnect() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// (args holds the arguments only, not the command name)
		if( args.length != 1 ) {
			ctx.stderr.println(help);
			return 2;
		}
		String at = (""+args[0].getValue(ctx)).trim();
		try {
			ctx.console.disconnect(at);
		} catch (IOException e) {
			ctx.stderr.println(name+": "+e.getMessage());
			return 1;
		}
		return 0;
	}
}
