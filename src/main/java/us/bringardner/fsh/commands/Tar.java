package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** tar, modeled on the GNU tar: makes, lists and extracts tar archives (POSIX ustar, with GNU long names), optionally gzipped. */
public class Tar extends ShellCommand{
	static String name = "tar";
	static String help = "tar -c|-x|-t [-zvO] [-f ARCHIVE] [-C DIR] [--strip-components=N] [FILE ...]\n"
			+ "\tMake (-c), extract (-x) or list (-t) a tar archive. The options can be written without the dash: tar czf a.tgz dir\n"
			+ "\t-f, --file=ARCHIVE   the archive (- or no -f is standard output when making, standard input otherwise)\n"
			+ "\t-z, --gzip           compress with gzip when making (a gzip archive is recognized when reading)\n"
			+ "\t-v, --verbose        list the files processed\n"
			+ "\t-C, --directory=DIR  change to DIR first\n"
			+ "\t-O, --to-stdout      with -x, write the files to standard output\n"
			+ "\t--strip-components=N with -x, remove N leading parts of the names\n"
			+ "\tWith -x or -t, FILEs name the members to process (a directory means everything under it).";

	public Tar() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// the old style: the first word has the options without a dash (tar czf a.tgz x)
		List<String> raw = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			raw.add(""+args[i].getValue(ctx));
		}
		if( !raw.isEmpty() && raw.get(0).matches("[a-zA-Z]+") && raw.get(0).matches(".*[cxt].*") && !raw.get(0).matches("--.*")) {
			String cluster = raw.remove(0);
			StringBuilder opts = new StringBuilder("-");
			List<String> values = new ArrayList<>();
			for (char c : cluster.toCharArray()) {
				opts.append(c);
				if( c == 'f' || c == 'C' ) {
					if( raw.isEmpty()) {
						ctx.stderr.println("tar: option requires an argument -- '"+c+"'");
						return 2;
					}
					values.add(raw.remove(0));
				}
			}
			// -czf takes its values next, in the order of the letters
			List<String> rebuilt = new ArrayList<>();
			rebuilt.add(opts.toString());
			rebuilt.addAll(values);
			rebuilt.addAll(raw);
			raw = rebuilt;
		}
		Args a = Args.parseWords(raw, "cxtzvOkpjJ", "fCS", null, "create:c", "extract:x", "get:x", "list:t", "gzip:z", "gunzip:z", "ungzip:z",
				"verbose:v", "to-stdout:O", "file=f", "directory=C", "strip-components=S");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		int ops = (a.has('c') ? 1 : 0)+(a.has('x') ? 1 : 0)+(a.has('t') ? 1 : 0);
		if( ops != 1 ) {
			ctx.stderr.println("tar: "+(ops == 0 ? "You must specify one of the '-ctx' options" : "You may not specify more than one '-ctx' option"));
			return 2;
		}
		String archive = a.value('f');
		String dir = a.value('C') == null ? "" : (a.value('C').endsWith("/") ? a.value('C') : a.value('C')+"/");
		boolean verbose = a.has('v');
		try {
			if( a.has('c')) {
				return create(ctx, a, archive, dir, verbose);
			}
			InputStream in;
			if( archive == null || archive.equals("-")) {
				in = ctx.stdin;
			} else {
				FileSource f = ctx.console.createFileSource(archive);
				if( !f.exists()) {
					ctx.stderr.println("tar: "+archive+": Cannot open: No such file or directory");
					ctx.stderr.println("tar: Error is not recoverable: exiting now");
					return 2;
				}
				in = f.getInputStream();
			}
			try (InputStream raw2 = in) {
				return read(ctx, a, raw2, dir, verbose);
			}
		} catch (IOException e) {
			ctx.stderr.println("tar: "+e.getMessage());
			return 2;
		}
	}

	// ------------------------------------------------------------ writing

	private static void field(byte [] h, int off, int len, String s) {
		byte [] b = s.getBytes(StandardCharsets.UTF_8);
		System.arraycopy(b, 0, h, off, Math.min(b.length, len));
	}

	private static void octal(byte [] h, int off, int len, long v) {
		String s = Long.toOctalString(v);
		StringBuilder z = new StringBuilder();
		for (int i = s.length(); i < len-1; i++) {
			z.append('0');
		}
		field(h, off, len, z+s);
	}

	private static byte [] header(String name, long size, long mtime, int mode, char type, String user) {
		byte [] h = new byte[512];
		String prefix = "";
		byte [] nb = name.getBytes(StandardCharsets.UTF_8);
		if( nb.length > 100 ) {
			// split at a slash so the last part fits in 100 and the first in 155
			int cut = -1;
			for (int i = name.length()-1; i > 0; i--) {
				if( name.charAt(i) == '/' && name.substring(i+1).getBytes(StandardCharsets.UTF_8).length <= 100 && name.substring(0, i).getBytes(StandardCharsets.UTF_8).length <= 155 ) {
					cut = i;
					break;
				}
			}
			if( cut > 0 ) {
				prefix = name.substring(0, cut);
				name = name.substring(cut+1);
			}
		}
		field(h, 0, 100, name);
		octal(h, 100, 8, mode);
		octal(h, 108, 8, 0);
		octal(h, 116, 8, 0);
		octal(h, 124, 12, size);
		octal(h, 136, 12, mtime);
		Arrays.fill(h, 148, 156, (byte) ' ');
		h[156] = (byte) type;
		field(h, 257, 6, "ustar");
		field(h, 263, 2, "00");
		field(h, 265, 32, user);
		field(h, 297, 32, user);
		field(h, 345, 155, prefix);
		long sum = 0;
		for (byte b : h) {
			sum += b & 0xff;
		}
		String cs = Long.toOctalString(sum);
		StringBuilder z = new StringBuilder();
		for (int i = cs.length(); i < 6; i++) {
			z.append('0');
		}
		field(h, 148, 7, z+cs);
		h[155] = ' ';
		return h;
	}

	private int create(ShellContext ctx, Args a, String archive, String dir, boolean verbose) throws IOException {
		if( a.operands.isEmpty()) {
			ctx.stderr.println("tar: Cowardly refusing to create an empty archive");
			return 2;
		}
		OutputStream raw;
		boolean toStdout = archive == null || archive.equals("-");
		raw = toStdout ? ctx.stdout : ctx.console.createFileSource(archive).getOutputStream();
		int ret = 0;
		ByteArrayOutputStream list = new ByteArrayOutputStream();
		GZIPOutputStream gz = a.has('z') ? new GZIPOutputStream(raw) : null;
		OutputStream out = gz != null ? gz : raw;
		String user = System.getProperty("user.name") == null ? "" : System.getProperty("user.name");
		try {
			for (String p : a.operands) {
				String member = p.replaceAll("^[A-Za-z]:(?=[/\\\\])", "").replace('\\', '/');
				if( member.startsWith("/")) {
					ctx.stderr.println("tar: Removing leading `/' from member names");
					member = member.replaceAll("^/+", "");
				}
				FileSource f = ctx.console.createFileSource(dir+p);
				if( !f.exists()) {
					ctx.stderr.println("tar: "+p+": Cannot stat: No such file or directory");
					ret = 2;
					continue;
				}
				ret = Math.max(ret, add(out, f, member, user, verbose, ctx));
			}
			out.write(new byte[1024]);
		} finally {
			if( gz != null ) {
				gz.finish();
			}
			out.flush();
			if( !toStdout ) {
				raw.close();
			}
		}
		return ret;
	}

	private int add(OutputStream out, FileSource f, String name, String user, boolean verbose, ShellContext ctx) throws IOException {
		int ret = 0;
		boolean dir = f.isDirectory();
		String entry = dir && !name.endsWith("/") ? name+"/" : name;
		long mtime = f.lastModified()/1000;
		int mode = dir || f.canExecute() ? 0755 : 0644;
		byte [] nameBytes = entry.getBytes(StandardCharsets.UTF_8);
		if( nameBytes.length > 100 && !fitsPrefix(entry)) {
			// a GNU long name: a pseudo entry that holds the name
			byte [] data = new byte[nameBytes.length+1];
			System.arraycopy(nameBytes, 0, data, 0, nameBytes.length);
			out.write(header("././@LongLink", data.length, 0, 0, 'L', ""));
			out.write(data);
			pad(out, data.length);
			entry = new String(nameBytes, 0, 100, StandardCharsets.UTF_8);
		}
		if( verbose ) {
			ctx.stdout.println(dir && !name.endsWith("/") ? name+"/" : name);
		}
		if( dir ) {
			out.write(header(entry, 0, mtime, mode, '5', user));
			FileSource [] kids = f.listFiles();
			if( kids != null ) {
				Arrays.sort(kids, (x, y) -> x.getName().compareTo(y.getName()));
				for (FileSource k : kids) {
					ret = Math.max(ret, add(out, k, (name.endsWith("/") ? name : name+"/")+k.getName(), user, verbose, ctx));
				}
			}
			return ret;
		}
		long size = f.length();
		out.write(header(entry, size, mtime, mode, '0', user));
		long written = 0;
		try (InputStream in = f.getInputStream()) {
			byte [] buf = new byte[65536];
			int n;
			while( written < size && (n = in.read(buf, 0, (int) Math.min(buf.length, size-written))) >= 0 ) {
				out.write(buf, 0, n);
				written += n;
			}
		}
		if( written < size ) {
			// the file shrank: pad with zeros so the archive stays readable
			byte [] zeros = new byte[(int) Math.min(65536, size-written)];
			while( written < size ) {
				int n = (int) Math.min(zeros.length, size-written);
				out.write(zeros, 0, n);
				written += n;
			}
			ctx.stderr.println("tar: "+name+": File shrank by "+(size-written)+" bytes; padding with zeros");
		}
		pad(out, size);
		return ret;
	}

	private static boolean fitsPrefix(String name) {
		byte [] nb = name.getBytes(StandardCharsets.UTF_8);
		for (int i = name.length()-1; i > 0; i--) {
			if( name.charAt(i) == '/' && name.substring(i+1).getBytes(StandardCharsets.UTF_8).length <= 100 && name.substring(0, i).getBytes(StandardCharsets.UTF_8).length <= 155 ) {
				return nb.length > 0;
			}
		}
		return false;
	}

	private static void pad(OutputStream out, long size) throws IOException {
		int rest = (int) (size%512);
		if( rest != 0 ) {
			out.write(new byte[512-rest]);
		}
	}

	// ------------------------------------------------------------ reading

	private static String string(byte [] h, int off, int len) {
		int end = off;
		while( end < off+len && h[end] != 0 ) {
			end++;
		}
		return new String(h, off, end-off, StandardCharsets.UTF_8);
	}

	private static long number(byte [] h, int off, int len) {
		if( (h[off] & 0x80) != 0 ) {
			// base 256, for sizes of 8 GB and up
			long v = h[off] & 0x7f;
			for (int i = 1; i < len; i++) {
				v = (v << 8) | (h[off+i] & 0xff);
			}
			return v;
		}
		String s = string(h, off, len).trim();
		return s.isEmpty() ? 0 : Long.parseLong(s, 8);
	}

	private int read(ShellContext ctx, Args a, InputStream raw, String dir, boolean verbose) throws IOException {
		PushbackInputStream pb = new PushbackInputStream(new BufferedInputStream(raw), 2);
		int b0 = pb.read(), b1 = pb.read();
		if( b1 >= 0 ) {
			pb.unread(b1);
		}
		if( b0 >= 0 ) {
			pb.unread(b0);
		}
		InputStream in = b0 == 0x1f && b1 == 0x8b ? new GZIPInputStream(pb) : pb;
		boolean extract = a.has('x');
		boolean toStdout = a.has('O');
		int strip = (int) a.number('S', 0);
		List<String> wanted = a.operands;
		int ret = 0;
		String longName = null;
		String paxPath = null;
		byte [] h = new byte[512];
		int zeroBlocks = 0;
		while( true ) {
			int got = in.readNBytes(h, 0, 512);
			if( got < 512 ) {
				if( got > 0 ) {
					ctx.stderr.println("tar: Unexpected EOF in archive");
					return 2;
				}
				break;
			}
			boolean zero = true;
			for (byte x : h) {
				if( x != 0 ) {
					zero = false;
					break;
				}
			}
			if( zero ) {
				if( ++zeroBlocks >= 2 ) {
					break;
				}
				continue;
			}
			zeroBlocks = 0;
			long cs = number(h, 148, 8), sum = 0;
			for (int i = 0; i < 512; i++) {
				sum += (i >= 148 && i < 156) ? ' ' : h[i] & 0xff;
			}
			if( cs != sum ) {
				ctx.stderr.println("tar: This does not look like a tar archive");
				ctx.stderr.println("tar: Exiting with failure status due to previous errors");
				return 2;
			}
			String name = string(h, 0, 100);
			if( string(h, 257, 5).equals("ustar") && !string(h, 345, 155).isEmpty()) {
				name = string(h, 345, 155)+"/"+name;
			}
			long size = number(h, 124, 12);
			long mtime = number(h, 136, 12);
			char type = h[156] == 0 ? '0' : (char) h[156];
			if( type == 'L' || type == 'x' ) {
				byte [] data = in.readNBytes((int) size);
				skipPad(in, size);
				String text = new String(data, StandardCharsets.UTF_8);
				if( type == 'L' ) {
					longName = text.replace("\0", "");
				} else {
					for (String line : text.split("\n")) {
						int sp = line.indexOf(' ');
						int eq = line.indexOf('=');
						if( sp > 0 && eq > sp && line.substring(sp+1, eq).equals("path")) {
							paxPath = line.substring(eq+1);
						}
					}
				}
				continue;
			}
			if( longName != null ) {
				name = longName;
				longName = null;
			}
			if( paxPath != null ) {
				name = paxPath;
				paxPath = null;
			}
			boolean isDir = type == '5' || name.endsWith("/");
			boolean take = type == '0' || type == '5' || type == '7';
			String member = name;
			if( !wanted.isEmpty() && !wantedMember(wanted, member)) {
				take = false;
			}
			if( !take ) {
				skip(in, size);
				if( type == '1' || type == '2' ) {
					if( wanted.isEmpty() || wantedMember(wanted, member)) {
						ctx.stderr.println("tar: "+member+": links are not supported; skipped");
					}
				}
				continue;
			}
			if( verbose || !extract ) {
				if( verbose || !extract ) {
					ctx.stdout.println(member);
				}
			}
			if( !extract ) {
				skip(in, size);
				continue;
			}
			// the name on disk
			String target = member;
			for (int s = 0; s < strip && target != null; s++) {
				int slash = target.indexOf('/');
				target = slash < 0 ? null : target.substring(slash+1);
			}
			if( target == null || target.isEmpty()) {
				skip(in, size);
				continue;
			}
			if( target.startsWith("/")) {
				target = target.replaceAll("^/+", "");
			}
			if( target.matches("^[A-Za-z]:.*") || target.equals("..") || target.startsWith("../") || target.contains("/../") || target.endsWith("/..")) {
				ctx.stderr.println("tar: "+member+": Member name contains '..'");
				skip(in, size);
				ret = 2;
				continue;
			}
			if( toStdout ) {
				if( !isDir ) {
					copy(in, ctx.stdout, size);
					skipPad(in, size);
				}
				continue;
			}
			FileSource f = ctx.console.createFileSource(dir+target);
			if( isDir ) {
				f.mkdirs();
				continue;
			}
			FileSource parent = f.getParentFile();
			if( parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			try (OutputStream os = f.getOutputStream()) {
				copy(in, os, size);
			}
			skipPad(in, size);
			if( mtime > 0 ) {
				f.setLastModifiedTime(mtime*1000);
			}
		}
		ctx.stdout.flush();
		return ret;
	}

	private static boolean wantedMember(List<String> wanted, String member) {
		String m = member.endsWith("/") ? member.substring(0, member.length()-1) : member;
		for (String w : wanted) {
			String x = w.endsWith("/") ? w.substring(0, w.length()-1) : w;
			if( m.equals(x) || m.startsWith(x+"/")) {
				return true;
			}
		}
		return false;
	}

	private static void copy(InputStream in, OutputStream out, long size) throws IOException {
		byte [] buf = new byte[65536];
		long left = size;
		while( left > 0 ) {
			int n = in.read(buf, 0, (int) Math.min(buf.length, left));
			if( n < 0 ) {
				throw new IOException("Unexpected EOF in archive");
			}
			out.write(buf, 0, n);
			left -= n;
		}
	}

	private static void skip(InputStream in, long size) throws IOException {
		long total = size+((512-size%512)%512);
		while( total > 0 ) {
			long n = in.skip(total);
			if( n <= 0 ) {
				if( in.read() < 0 ) {
					return;
				}
				n = 1;
			}
			total -= n;
		}
	}

	private static void skipPad(InputStream in, long size) throws IOException {
		long rest = (512-size%512)%512;
		while( rest > 0 ) {
			long n = in.skip(rest);
			if( n <= 0 ) {
				if( in.read() < 0 ) {
					return;
				}
				n = 1;
			}
			rest -= n;
		}
	}
}
