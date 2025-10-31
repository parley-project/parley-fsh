package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Cd extends ShellCommand{
	static String name = "cd";
	// physical 
	static String help = "change the current directory\n"
			+ "\t [-L] (default) Change the current working directory\n"
			+ "\t -P (physical) Change current working directory after following all symbolic links.";
	
	public Cd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		boolean follow = false;
		List<String> sargs = new ArrayList<>();
		for(int idx=0; idx < args.length; idx++ ) {
			String arg = (""+args[idx].getValue(ctx)).trim();
			if( arg.startsWith("-")) {
				follow = arg.equals("-P");
			} else {
				sargs.add(arg);
			}
		}
		String path = "~";
		
		if( sargs.size()>0) {
			if( sargs.size()>2) {
				ctx.stderr.println("cd: too many args: "+path);
				return -1;
			}			
			path = sargs.get(0);
		}
		
		List<FileSource> dirs = getFiles(ctx, path);
		if( dirs==null || dirs.size()==0) {
			ctx.stderr.println("cd: no such file or directory: "+path);
			return -1;
		}
		FileSource dir = dirs.get(0);
		
		if( follow ) {
			FileSource link = dir.getLinkedTo();
			while(link !=null ) {
				dir = link;
				link = dir.getLinkedTo();
			}
		}
		if( !dir.isDirectory()) {
			ctx.stderr.println("cd: not a directory: "+path);
			return -1;
		}

		//  PWD and OLD_PWD variables are managed by console
		ctx.console.setCurrentDirectory(dir);
		
		
		
		return ret;
	}

}
