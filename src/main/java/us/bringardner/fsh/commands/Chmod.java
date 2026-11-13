package us.bringardner.fsh.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.tree.LocalFiles;

/**
 * chmod, modeled on the GNU chmod, for local files. Where the file system has no Unix permissions
 * (Windows) the write bit of the owner sets whether the file is read only, and the execute bit
 * whether it can be executed.
 */
public class Chmod extends ShellCommand{
	static String name = "chmod";
	static String help = "chmod [-Rv] MODE FILE...\n"
			+ "\tChange the permissions of each FILE (local files only).\n"
			+ "\tMODE is octal (755) or symbolic: [ugoa]*([-+=]([rwxXst]*|[ugo]))+ as in u+x,go-w or a=r.\n"
			+ "\t-R, --recursive   change the files under the directories too\n"
			+ "\t-v, --verbose     say what is done to every file\n"
			+ "\t-c, --changes     like -v but only when something changes";

	public Chmod() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// (-w and -x are modes, not options)
		Args a = Args.parse(ctx, args, "Rvcf", "", "-[ugoa]*[-+=]?[rwxXst]+", "recursive:R", "verbose:v", "changes:c", "silent:f", "quiet:f");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.size() < 2 ) {
			ctx.stderr.println("chmod: missing operand"+(a.operands.isEmpty() ? "" : " after '"+a.operands.get(0)+"'"));
			return 1;
		}
		String mode = a.operands.get(0);
		int ret = 0;
		for (String p : a.operands.subList(1, a.operands.size())) {
			FileSource f = ctx.console.createFileSource(p);
			if( !f.exists()) {
				if( !a.has('f')) {
					ctx.stderr.println("chmod: cannot access '"+p+"': No such file or directory");
				}
				ret = 1;
				continue;
			}
			ret = Math.max(ret, apply(ctx, a, f, p, mode));
		}
		return ret;
	}

	private int apply(ShellContext ctx, Args a, FileSource f, String label, String mode) throws IOException {
		if( !LocalFiles.isLocal(f)) {
			ctx.stderr.println("chmod: changing permissions of '"+label+"': not a local file");
			return 1;
		}
		int ret = 0;
		File file = LocalFiles.of(f);
		Path path = file.toPath();
		int current = current(file, path);
		int bits;
		if( mode.matches("[0-7]{1,4}")) {
			bits = Integer.parseInt(mode, 8) & 07777;
		} else {
			bits = Umask.parse(ctx, mode, current);
			if( bits < 0 ) {
				ctx.stderr.println("chmod: invalid mode: '"+mode+"'");
				return 1;
			}
		}
		try {
			try {
				Files.setPosixFilePermissions(path, Mkdir.permissions(bits));
			} catch (UnsupportedOperationException e) {
				file.setWritable((bits & 0200) != 0, true);
				file.setExecutable((bits & 0100) != 0, true);
				file.setReadable((bits & 0400) != 0, true);
			}
			if( a.has('v') || (a.has('c') && bits != current)) {
				ctx.stdout.println("mode of '"+label+"' "+(bits == current ? "retained as " : "changed from "+String.format("%04o", current)+" to ")+String.format("%04o", bits));
			}
		} catch (IOException e) {
			ctx.stderr.println("chmod: changing permissions of '"+label+"': "+e.getMessage());
			ret = 1;
		}
		if( a.has('R') && f.isDirectory()) {
			FileSource [] kids = f.listFiles();
			if( kids != null ) {
				for (FileSource k : kids) {
					ret = Math.max(ret, apply(ctx, a, k, label+"/"+k.getName(), mode));
				}
			}
		}
		return ret;
	}

	private static int current(File file, Path path) {
		try {
			Set<PosixFilePermission> ps = Files.getPosixFilePermissions(path);
			int bits = 0;
			for (PosixFilePermission p : ps) {
				bits |= 0400 >> p.ordinal();
			}
			return bits;
		} catch (UnsupportedOperationException | IOException e) {
			return (file.canRead() ? 0444 : 0) | (file.canWrite() ? 0222 : 0) | (file.canExecute() ? 0111 : 0);
		}
	}
}
