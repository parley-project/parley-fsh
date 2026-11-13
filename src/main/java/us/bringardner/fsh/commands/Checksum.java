package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** md5sum, sha1sum, sha256sum and sha512sum, modeled on the GNU commands. */
public class Checksum extends ShellCommand{
	private final String algorithm;

	public Checksum(String name, String algorithm) {
		super(name, name+" [-bt] [--quiet] [--status] [-c] [FILE ...]\n"
				+ "\tPrint the "+algorithm+" checksum of each FILE (standard input with no FILE or -).\n"
				+ "\t-c, --check   read the sums from the FILEs, lines like those this prints, and check the files\n"
				+ "\t--quiet       with -c, don't print OK for the files that pass\n"
				+ "\t--status      with -c, print nothing, only set the exit status\n"
				+ "\t-b, -t        accepted (binary and text mode are the same here)");
		this.algorithm = algorithm;
	}

	private String sum(InputStream in) throws IOException {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		byte [] buf = new byte[65536];
		int n;
		while( (n = in.read(buf)) >= 0 ) {
			md.update(buf, 0, n);
		}
		StringBuilder sb = new StringBuilder();
		for (byte b : md.digest()) {
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "bctw", "", false, "check:c", "binary:b", "text:t", "quiet:q", "status:s", "warn:w");
		if( a.error != null ) {
			return a.fail(ctx, getName());
		}
		if( a.has('c')) {
			return check(ctx, a);
		}
		StringBuilder out = new StringBuilder();
		int ret = TextFiles.forEach(ctx, getName(), a.operands, (label, file, in) -> {
			out.append(sum(in)).append("  ").append(file == null && a.operands.isEmpty() ? "-" : label.equals("standard input") ? "-" : label).append('\n');
		});
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return ret;
	}

	private int check(ShellContext ctx, Args a) throws IOException {
		int [] st = {0};
		List<String> lines = TextFiles.readLines(ctx, getName(), a.operands, st);
		boolean quiet = a.has('q'), status = a.has('s');
		int failed = 0, bad = 0, unreadable = 0, ok = 0;
		StringBuilder out = new StringBuilder();
		for (String line : lines) {
			if( line.trim().isEmpty() || line.startsWith("#")) {
				continue;
			}
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9a-fA-F]+) [ *](.+)").matcher(line);
			if( !m.matches()) {
				bad++;
				continue;
			}
			String name = m.group(2);
			FileSource f = ctx.console.createFileSource(name);
			if( !f.exists() || f.isDirectory()) {
				unreadable++;
				if( !status ) {
					out.append(name).append(": FAILED open or read\n");
					ctx.stderr.println(getName()+": "+name+": No such file or directory");
				}
				continue;
			}
			String actual;
			try (InputStream in = f.getInputStream()) {
				actual = sum(in);
			}
			if( actual.equalsIgnoreCase(m.group(1))) {
				ok++;
				if( !quiet && !status ) {
					out.append(name).append(": OK\n");
				}
			} else {
				failed++;
				if( !status ) {
					out.append(name).append(": FAILED\n");
				}
			}
		}
		ctx.stdout.print(out);
		if( !status ) {
			if( bad > 0 ) {
				ctx.stderr.println(getName()+": WARNING: "+bad+" line"+(bad == 1 ? " is" : "s are")+" improperly formatted");
			}
			if( unreadable > 0 ) {
				ctx.stderr.println(getName()+": WARNING: "+unreadable+" listed file"+(unreadable == 1 ? "" : "s")+" could not be read");
			}
			if( failed > 0 ) {
				ctx.stderr.println(getName()+": WARNING: "+failed+" computed checksum"+(failed == 1 ? "" : "s")+" did NOT match");
			}
		}
		ctx.stdout.flush();
		return (failed > 0 || unreadable > 0 || st[0] != 0 || (ok == 0 && bad > 0)) ? 1 : 0;
	}
}
