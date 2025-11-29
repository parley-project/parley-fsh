package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Pushd extends DirStack {	
	static String name = "pushd";
	// physical 
	static String help = "pushd\n"
			+ "pushd [-n] [+N | -N | dir]\n"
			+ "Add a directory to the top of the directory stack, or rotate the stack, making the new top of the stack the current working directory. With no arguments, pushd exchanges the top two elements of the directory stack.\n"
			+ "\n"
			+ "Arguments, if supplied, have the following meanings:\n"
			+ "\n"
			+ "-n\n"
			+ "Suppress the normal change of directory when rotating or adding directories to the stack, only manipulate the stack.\n"
			+ "\n"
			+ "+N\n"
			+ "Rotate the stack so that the Nth directory (counting from the left of the list printed by dirs, starting with zero) is at the top.\n"
			+ "\n"
			+ "-N\n"
			+ "Rotate the stack so that the Nth directory (counting from the right of the list printed by dirs, starting with zero) is at the top.\n"
			+ "\n"
			+ "dir\n"
			+ "Make dir be the top of the stack.\n"
			+ "\n"
			+ "After the stack has been modified, if the -n option was not supplied, pushd uses the cd builtin to change to the directory at the top of the stack. If the cd fails, pushd returns a non-zero value.\n"
			+ "\n"
			+ "Otherwise, if no arguments are supplied, pushd returns zero unless the directory stack is empty. When rotating the directory stack, pushd returns zero unless the directory stack is empty or N specifies a non-existent directory stack element.\n"
			+ "\n"
			+ "If the pushd command is successful, Bash runs dirs to show the final contents of the directory stack.";

	public Pushd() {
		super(name, help);
	}
	
	public int process(ShellContext ctx, List<FileSource> dirStack) throws IOException {
		int ret = 0;
		if( args.length== 0 ) {
			//With no arguments, pushd exchanges the top two elements of the directory stack.
			if( dirStack.size()>1) {
				FileSource o = dirStack.remove(1);
				dirStack.add(0,o);
				return ret;
			} else {
				return 1;
			}
		}

		boolean n=false;

		for(Argument arg : args) {
			String a = arg.getValue(ctx).toString();
			if( a.equals("-n")) {
				n = true;
				break;
			} 
		}

		// first replace the top element with cwd
		if( dirStack.size()==0) {
			dirStack.add(ctx.console.getCurrentDirectory());
		} else {
			dirStack.set(0, ctx.console.getCurrentDirectory());
		}
		int direrction = 1;
		int cnt = 0;

		for(int idx=0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.startsWith("-")) {
				cnt = Integer.parseInt(a.substring(1));
				direrction = -1;
			} else if( a.startsWith("+")) {
				cnt = Integer.parseInt(a.substring(1))-1;
			}else {
				if( !n ) {
					Cd cd = new Cd();
					Argument [] aa = {new Argument(a)};
					
					cd.setArgs(aa);
					ret = cd.process(ctx);
					if( ret == 0 ) {
						dirStack.add(0,ctx.console.getCurrentDirectory());						
					}
					print(dirStack,ctx,false,false,false,null);
					return ret;
				} else {
					// change stack but not cwd
					List<FileSource> dirs = getFiles(ctx, a);
					if( dirs==null || dirs.size()==0) {
						ctx.stderr.println("cd: no such file or directory: "+a);
						return -1;
					}
					FileSource dir = dirs.get(0);
					dirStack.add(0,dir);
					print(dirStack,ctx,false,false,false,null);
					return 0;
				}
			}
		}
		ret = rotate(direrction,cnt,ctx,dirStack);
		if( !n ) {
			ctx.console.setCurrentDirectory((FileSource) dirStack.get(0));
		}
		print(dirStack, ctx,false,false,false,null);

		return ret;

	}

}
