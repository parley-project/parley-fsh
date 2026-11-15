package us.bringardner.fsh.commands;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.archive.compress.Compression;
import us.bringardner.parley.files.archive.tar.TarArchiveEntry;
import us.bringardner.parley.files.archive.tar.TarStreamReader;
import us.bringardner.parley.files.archive.tar.TarWriter;

/** tar, modeled on the GNU tar: makes (POSIX pax), lists and extracts tar archives (v7, ustar, GNU and pax), optionally gzipped. The format is parley-files-archive's. */
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
	//
	// The tar format is parley-files-archive's (TarWriter, TarStreamReader): this command only
	// walks the files and says what it did.

	/** stdout, which closing the archive mustn't close */
	private static OutputStream unclosable(OutputStream out) {
		return new FilterOutputStream(out) {
			@Override
			public void write(byte [] b, int off, int len) throws IOException {
				out.write(b, off, len);
			}

			@Override
			public void close() throws IOException {
				flush();
			}
		};
	}

	private int create(ShellContext ctx, Args a, String archive, String dir, boolean verbose) throws IOException {
		if( a.operands.isEmpty()) {
			ctx.stderr.println("tar: Cowardly refusing to create an empty archive");
			return 2;
		}
		boolean toStdout = archive == null || archive.equals("-");
		OutputStream raw = toStdout ? unclosable(ctx.stdout) : ctx.console.createFileSource(archive).getOutputStream();
		int ret = 0;
		String user = System.getProperty("user.name") == null ? "" : System.getProperty("user.name");
		try (TarWriter out = new TarWriter(a.has('z') ? Compression.GZIP.compress(raw) : raw)) {
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
		}
		return ret;
	}

	private int add(TarWriter out, FileSource f, String name, String user, boolean verbose, ShellContext ctx) throws IOException {
		int ret = 0;
		boolean dir = f.isDirectory();
		long mtime = f.lastModified()/1000*1000;
		int mode = dir || f.canExecute() ? 0755 : 0644;
		if( verbose ) {
			ctx.stdout.println(dir && !name.endsWith("/") ? name+"/" : name);
		}
		TarArchiveEntry.Builder b = TarArchiveEntry.builder(name).lastModified(mtime).unixMode(mode).names(user, user);
		if( dir ) {
			out.putEntry(b.directory().build()).close();
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
		long written = 0;
		try (OutputStream entry = out.putEntry(b.size(size).build())) {
			try (InputStream in = f.getInputStream()) {
				byte [] buf = new byte[65536];
				int n;
				while( written < size && (n = in.read(buf, 0, (int) Math.min(buf.length, size-written))) >= 0 ) {
					entry.write(buf, 0, n);
					written += n;
				}
			}
			if( written < size ) {
				// the file shrank: pad with zeros so the archive stays readable
				long shrank = size-written;
				byte [] zeros = new byte[(int) Math.min(65536, shrank)];
				while( written < size ) {
					int n = (int) Math.min(zeros.length, size-written);
					entry.write(zeros, 0, n);
					written += n;
				}
				ctx.stderr.println("tar: "+name+": File shrank by "+shrank+" bytes; padding with zeros");
			}
		}
		return ret;
	}

	// ------------------------------------------------------------ reading

	private int read(ShellContext ctx, Args a, InputStream raw, String dir, boolean verbose) throws IOException {
		boolean extract = a.has('x');
		boolean toStdout = a.has('O');
		int strip = (int) a.number('S', 0);
		List<String> wanted = a.operands;
		int ret = 0;
		// (a gzipped archive is recognized by its first bytes)
		TarStreamReader in;
		try {
			in = new TarStreamReader(new FilterInputStream(raw) {
				@Override
				public void close() {
					// the caller closes the archive's stream
				}
			});
		} catch (IOException e) {
			ctx.stderr.println("tar: This does not look like a tar archive");
			return 2;
		}
		try (in) {
			while( true ) {
				TarArchiveEntry e;
				try {
					e = in.nextEntry();
				} catch (java.io.EOFException ex) {
					ctx.stderr.println("tar: Unexpected EOF in archive");
					return 2;
				} catch (IOException ex) {
					ctx.stderr.println("tar: This does not look like a tar archive");
					ctx.stderr.println("tar: Exiting with failure status due to previous errors");
					return 2;
				}
				if( e == null ) {
					break;
				}
				String member = e.getRawName();
				boolean isDir = e.isDirectory();
				if( isDir && !member.endsWith("/")) {
					member += "/";
				}
				boolean take = e.isRegularFile() || isDir;
				if( !wanted.isEmpty() && !wantedMember(wanted, member)) {
					continue;
				}
				if( !take ) {
					if( e.isHardLink() || e.isSymbolicLink()) {
						ctx.stderr.println("tar: "+member+": links are not supported; skipped");
					}
					continue;
				}
				if( verbose || !extract ) {
					ctx.stdout.println(member);
				}
				if( !extract ) {
					continue;
				}
				// the name on disk
				String target = member;
				for (int s = 0; s < strip && target != null; s++) {
					int slash = target.indexOf('/');
					target = slash < 0 ? null : target.substring(slash+1);
				}
				if( target == null || target.isEmpty()) {
					continue;
				}
				if( target.startsWith("/")) {
					target = target.replaceAll("^/+", "");
				}
				if( target.matches("^[A-Za-z]:.*") || target.equals("..") || target.startsWith("../") || target.contains("/../") || target.endsWith("/..")) {
					ctx.stderr.println("tar: "+member+": Member name contains '..'");
					ret = 2;
					continue;
				}
				if( toStdout ) {
					if( !isDir ) {
						in.getInputStream().transferTo(ctx.stdout);
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
					in.getInputStream().transferTo(os);
				}
				if( e.getLastModified() > 0 ) {
					f.setLastModifiedTime(e.getLastModified());
				}
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
}
