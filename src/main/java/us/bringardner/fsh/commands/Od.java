package us.bringardner.fsh.commands;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** od, modeled on the GNU od: dumps files in octal and other formats. */
public class Od extends ShellCommand{
	static String name = "od";
	static String help = "od [OPTION]... [FILE ...]\n"
			+ "\tDump the bytes of each FILE (standard input with no FILE or -), 16 to a line, in octal 2 byte words.\n"
			+ "\t-A RADIX   the radix of the offsets: d (decimal), o (octal, the default), x (hex), n (none)\n"
			+ "\t-t TYPE    the format of the values: a (named characters), c (characters), d, o, u or x followed by the\n"
			+ "\t           size 1, 2, 4 or 8 (like x1 or d4); may be repeated\n"
			+ "\t-j BYTES   skip BYTES at the start\n"
			+ "\t-N BYTES   only dump BYTES\n"
			+ "\t-w[BYTES]  BYTES per line (default 16)\n"
			+ "\t-v         show every line (otherwise a line like the one before is replaced by a *)\n"
			+ "\t-b -c -d -o -x   short for -t o1, -t c, -t u2, -t o2, -t x2";

	public Od() {
		super(name, help);
	}

	private static final String [] NAMES = {"nul", "soh", "stx", "etx", "eot", "enq", "ack", "bel", "bs", "ht", "nl", "vt", "ff", "cr", "so", "si",
			"dle", "dc1", "dc2", "dc3", "dc4", "nak", "syn", "etb", "can", "em", "sub", "esc", "fs", "gs", "rs", "us", "sp"};

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "bcdoxv", "AtjNw", false, "address-radix=A", "format=t", "skip-bytes=j", "read-bytes=N", "width=w", "output-duplicates:v");
		long skip = a.number('j', 0), limit = a.number('N', -1), width = a.number('w', 16);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		String radix = a.value('A') == null ? "o" : a.value('A');
		if( !radix.matches("[doxn]")) {
			ctx.stderr.println("od: invalid output address radix '"+radix+"'; it must be one character from [doxn]");
			return 1;
		}
		List<String> types = new ArrayList<>(a.values('t'));
		if( a.has('b')) {
			types.add("o1");
		}
		if( a.has('c')) {
			types.add("c");
		}
		if( a.has('d')) {
			types.add("u2");
		}
		if( a.has('o')) {
			types.add("o2");
		}
		if( a.has('x')) {
			types.add("x2");
		}
		if( types.isEmpty()) {
			types.add("o2");
		}
		for (String t : types) {
			if( !t.matches("[ac]|[doux][1248]?")) {
				ctx.stderr.println("od: invalid type string '"+t+"'");
				return 1;
			}
		}
		if( width < 1 ) {
			width = 16;
		}
		java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
		int ret = TextFiles.forEach(ctx, name, a.operands, (label, file, in) -> in.transferTo(all));
		byte [] data = all.toByteArray();
		int start = (int) Math.min(skip, data.length);
		int end = limit >= 0 ? (int) Math.min(data.length, start+limit) : data.length;
		StringBuilder out = new StringBuilder();
		String previous = null;
		boolean squeezing = false;
		for (int pos = start; pos < end; pos += width) {
			int n = (int) Math.min(width, end-pos);
			StringBuilder body = new StringBuilder();
			for (int t = 0; t < types.size(); t++) {
				if( t > 0 ) {
					body.append('\n').append(address(radix, -1));
				}
				body.append(values(types.get(t), data, pos, n));
			}
			String content = body.toString();
			if( !a.has('v') && content.equals(previous) && n == width ) {
				if( !squeezing ) {
					out.append("*\n");
					squeezing = true;
				}
				continue;
			}
			squeezing = false;
			previous = content;
			out.append(address(radix, pos)).append(content).append('\n');
		}
		if( end > start || skip > 0 ) {
			String last = address(radix, end);
			if( !last.isEmpty()) {
				out.append(last.trim().isEmpty() ? "" : last).append('\n');
			}
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return ret;
	}

	private static String address(String radix, long pos) {
		if( radix.equals("n")) {
			return "";
		}
		if( pos < 0 ) {
			return radix.equals("x") ? "       " : "       ";
		}
		return String.format(radix.equals("o") ? "%07o" : radix.equals("x") ? "%06x" : "%07d", pos);
	}

	private static String named(int b) {
		if( b < NAMES.length ) {
			return NAMES[b];
		}
		if( b == 127 ) {
			return "del";
		}
		return b < 127 ? String.valueOf((char) b) : String.format("%03o", b);
	}

	private static String character(int b) {
		switch (b) {
		case 0: return "\\0";
		case 7: return "\\a";
		case 8: return "\\b";
		case 9: return "\\t";
		case 10: return "\\n";
		case 11: return "\\v";
		case 12: return "\\f";
		case 13: return "\\r";
		}
		return b >= 32 && b < 127 ? String.valueOf((char) b) : String.format("%03o", b);
	}

	private static String values(String type, byte [] data, int pos, int n) {
		StringBuilder sb = new StringBuilder();
		char kind = type.charAt(0);
		if( kind == 'a' || kind == 'c' ) {
			for (int i = 0; i < n; i++) {
				int b = data[pos+i] & 0xff;
				String s = kind == 'a' ? named(b & 0x7f) : character(b);
				sb.append(String.format(" %3s", s));
			}
			return sb.toString();
		}
		int size = type.length() > 1 ? type.charAt(1)-'0' : (kind == 'd' || kind == 'u' || kind == 'o' || kind == 'x' ? 4 : 1);
		if( type.length() == 1 ) {
			size = 4;
		}
		int width;
		switch (kind) {
		case 'o': width = new int[] {0, 3, 6, 0, 11, 0, 0, 0, 22}[size]; break;
		case 'x': width = size*2; break;
		case 'u': width = new int[] {0, 3, 5, 0, 10, 0, 0, 0, 20}[size]; break;
		default: width = new int[] {0, 4, 6, 0, 11, 0, 0, 0, 20}[size];
		}
		for (int i = 0; i < n; i += size) {
			BigInteger v = BigInteger.ZERO;
			for (int k = size-1; k >= 0; k--) {
				int b = i+k < n ? data[pos+i+k] & 0xff : 0;
				v = v.shiftLeft(8).or(BigInteger.valueOf(b));
			}
			String s;
			switch (kind) {
			case 'o': s = v.toString(8); break;
			case 'x': s = v.toString(16); break;
			case 'u': s = v.toString(); break;
			default:
				if( v.testBit(size*8-1)) {
					v = v.subtract(BigInteger.ONE.shiftLeft(size*8));
				}
				s = v.toString();
			}
			if( kind == 'o' || kind == 'x' ) {
				StringBuilder z = new StringBuilder(s);
				while( z.length() < width ) {
					z.insert(0, '0');
				}
				s = z.toString();
			}
			sb.append(String.format(" %"+width+"s", s));
		}
		return sb.toString();
	}
}
