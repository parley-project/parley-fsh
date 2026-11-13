package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** uniq, modeled on the GNU uniq: reports or omits the repeated adjacent lines. */
public class Uniq extends ShellCommand{
	static String name = "uniq";
	static String help = "uniq [OPTION]... [INPUT [OUTPUT]]\n"
			+ "\tFilter the adjacent matching lines of INPUT (standard input with no INPUT or -) to OUTPUT\n"
			+ "\t(standard output). It only compares neighbors: sort first to find all of them.\n"
			+ "\t-c, --count          put the number of occurrences before each line\n"
			+ "\t-d, --repeated       print only one of each group of repeated lines\n"
			+ "\t-D, --all-repeated   print all the lines that are repeated\n"
			+ "\t-u, --unique         print only the lines that are not repeated\n"
			+ "\t-i, --ignore-case    ignore case when comparing\n"
			+ "\t-f, --skip-fields=N  skip the first N fields of each line when comparing\n"
			+ "\t-s, --skip-chars=N   skip the first N characters\n"
			+ "\t-w, --check-chars=N  compare no more than N characters";

	private static final String USAGE = "usage: uniq [-cdDui] [-f fields] [-s chars] [-w chars] [input [output]]";

	public Uniq() {
		super(name, help);
	}

	private static class Opts {
		boolean count, repeated, all, unique, ignoreCase;
		int skipFields, skipChars, checkChars = -1;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Opts o = new Opts();
		List<String> files = new ArrayList<>();
		boolean optionsEnded = false;
		for (int idx = 0; idx < args.length; idx++) {
			String arg = ""+args[idx].getValue(ctx);
			if( optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
				files.add(arg);
				continue;
			}
			if( arg.equals("--")) {
				optionsEnded = true;
			} else if( arg.startsWith("--")) {
				int eq = arg.indexOf('=');
				String key = eq > 0 ? arg.substring(0, eq) : arg;
				String val = eq > 0 ? arg.substring(eq+1) : null;
				char opt = 0;
				switch (key) {
				case "--count": o.count = true; break;
				case "--repeated": o.repeated = true; break;
				case "--all-repeated": o.all = true; break;
				case "--unique": o.unique = true; break;
				case "--ignore-case": o.ignoreCase = true; break;
				case "--skip-fields": opt = 'f'; break;
				case "--skip-chars": opt = 's'; break;
				case "--check-chars": opt = 'w'; break;
				default:
					ctx.stderr.println("uniq: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 1;
				}
				if( opt != 0 ) {
					if( val == null ) {
						if( idx+1 >= args.length ) {
							ctx.stderr.println("uniq: option '"+key+"' requires an argument");
							return 1;
						}
						val = ""+args[++idx].getValue(ctx);
					}
					if( !number(ctx, o, opt, val)) {
						return 1;
					}
				}
			} else {
				String cluster = arg.substring(1);
				for (int c = 0; c < cluster.length(); c++) {
					char ch = cluster.charAt(c);
					switch (ch) {
					case 'c': o.count = true; break;
					case 'd': o.repeated = true; break;
					case 'D': o.all = true; break;
					case 'u': o.unique = true; break;
					case 'i': o.ignoreCase = true; break;
					case 'f': case 's': case 'w': {
						String val;
						if( c+1 < cluster.length()) {
							val = cluster.substring(c+1);
						} else if( idx+1 < args.length ) {
							val = ""+args[++idx].getValue(ctx);
						} else {
							ctx.stderr.println("uniq: option requires an argument -- '"+ch+"'");
							ctx.stderr.println(USAGE);
							return 1;
						}
						if( !number(ctx, o, ch, val)) {
							return 1;
						}
						c = cluster.length();
						break;
					}
					default:
						ctx.stderr.println("uniq: invalid option -- '"+ch+"'");
						ctx.stderr.println(USAGE);
						return 1;
					}
				}
			}
		}
		if( files.size() > 2 ) {
			ctx.stderr.println("uniq: extra operand '"+files.get(2)+"'");
			ctx.stderr.println(USAGE);
			return 1;
		}

		List<String> lines = new ArrayList<>();
		List<String> input = new ArrayList<>();
		if( !files.isEmpty()) {
			input.add(files.get(0));
		}
		int ret = TextFiles.forEach(ctx, "uniq", input, (label, file, in) -> {
			InputStream bin = new BufferedInputStream(in);
			byte [] line;
			while( (line = TextFiles.line(bin)) != null ) {
				lines.add(TextFiles.chomp(line));
			}
		});
		if( ret != 0 ) {
			return 1;
		}

		StringBuilder out = new StringBuilder();
		int i = 0;
		while( i < lines.size()) {
			int j = i+1;
			String key = key(lines.get(i), o);
			while( j < lines.size() && key.equals(key(lines.get(j), o))) {
				j++;
			}
			int n = j-i;
			boolean show;
			if( o.all ) {
				show = n > 1;
			} else if( o.repeated || o.unique ) {
				show = (o.repeated && n > 1) || (o.unique && n == 1);
			} else {
				show = true;
			}
			if( show ) {
				if( o.all ) {
					for (int k = i; k < j; k++) {
						out.append(lines.get(k)).append('\n');
					}
				} else {
					if( o.count ) {
						out.append(String.format("%7d ", n));
					}
					out.append(lines.get(i)).append('\n');
				}
			}
			i = j;
		}

		if( files.size() > 1 && !files.get(1).equals("-")) {
			FileSource f = ctx.console.createFileSource(files.get(1));
			try (OutputStream os = f.getOutputStream()) {
				os.write(out.toString().getBytes(StandardCharsets.UTF_8));
			}
		} else {
			ctx.stdout.print(out);
			ctx.stdout.flush();
		}
		return 0;
	}

	private static boolean number(ShellContext ctx, Opts o, char opt, String val) {
		int n;
		try {
			n = Integer.parseInt(val.trim());
			if( n < 0 ) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			ctx.stderr.println("uniq: "+val+": invalid number of "+(opt == 'f' ? "fields to skip" : opt == 's' ? "bytes to skip" : "bytes to compare"));
			return false;
		}
		if( opt == 'f' ) {
			o.skipFields = n;
		} else if( opt == 's' ) {
			o.skipChars = n;
		} else {
			o.checkChars = n;
		}
		return true;
	}

	/** the part of a line that is compared */
	private static String key(String line, Opts o) {
		int pos = 0, n = line.length();
		for (int f = 0; f < o.skipFields; f++) {
			while( pos < n && (line.charAt(pos) == ' ' || line.charAt(pos) == '\t')) {
				pos++;
			}
			while( pos < n && line.charAt(pos) != ' ' && line.charAt(pos) != '\t') {
				pos++;
			}
		}
		pos = Math.min(n, pos+o.skipChars);
		String s = line.substring(pos);
		if( o.checkChars >= 0 && s.length() > o.checkChars ) {
			s = s.substring(0, o.checkChars);
		}
		return o.ignoreCase ? s.toLowerCase() : s;
	}
}
