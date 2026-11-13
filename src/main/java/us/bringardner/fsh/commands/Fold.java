package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** fold, modeled on the GNU fold: wraps long lines. */
public class Fold extends ShellCommand{
	static String name = "fold";
	static String help = "fold [-bs] [-w WIDTH] [FILE ...]\n"
			+ "\tWrap the lines of each FILE (standard input with no FILE or -) to WIDTH columns (default 80).\n"
			+ "\t-b, --bytes    count bytes, not columns (a tab is one column, not up to the next multiple of 8)\n"
			+ "\t-s, --spaces   break at the last space before the width";

	public Fold() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "bs", "w", false, "bytes:b", "spaces:s", "width=w");
		long w = a.number('w', 80);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( w < 1 ) {
			ctx.stderr.println("fold: invalid number of columns: '"+a.value('w')+"'");
			return 1;
		}
		final boolean bytes = a.has('b'), spaces = a.has('s');
		int ret = TextFiles.forEach(ctx, name, a.operands, (label, file, in) -> {
			java.io.InputStream bin = new java.io.BufferedInputStream(in);
			byte [] raw;
			while( (raw = TextFiles.line(bin)) != null ) {
				ctx.stdout.print(fold(TextFiles.chomp(raw), (int) w, bytes, spaces).append('\n'));
			}
		});
		ctx.stdout.flush();
		return ret;
	}

	private static int advance(int col, char c, boolean bytes) {
		if( c == '\t' && !bytes ) {
			return (col/8+1)*8;
		}
		if( c == '\b' && !bytes ) {
			return Math.max(0, col-1);
		}
		if( c == '\r' && !bytes ) {
			return 0;
		}
		return col+1;
	}

	static StringBuilder fold(String line, int width, boolean bytes, boolean spaces) {
		StringBuilder out = new StringBuilder();
		StringBuilder cur = new StringBuilder();
		int col = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			int next = advance(col, c, bytes);
			if( next > width && c != '\b' && c != '\r' ) {
				if( spaces ) {
					int sp = Math.max(cur.lastIndexOf(" "), cur.lastIndexOf("\t"));
					if( sp >= 0 ) {
						out.append(cur, 0, sp+1).append('\n');
						cur.delete(0, sp+1);
						col = 0;
						for (int k = 0; k < cur.length(); k++) {
							col = advance(col, cur.charAt(k), bytes);
						}
						next = advance(col, c, bytes);
					} else {
						out.append(cur).append('\n');
						cur.setLength(0);
						col = 0;
						next = advance(0, c, bytes);
					}
				} else {
					out.append(cur).append('\n');
					cur.setLength(0);
					col = 0;
					next = advance(0, c, bytes);
				}
			}
			cur.append(c);
			col = next;
		}
		return out.append(cur);
	}
}
