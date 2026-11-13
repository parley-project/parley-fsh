package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** hexdump: shows the bytes of files in hex (the canonical -C format, or 16 bit words). */
public class Hexdump extends ShellCommand{
	static String name = "hexdump";
	static String help = "hexdump [-Cv] [-n LENGTH] [-s OFFSET] [FILE ...]\n"
			+ "\tShow the bytes of each FILE (standard input with no FILE or -) in hex.\n"
			+ "\t-C, --canonical   offset, 16 bytes in hex, and the same as text between | |\n"
			+ "\t                  (without it: 8 words of 2 bytes, as the little-endian numbers they are)\n"
			+ "\t-n, --length=N    only the first N bytes\n"
			+ "\t-s, --skip=N      start at byte N\n"
			+ "\t-v, --no-squeezing  show every line (otherwise a line like the one before is replaced by a *)";

	public Hexdump() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "Cv", "ns", false, "canonical:C", "no-squeezing:v", "length=n", "skip=s");
		long length = a.number('n', -1), skip = a.number('s', 0);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
		int ret = TextFiles.forEach(ctx, name, a.operands, (label, file, in) -> in.transferTo(all));
		byte [] data = all.toByteArray();
		int start = (int) Math.min(Math.max(skip, 0), data.length);
		int end = length >= 0 ? (int) Math.min(data.length, start+length) : data.length;
		boolean canonical = a.has('C'), verbose = a.has('v');
		StringBuilder out = new StringBuilder();
		String previous = null;
		boolean squeezing = false;
		for (int pos = start; pos < end; pos += 16) {
			int n = Math.min(16, end-pos);
			StringBuilder line = new StringBuilder();
			if( canonical ) {
				for (int i = 0; i < 16; i++) {
					if( i == 8 ) {
						line.append(' ');
					}
					line.append(i < n ? String.format("%02x ", data[pos+i] & 0xff) : "   ");
				}
				line.append(" |");
				for (int i = 0; i < n; i++) {
					int c = data[pos+i] & 0xff;
					line.append(c >= 32 && c < 127 ? (char) c : '.');
				}
				line.append('|');
			} else {
				for (int i = 0; i < 16; i += 2) {
					if( i < n ) {
						int lo = data[pos+i] & 0xff, hi = i+1 < n ? data[pos+i+1] & 0xff : 0;
						line.append(String.format(" %04x", hi << 8 | lo));
					}
				}
			}
			String content = line.toString();
			if( !verbose && content.equals(previous) && n == 16 ) {
				if( !squeezing ) {
					out.append("*\n");
					squeezing = true;
				}
				continue;
			}
			squeezing = false;
			previous = content;
			out.append(canonical ? String.format("%08x  ", pos) : String.format("%07x", pos)).append(content).append('\n');
		}
		if( end > start ) {
			out.append(canonical ? String.format("%08x%n", end) : String.format("%07x%n", end));
		}
		ctx.stdout.print(out.toString().replace("\r\n", "\n"));
		ctx.stdout.flush();
		return ret;
	}
}
