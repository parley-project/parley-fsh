package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Cp extends ShellCommand{
	private enum Arguments {R,r,H,L,P,f,i,n,a,p,v,x};

	static String name = "cp";
	static String help = "cp [-Rafinpvx] SOURCE TARGET   or   cp [-Rafinpvx] SOURCE ... DIRECTORY\n"
			+ "\tCopy files. With two operands SOURCE is copied to TARGET; when the last operand is an existing\n"
			+ "\tdirectory, each SOURCE is copied into it. Works between any mounted file systems.\n"
			+ "\t-R, -r   copy directories and everything in them; with SOURCE/ (a trailing slash) only what is in it\n"
			+ "\t-a       the same as -Rp\n"
			+ "\t-f       replace existing files without asking (the default)\n"
			+ "\t-i       ask before replacing an existing file\n"
			+ "\t-n       never replace an existing file\n"
			+ "\t-p       keep the files' modification and access times\n"
			+ "\t-v       show each file as it is copied\n"
			+ "\t-x       when copying recursively, skip directories on a different kind of file system\n"
			+ "\tThe last of -f, -i and -n given wins. -H, -L and -P are accepted; links are followed.";

	public Cp() {
		super(name, help);
	}

	/** -f, -i or -n, and the others, as given */
	private static class Choices {
		boolean recursive, keepTimes, verbose, sameKind;
		Arguments overwrite = Arguments.f;
		String rootType;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		ShellArgument sa = parseArgs(ctx, Arguments.class);
		Choices how = new Choices();
		for(Object o : sa.options) {
			switch ((Arguments) o) {
			case R: case r: how.recursive = true; break;
			case a: how.recursive = true; how.keepTimes = true; break;
			case p: how.keepTimes = true; break;
			case v: how.verbose = true; break;
			case x: how.sameKind = true; break;
			case f: case i: case n: how.overwrite = (Arguments) o; break;
			default: break; // H, L, P: links are followed
			}
		}

		List<String> paths = sa.paths;
		if( paths.size() < 2 ) {
			ctx.stderr.println(name+": "+(paths.isEmpty() ? "missing file operand" : "missing destination file operand after '"+paths.get(0)+"'"));
			ctx.stderr.println("usage: "+help.split("\n", 2)[0]);
			return 1;
		}
		String targetPath = paths.get(paths.size()-1);
		List<FileSource> found = getFiles(ctx, targetPath);
		if( found.size() != 1 ) {
			ctx.stderr.println(name+": "+targetPath+": the target must be one file or directory");
			return 1;
		}
		FileSource target = found.get(0);
		List<String> sources = paths.subList(0, paths.size()-1);
		boolean intoDir = target.isDirectory();
		if( !intoDir && sources.size() > 1 ) {
			ctx.stderr.println(name+": "+targetPath+" is not a directory");
			return 1;
		}

		int ret = 0;
		for(String path : sources) {
			List<FileSource> files = getFiles(ctx, path);
			for(FileSource from : files) {
				try {
					if( !from.exists()) {
						throw new IOException("No such file or directory");
					}
					how.rootType = from.getFileSourceFactory().getTypeId();
					if( from.isDirectory()) {
						if( !how.recursive ) {
							throw new IOException("is a directory (not copied; use -R)");
						}
						FileSource to;
						if( path.endsWith("/") && path.length() > 1 ) {
							// SOURCE/: what is in it, into the target
							to = target;
						} else {
							to = intoDir ? target.getChild(from.getName()) : target;
						}
						if( isInside(to, from)) {
							throw new IOException("can't copy a directory into itself, "+to);
						}
						copyDir(ctx, how, from, to);
					} else {
						copyFile(ctx, how, from, intoDir ? target.getChild(from.getName()) : target);
					}
				} catch (IOException e) {
					ctx.stderr.println(name+": "+path+": "+e.getMessage());
					ret = 1;
				}
			}
		}
		return ret;
	}

	/** whether dir is from, or inside it, on the same file system */
	private static boolean isInside(FileSource dir, FileSource from) {
		if( dir.getFileSourceFactory() != from.getFileSourceFactory()) {
			return false;
		}
		String d = dir.getAbsolutePath(), f = from.getAbsolutePath();
		return d.equals(f) || d.startsWith(f.endsWith("/") ? f : f+"/");
	}

	private void copyDir(ShellContext ctx, Choices how, FileSource from, FileSource to) throws IOException {
		if( !to.exists() && !to.mkdirs()) {
			throw new IOException("can't create the directory "+to);
		}
		if( !to.isDirectory()) {
			throw new IOException(to+" is not a directory");
		}
		FileSource[] kids = from.listFiles();
		if( kids != null ) {
			for(FileSource kid : kids) {
				FileSource dest = to.getChild(kid.getName());
				if( kid.isDirectory()) {
					if( how.sameKind && !kid.getFileSourceFactory().getTypeId().equals(how.rootType)) {
						continue;
					}
					copyDir(ctx, how, kid, dest);
				} else {
					copyFile(ctx, how, kid, dest);
				}
			}
		}
		if( how.keepTimes ) {
			keepTimes(from, to);
		}
	}

	private void copyFile(ShellContext ctx, Choices how, FileSource from, FileSource to) throws IOException {
		if( to.exists()) {
			if( to.isDirectory()) {
				throw new IOException("can't replace the directory "+to+" with a file");
			}
			if( from.getFileSourceFactory() == to.getFileSourceFactory() && from.getAbsolutePath().equals(to.getAbsolutePath())) {
				throw new IOException(to+" is the same file");
			}
			if( how.overwrite == Arguments.n ) {
				return;
			}
			if( how.overwrite == Arguments.i ) {
				String answer = promptAndGetReponse(ctx, "overwrite "+to+"? (y/n [n])");
				if( answer == null || !answer.trim().toLowerCase().startsWith("y")) {
					ctx.stdout.println(to+" not overwritten.");
					return;
				}
			}
		}
		copyFileToFile(ctx, from, to);
		if( how.keepTimes ) {
			keepTimes(from, to);
		}
		if( how.verbose ) {
			ctx.stdout.println(from+" -> "+to);
		}
	}

	private static void keepTimes(FileSource from, FileSource to) throws IOException {
		to.setLastModifiedTime(from.lastModified());
		to.setLastAccessTime(from.lastAccessTime());
	}

	private void copyFileToFile(ShellContext ctx, FileSource from, FileSource to) throws IOException {
		boolean existed = to.exists();
		try(InputStream in = from.getInputStream()) {
			try(OutputStream out = to.getOutputStream()) {
				copyStream(ctx,in,out);
			}
		}		
		if( !existed && us.bringardner.fsh.LocalFiles.isLocal(from)
				&& us.bringardner.fsh.LocalFiles.isLocal(to) ) {
			// as cp: a new file gets the source's mode (less group and other write, as umask 022)
			try {
				java.util.Set<java.nio.file.attribute.PosixFilePermission> mode = java.nio.file.Files.getPosixFilePermissions(us.bringardner.fsh.LocalFiles.of(from).toPath());
				mode.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
				mode.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
				java.nio.file.Files.setPosixFilePermissions(us.bringardner.fsh.LocalFiles.of(to).toPath(), mode);
			} catch (UnsupportedOperationException e) {
			}
		}
	}


	

}
