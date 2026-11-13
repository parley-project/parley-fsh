package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.tree.LocalFiles;

/** truncate, modeled on the GNU truncate: sets the size of files. */
public class Truncate extends ShellCommand{
	static String name = "truncate";
	static String help = "truncate -s SIZE [-c] FILE...\n"
			+ "\tShrink or extend (with zeros) each FILE to SIZE bytes; a file that doesn't exist is created.\n"
			+ "\t-s, --size=SIZE   SIZE is a number with an optional K, M or G; +SIZE extends and -SIZE shrinks by that much\n"
			+ "\t-c, --no-create   don't create files";

	public Truncate() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "c", "s", false, "no-create:c", "size=s");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.value('s') == null ) {
			ctx.stderr.println("truncate: you must specify a size with --size");
			return 1;
		}
		if( a.operands.isEmpty()) {
			ctx.stderr.println("truncate: missing file operand");
			return 1;
		}
		String spec = a.value('s');
		char rel = spec.isEmpty() ? 0 : spec.charAt(0);
		long amount;
		try {
			amount = TextFiles.count(rel == '+' || rel == '-' ? spec.substring(1) : spec);
		} catch (NumberFormatException e) {
			ctx.stderr.println("truncate: Invalid number: '"+spec+"'");
			return 1;
		}
		int ret = 0;
		for (String p : a.operands) {
			FileSource f = ctx.console.createFileSource(p);
			if( !f.exists()) {
				if( a.has('c')) {
					continue;
				}
				f.createNewFile();
			}
			if( f.isDirectory()) {
				ctx.stderr.println("truncate: cannot open '"+p+"' for writing: Is a directory");
				ret = 1;
				continue;
			}
			long current = f.length();
			long size = rel == '+' ? current+amount : rel == '-' ? Math.max(0, current-amount) : amount;
			if( LocalFiles.isLocal(f)) {
				try (RandomAccessFile raf = new RandomAccessFile(LocalFiles.of(f), "rw")) {
					raf.setLength(size);
				}
				continue;
			}
			byte [] data = new byte[(int) size];
			try (InputStream in = f.getInputStream()) {
				int got = 0, n;
				while( got < data.length && (n = in.read(data, got, data.length-got)) >= 0 ) {
					got += n;
				}
			}
			try (OutputStream os = f.getOutputStream()) {
				os.write(data);
			}
		}
		return ret;
	}
}
