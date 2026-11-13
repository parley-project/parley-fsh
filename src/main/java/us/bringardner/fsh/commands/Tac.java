package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** tac, modeled on the GNU tac: the lines of each file, last first. */
public class Tac extends ShellCommand{
	static String name = "tac";
	static String help = "tac [FILE ...]\n"
			+ "\tPrint each FILE (standard input with no FILE or -) with its lines in reverse order.\n"
			+ "\tEach file is reversed on its own.";

	public Tac() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "", "", false);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		int ret = TextFiles.forEach(ctx, name, a.operands, (label, file, in) -> {
			List<String> lines = new ArrayList<>();
			java.io.InputStream bin = new java.io.BufferedInputStream(in);
			byte [] line;
			while( (line = TextFiles.line(bin)) != null ) {
				lines.add(TextFiles.chomp(line));
			}
			Collections.reverse(lines);
			StringBuilder out = new StringBuilder();
			for (String l : lines) {
				out.append(l).append('\n');
			}
			ctx.stdout.print(out);
		});
		ctx.stdout.flush();
		return ret;
	}
}
