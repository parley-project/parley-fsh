package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

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
		boolean options = true;
		for(int idx=0; idx < args.length; idx++ ) {
			String arg = (""+args[idx].getValue(ctx)).trim();
			if( options && arg.equals("--")) {
				options = false;
			} else if( options && arg.startsWith("-") && arg.length() > 1 ) {
				follow = arg.equals("-P");
			} else {
				sargs.add(arg);
			}
		}
		if( sargs.size() > 1 ) {
			ctx.stderr.println("cd: too many arguments");
			return 1;
		}
		String path;
		// cd - (and a directory found on $CDPATH) prints where it went, as in bash
		boolean print = false;
		if( sargs.isEmpty()) {
			Object home = ctx.getVariable("HOME");
			if( home == null || home.toString().isEmpty()) {
				ctx.stderr.println("cd: HOME not set");
				return 1;
			}
			path = home.toString();
		} else if( sargs.get(0).equals("-")) {
			Object old = ctx.getVariable(Console.VARIABLE_OLDPWD);
			if( old == null || old.toString().isEmpty()) {
				ctx.stderr.println("cd: OLDPWD not set");
				return 1;
			}
			path = old.toString();
			print = true;
		} else {
			path = sargs.get(0);
		}

		FileSource dir = null;
		if( !path.isEmpty() && !path.startsWith("/") && !path.startsWith("./") && !path.startsWith("../")
				&& !path.equals(".") && !path.equals("..") && !path.startsWith("~")) {
			// a relative name is looked for in each directory of $CDPATH (an empty entry is .)
			Object cdpath = ctx.getVariable("CDPATH");
			if( cdpath != null && !cdpath.toString().isEmpty()) {
				for(String entry : cdpath.toString().split(":", -1)) {
					List<FileSource> found = getFiles(ctx, entry.isEmpty() ? path : entry+"/"+path);
					if( found != null && !found.isEmpty() && found.get(0).isDirectory()) {
						dir = found.get(0);
						print |= !entry.isEmpty();
						break;
					}
				}
			}
		}
		if( dir == null ) {
			List<FileSource> dirs = getFiles(ctx, path);
			if( dirs==null || dirs.size()==0 || !dirs.get(0).exists()) {
				ctx.stderr.println("cd: "+path+": No such file or directory");
				return 1;
			}
			dir = dirs.get(0);
		}
		
		if( follow ) {
			FileSource link = dir.getLinkedTo();
			while(link !=null ) {
				dir = link;
				link = dir.getLinkedTo();
			}
		}
		if( !dir.isDirectory()) {
			ctx.stderr.println("cd: "+path+": Not a directory");
			return 1;
		}
		//  PWD and OLD_PWD variables are managed by console (in a pipe stage, set here: the stage's own)
		String old = ctx.console.getCurrentDirectory().getAbsolutePath();
		ctx.console.setCurrentDirectory(dir);
		if( ctx.isIsolated()) {
			ctx.setVariable(Console.VARIABLE_OLDPWD, old);
			ctx.setVariable(Console.VARIABLE_PWD, dir.getAbsolutePath());
		}
		if( print ) {
			ctx.stdout.println(dir.getAbsolutePath());
		}
		return ret;
	}

}
