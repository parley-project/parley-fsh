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
		// -O n: put the lines from index n, keeping the array (without -O it is replaced)
		Integer origin = null;
		int fromFd = 0;
		// -C callback -c quantum: callback index line, every quantum lines
		String callback = null;
		int quantum = 5000;
		for(int idx = 0; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			try {
				switch (text) {
				case "-t": trim = true; break;
				case "-n": max = Integer.parseInt(""+args[++idx].getValue(ctx)); break;
				case "-s": skip = Integer.parseInt(""+args[++idx].getValue(ctx)); break;
				case "-O": origin = Integer.parseInt(""+args[++idx].getValue(ctx)); break;
				case "-u": fromFd = Integer.parseInt(""+args[++idx].getValue(ctx)); break;
				case "-C": callback = ""+args[++idx].getValue(ctx); break;
				case "-c":
					quantum = Integer.parseInt(""+args[++idx].getValue(ctx));
					if( quantum <= 0 ) {
						ctx.error(getName()+": "+quantum+": invalid callback quantum");
						return 1;
					}
					break;
				case "-d": {
					String d = ""+args[++idx].getValue(ctx);
					delim = d.isEmpty() ? '\0' : d.charAt(0);
					break;
				}
				default:
					if( text.startsWith("-")) {
						ctx.error(getName()+": "+text+": invalid option");
						return 2;
					}
					var = text;
				}
			} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
				ctx.error(getName()+": "+text+": option requires a number");
				return 2;
			}
		}
		FshList lines = new FshList();
		InputStream in = ctx.stdin;
		if( fromFd != 0 ) {
			us.bringardner.fsh.Console.FileDiscriptor fd = ctx.console.getFileDistcriptor(fromFd);
			if( fd == null || fd.getIn() == null ) {
				ctx.error(getName()+": "+fromFd+": invalid file descriptor: Bad file descriptor");
				return 1;
			}
			in = fd.getIn();
		}
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int read = 0;
		int c;
		while( (max < 0 || lines.size() < max) && (c = in.read()) >= 0 ) {
			if( (c != delim || !trim) && c != 0 ) {
				// (a value cannot hold NUL, as in bash)
				line.write(c);
			}
			if( c == delim ) {
				if( read++ >= skip ) {
					add(ctx, lines, line.toString(java.nio.charset.StandardCharsets.UTF_8), callback, quantum, origin);
				}
				line.reset();
			}
		}
		if( line.size() > 0 && (max < 0 || lines.size() < max) && read >= skip ) {
			add(ctx, lines, line.toString(java.nio.charset.StandardCharsets.UTF_8), callback, quantum, origin);
		}
		if( origin != null ) {
			Object old = ctx.getVariable(var);
			FshList array = new FshList();
			if( old instanceof FshList ) {
				for(int idx : ((FshList) old).getIndexes()) {
					array.set(idx, ((FshList) old).get(idx));
				}
			} else if( old instanceof java.util.List<?> ) {
				array.addAll((java.util.List<?>) old);
			} else if( old != null ) {
				array.add(old);
			}
			for (int idx = 0; idx < lines.size(); idx++) {
				array.set(origin+idx, lines.get(idx));
			}
			lines = array;
		}
		ctx.setVariable(var, lines);
		return 0;
	}

	/** a line into the array; every quantum lines the callback runs first (index, line), as bash's */
	private static void add(ShellContext ctx, FshList lines, String line, String callback, int quantum, Integer origin) throws IOException {
		if( callback != null && (lines.size()+1) % quantum == 0 ) {
			int index = (origin == null ? 0 : origin)+lines.size();
			ctx.console.runCode(ctx, callback+" "+index+" '"+line.replace("'", "'\\''")+"'");
		}
		lines.add(line);
	}
}
