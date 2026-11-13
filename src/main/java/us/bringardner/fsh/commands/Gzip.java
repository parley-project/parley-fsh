package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** gzip, gunzip and zcat, modeled on the GNU commands. */
public class Gzip extends ShellCommand{
	private final char mode;

	public Gzip() {
		this("gzip");
	}

	/** @param name gzip, gunzip (gzip -d) or zcat (gzip -dc) */
	protected Gzip(String name) {
		super(name, name.equals("gzip") ? "gzip [-cdfkv] [-1..-9] [FILE ...]\n"
				+ "\tCompress each FILE to FILE.gz and remove FILE (standard input to standard output with no FILE or -).\n"
				+ "\t-c, --stdout       write to standard output, keep the files\n"
				+ "\t-d, --decompress   decompress (same as gunzip)\n"
				+ "\t-f, --force        overwrite files that exist\n"
				+ "\t-k, --keep         keep the input files\n"
				+ "\t-v, --verbose      say what is done\n"
				+ "\t-1 .. -9           accepted (the compression level is not changed)"
				: name.equals("gunzip") ? "gunzip [-cfkv] [FILE ...]\n"
				+ "\tDecompress each FILE.gz to FILE and remove FILE.gz (same as gzip -d; options as gzip).\n"
				+ "\tWith no FILE, or -, standard input to standard output."
				: "zcat [FILE ...]\n"
				+ "\tDecompress each FILE.gz to standard output (same as gzip -dc).");
		mode = name.equals("gzip") ? 'g' : name.equals("gunzip") ? 'u' : 'z';
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "cdfklvqtr123456789", "", false, "stdout:c", "to-stdout:c", "decompress:d", "uncompress:d", "force:f", "keep:k",
				"verbose:v", "quiet:q", "fast:1", "best:9");
		if( a.error != null ) {
			return a.fail(ctx, getName());
		}
		boolean decompress = mode != 'g' || a.has('d');
		boolean toStdout = mode == 'z' || a.has('c');
		boolean keep = a.has('k') || toStdout;
		int ret = 0;
		if( a.operands.isEmpty()) {
			if( ctx.stdin == null ) {
				return 1;
			}
			copy(ctx.stdin, ctx.stdout, decompress);
			ctx.stdout.flush();
			return 0;
		}
		for (String p : a.operands) {
			if( p.equals("-")) {
				copy(ctx.stdin, ctx.stdout, decompress);
				continue;
			}
			FileSource f = ctx.console.createFileSource(p);
			if( !f.exists()) {
				ctx.stderr.println(getName()+": "+p+": No such file or directory");
				ret = 1;
				continue;
			}
			if( f.isDirectory()) {
				ctx.stderr.println(getName()+": "+p+" is a directory -- ignored");
				ret = 1;
				continue;
			}
			String target;
			if( decompress ) {
				if( p.endsWith(".gz")) {
					target = p.substring(0, p.length()-3);
				} else if( p.endsWith(".tgz")) {
					target = p.substring(0, p.length()-4)+".tar";
				} else if( p.endsWith(".z") || p.endsWith(".Z")) {
					target = p.substring(0, p.length()-2);
				} else if( toStdout ) {
					target = null;
				} else {
					ctx.stderr.println(getName()+": "+p+": unknown suffix -- ignored");
					ret = 1;
					continue;
				}
			} else {
				if( p.endsWith(".gz")) {
					ctx.stderr.println(getName()+": "+p+" already has .gz suffix -- unchanged");
					ret = 1;
					continue;
				}
				target = p+".gz";
			}
			try {
				if( toStdout ) {
					try (InputStream in = f.getInputStream()) {
						copy(in, ctx.stdout, decompress);
					}
					ctx.stdout.flush();
					continue;
				}
				FileSource out = ctx.console.createFileSource(target);
				if( out.exists() && !a.has('f')) {
					ctx.stderr.println(getName()+": "+target+" already exists; not overwritten");
					ret = 1;
					continue;
				}
				long before = f.length();
				try (InputStream in = f.getInputStream(); OutputStream os = out.getOutputStream()) {
					copy(in, os, decompress);
				}
				if( a.has('v')) {
					long after = out.length();
					long small = decompress ? before : after, big = decompress ? after : before;
					ctx.stderr.println(p+":\t"+(big == 0 ? 0.0 : Math.round((1.0-(double) small/big)*1000)/10.0)+"% -- replaced with "+target);
				}
				if( !keep ) {
					f.delete();
				}
			} catch (IOException e) {
				ctx.stderr.println(getName()+": "+p+": "+(e.getMessage() == null ? "error" : e.getMessage()));
				ret = 1;
			}
		}
		ctx.stdout.flush();
		return ret;
	}

	private static void copy(InputStream in, OutputStream out, boolean decompress) throws IOException {
		byte [] buf = new byte[65536];
		int n;
		if( decompress ) {
			try (InputStream gz = new GZIPInputStream(new BufferedInputStream(in))) {
				while( (n = gz.read(buf)) >= 0 ) {
					out.write(buf, 0, n);
				}
			}
		} else {
			GZIPOutputStream gz = new GZIPOutputStream(out);
			while( (n = in.read(buf)) >= 0 ) {
				gz.write(buf, 0, n);
			}
			gz.finish();
			out.flush();
		}
	}
}
