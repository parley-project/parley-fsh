package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Rm extends ShellCommand{
	
	
	private enum Arguments {d,f,i,I,R,r,v,x};




	static String name = "rm";
	static String help = "rm [-f | -i | -I] [-dRrvx] FILE ...\n"
			+ "\tRemove files, and directories with -r or -d. It refuses /, . and ..\n"
			+ "\t-d       remove empty directories\n"
			+ "\t-f       ignore files that don't exist, and never ask\n"
			+ "\t-i       ask before removing each file\n"
			+ "\t-I       ask once before removing more than three files, or a directory recursively\n"
			+ "\t-r, -R   remove directories and everything in them\n"
			+ "\t-v       show each file as it is removed\n"
			+ "\t-x       don't go into a mounted file system of a different kind\n"
			+ "\tA symbolic link is removed itself, never the file it points to.";

	public Rm() {
		super(name, help);
	}

	/** The person said no to -I's question: nothing more is removed, and it isn't an error. */
	private static class Declined extends IOException {
		private static final long serialVersionUID = 1L;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		ShellArgument sa = parseArgs(ctx, Arguments.class);

		// -f, -i and -I: the last one given wins
		List<Arguments> options = new ArrayList<>();
		for(Object o : sa.options) {
			Arguments a = (Arguments) o;
			if( a == Arguments.R ) {
				a = Arguments.r;
			}
			if( a == Arguments.f || a == Arguments.i || a == Arguments.I ) {
				options.remove(Arguments.f);
				options.remove(Arguments.i);
				options.remove(Arguments.I);
			}
			if( !options.contains(a)) {
				options.add(a);
			}
		}

		List<String> paths = sa.paths;
		if( paths.isEmpty()) {
			if( options.contains(Arguments.f)) {
				// rm -f with nothing to remove is not an error
				return 0;
			}
			ctx.stderr.println("usage: "+help.split("\n", 2)[0]);
			return 1;
		}

		boolean asked = false;
		if( options.contains(Arguments.I) && paths.size() > 3 ) {
			String str = promptAndGetReponse(ctx, "remove "+paths.size()+" arguments?");
			if( str == null || !str.trim().toLowerCase().startsWith("y")) {
				return 0;
			}
			asked = true;
		}

		int ret = 0;
		for(String path : paths) {
			String p = path;
			while( p.length() > 1 && p.endsWith("/")) {
				p = p.substring(0, p.length()-1);
			}
			String base = p.substring(p.lastIndexOf('/')+1);
			if( p.equals("/") || base.equals(".") || base.equals("..")) {
				ctx.stderr.println(name+": refusing to remove '"+path+"'");
				ret = 1;
				continue;
			}
			// the shell expanded the patterns already: a * here is a name
			FileSource file = isRelative(path) ? ctx.console.getCurrentDirectory().getChild(path)
					: ctx.console.createFileSource(path);
			try {
				remove(ctx, options, file, file.getFileSourceFactory().getTypeId(), asked);
			} catch (Declined e) {
				return ret;
			} catch (IOException e) {
				ctx.stderr.println(name+": "+path+": "+e.getMessage());
				ret = 1;
			}
		}
		return ret;
	}

	private void remove(ShellContext ctx, List<Arguments> options, FileSource file, String rootType, boolean asked) throws IOException {
		if( file instanceof us.bringardner.parley.files.fileproxy.FileProxy
				&& java.nio.file.Files.isSymbolicLink(((us.bringardner.parley.files.fileproxy.FileProxy) file).getTarget().toPath())) {
			// a link (to a directory too) is removed itself, not what it points to
			if( !confirm(ctx, options, file)) {
				return;
			}
			java.nio.file.Files.delete(((us.bringardner.parley.files.fileproxy.FileProxy) file).getTarget().toPath());
			if( options.contains(Arguments.v)) {
				ctx.stdout.println(file.getAbsolutePath());
			}
			return;
		}
		if( !file.exists()) {
			if( options.contains(Arguments.f)) {
				return;
			}
			throw new IOException("does not exist");
		}
		if( options.contains(Arguments.x) && !file.getFileSourceFactory().getTypeId().equals(rootType)) {
			// (a mount point of another kind of file system)
			return;
		}
		if( file.isDirectory()) {
			FileSource[] kids = file.listFiles();
			if( kids == null ) {
				kids = new FileSource[0];
			}
			if( options.contains(Arguments.r)) {
				if( options.contains(Arguments.I) && !asked ) {
					String str = promptAndGetReponse(ctx, "remove "+file+" and everything in it?");
					if( str == null || !str.trim().toLowerCase().startsWith("y")) {
						throw new Declined();
					}
					asked = true;
				}
				IOException first = null;
				for(FileSource kid : kids) {
					try {
						remove(ctx, options, kid, rootType, asked);
					} catch (Declined e) {
						throw e;
					} catch (IOException e) {
						if( first == null ) {
							first = new IOException(kid.getName()+": "+e.getMessage());
						}
					}
				}
				if( first != null ) {
					throw first;
				}
				if( file.listFiles() != null && file.listFiles().length > 0 ) {
					// (some were kept: -i answered no, or -x)
					return;
				}
			} else if( options.contains(Arguments.d)) {
				if( kids.length > 0 ) {
					throw new IOException("Directory not empty");
				}
			} else {
				throw new IOException("is a directory");
			}
		}
		if( !confirm(ctx, options, file)) {
			return;
		}
		if( !file.delete()) {
			throw new IOException("can't remove it");
		}
		if( options.contains(Arguments.v)) {
			ctx.stdout.println(file.getAbsolutePath());
		}
	}

	/** -i: whether the person says to remove file (always, without -i) */
	private boolean confirm(ShellContext ctx, List<Arguments> options, FileSource file) throws IOException {
		if( !options.contains(Arguments.i)) {
			return true;
		}
		String str = promptAndGetReponse(ctx, "remove "+file+"?");
		return str != null && str.trim().toLowerCase().startsWith("y");
	}
}
