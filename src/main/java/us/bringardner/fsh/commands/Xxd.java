package us.bringardner.fsh.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** xxd: a hex dump of a file, and back again. */
public class Xxd extends ShellCommand{
	static String name = "xxd";
	static String help = "xxd [-pru] [-c COLS] [-g BYTES] [-l LEN] [-s SEEK] [FILE]\n"
			+ "\tMake a hex dump of FILE (standard input with no FILE or -).\n"
			+ "\t-c COLS    bytes per line (default 16; 30 with -p)\n"
			+ "\t-g BYTES   bytes in a group (default 2)\n"
			+ "\t-l LEN     stop after LEN bytes\n"
			+ "\t-s SEEK    start at byte SEEK\n"
			+ "\t-p         plain: just the hex digits\n"
			+ "\t-u         upper case hex digits\n"
			+ "\t-r         the other way: turn a hex dump (or, with -p, plain hex) back into the bytes";

	public Xxd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "pru", "cglsb", false, "plain:p", "revert:r", "uppercase:u", "cols=c", "groupsize=g", "len=l", "seek=s");
		boolean plain = a.has('p');
		long cols = a.number('c', plain ? 30 : 16), group = a.number('g', 2), len = a.number('l', -1), seek = a.number('s', 0);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( cols < 1 || group < 1 ) {
			ctx.stderr.println("xxd: invalid number of columns");
			return 1;
		}
		if( a.operands.size() > 1 ) {
			ctx.stderr.println("xxd: extra operand '"+a.operands.get(1)+"'");
			return 1;
		}
		byte [] data = TextFiles.readAll(ctx, name, a.operands.isEmpty() ? "-" : a.operands.get(0));
		if( data == null ) {
			return 1;
		}
		if( a.has('r')) {
			return revert(ctx, new String(data, java.nio.charset.StandardCharsets.ISO_8859_1), plain);
		}
		int start = (int) Math.min(Math.max(seek, 0), data.length);
		int end = len >= 0 ? (int) Math.min(data.length, start+len) : data.length;
		String fmt = a.has('u') ? "%02X" : "%02x";
		StringBuilder out = new StringBuilder();
		for (int pos = start; pos < end; pos += cols) {
			int n = (int) Math.min(cols, end-pos);
			if( plain ) {
				for (int i = 0; i < n; i++) {
					out.append(String.format(fmt, data[pos+i] & 0xff));
				}
				out.append('\n');
				continue;
			}
			out.append(String.format(a.has('u') ? "%08X:" : "%08x:", pos));
			for (int g = 0; g < cols; g += group) {
				out.append(' ');
				for (int i = g; i < g+group && i < cols; i++) {
					out.append(i < n ? String.format(fmt, data[pos+i] & 0xff) : "  ");
				}
			}
			out.append("  ");
			for (int i = 0; i < n; i++) {
				int c = data[pos+i] & 0xff;
				out.append(c >= 32 && c < 127 ? (char) c : '.');
			}
			out.append('\n');
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return 0;
	}

	private int revert(ShellContext ctx, String text, boolean plain) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (String line : text.split("\r?\n")) {
			String hex = line;
			if( !plain ) {
				int colon = line.indexOf(':');
				if( colon < 0 ) {
					continue;
				}
				hex = line.substring(colon+1);
			}
			// hex digits in pairs; single spaces between groups; two spaces in a row end the hex (the text column follows)
			StringBuilder digits = new StringBuilder();
			char prev = 0;
			for (char c : hex.toCharArray()) {
				if( !plain && c == ' ' && prev == ' ' ) {
					break;
				}
				if( Character.digit(c, 16) >= 0 ) {
					digits.append(c);
				}
				prev = c;
			}
			if( digits.length() % 2 != 0 ) {
				digits.setLength(digits.length()-1);
			}
			for (int i = 0; i < digits.length(); i += 2) {
				out.write(Integer.parseInt(digits.substring(i, i+2), 16));
			}
		}
		ctx.stdout.write(out.toByteArray(), 0, out.size());
		ctx.stdout.flush();
		return 0;
	}
}
