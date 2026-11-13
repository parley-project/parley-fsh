package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.regex.Pattern;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** nl, modeled on the GNU nl: numbers the lines of the files. */
public class Nl extends ShellCommand{
	static String name = "nl";
	static String help = "nl [OPTION]... [FILE ...]\n"
			+ "\tWrite each FILE (standard input with no FILE or -) with line numbers added.\n"
			+ "\t-b, --body-numbering=STYLE   which lines to number: a (all), t (non-empty, the default), n (none), pREGEX\n"
			+ "\t-n, --number-format=FORMAT   ln (left), rn (right, the default), rz (right with zeros)\n"
			+ "\t-w, --number-width=N         the width of the number (default 6)\n"
			+ "\t-s, --number-separator=STR   what goes after the number (default: a tab)\n"
			+ "\t-v, --starting-line-number=N the first number (default 1)\n"
			+ "\t-i, --line-increment=N       the step (default 1)";

	public Nl() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "", "bnwsvi", false,
				"body-numbering=b", "number-format=n", "number-width=w", "number-separator=s", "starting-line-number=v", "line-increment=i");
		String style = a.value('b') == null ? "t" : a.value('b');
		String format = a.value('n') == null ? "rn" : a.value('n');
		long width = a.number('w', 6), start = a.number('v', 1), step = a.number('i', 1);
		String sep = a.value('s') == null ? "\t" : a.value('s');
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( !format.equals("ln") && !format.equals("rn") && !format.equals("rz")) {
			ctx.stderr.println("nl: invalid line numbering format: '"+format+"'");
			return 1;
		}
		Pattern regex = null;
		if( style.startsWith("p")) {
			regex = Pattern.compile(Grep.convert(style.substring(1), false));
		} else if( !style.equals("a") && !style.equals("t") && !style.equals("n")) {
			ctx.stderr.println("nl: invalid body numbering style: '"+style+"'");
			return 1;
		}
		final Pattern re = regex;
		long [] n = {start};
		int ret = TextFiles.forEach(ctx, name, a.operands, (label, file, in) -> {
			java.io.InputStream bin = new java.io.BufferedInputStream(in);
			byte [] raw;
			while( (raw = TextFiles.line(bin)) != null ) {
				String line = TextFiles.chomp(raw);
				boolean number = style.equals("a") || (style.equals("t") && !line.isEmpty()) || (re != null && re.matcher(line).find());
				StringBuilder sb = new StringBuilder();
				if( number ) {
					String num = Long.toString(n[0]);
					n[0] += step;
					StringBuilder pad = new StringBuilder();
					for (long i = num.length(); i < width; i++) {
						pad.append(format.equals("rz") ? '0' : ' ');
					}
					sb.append(format.equals("ln") ? num+pad : pad+num).append(sep);
				} else if( !line.isEmpty()) {
					// (an empty line stays empty)
					for (long i = 0; i < width+sep.length(); i++) {
						sb.append(' ');
					}
				}
				ctx.stdout.print(sb.append(line).append('\n'));
			}
		});
		ctx.stdout.flush();
		return ret;
	}
}
