package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** tail, modeled on the GNU tail: the last part of each file, and optionally what is added to it. */
public class Tail extends ShellCommand{
	static String name = "tail";
	static String help = "tail [-fqv] [-n [+]NUM | -c [+]NUM] [FILE ...]\n"
			+ "\tPrint the last 10 lines of each FILE (standard input with no FILE or -).\n"
			+ "\t-n, --lines=NUM    print the last NUM lines; with +NUM, start with line NUM\n"
			+ "\t-c, --bytes=NUM    print the last NUM bytes; with +NUM, start with byte NUM\n"
			+ "\t-f, --follow       keep reading the files and print what is added (until the command is stopped)\n"
			+ "\t-s, --sleep-interval=SECONDS   with -f, the time between checks (default 1)\n"
			+ "\t-q, --quiet, --silent   never print the file name headers\n"
			+ "\t-v, --verbose      always print the file name headers\n"
			+ "\t-NUM               same as -n NUM\n"
			+ "\tNUM may end in b (512), kB (1000), K (1024), MB, M, GB or G.";

	private static final String USAGE = "usage: tail [-fqv] [-n number | -c number] [file ...]";

	public Tail() {
		super(name, help);
	}

	/** a file being followed: what it was last read to */
	private static class Followed {
		String label;
		FileSource file;
		long pos;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		long num = 10;
		boolean fromStart = false, bytes = false, quiet = false, verbose = false, follow = false;
		double interval = 1;
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
				case "--follow": follow = true; break;
				case "--lines": case "--bytes": case "--sleep-interval":
					if( v == null ) {
						if( idx+1 >= args.length ) {
							ctx.stderr.println("tail: option '"+key+"' requires an argument");
							return 1;
						}
						v = ""+args[++idx].getValue(ctx);
					}
					kind = key.equals("--lines") ? 'n' : key.equals("--bytes") ? 'c' : 's';
					val = v;
					break;
				default:
					ctx.stderr.println("tail: unrecognized option '"+arg+"'");
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
						case 'f': case 'F': follow = true; break;
						case 'n': case 'c': case 's':
							kind = ch;
							if( c+1 < cluster.length()) {
								val = cluster.substring(c+1);
							} else if( idx+1 < args.length ) {
								val = ""+args[++idx].getValue(ctx);
							} else {
								ctx.stderr.println("tail: option requires an argument -- '"+ch+"'");
								ctx.stderr.println(USAGE);
								return 1;
							}
							break;
						default:
							ctx.stderr.println("tail: invalid option -- '"+ch+"'");
							ctx.stderr.println(USAGE);
							return 1;
						}
					}
				}
			}
			if( kind == 's' ) {
				try {
					interval = Double.parseDouble(val);
				} catch (NumberFormatException e) {
					ctx.stderr.println("tail: invalid number of seconds: '"+val+"'");
					return 1;
				}
			} else if( kind != 0 ) {
				boolean plus = val.startsWith("+");
				try {
					num = TextFiles.count(plus || val.startsWith("-") ? val.substring(1) : val);
				} catch (NumberFormatException e) {
					ctx.stderr.println("tail: invalid number of "+(kind == 'n' ? "lines" : "bytes")+": '"+val+"'");
					return 1;
				}
				fromStart = plus;
				bytes = kind == 'c';
			}
		}

		boolean headers = verbose || (!quiet && files.size() > 1);
		final long n = num;
		final boolean plus = fromStart, byBytes = bytes;
		boolean [] first = {true};
		List<Followed> followed = new ArrayList<>();
		int ret = TextFiles.forEach(ctx, "tail", files, (label, file, in) -> {
			if( headers ) {
				ctx.stdout.print((first[0] ? "" : "\n")+"==> "+label+" <==\n");
			}
			first[0] = false;
			InputStream bin = new BufferedInputStream(in);
			if( byBytes ) {
				tailBytes(ctx, bin, n, plus);
			} else {
				tailLines(ctx, bin, n, plus);
			}
			if( file != null ) {
				Followed f = new Followed();
				f.label = label;
				f.file = file;
				f.pos = file.length();
				followed.add(f);
			}
		});
		ctx.stdout.flush();

		if( follow && !followed.isEmpty()) {
			follow(ctx, followed, headers, interval);
		}
		return ret;
	}

	private static void follow(ShellContext ctx, List<Followed> files, boolean headers, double interval) throws IOException {
		Followed lastShown = files.get(files.size()-1);
		long millis = Math.max(50, (long)(interval*1000));
		byte [] buf = new byte[8192];
		while( ctx.getException() == null && !ctx.hasPendingSignal()) {
			ctx.sleep(millis);
			for (Followed f : files) {
				long len = f.file.length();
				if( len < f.pos ) {
					ctx.stderr.println("tail: "+f.label+": file truncated");
					f.pos = 0;
				}
				if( len == f.pos ) {
					continue;
				}
				if( headers && f != lastShown ) {
					ctx.stdout.print("\n==> "+f.label+" <==\n");
				}
				lastShown = f;
				try (InputStream in = f.file.getInputStream()) {
					TextFiles.skip(in, f.pos);
					long left = len-f.pos;
					while( left > 0 ) {
						int got = in.read(buf, 0, (int) Math.min(buf.length, left));
						if( got < 0 ) {
							break;
						}
						ctx.stdout.write(buf, 0, got);
						left -= got;
					}
				}
				f.pos = len;
				ctx.stdout.flush();
			}
		}
	}

	private static void tailBytes(ShellContext ctx, InputStream in, long n, boolean fromStart) throws IOException {
		if( fromStart ) {
			// +NUM: start with byte NUM
			TextFiles.skip(in, Math.max(n-1, 0));
			byte [] buf = new byte[8192];
			int got;
			while( (got = in.read(buf)) >= 0 ) {
				ctx.stdout.write(buf, 0, got);
			}
			return;
		}
		if( n == 0 ) {
			return;
		}
		ArrayDeque<Byte> held = new ArrayDeque<>();
		int c;
		while( (c = in.read()) >= 0 ) {
			held.addLast((byte) c);
			if( held.size() > n ) {
				held.removeFirst();
			}
		}
		for (byte b : held) {
			ctx.stdout.write(b);
		}
	}

	private static void tailLines(ShellContext ctx, InputStream in, long n, boolean fromStart) throws IOException {
		if( fromStart ) {
			// +NUM: start with line NUM
			for (long i = 1; i < n; i++) {
				if( TextFiles.line(in) == null ) {
					return;
				}
			}
			byte [] buf = new byte[8192];
			int got;
			while( (got = in.read(buf)) >= 0 ) {
				ctx.stdout.write(buf, 0, got);
			}
			return;
		}
		if( n == 0 ) {
			return;
		}
		ArrayDeque<byte[]> held = new ArrayDeque<>();
		byte [] line;
		while( (line = TextFiles.line(in)) != null ) {
			held.addLast(line);
			if( held.size() > n ) {
				held.removeFirst();
			}
		}
		for (byte [] l : held) {
			ctx.stdout.write(l, 0, l.length);
		}
	}
}
