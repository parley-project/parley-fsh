package us.bringardner.fsh.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * What the text commands (head, tail, sort, uniq, cut ...) share: running a handler on each file
 * named (or on standard input), with the messages and the exit status the GNU commands give.
 */
final class TextFiles {

	private TextFiles() {
	}

	interface Handler {
		/** @param file null for standard input */
		void handle(String label, FileSource file, InputStream in) throws IOException;
	}

	/**
	 * Run handler on each of files: - and no files at all are standard input. A file that is
	 * missing or is a directory is reported and the rest go on.
	 * @return 0, or 1 if a file couldn't be read
	 */
	static int forEach(ShellContext ctx, String cmd, List<String> files, Handler handler) throws IOException {
		int ret = 0;
		if( files.isEmpty()) {
			files = new ArrayList<>();
			files.add("-");
		}
		for (String path : files) {
			if( path.equals("-")) {
				handler.handle("standard input", null, ctx.stdin);
				continue;
			}
			int slash = path.lastIndexOf('/');
			String dirPrefix = slash >= 0 ? path.substring(0, slash+1) : "";
			boolean glob = ShellCommand.hasWildcard(path) && !ctx.console.createFileSource(path).exists();
			for (FileSource file : ShellCommand.getFiles(ctx, path)) {
				String label = glob ? dirPrefix+file.getName() : path;
				if( !file.exists()) {
					ctx.stderr.println(cmd+": "+label+": No such file or directory");
					ret = 1;
				} else if( file.isDirectory()) {
					ctx.stderr.println(cmd+": "+label+": Is a directory");
					ret = 1;
				} else {
					try (InputStream in = file.getInputStream()) {
						handler.handle(label, file, in);
					} catch (IOException e) {
						ctx.stderr.println(cmd+": "+label+": "+e.getMessage());
						ret = 1;
					}
				}
			}
		}
		return ret;
	}

	/** the next line of in with its \n (the last may have none), or null at the end */
	static byte [] line(InputStream in) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
		int c;
		while( (c = in.read()) >= 0 ) {
			buf.write(c);
			if( c == '\n' ) {
				break;
			}
		}
		return buf.size() == 0 ? null : buf.toByteArray();
	}

	private static final Pattern COUNT = Pattern.compile("(\\d+)(b|kB|KB|K|k|MB|M|m|GB|G|g)?");

	/**
	 * A count with the multiplier suffixes of head and tail: b 512, kB 1000, K 1024, MB 1000000, M
	 * 1048576, GB, G.
	 * @throws NumberFormatException if it is not one
	 */
	static long count(String text) {
		Matcher m = COUNT.matcher(text.trim());
		if( !m.matches()) {
			throw new NumberFormatException(text);
		}
		long n = Long.parseLong(m.group(1));
		String unit = m.group(2);
		if( unit == null ) {
			return n;
		}
		switch (unit) {
		case "b": return n*512;
		case "kB": case "KB": return n*1000;
		case "K": case "k": return n*1024;
		case "MB": return n*1000000;
		case "M": case "m": return n*1048576;
		case "GB": return n*1000000000L;
		default: return n*1073741824L;
		}
	}

	/** skip n bytes of in, or all of it if it is shorter */
	static void skip(InputStream in, long n) throws IOException {
		while( n > 0 ) {
			long got = in.skip(n);
			if( got <= 0 ) {
				if( in.read() < 0 ) {
					return;
				}
				got = 1;
			}
			n -= got;
		}
	}

	/** the text of a line as a string without its line end */
	static String chomp(byte [] line) {
		int n = line.length;
		if( n > 0 && line[n-1] == '\n' ) {
			n--;
		}
		return new String(line, 0, n, java.nio.charset.StandardCharsets.UTF_8);
	}
}
