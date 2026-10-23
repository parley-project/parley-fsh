package us.bringardner.fsh.commands;

import us.bringardner.parley.files.tree.LocalFiles;
import java.io.IOException;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Mkdir extends ShellCommand{
	static String name = "mkdir";
	// physical 
	static String help = "mkdir [-pv] [-m MODE] DIRECTORY ...\n"
			+ "\tCreate directories.\n"
			+ "\t-m MODE   the new directory's permissions, octal (755) or symbolic (u=rwx,go=rx); local files only\n"
			+ "\t-p        also create missing parent directories; a directory that exists isn't an error\n"
			+ "\t-v        show each directory as it is created\n"
			+ "\tWithout -m the shell's umask applies.";

	public Mkdir() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean mkdirs = false, verbose = false;
		String mode = null;
		List<String> paths = new java.util.ArrayList<>();
		boolean options = true;
		for (int idx = 0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( options && a.equals("--")) {
				options = false;
			} else if( options && a.startsWith("-") && a.length() > 1 ) {
				for (int c = 1; c < a.length(); c++) {
					char ch = a.charAt(c);
					if( ch == 'p' ) {
						mkdirs = true;
					} else if( ch == 'v' ) {
						verbose = true;
					} else if( ch == 'm' ) {
						// -m mode, or -mmode
						if( c+1 < a.length()) {
							mode = a.substring(c+1);
						} else if( idx+1 < args.length ) {
							mode = ""+args[++idx].getValue(ctx);
						} else {
							ctx.stderr.println("mkdir: option requires an argument -- m");
							ctx.stderr.println("usage: mkdir [-pv] [-m mode] directory_name ...");
							return 64;
						}
						break;
					} else {
						ctx.stderr.println("mkdir: illegal option -- "+ch);
						ctx.stderr.println("usage: mkdir [-pv] [-m mode] directory_name ...");
						return 64;
					}
				}
			} else {
				options = false;
				paths.add(a);
			}
		}
		if( paths.isEmpty()) {
			ctx.stderr.println("usage: mkdir [-pv] [-m mode] directory_name ...");
			return 64;
		}
		Integer bits = null;
		if( mode != null ) {
			if( mode.matches("[0-7]+")) {
				bits = Integer.parseInt(mode, 8) & 07777;
			} else {
				int b = Umask.parse(ctx, mode, 0777);
				if( b < 0 ) {
					ctx.stderr.println("mkdir: invalid file mode: "+mode);
					return 1;
				}
				bits = b;
			}
		}
		if( bits == null && ctx.console.umask != null ) {
			// (the shell's umask, which the JVM does not have)
			bits = 0777 & ~ctx.console.umask;
		}
		int ret = 0;
		for(String path : paths) {
			FileSource dir = ctx.console.createFileSource(path);
			if( dir.exists()) {
				if( mkdirs && dir.isDirectory()) {
					continue;
				}
				ctx.stderr.println("mkdir: "+path+": File exists");
				ret = 1;
				continue;
			}
			boolean made = mkdirs ? dir.mkdirs() : dir.mkdir();
			if( !made ) {
				String up = path.replaceAll("/*[^/]+/*$", "");
				FileSource parent = ctx.console.createFileSource(up.isEmpty() ? (path.startsWith("/") ? "/" : ".") : up);
				ctx.stderr.println("mkdir: "+path+": "+(!parent.isDirectory() ? "No such file or directory" : "Permission denied"));
				ret = 1;
				continue;
			}
			if( bits != null && us.bringardner.parley.files.tree.LocalFiles.isLocal(dir) ) {
				try {
					java.nio.file.Files.setPosixFilePermissions(us.bringardner.parley.files.tree.LocalFiles.of(dir).toPath(), permissions(bits));
				} catch (Exception e) {
					ctx.stderr.println("mkdir: "+path+": "+e.getMessage());
					ret = 1;
				}
			}
			if( verbose ) {
				ctx.stdout.println("mkdir: "+dir);
			}
		}
		return ret;
	}

	/** rwxrwxrwx bits as posix permissions */
	static java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions(int bits) {
		java.util.Set<java.nio.file.attribute.PosixFilePermission> ret = java.util.EnumSet.noneOf(java.nio.file.attribute.PosixFilePermission.class);
		java.nio.file.attribute.PosixFilePermission [] all = java.nio.file.attribute.PosixFilePermission.values();
		// (OWNER_READ ... OTHERS_EXECUTE: bit 8 down to bit 0)
		for (int i = 0; i < 9; i++) {
			if( (bits & (0400 >> i)) != 0 ) {
				ret.add(all[i]);
			}
		}
		return ret;
	}
}
