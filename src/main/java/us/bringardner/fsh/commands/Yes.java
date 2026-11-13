package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** yes, modeled on the GNU yes: prints a line again and again until it is stopped or its output is closed. */
public class Yes extends ShellCommand{
	static String name = "yes";
	static String help = "yes [STRING ...]\n"
			+ "\tPrint STRING (the arguments joined by spaces; y if there are none) on a line, over and over,\n"
			+ "\tuntil the command is stopped or what reads its output (head, for one) is done.";

	public Yes() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			if( i > 0 ) {
				line.append(' ');
			}
			line.append(args[i].getValue(ctx));
		}
		if( args.length == 0 ) {
			line.append('y');
		}
		line.append('\n');
		StringBuilder block = new StringBuilder();
		while( block.length() < 4096 ) {
			block.append(line);
		}
		String text = block.toString();
		while( ctx.getException() == null && !ctx.hasPendingSignal() && !Thread.currentThread().isInterrupted()) {
			ctx.stdout.print(text);
			ctx.stdout.flush();
			if( ctx.stdout.checkError()) {
				break;
			}
		}
		return 0;
	}
}
