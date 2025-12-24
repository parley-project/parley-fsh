package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Rm extends ShellCommand{
	
	
	private enum Arguments {d,f,i,I,R,r,v,x};




	static String name = "rm";
	static String help = ""
			+ "USAGE:  rm [-f | -i] [-dIRrvWx] file ...\n"

			+ "\n"
			+ "     The rm utility attempts to remove the files specified on the command line.  "
			+ "\t see 'man rm' for more information";

	public Rm() {
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
				if( o == Arguments.R) {
					a = Arguments.r;
				} else if( o == Arguments.i) {
					options.remove(Arguments.f);
				} else if( o == Arguments.f) {
					options.remove(Arguments.i);
					options.remove(Arguments.I);
				} else if( o == Arguments.I) {
					// The -R option implies the -d option
					options.add(Arguments.d);
					options.remove(Arguments.f);
				}
				options.add(a);				
			}
		}
		
		List<String> paths = sa.paths;
		List<FileSource> files = new ArrayList<>();

		for(String path : paths) {
			List<FileSource> kids = new ArrayList<>();

			if( hasWildcard(path)) {
				kids.addAll(getFiles(ctx, path));
			} else {
				FileSource cwd = ctx.console.getCurrentDirectory();
				if( isRelative(path)) {
					kids.add(cwd.getChild(path));
				} else {
					kids.add(ctx.console.createFileSource(path));					
				}
			}
			//If the source_file ends in a /, the contents of the directory are copied rather than the directory itself.  

			if(path.equals("/") || path.equals(".") || path.equals("..")) {
				throw new IOException("Can't remove +"+path);
			}

			files.addAll(kids);
		}

		if( files.size() < 1) {
			ctx.stdout.println(help);
			return 1;
		}

		if( files.size() > 3) {
			if( options.contains(Arguments.I)) {
				String str = promptAndGetReponse(ctx, "Are you sure you want to delete "+files.size()+" files?");
				if( !str.trim().toLowerCase().startsWith("y")) {
					return 1;
				} else {
					ctx.setValue(Arguments.I, true);
				}
			}
		}


		for(FileSource file : files) {
			ctx.setValue(ROOT_FACTORY_ID, file.getFileSourceFactory().getTypeId());
			remove(ctx,options,file);
		}

		return ret;
	}

	private void remove(ShellContext ctx, List<Arguments> options, FileSource file) throws IOException {
		if( !file.exists()) {
			if(!options.contains(Arguments.f)) {
				return; 
			} else {
				throw new IOException(""+file+" does not exist");
			}
		}
		if( options.contains(Arguments.x)) {
			if(!file.getFileSourceFactory().getTypeId().equals(ctx.getValue(ROOT_FACTORY_ID, ""))) {
				return;
			}
		}
		if(file.isDirectory()) {
			if( !options.contains(Arguments.r) && !options.contains(Arguments.d)) {
				throw new IOException(""+file+" is a directory");
			}

			FileSource [] kids = file.listFiles();
			if(options.contains(Arguments.d) && kids.length>0) {
				throw new IOException("the directory, "+file+", is NOT empty.");
			}
			if( options.contains(Arguments.r)) {
				if( options.contains(Arguments.I)) {
					boolean val =  (boolean) ctx.getValue(Arguments.I, false);
					if( !val ) {
						String str = promptAndGetReponse(ctx, "Are you sure you want to delete the entire directory "+file+" ?");
						if( !str.trim().toLowerCase().startsWith("y")) {
							throw new IOException("User abort");							
						} else {
							ctx.setValue(Arguments.I, true);
						}
					}
				}
				for(FileSource kid : kids) {
					remove(ctx, options, kid);
				}
			} else {
				if( kids.length>0) {
					throw new IOException("the directory, "+file+", is NOT empty.");
				}
			}
		}
		if(options.contains(Arguments.i)) {
			String str = promptAndGetReponse(ctx, "remove "+file+"?");
			if(!str.trim().toLowerCase().startsWith("y")) {
				return;
			}
		}

		if( !file.delete()) {
			throw new IOException("Can't delete "+file);
		} else if( options.contains(Arguments.v)) {
			ctx.stdout.println(file.getAbsolutePath());
		}
	}


}
