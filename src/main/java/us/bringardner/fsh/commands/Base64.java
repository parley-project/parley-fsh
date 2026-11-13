package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** base64, modeled on the GNU base64: encodes or decodes data. */
public class Base64 extends ShellCommand{
	static String name = "base64";
	static String help = "base64 [-di] [-w COLS] [FILE]\n"
			+ "\tEncode FILE (standard input with no FILE or -) as base64, or with -d decode it.\n"
			+ "\t-d, --decode          decode\n"
			+ "\t-i, --ignore-garbage  when decoding, skip characters that aren't base64\n"
			+ "\t-w, --wrap=COLS       wrap the encoded lines after COLS characters (default 76; 0 for no wrapping)";

	public Base64() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "di", "w", false, "decode:d", "ignore-garbage:i", "wrap=w");
		long wrap = a.number('w', 76);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.size() > 1 ) {
			ctx.stderr.println("base64: extra operand '"+a.operands.get(1)+"'");
			return 1;
		}
		byte [] data = TextFiles.readAll(ctx, name, a.operands.isEmpty() ? "-" : a.operands.get(0));
		if( data == null ) {
			return 1;
		}
		if( a.has('d')) {
			String text = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
			text = a.has('i') ? text.replaceAll("[^A-Za-z0-9+/=]", "") : text.replaceAll("[\r\n]", "");
			byte [] decoded;
			try {
				decoded = java.util.Base64.getDecoder().decode(text);
			} catch (IllegalArgumentException e) {
				ctx.stderr.println("base64: invalid input");
				return 1;
			}
			ctx.stdout.write(decoded, 0, decoded.length);
		} else {
			String enc = java.util.Base64.getEncoder().encodeToString(data);
			StringBuilder out = new StringBuilder();
			if( wrap <= 0 ) {
				out.append(enc);
			} else {
				for (int i = 0; i < enc.length(); i += wrap) {
					out.append(enc, i, (int) Math.min(enc.length(), i+wrap)).append('\n');
				}
			}
			if( wrap <= 0 && data.length > 0 ) {
				out.append('\n');
			}
			ctx.stdout.print(out);
		}
		ctx.stdout.flush();
		return 0;
	}
}
