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
	static String help = "cd [-L|-P] [dir]\n\tChange the current directory (to $HOME without dir).\n"
			+ "\t [-L] (default) Change the current working directory\n"
			+ "\t -P (physical) Change current working directory after following all symbolic links.";
	
	public Cd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( ctx.console.restricted ) {
			ctx.error(getName()+": restricted");
			return 1;
		}
		int ret = 0;
		// (set -P: physically, unless -L)
		boolean follow = ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.DontFollowLinks);
		List<String> sargs = new ArrayList<>();
		boolean options = true;
		for(int idx=0; idx < args.length; idx++ ) {
			String arg = (""+args[idx].getValue(ctx)).trim();
			if( options && arg.equals("--")) {
				options = false;
			} else if( options && arg.startsWith("-") && arg.length() > 1 ) {
				for(char c : arg.substring(1).toCharArray()) {
					if( c == 'P' ) {
						follow = true;
					} else if( c == 'L' ) {
						follow = false;
					}
				}
			} else {
				sargs.add(arg);
			}
		}
		if( sargs.size() > 1 ) {
			ctx.error("cd: too many arguments");
			return 1;
		}
		String path;
		// cd - (and a directory found on $CDPATH) prints where it went, as in bash
		boolean print = false;
		if( sargs.isEmpty()) {
			Object home = ctx.getVariable("HOME");
			if( home == null || home.toString().isEmpty()) {
				ctx.error("cd: HOME not set");
				return 1;
			}
			path = home.toString();
		} else if( sargs.get(0).equals("-")) {
			Object old = ctx.getVariable(Console.VARIABLE_OLDPWD);
			if( old == null ) {
				ctx.error("cd: OLDPWD not set");
				return 1;
			}
			if( old.toString().isEmpty()) {
				// (an empty OLDPWD: nowhere to go, said as the directory, and it is set to here, as bash's)
				ctx.stdout.println();
				ctx.setVariable(Console.VARIABLE_OLDPWD, ctx.getVariable(Console.VARIABLE_PWD));
				return 0;
			}
			path = old.toString();
			print = true;
		} else {
			path = sargs.get(0);
			if( path.isEmpty()) {
				// cd "": as bash says it
				ctx.error("cd: null directory");
				return 1;
			}
		}

		return change(ctx, "cd", path, follow, print);
	}

	/** go to path (on $CDPATH for a relative name) as cd does; messages say who (cd, pushd, popd) */
	static int change(ShellContext ctx, String who, String path, boolean follow, boolean print) throws IOException {
		int ret = 0;
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
				Object named = us.bringardner.fsh.Glob.option(ctx, "cdable_vars") && path.matches("[A-Za-z_][A-Za-z0-9_]*")
						? ctx.getVariable(path) : null;
				if( named != null && !named.toString().isEmpty()) {
					// shopt -s cdable_vars: a variable's value is the directory (and is said)
					return change(ctx, who, named.toString(), follow, true);
				}
				ctx.error(who+": "+path+": No such file or directory");
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
			if( dir instanceof us.bringardner.parley.files.fileproxy.FileProxy proxy && proxy.getTarget().exists()) {
				// (every link in the path resolved, as cd -P does)
				dir = ctx.console.createFileSource(proxy.getTarget().getCanonicalPath());
			}
		}
		if( !dir.isDirectory()) {
			ctx.error(who+": "+path+": Not a directory");
			return 1;
		}
		//  PWD and OLD_PWD variables are managed by console (in a pipe stage, set here: the stage's own)
		String old = ctx.console.getCurrentDirectory().getAbsolutePath();
		if( ctx.console.isReadonly(Console.VARIABLE_PWD) && !ctx.isIsolated()) {
			// a readonly PWD: the directory changes, PWD does not (said, status 1), as bash's
			ctx.console.changeDirectory(dir);
			ctx.setVariable(Console.VARIABLE_OLDPWD, old);
			ctx.error(Console.VARIABLE_PWD+": readonly variable");
			return 1;
		}
		ctx.console.setCurrentDirectory(dir);
		ctx.setVariable(Console.VARIABLE_OLDPWD, old);
		if( ctx.isIsolated()) {
			ctx.setVariable(Console.VARIABLE_PWD, dir.getAbsolutePath());
		}
		if( print ) {
			ctx.stdout.println(dir.getAbsolutePath());
		}
		return ret;
	}

}
