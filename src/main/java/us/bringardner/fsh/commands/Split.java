package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** split, modeled on the GNU split: cuts a file into pieces. */
public class Split extends ShellCommand{
	static String name = "split";
	static String help = "split [OPTION]... [FILE [PREFIX]]\n"
			+ "\tWrite the pieces of FILE (standard input with no FILE or -) to PREFIXaa, PREFIXab, ... (PREFIX is x).\n"
			+ "\t-l, --lines=N        N lines per piece (default 1000)\n"
			+ "\t-b, --bytes=SIZE     SIZE bytes per piece (a K, M or G suffix works)\n"
			+ "\t-d, --numeric-suffixes   numbers 00 01 ... instead of letters\n"
			+ "\t-a, --suffix-length=N    the suffix is N long (default 2)\n"
			+ "\t--additional-suffix=SUF  add SUF to the end of each name";

	public Split() {
		super(name, help);
	}

	private static String suffix(long index, int len, boolean numeric) {
		char [] s = new char[len];
		for (int i = len-1; i >= 0; i--) {
			s[i] = numeric ? (char) ('0'+index%10) : (char) ('a'+index%26);
			index /= numeric ? 10 : 26;
		}
		return new String(s);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "d", "lbaB", false, "numeric-suffixes:d", "lines=l", "bytes=b", "suffix-length=a", "additional-suffix=B");
		long suffixLen = a.number('a', 2), lines = a.number('l', 1000);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		long bytes = -1;
		if( a.value('b') != null ) {
			try {
				bytes = TextFiles.count(a.value('b'));
			} catch (NumberFormatException e) {
				ctx.stderr.println("split: invalid number of bytes: '"+a.value('b')+"'");
				return 1;
			}
		}
		if( lines < 1 || bytes == 0 || suffixLen < 1 ) {
			ctx.stderr.println("split: invalid number");
			return 1;
		}
		if( a.operands.size() > 2 ) {
			ctx.stderr.println("split: extra operand '"+a.operands.get(2)+"'");
			return 1;
		}
		String input = a.operands.isEmpty() ? "-" : a.operands.get(0);
		String prefix = a.operands.size() > 1 ? a.operands.get(1) : "x";
		String extra = a.value('B') == null ? "" : a.value('B');
		boolean numeric = a.has('d');
		InputStream raw;
		if( input.equals("-")) {
			raw = ctx.stdin;
		} else {
			FileSource f = ctx.console.createFileSource(input);
			if( !f.exists() || f.isDirectory()) {
				ctx.stderr.println("split: cannot open '"+input+"' for reading: "+(f.exists() ? "Is a directory" : "No such file or directory"));
				return 1;
			}
			raw = f.getInputStream();
		}
		long limit = numeric ? (long) Math.pow(10, suffixLen) : (long) Math.pow(26, suffixLen);
		long index = 0;
		try (InputStream in = new BufferedInputStream(raw)) {
			while( true ) {
				// peek: don't make an empty piece at the end
				in.mark(1);
				if( in.read() < 0 ) {
					break;
				}
				in.reset();
				if( index >= limit ) {
					ctx.stderr.println("split: output file suffixes exhausted");
					return 1;
				}
				FileSource out = ctx.console.createFileSource(prefix+suffix(index++, (int) suffixLen, numeric)+extra);
				try (OutputStream os = out.getOutputStream()) {
					if( bytes > 0 ) {
						byte [] buf = new byte[8192];
						long left = bytes;
						while( left > 0 ) {
							int got = in.read(buf, 0, (int) Math.min(buf.length, left));
							if( got < 0 ) {
								break;
							}
							os.write(buf, 0, got);
							left -= got;
						}
					} else {
						for (long i = 0; i < lines; i++) {
							byte [] l = TextFiles.line(in);
							if( l == null ) {
								break;
							}
							os.write(l);
						}
					}
				}
			}
		}
		return 0;
	}
}
