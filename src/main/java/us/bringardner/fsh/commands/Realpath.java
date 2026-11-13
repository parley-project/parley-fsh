package us.bringardner.fsh.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.tree.LocalFiles;

/** realpath and readlink, modeled on the GNU commands. */
public class Realpath extends ShellCommand{
	static String help = "realpath [-emqs] FILE...\n"
			+ "\tPrint the absolute path of each FILE with . and .. resolved and symbolic links followed.\n"
			+ "\t-e, --canonicalize-existing   every part of the path must exist\n"
			+ "\t-m, --canonicalize-missing    no part of the path has to exist\n"
			+ "\t-s, --strip, --no-symlinks    don't follow symbolic links\n"
			+ "\t-q, --quiet                   don't report errors";

	private final boolean readlink;

	public Realpath() {
		this(false);
	}

	Realpath(boolean readlink) {
		super(readlink ? "readlink" : "realpath", readlink ? "readlink [-fenmqs] FILE...\n"
				+ "\tPrint where the symbolic link FILE points to (nothing and status 1 if it isn't a link).\n"
				+ "\t-f, --canonicalize            the absolute path with every link followed; all but the last part must exist\n"
				+ "\t-e, --canonicalize-existing   as -f, and every part must exist\n"
				+ "\t-m, --canonicalize-missing    as -f, and nothing has to exist\n"
				+ "\t-n, --no-newline              no newline after the output\n"
				+ "\t-q, -s, --quiet, --silent     don't report errors" : help);
		this.readlink = readlink;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = readlink ? Args.parse(ctx, args, "fenmqsv", "", false, "canonicalize:f", "canonicalize-existing:e", "canonicalize-missing:m", "no-newline:n", "quiet:q", "silent:s", "verbose:v")
				: Args.parse(ctx, args, "emqs", "", false, "canonicalize-existing:e", "canonicalize-missing:m", "strip:s", "no-symlinks:s", "quiet:q");
		if( a.error != null ) {
			return a.fail(ctx, getName());
		}
		if( a.operands.isEmpty()) {
			ctx.stderr.println(getName()+": missing operand");
			return 1;
		}
		boolean canon = !readlink || a.has('f') || a.has('e') || a.has('m');
		boolean quiet = a.has('q') || (readlink && a.has('s'));
		int ret = 0;
		StringBuilder out = new StringBuilder();
		for (String p : a.operands) {
			FileSource f = ctx.console.createFileSource(p);
			if( !canon ) {
				String target = null;
				if( LocalFiles.isLocal(f)) {
					Path path = LocalFiles.of(f).toPath();
					if( Files.isSymbolicLink(path)) {
						target = Files.readSymbolicLink(path).toString().replace('\\', '/');
					}
				}
				if( target == null ) {
					ret = 1;
				} else {
					out.append(target).append(a.has('n') ? "" : "\n");
				}
				continue;
			}
			boolean mustExist = a.has('e');
			if( mustExist && !f.exists()) {
				if( !quiet ) {
					ctx.stderr.println(getName()+": "+p+": No such file or directory");
				}
				ret = 1;
				continue;
			}
			String parent = Basename.dirOf(p);
			if( !a.has('m') && !a.has('e') && !readlink ) {
				// realpath without -m needs the directory part (not the last name) to exist
				if( !f.exists() && !ctx.console.createFileSource(parent).exists()) {
					if( !quiet ) {
						ctx.stderr.println(getName()+": "+p+": No such file or directory");
					}
					ret = 1;
					continue;
				}
			}
			if( readlink && !a.has('m') && !a.has('e') && !f.exists() && !ctx.console.createFileSource(parent).exists()) {
				ret = 1;
				continue;
			}
			String res = a.has('s') && !readlink ? f.getAbsolutePath() : f.getCanonicalPath();
			out.append(res.replace('\\', '/')).append(readlink && a.has('n') ? "" : "\n");
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return ret;
	}
}
