package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Ln extends ShellCommand{
	private enum Arguments {s,L,P,f,i,v,x,w,h,n};



	static String name = "ln";
	static String help = "link files."
			+ "     ln [-L | -P | -s ] [-f | -iw] [-hnv] source_file [target_file]\n"
			+ "     ln [-L | -P | -s ] [-f | -iw] [-hnv] source_file ... target_dir\n"
			+ ""
			+ "\t see 'man ln' for more information";

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
		List<FileSource> files = new ArrayList<>();

		for(String path : paths) {
			List<FileSource> kids = getFiles(ctx, path);

			
			files.addAll(kids);
		}

		if( files.size() < 2) {
			ctx.stdout.println(help);
			return -1;
		}

		FileSource lastFile = files.removeLast();
		if( lastFile.isDirectory()) {
			ret = linkFilesInDir(ctx, options, files, lastFile);
		} else {
			if( files.size() != 1) {
				ctx.stdout.println(help);
				return -1;
			}
			ret = linkFiles(ctx, options, files.getFirst(), lastFile);
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
		FileSource link = source.getFileSourceFactory().createSymbolicLink(target, source);
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
