package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class DirStack extends ShellCommand{
	public static final String DIRSTACK="DIRSTACK";
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

	public DirStack() {
		super(name, help);
	}
	//dirs [-clpv] [+N | -N]

	@Override
	public int process(ShellContext ctx) throws IOException {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<FileSource> tmp = (List)ctx.getVariable(DIRSTACK);
		if( tmp == null ) {
			// can't use fssshlist becouse it really a map :-(
			tmp = new ArrayList<>();
			ctx.setVariable(DIRSTACK, tmp);
		}
		if (tmp instanceof List) {
			List<FileSource> dirStack = tmp;
			if( ctx.args[0].equals("pushd")) {
				return pushd(ctx,dirStack);
			} else if( ctx.args[0].equals("popd")) {
				return popd(ctx,dirStack);
			}else if( ctx.args[0].equals("dirs")) {
				return dirs(ctx,dirStack);
			} else {
				throw new IOException("Unknown function = "+ctx.args[0]);
			}
		} else {
			return 1;
		}
	}

	private int dirs(ShellContext ctx, List<FileSource> stack) throws IOException {
		int ret = 0;
		boolean fullPath= false;
		boolean onePerLine= false;
		boolean showIndex= false;
		Integer N=null;
		for(String a : ctx.args) {
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

	private int popd(ShellContext ctx, List<FileSource> stack) throws IOException {
		int ret = 0;
		if(stack.size()>0) {
			boolean n=false;
			int idx =0;

			for(String a : ctx.args) {
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
					String [] aa = {"cd",stack.get(0).toString()};
					ctx.args = aa;
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

	private int pushd(ShellContext ctx, List<FileSource> dirStack) throws IOException {
		int ret = 0;
		if( ctx.args.length== 1 ) {
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

		for(String a : ctx.args) {
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
					String [] aa = {"cd",a};
					ctx.args = aa;
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

	private void print(List<FileSource> stack,ShellContext ctx,boolean onePerLine,boolean showIndex,boolean fullPath,Integer N) {
		String tmp = ""+ctx.getVariable("HOME");
		StringBuilder buf = new StringBuilder();

		for (int idx = 0,sz=stack.size(); idx < sz; idx++) {
			if(N!=null && N!=idx) {
				continue;
			}
			if( idx > 0 ) {
				if( onePerLine) {
					if( !buf.isEmpty()) {
						buf.append('\n');
					}
				} else {
					if( !buf.isEmpty()) {
						buf.append(' ');
					}
				}
			}
			if( showIndex) {
				buf.append(""+idx+"\t");
			}
			String path = stack.get(idx).toString();
			if(!fullPath && path.startsWith(tmp)) {
				path = path.replace(tmp, "~");
			}
			buf.append(path);
		}
		ctx.stdout.println(buf);
	}

	private int rotate(int direrction, int cnt, ShellContext ctx,List<FileSource> dirStack) {
		int ret = 0;
		if( cnt >= 0 && dirStack.size()> cnt) {
			FileSource d1 = null;

			while(cnt-->=0) {
				if( direrction==1) {
					d1 = (FileSource) dirStack.remove(0);
					dirStack.add(d1);
				} else {
					d1 = (FileSource) dirStack.remove(dirStack.size()-1);
					dirStack.add(0,d1);
				}				
			}
		}else {
			ret = 1;
		}

		return ret;
	}

}
