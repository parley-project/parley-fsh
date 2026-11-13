package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** cut, modeled on the GNU cut: selects bytes, characters or delimited fields of each line. */
public class Cut extends ShellCommand{
	static String name = "cut";
	static String help = "cut OPTION... [FILE ...]\n"
			+ "\tPrint the selected parts of each line of each FILE (standard input with no FILE or -).\n"
			+ "\t-b, --bytes=LIST        select only these bytes\n"
			+ "\t-c, --characters=LIST   select only these characters\n"
			+ "\t-f, --fields=LIST       select only these fields; a line with no delimiter is printed whole unless -s\n"
			+ "\t-d, --delimiter=DELIM   the field delimiter, one character (default: tab)\n"
			+ "\t-s, --only-delimited    with -f, don't print the lines that have no delimiter\n"
			+ "\t--complement            select everything but the selection\n"
			+ "\t--output-delimiter=STR  separate the fields of the output with STR instead of the delimiter\n"
			+ "\tA LIST is numbers and ranges separated by commas: 1,3  2-5  4-  -3 (counted from 1).";

	private static final String USAGE = "usage: cut -b list | -c list | -f list [-d delim] [-s] [--complement] [file ...]";

	public Cut() {
		super(name, help);
	}

	/** inclusive ranges, in order and not overlapping */
	private static List<long[]> parseList(String list) {
		List<long[]> ranges = new ArrayList<>();
		for (String part : list.split(",")) {
			if( part.isEmpty()) {
				throw new IllegalArgumentException("invalid byte, character or field list");
			}
			long lo, hi;
			int dash = part.indexOf('-');
			try {
				if( dash < 0 ) {
					lo = hi = Long.parseLong(part);
				} else {
					lo = dash == 0 ? 1 : Long.parseLong(part.substring(0, dash));
					hi = dash == part.length()-1 ? Long.MAX_VALUE : Long.parseLong(part.substring(dash+1));
				}
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("invalid byte, character or field list");
			}
			if( lo == 0 || hi == 0 ) {
				throw new IllegalArgumentException("fields and positions are numbered from 1");
			}
			if( lo > hi ) {
				throw new IllegalArgumentException("invalid decreasing range");
			}
			ranges.add(new long[] {lo, hi});
		}
		ranges.sort((a, b) -> Long.compare(a[0], b[0]));
		List<long[]> merged = new ArrayList<>();
		for (long[] r : ranges) {
			if( !merged.isEmpty() && r[0] <= merged.get(merged.size()-1)[1]+1 ) {
				long[] last = merged.get(merged.size()-1);
				last[1] = Math.max(last[1], r[1]);
			} else {
				merged.add(new long[] {r[0], r[1]});
			}
		}
		return merged;
	}

	private static boolean selected(List<long[]> ranges, long pos, boolean complement) {
		boolean in = false;
		for (long[] r : ranges) {
			if( pos >= r[0] && pos <= r[1] ) {
				in = true;
				break;
			}
		}
		return in != complement;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		char mode = 0;
		String list = null;
		String delim = "\t", outDelim = null;
		boolean onlyDelimited = false, complement = false;
		List<String> files = new ArrayList<>();
		boolean optionsEnded = false;
		for (int idx = 0; idx < args.length; idx++) {
			String arg = ""+args[idx].getValue(ctx);
			if( optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
				files.add(arg);
				continue;
			}
			char opt = 0;
			String val = null;
			if( arg.equals("--")) {
				optionsEnded = true;
			} else if( arg.startsWith("--")) {
				int eq = arg.indexOf('=');
				String key = eq > 0 ? arg.substring(0, eq) : arg;
				val = eq > 0 ? arg.substring(eq+1) : null;
				switch (key) {
				case "--only-delimited": onlyDelimited = true; break;
				case "--complement": complement = true; break;
				case "--bytes": opt = 'b'; break;
				case "--characters": opt = 'c'; break;
				case "--fields": opt = 'f'; break;
				case "--delimiter": opt = 'd'; break;
				case "--output-delimiter": opt = 'o'; break;
				default:
					ctx.stderr.println("cut: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 1;
				}
				if( opt != 0 && val == null ) {
					if( idx+1 >= args.length ) {
						ctx.stderr.println("cut: option '"+key+"' requires an argument");
						return 1;
					}
					val = ""+args[++idx].getValue(ctx);
				}
			} else {
				String cluster = arg.substring(1);
				for (int c = 0; c < cluster.length() && opt == 0; c++) {
					char ch = cluster.charAt(c);
					switch (ch) {
					case 's': onlyDelimited = true; break;
					case 'n': break;
					case 'b': case 'c': case 'f': case 'd':
						opt = ch;
						if( c+1 < cluster.length()) {
							val = cluster.substring(c+1);
						} else if( idx+1 < args.length ) {
							val = ""+args[++idx].getValue(ctx);
						} else {
							ctx.stderr.println("cut: option requires an argument -- '"+ch+"'");
							ctx.stderr.println(USAGE);
							return 1;
						}
						break;
					default:
						ctx.stderr.println("cut: invalid option -- '"+ch+"'");
						ctx.stderr.println(USAGE);
						return 1;
					}
				}
			}
			if( opt == 'b' || opt == 'c' || opt == 'f' ) {
				if( mode != 0 && mode != opt ) {
					ctx.stderr.println("cut: only one type of list may be specified");
					ctx.stderr.println(USAGE);
					return 1;
				}
				mode = opt;
				list = val;
			} else if( opt == 'd' ) {
				if( val.codePointCount(0, val.length()) != 1 ) {
					ctx.stderr.println("cut: the delimiter must be a single character");
					return 1;
				}
				delim = val;
			} else if( opt == 'o' ) {
				outDelim = val;
			}
		}
		if( mode == 0 ) {
			ctx.stderr.println("cut: you must specify a list of bytes, characters, or fields");
			ctx.stderr.println(USAGE);
			return 1;
		}
		List<long[]> ranges;
		try {
			ranges = parseList(list);
		} catch (IllegalArgumentException e) {
			ctx.stderr.println("cut: "+e.getMessage());
			return 1;
		}
		final char m = mode;
		final boolean comp = complement, only = onlyDelimited;
		final String d = delim, od = outDelim == null ? delim : outDelim;
		final List<long[]> rs = ranges;
		int ret = TextFiles.forEach(ctx, "cut", files, (label, file, in) -> {
			InputStream bin = new BufferedInputStream(in);
			byte [] raw;
			while( (raw = TextFiles.line(bin)) != null ) {
				String line = TextFiles.chomp(raw);
				if( m == 'b' ) {
					byte [] bytes = line.getBytes(StandardCharsets.UTF_8);
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					for (int i = 0; i < bytes.length; i++) {
						if( selected(rs, i+1, comp)) {
							out.write(bytes[i]);
						}
					}
					out.write('\n');
					ctx.stdout.write(out.toByteArray(), 0, out.size());
				} else if( m == 'c' ) {
					StringBuilder sb = new StringBuilder();
					int pos = 0;
					for (int i = 0; i < line.length(); ) {
						int cp = line.codePointAt(i);
						i += Character.charCount(cp);
						if( selected(rs, ++pos, comp)) {
							sb.appendCodePoint(cp);
						}
					}
					ctx.stdout.print(sb.append('\n'));
				} else {
					if( !line.contains(d)) {
						if( !only ) {
							ctx.stdout.print(line+"\n");
						}
						continue;
					}
					String [] fields = line.split(java.util.regex.Pattern.quote(d), -1);
					StringBuilder sb = new StringBuilder();
					boolean any = false;
					for (int i = 0; i < fields.length; i++) {
						if( selected(rs, i+1, comp)) {
							if( any ) {
								sb.append(od);
							}
							sb.append(fields[i]);
							any = true;
						}
					}
					ctx.stdout.print(sb.append('\n'));
				}
			}
		});
		ctx.stdout.flush();
		return ret;
	}
}
