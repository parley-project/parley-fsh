package us.bringardner.fsh.commands;

import java.io.IOException;
import java.math.BigDecimal;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** seq, modeled on the GNU seq: prints a sequence of numbers. */
public class Seq extends ShellCommand{
	static String name = "seq";
	static String help = "seq [OPTION]... [FIRST [INCREMENT]] LAST\n"
			+ "\tPrint the numbers from FIRST (1) to LAST in steps of INCREMENT (1).\n"
			+ "\t-s, --separator=STRING   put STRING between the numbers (default: a newline)\n"
			+ "\t-w, --equal-width        pad with zeros to the same width\n"
			+ "\t-f, --format=FORMAT      a printf format for the numbers, like %05.1f";

	public Seq() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "w", "sf", true, "equal-width:w", "separator=s", "format=f");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		int n = a.operands.size();
		if( n < 1 || n > 3 ) {
			ctx.stderr.println("seq: "+(n < 1 ? "missing operand" : "extra operand '"+a.operands.get(3)+"'"));
			return 1;
		}
		BigDecimal first = BigDecimal.ONE, inc = BigDecimal.ONE, last;
		try {
			last = new BigDecimal(a.operands.get(n-1));
			if( n >= 2 ) {
				first = new BigDecimal(a.operands.get(0));
			}
			if( n == 3 ) {
				inc = new BigDecimal(a.operands.get(1));
			}
		} catch (NumberFormatException e) {
			ctx.stderr.println("seq: invalid floating point argument");
			return 1;
		}
		if( inc.signum() == 0 ) {
			ctx.stderr.println("seq: invalid zero increment value: '"+a.operands.get(1)+"'");
			return 1;
		}
		String sep = a.value('s') == null ? "\n" : a.value('s');
		String format = a.value('f');
		int scale = Math.max(first.scale(), Math.max(inc.scale(), 0));
		boolean equal = a.has('w');
		int width = 0;
		if( equal ) {
			width = Math.max(text(first, scale).length(), text(last.subtract(last.subtract(first).remainder(inc)), scale).length());
		}
		StringBuilder out = new StringBuilder();
		boolean any = false;
		long count = 0;
		for (BigDecimal v = first; inc.signum() > 0 ? v.compareTo(last) <= 0 : v.compareTo(last) >= 0; v = v.add(inc)) {
			if( any ) {
				out.append(sep);
			}
			String s;
			if( format != null ) {
				try {
					if( format.contains("%g")) {
						s = String.format(format.replace("%g", "%s"), v.stripTrailingZeros().toPlainString());
					} else if( format.matches(".*%[-+ 0#]*\\d*(\\.\\d+)?[eEf].*")) {
						s = String.format(format, v.doubleValue());
					} else {
						s = String.format(format, v.toBigInteger());
					}
				} catch (RuntimeException e) {
					ctx.stderr.println("seq: invalid format string: '"+format+"'");
					return 1;
				}
			} else {
				s = text(v, scale);
				if( equal ) {
					boolean neg = s.startsWith("-");
					String digits = neg ? s.substring(1) : s;
					StringBuilder z = new StringBuilder(digits);
					while( z.length()+(neg ? 1 : 0) < width ) {
						z.insert(0, '0');
					}
					s = (neg ? "-" : "")+z;
				}
			}
			out.append(s);
			any = true;
			if( out.length() > 8192 ) {
				ctx.stdout.print(out);
				out.setLength(0);
				if( ctx.stdout.checkError() || ctx.getException() != null ) {
					return 0;
				}
			}
			if( ++count < 0 ) {
				break;
			}
		}
		if( any ) {
			out.append('\n');
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return 0;
	}

	private static String text(BigDecimal v, int scale) {
		return v.setScale(scale, java.math.RoundingMode.HALF_EVEN).toPlainString();
	}
}
