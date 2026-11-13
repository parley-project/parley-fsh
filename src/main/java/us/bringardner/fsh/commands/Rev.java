package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** rev: reverses the characters of each line. */
public class Rev extends ShellCommand{
	static String name = "rev";
	static String help = "rev [FILE ...]\n"
			+ "\tCopy each FILE (standard input with no FILE or -) reversing the characters of every line.";

	public Rev() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "", "", false);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		int ret = TextFiles.forEach(ctx, name, a.operands, (label, file, in) -> {
			java.io.InputStream bin = new java.io.BufferedInputStream(in);
			byte [] line;
			while( (line = TextFiles.line(bin)) != null ) {
				ctx.stdout.print(new StringBuilder(TextFiles.chomp(line)).reverse().append('\n'));
			}
		});
		ctx.stdout.flush();
		return ret;
	}
}
