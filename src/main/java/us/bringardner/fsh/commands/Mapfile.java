package us.bringardner.fsh.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Mapfile extends ShellCommand{
	static String name = "mapfile";
	static String help = "mapfile [-t] [-n count] [-s count] [-d delim] [array]\n"
			+ "	Read lines from standard input into the indexed array (MAPFILE if none is named).\n"
			+ "	-t removes the delimiter from each line, -n reads at most count lines, -s skips the first\n"
			+ "	count, -d ends lines with delim instead of a newline. readarray is the same command."
			;

	public Mapfile() {
		this(name);
	}

	protected Mapfile(String name) {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean trim = false;
		int max = -1;
		int skip = 0;
		char delim = '\n';
		String var = "MAPFILE";
		for(int idx = 0; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			try {
				switch (text) {
				case "-t": trim = true; break;
				case "-n": max = Integer.parseInt(""+args[++idx].getValue(ctx)); break;
				case "-s": skip = Integer.parseInt(""+args[++idx].getValue(ctx)); break;
				case "-d": {
					String d = ""+args[++idx].getValue(ctx);
					delim = d.isEmpty() ? '\0' : d.charAt(0);
					break;
				}
				default:
					if( text.startsWith("-")) {
						ctx.stderr.println(getName()+": "+text+": invalid option");
						return 2;
					}
					var = text;
				}
			} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
				ctx.stderr.println(getName()+": "+text+": option requires a number");
				return 2;
			}
		}
		FshList lines = new FshList();
		InputStream in = ctx.stdin;
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int read = 0;
		int c;
		while( (max < 0 || lines.size() < max) && (c = in.read()) >= 0 ) {
			if( c != delim || !trim ) {
				line.write(c);
			}
			if( c == delim ) {
				if( read++ >= skip ) {
					lines.add(line.toString());
				}
				line.reset();
			}
		}
		if( line.size() > 0 && (max < 0 || lines.size() < max) && read >= skip ) {
			lines.add(line.toString());
		}
		ctx.setVariable(var, lines);
		return 0;
	}
}
