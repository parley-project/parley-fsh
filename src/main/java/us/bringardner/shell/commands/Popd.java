package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Popd extends DirStack {
	static String name = "popd";
	// physical 
	static String help = "popd [-n] [+N | -N]\n"
			+ "Remove elements from the directory stack. The elements are numbered from 0 starting at the first directory listed by dirs; that is, popd is equivalent to popd +0.\n"
			+ "\n"
			+ "When no arguments are given, popd removes the top directory from the stack and changes to the new top directory.\n"
			+ "\n"
			+ "Arguments, if supplied, have the following meanings:\n"
			+ "\n"
			+ "-n\n"
			+ "Suppress the normal change of directory when removing directories from the stack, only manipulate the stack.\n"
			+ "\n"
			+ "+N\n"
			+ "Remove the Nth directory (counting from the left of the list printed by dirs), starting with zero, from the stack.\n"
			+ "\n"
			+ "-N\n"
			+ "Remove the Nth directory (counting from the right of the list printed by dirs), starting with zero, from the stack.\n"
			+ "\n"
			+ "If the top element of the directory stack is modified, and the -n option was not supplied, popd uses the cd builtin to change to the directory at the top of the stack. If the cd fails, popd returns a non-zero value.\n"
			+ "\n"
			+ "Otherwise, popd returns an unsuccessful status if an invalid option is specified, the directory stack is empty, or N specifies a non-existent directory stack entry.\n"
			+ "\n"
			+ "If the popd command is successful, Bash runs dirs to show the final contents of the directory stack, and the return status is 0.";

	public Popd() {
		super(name, help);
	}
	
	
	public int process(ShellContext ctx, List<FileSource> stack) throws IOException {
		int ret = 0;
		if(stack.size()>0) {
			boolean n=false;
			int idx =0;

			for(Argument arg : args) {
				String a = ""+arg.getValue(ctx);
				if( a.equals("-n")) {
					n = true;
					break;
				} else if( a.startsWith("+")) {
					idx = Integer.parseInt(a.substring(1));					
				} else if( a.startsWith("-")) {
					idx = stack.size()-Integer.parseInt(a.substring(1))-1;
				}
			}
			if( idx >=0 && idx < stack.size()) {
				stack.remove(idx);
				if( !n && stack.size()>0) {
					Cd cd = new Cd();
					Argument [] aa = {new DirStackArgument(stack.get(0).toString())};
					cd.setArgs(aa);
					ret = cd.process(ctx);
					if( ret == 0 ) {
						print(stack,ctx,false,false,false,null);
					}
					return ret;		
				}
			}
		}
		return ret;
	}

}
