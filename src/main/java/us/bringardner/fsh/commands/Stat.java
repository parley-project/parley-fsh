package us.bringardner.fsh.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.tree.LocalFiles;

/** stat, modeled on the GNU stat: the size, type, permissions and times of files. */
public class Stat extends ShellCommand{
	static String name = "stat";
	static String help = "stat [-L] [-c FORMAT] FILE...\n"
			+ "\tShow the status of each FILE.\n"
			+ "\t-c, --format=FORMAT   print FORMAT instead, with these sequences:\n"
			+ "\t    %n name   %N quoted name   %s size in bytes   %F file type   %a permissions in octal   %A permissions as rwx\n"
			+ "\t    %U owner   %y %x %w last modification, access, birth (readable)   %Y %X %W the same in seconds since 1970\n"
			+ "\t    %z %Z last change (the same as the modification time here)   %% a percent sign\n"
			+ "\t-L, --dereference     follow symbolic links";

	public Stat() {
		super(name, help);
	}

	private static String rwx(int bits, char type) {
		StringBuilder sb = new StringBuilder().append(type);
		String rwx = "rwxrwxrwx";
		for (int i = 0; i < 9; i++) {
			sb.append((bits & (0400 >> i)) != 0 ? rwx.charAt(i) : '-');
		}
		return sb.toString();
	}

	private static String time(long millis) {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS000000 Z").format(new Date(millis));
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "L", "c", false, "dereference:L", "format=c");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.isEmpty()) {
			ctx.stderr.println("stat: missing operand");
			return 1;
		}
		int ret = 0;
		for (String p : a.operands) {
			FileSource f = ctx.console.createFileSource(p);
			if( !f.exists()) {
				ctx.stderr.println("stat: cannot statx '"+p+"': No such file or directory");
				ret = 1;
				continue;
			}
			long size = f.isDirectory() ? 0 : f.length();
			long mod = f.lastModified(), access = mod, birth = mod;
			int perms = f.isDirectory() ? 0755 : 0644;
			String owner = "?";
			String type = f.isDirectory() ? "directory" : size == 0 ? "regular empty file" : "regular file";
			char tc = f.isDirectory() ? 'd' : '-';
			if( LocalFiles.isLocal(f)) {
				File file = LocalFiles.of(f);
				Path path = file.toPath();
				LinkOption [] lo = a.has('L') ? new LinkOption[0] : new LinkOption[] {LinkOption.NOFOLLOW_LINKS};
				try {
					BasicFileAttributes at = Files.readAttributes(path, BasicFileAttributes.class, lo);
					access = at.lastAccessTime().toMillis();
					birth = at.creationTime().toMillis();
					if( at.isSymbolicLink()) {
						type = "symbolic link";
						tc = 'l';
					}
				} catch (IOException e) {
					// keep what the file source said
				}
				try {
					Set<PosixFilePermission> ps = Files.getPosixFilePermissions(path);
					perms = 0;
					for (PosixFilePermission pp : ps) {
						perms |= 0400 >> pp.ordinal();
					}
				} catch (UnsupportedOperationException | IOException e) {
					perms = (file.canWrite() ? 0644 : 0444) | (file.canExecute() ? 0111 : 0);
				}
				try {
					owner = Files.getOwner(path).getName();
				} catch (IOException | UnsupportedOperationException e) {
					// unknown
				}
			}
			String format = a.value('c');
			StringBuilder sb = new StringBuilder();
			if( format == null ) {
				sb.append("  File: ").append(p).append('\n');
				sb.append("  Size: ").append(size).append("\tType: ").append(type).append('\n');
				sb.append("Access: (").append(String.format("%04o", perms)).append('/').append(rwx(perms, tc)).append(")  Owner: ").append(owner).append('\n');
				sb.append("Access: ").append(time(access)).append('\n');
				sb.append("Modify: ").append(time(mod)).append('\n');
				sb.append("Change: ").append(time(mod)).append('\n');
				sb.append(" Birth: ").append(time(birth)).append('\n');
			} else {
				for (int i = 0; i < format.length(); i++) {
					char c = format.charAt(i);
					if( c != '%' || i+1 >= format.length()) {
						sb.append(c);
						continue;
					}
					char d = format.charAt(++i);
					switch (d) {
					case 'n': sb.append(p); break;
					case 'N': sb.append('\'').append(p).append('\''); break;
					case 's': sb.append(size); break;
					case 'F': sb.append(type); break;
					case 'a': sb.append(String.format("%o", perms)); break;
					case 'A': sb.append(rwx(perms, tc)); break;
					case 'U': sb.append(owner); break;
					case 'y': case 'z': sb.append(time(mod)); break;
					case 'x': sb.append(time(access)); break;
					case 'w': sb.append(time(birth)); break;
					case 'Y': case 'Z': sb.append(mod/1000); break;
					case 'X': sb.append(access/1000); break;
					case 'W': sb.append(birth/1000); break;
					case '%': sb.append('%'); break;
					default: sb.append('%').append(d);
					}
				}
				sb.append('\n');
			}
			ctx.stdout.print(sb);
		}
		ctx.stdout.flush();
		return ret;
	}
}
