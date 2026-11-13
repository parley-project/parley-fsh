package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** head, modeled on the GNU head: the first part of each file. */
public class Head extends ShellCommand{
	static String name = "head";
	static String help = "head [-qv] [-n [-]NUM | -c [-]NUM] [FILE ...]\n"
			+ "\tPrint the first 10 lines of each FILE (standard input with no FILE or -).\n"
			+ "\t-n, --lines=NUM    print the first NUM lines; with -NUM, all but the last NUM lines\n"
			+ "\t-c, --bytes=NUM    print the first NUM bytes; with -NUM, all but the last NUM bytes\n"
			+ "\t-q, --quiet, --silent   never print the file name headers\n"
			+ "\t-v, --verbose      always print the file name headers\n"
			+ "\t-NUM               same as -n NUM\n"
			+ "\tNUM may end in b (512), kB (1000), K (1024), MB, M, GB or G.";

	private static final String USAGE = "usage: head [-qv] [-n number | -c number] [file ...]";

	public Head() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		long num = 10;
		boolean negative = false, bytes = false, quiet = false, verbose = false;
		List<String> files = new ArrayList<>();
		boolean optionsEnded = false;
		for (int idx = 0; idx < args.length; idx++) {
			String arg = ""+args[idx].getValue(ctx);
			String val = null;
			char kind = 0;
			if( optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
				files.add(arg);
				continue;
			}
			if( arg.equals("--")) {
				optionsEnded = true;
			} else if( arg.startsWith("--")) {
				int eq = arg.indexOf('=');
				String key = eq > 0 ? arg.substring(0, eq) : arg;
				String v = eq > 0 ? arg.substring(eq+1) : null;
				switch (key) {
				case "--quiet": case "--silent": quiet = true; verbose = false; break;
				case "--verbose": verbose = true; quiet = false; break;
				case "--lines": case "--bytes":
					if( v == null ) {
						if( idx+1 >= args.length ) {
							ctx.stderr.println("head: option '"+key+"' requires an argument");
							return 1;
						}
						v = ""+args[++idx].getValue(ctx);
					}
					kind = key.equals("--lines") ? 'n' : 'c';
					val = v;
					break;
				default:
					ctx.stderr.println("head: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 1;
				}
			} else {
				String cluster = arg.substring(1);
				if( cluster.matches("\\d+")) {
					kind = 'n';
					val = cluster;
				} else {
					for (int c = 0; c < cluster.length() && kind == 0; c++) {
						char ch = cluster.charAt(c);
						switch (ch) {
						case 'q': quiet = true; verbose = false; break;
						case 'v': verbose = true; quiet = false; break;
						case 'n': case 'c':
							kind = ch;
							if( c+1 < cluster.length()) {
								val = cluster.substring(c+1);
							} else if( idx+1 < args.length ) {
								val = ""+args[++idx].getValue(ctx);
							} else {
								ctx.stderr.println("head: option requires an argument -- '"+ch+"'");
								ctx.stderr.println(USAGE);
								return 1;
							}
							break;
						default:
							ctx.stderr.println("head: invalid option -- '"+ch+"'");
							ctx.stderr.println(USAGE);
							return 1;
						}
					}
				}
			}
			if( kind != 0 ) {
				boolean neg = val.startsWith("-");
				try {
					num = TextFiles.count(neg || val.startsWith("+") ? val.substring(1) : val);
				} catch (NumberFormatException e) {
					ctx.stderr.println("head: invalid number of "+(kind == 'n' ? "lines" : "bytes")+": '"+val+"'");
					return 1;
				}
				negative = neg;
				bytes = kind == 'c';
			}
		}

		boolean headers = verbose || (!quiet && files.size() > 1);
		final long n = num;
		final boolean neg = negative, byBytes = bytes;
		boolean [] first = {true};
		int ret = TextFiles.forEach(ctx, "head", files, (label, file, in) -> {
			if( headers ) {
				ctx.stdout.print((first[0] ? "" : "\n")+"==> "+label+" <==\n");
			}
			first[0] = false;
			InputStream bin = new BufferedInputStream(in);
			if( byBytes ) {
				copyBytes(ctx, bin, n, neg);
			} else {
				copyLines(ctx, bin, n, neg);
			}
		});
		ctx.stdout.flush();
		return ret;
	}

	private static void copyBytes(ShellContext ctx, InputStream in, long n, boolean allButLast) throws IOException {
		byte [] buf = new byte[8192];
		if( !allButLast ) {
			long left = n;
			while( left > 0 ) {
				int got = in.read(buf, 0, (int) Math.min(buf.length, left));
				if( got < 0 ) {
					break;
				}
				ctx.stdout.write(buf, 0, got);
				left -= got;
			}
			return;
		}
		// all but the last n bytes: hold n bytes back
		ArrayDeque<Byte> held = new ArrayDeque<>();
		int c;
		while( (c = in.read()) >= 0 ) {
			held.addLast((byte) c);
			if( held.size() > n ) {
				ctx.stdout.write(held.removeFirst());
			}
		}
	}

	private static void copyLines(ShellContext ctx, InputStream in, long n, boolean allButLast) throws IOException {
		if( !allButLast ) {
			for (long i = 0; i < n; i++) {
				byte [] line = TextFiles.line(in);
				if( line == null ) {
					break;
				}
				ctx.stdout.write(line, 0, line.length);
			}
			return;
		}
		ArrayDeque<byte[]> held = new ArrayDeque<>();
		byte [] line;
		while( (line = TextFiles.line(in)) != null ) {
			held.addLast(line);
			if( held.size() > n ) {
				byte [] out = held.removeFirst();
				ctx.stdout.write(out, 0, out.length);
			}
		}
	}
}
