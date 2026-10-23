package us.bringardner.fsh.commands;

import us.bringardner.parley.files.tree.TreeFile;
import us.bringardner.parley.files.tree.LocalFiles;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Ln extends ShellCommand{
	private enum Arguments {s,L,P,f,i,v,x,w,h,n};



	static String name = "ln";
	static String help = "ln [-s] [-f | -i] [-hnv] [-L | -P] SOURCE TARGET   or   ln [options] SOURCE ... DIRECTORY\n"
			+ "\tMake links: hard links, or symbolic links with -s. Works on connected file systems that support\n"
			+ "\tlinks.\n"
			+ "\t-s       make symbolic links; SOURCE is stored as given and need not exist\n"
			+ "\t-f       replace an existing TARGET\n"
			+ "\t-i       ask before replacing an existing TARGET\n"
			+ "\t-v       show each link as it is made\n"
			+ "\t-h, -n   if TARGET is a symbolic link to a directory, replace it instead of linking inside it\n"
			+ "\t-L       a hard link to a symbolic link links to the file it points to (the default)\n"
			+ "\t-P       a hard link to a symbolic link links to the link itself";

	public Ln() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;

		ShellArgument sa = parseArgs(ctx, Arguments.class);
		List<Arguments> options = new ArrayList<>();

		for(Object o : sa.options) {
			if (o instanceof Arguments) {
				Arguments a = (Arguments) o;
				if( a == Arguments.L) {
					options.remove(Arguments.P);
				} else if( a == Arguments.P) {
					options.remove(Arguments.L);
				} else if( a == Arguments.f) {
					options.remove(Arguments.i);
					options.remove(Arguments.w);
				}  else if( a == Arguments.i || a == Arguments.w) {
					options.remove(Arguments.f);
				}
				options.add(a);				
			}
		}

		if(options.size()==0) {
			options.add(Arguments.L);
		}

		List<String> paths = sa.paths;
		if( options.contains(Arguments.s) && paths.size() >= 2 && us.bringardner.parley.files.tree.LocalFiles.isLocal(ctx.getFileSource(".")) ) {
			// a local symbolic link holds the text given, as the system's ln makes it (ln -s bar
			// dir/foo points at dir/bar), and its target need not exist
			return localSymbolicLinks(ctx, options, paths);
		}
		List<FileSource> files = new ArrayList<>();

		for(String path : paths) {
			List<FileSource> kids = getFiles(ctx, path);

			
			files.addAll(kids);
		}

		if( files.size() < 2) {
			ctx.stdout.println(help);
			return -1;
		}

		FileSource lastFile = files.remove(files.size()-1);
		if( lastFile.isDirectory()) {
			ret = linkFilesInDir(ctx, options, files, lastFile);
		} else {
			if( files.size() != 1) {
				ctx.stdout.println(help);
				return -1;
			}
			ret = linkFiles(ctx, options, files.get(0), lastFile);
		}

		return ret;
	}

	/** ln -s text ... link_or_dir on the local file system (see process) */
	private int localSymbolicLinks(ShellContext ctx, List<Arguments> options, List<String> paths) throws IOException {
		java.nio.file.Path cwd = us.bringardner.parley.files.tree.LocalFiles.of(ctx.getFileSource(".")).toPath();
		String last = paths.get(paths.size()-1);
		java.nio.file.Path lastPath = cwd.resolve(last);
		boolean intoDir = !options.contains(Arguments.n) && !options.contains(Arguments.h) ? java.nio.file.Files.isDirectory(lastPath)
				: java.nio.file.Files.isDirectory(lastPath, java.nio.file.LinkOption.NOFOLLOW_LINKS);
		if( paths.size() > 2 && !intoDir ) {
			// (said as the system's ln says it: it is a program in bash)
			ctx.stderr.println("ln: "+last+": "+(java.nio.file.Files.exists(lastPath) ? "Not a directory" : "No such file or directory"));
			return 1;
		}
		int ret = 0;
		for(String text : paths.subList(0, paths.size()-1)) {
			String name = text.replaceAll("/+$", "");
			name = name.substring(name.lastIndexOf('/')+1);
			java.nio.file.Path link = intoDir ? lastPath.resolve(name) : lastPath;
			String shown = intoDir ? (last.endsWith("/") ? last : last+"/")+name : last;
			if( java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
				if( options.contains(Arguments.i)) {
					String str = promptAndGetReponse(ctx.stderr, ctx.stdin, "replace "+shown+"?");
					if( !str.trim().toLowerCase().startsWith("y")) {
						continue;
					}
				} else if( !options.contains(Arguments.f)) {
					ctx.stderr.println("ln: "+shown+": File exists");
					ret = 1;
					continue;
				}
				java.nio.file.Files.delete(link);
			}
			try {
				java.nio.file.Files.createSymbolicLink(link, java.nio.file.Paths.get(text));
			} catch (IOException e) {
				ctx.stderr.println("ln: "+shown+": "+(e instanceof java.nio.file.NoSuchFileException ? "No such file or directory"
						: e instanceof java.nio.file.AccessDeniedException ? "Permission denied" : e.getMessage()));
				ret = 1;
				continue;
			}
			if( options.contains(Arguments.v)) {
				ctx.stdout.println(shown+" -> "+text);
			}
		}
		return ret;
	}

	private int linkFilesInDir(ShellContext ctx, List<Arguments> options, List<FileSource> files, FileSource targetDir) throws IOException {
		int ret = 0;
		if( !options.contains(Arguments.h)) {
			FileSource link = targetDir.getLinkedTo();
			while( link != null) {
				targetDir = link;
				link = targetDir.getLinkedTo();
			}
		}
		
		for(FileSource source : files) {
			FileSource target = targetDir.getChild(source.getName());			
			linkFiles(ctx, options, source, target);
		}

		return ret;
	}

	private int linkFiles(ShellContext ctx, List<Arguments> options, FileSource source, FileSource target) throws IOException {
		int ret = 0;
		if( target.exists()) {
			if( !options.contains(Arguments.f)) {
				throw new IOException(target.getName()+" already exists");
			}
			if( options.contains(Arguments.i)) {
				String str = promptAndGetReponse(ctx.stderr,ctx.stdin, "replace "+target+"?");
				if(!str.trim().toLowerCase().startsWith("y")) {
					return 0;
				}
			}
			if( !target.delete() ) {
				throw new IOException("Can't delete "+target);
			}				
		}
		
		if( options.contains(Arguments.v) ) {
			ctx.stdout.println(""+source+" => "+target);
		}
		
		if( !options.contains(Arguments.s) ) {
			ret = createHardLink(ctx,options,target,source);
		} else {
			ret = createSymbolicLink(ctx,options,target, source);
		}
		
		return ret;
	}

	private int createSymbolicLink(ShellContext ctx, List<Arguments> options, FileSource target, FileSource source) throws IOException {
		FileSource link = source.getFileSourceFactory().createSymbolicLink(us.bringardner.parley.files.tree.TreeFile.unwrap(target), us.bringardner.parley.files.tree.TreeFile.unwrap(source));
		if( link == null || !link.exists()) {
			return -1;
		}
		
		return 0;
	}

	private int createHardLink(ShellContext ctx, List<Arguments> options, FileSource source, FileSource target) throws IOException {
		int ret = 0;
		
		if( options.contains(Arguments.L) && !options.contains(Arguments.h)) {
			FileSource link = target.getLinkedTo();
			if( link != null ) {
				target =  link;
			}
		}
		FileSource link = source.getFileSourceFactory().createLink(source, target);
		if( link == null || !link.exists()) {
			ret = -1;
		}
		
		return ret;
	}


}
