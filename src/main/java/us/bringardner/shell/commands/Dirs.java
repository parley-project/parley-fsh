package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Dirs extends DirStack{
	static String name = "dirs";
	// physical 
	static String help = "dirs [-clpv] [+N | -N]\n"
			+ "Without options, display the list of currently remembered directories. Directories are added to the list with the pushd command; the popd command removes directories from the list. The current directory is always the first directory in the stack.\n"
			+ "\n"
			+ "Options, if supplied, have the following meanings:\n"
			+ "\n"
			+ "-c\n"
			+ "Clears the directory stack by deleting all of the elements.\n"
			+ "\n"
			+ "-l\n"
			+ "Produces a listing using full pathnames; the default listing format uses a tilde to denote the home directory.\n"
			+ "\n"
			+ "-p\n"
			+ "Causes dirs to print the directory stack with one entry per line.\n"
			+ "\n"
			+ "-v\n"
			+ "Causes dirs to print the directory stack with one entry per line, prefixing each entry with its index in the stack.\n"
			+ "\n"
			+ "+N\n"
			+ "Displays the Nth directory (counting from the left of the list printed by dirs when invoked without options), starting with zero.\n"
			+ "\n"
			+ "-N\n"
			+ "Displays the Nth directory (counting from the right of the list printed by dirs when invoked without options), starting with zero.";

	public Dirs() {
		super(name, help);
	}
	
	public int process(ShellContext ctx, List<FileSource> stack) throws IOException {
		int ret = 0;
		boolean fullPath= false;
		boolean onePerLine= false;
		boolean showIndex= false;
		Integer N=null;
		for(Argument arg : args) {
			String a = ""+arg.getValue(ctx);
			if( a.equals("dirs")) {
				continue;
			}
			
			if( a.startsWith("+")) {
				N = Integer.parseInt(a.substring(1));	
			} else if( a.startsWith("-")) {
				if(Character.isDigit(a.charAt(1))) {
					N = stack.size()-Integer.parseInt(a.substring(1))-1;
				} else {
					for(int i=1; i < a.length(); i++) {
						switch (a.charAt(i)) {
						case 'c':stack.clear();return 0;
						case 'l':fullPath = true;break;
						case 'p':onePerLine = true;break;
						case 'v':showIndex = onePerLine = true; break;
						default:
							throw new IllegalArgumentException("Unexpected value: " + a.charAt(i));
						}
					}
				}
			}
		}
		List<FileSource> tmp = new ArrayList<>();
		tmp.addAll(stack);
		if( tmp.size()==0) {
			tmp.add(ctx.console.getCurrentDirectory());
		} else {
			tmp.set(0, ctx.console.getCurrentDirectory());
		}
		if( N != null ) {
			if( N<0 || N>=tmp.size()) {
				ctx.stderr.println("dirs: "+N+": directory stack index out of range");
				ret = 1;
			} else {
				print(tmp, ctx, onePerLine, showIndex, fullPath,N);
			}
		} else {		
			print(tmp, ctx, onePerLine, showIndex, fullPath,N);
		}
		return ret;
	}
		
}
