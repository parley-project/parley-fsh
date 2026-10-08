package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Mkdir extends ShellCommand{
	static String name = "mkdir";
	// physical 
	static String help = " mkdir – make directories\n"
			+ "\n"
			+ "SYNOPSIS\n"
			+ "     mkdir [-pv] [-m mode] directory_name ...\n"
			+ "\n"
			+ "     The options are as follows:\n"
			+ "\n"
			+ "     -m mode        Set the file permission bits of the final created directory to the specified mode.  The mode argument can be in any of the formats specified to the chmod(1) command.  If a\n"
			+ "                    symbolic mode is specified, the operation characters ‘+’ and ‘-’ are interpreted relative to an initial mode of “a=rwx”.\n"
			+ "\n"
			+ "     -p             Create intermediate directories as required.  If this option is not specified, the full path prefix of each operand must already exist.  On the other hand, with this option\n"
			+ "                    specified, no error will be reported if a directory given as an operand already exists.  Intermediate directories are created with permission bits of “rwxrwxrwx” (0777) as\n"
			+ "                    modified by the current umask, plus write and search permission for the owner.\n"
			+ "\n"
			+ "     -v             Be verbose when creating directories, listing them as they are created.\n"
			+ "\n"
			+ "     The user must have write permission in the parent directory.\n"
			+ "";

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
			if( bits != null && dir instanceof us.bringardner.parley.files.fileproxy.FileProxy proxy ) {
				try {
					java.nio.file.Files.setPosixFilePermissions(proxy.getTarget().toPath(), permissions(bits));
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
