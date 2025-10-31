package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public abstract class DirStack extends ShellCommand{

	public DirStack(String name, String help) {
		super(name, help);
	}

	public static final String DIRSTACK="DIRSTACK";
	
	protected static class DirStackArgument extends Argument {
		String value;
		public DirStackArgument(String value) {
			super(null);
			this.value = value;
		}
		
		@Override
		public Object getValue(ShellContext ctx) throws IOException {
			return value;
		}
		
	}
	
	public int process(ShellContext ctx) throws IOException {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<FileSource> tmp = (List)ctx.getVariable(DIRSTACK);
		if( tmp == null ) {
			// can't use fssshlist because it really a map :-(
			tmp = new ArrayList<>();
			ctx.setVariable(DIRSTACK, tmp);
		}
		if (tmp instanceof List) {
			List<FileSource> dirStack = tmp;
			return process(ctx,dirStack);
			
		} else {
			return 1;
		}
	}

	public abstract int process(ShellContext ctx, List<FileSource> dirStack2) throws IOException ;

	protected void print(List<FileSource> stack,ShellContext ctx,boolean onePerLine,boolean showIndex,boolean fullPath,Integer N) {
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

	protected int rotate(int direrction, int cnt, ShellContext ctx,List<FileSource> dirStack) {
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
