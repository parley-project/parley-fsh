package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.ShellContext;

/** pushd [-n] [+N | -N | dir], as bash's */
public class Pushd extends DirStack {
	static String name = "pushd";
	static String help = "pushd [-n] [+N | -N | dir]\n"
			+ "	Add dir to the top of the directory stack (going there), or rotate the stack (+N, -N,\n"
			+ "	counted from the left or right of dirs' list) so that entry is on top. With nothing, swap the\n"
			+ "	top two. -n changes the stack only, not the directory.";
	static final String USAGE = "pushd [-n] [+N | -N | dir]";

	public Pushd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> stack = stack(ctx);
		int idx = 0;
		boolean skipOptions = false;
		if( args.length > 0 && "--".equals(""+args[0].getValue(ctx))) {
			idx++;
			skipOptions = true;
		}
		if( idx >= args.length ) {
			// swap the top two
			if( stack.isEmpty()) {
				ctx.error("pushd: no other directory");
				return 1;
			}
			String here = cwd(ctx);
			String top = stack.get(stack.size()-1);
			int ret = cd(ctx, "pushd", top);
			if( ret == 0 ) {
				stack.set(stack.size()-1, here);
				print(ctx);
			}
			return ret;
		}
		boolean nocd = false;
		Long rotate = null;
		for(; !skipOptions && idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.equals("-n")) {
				nocd = true;
			} else if( a.equals("--")) {
				idx++;
				break;
			} else if( a.equals("-")) {
				break;
			} else if( a.startsWith("+") || a.startsWith("-")) {
				Long n = number(a);
				if( n == null ) {
					ctx.error("pushd: "+a+": invalid number");
					usage(ctx, USAGE);
					return 2;
				}
				if( a.startsWith("-")) {
					n = stack.size()-n;
				}
				if( n > stack.size() || n < 0 ) {
					indexError(ctx, a);
					return 1;
				}
				rotate = n;
			} else {
				break;
			}
		}
		if( rotate != null ) {
			// the current directory is part of what turns
			String temp = cwd(ctx);
			long n = rotate;
			if( n == 0 ) {
				return nocd ? 0 : cdAndPrint(ctx, temp);
			}
			do {
				String top = stack.get(stack.size()-1);
				stack.remove(stack.size()-1);
				stack.add(0, temp);
				temp = top;
			} while( --n > 0 );
			if( nocd ) {
				print(ctx);
				return 0;
			}
			return cdAndPrint(ctx, temp);
		}
		if( idx >= args.length ) {
			return 0;
		}
		String dir = ""+args[idx].getValue(ctx);
		String here = cwd(ctx);
		int ret = nocd ? 0 : cd(ctx, "pushd", dir);
		if( ret == 0 ) {
			stack.add(nocd ? dir : here);
			print(ctx);
		}
		return ret;
	}

	private static int cdAndPrint(ShellContext ctx, String dir) throws IOException {
		int ret = cd(ctx, "pushd", dir);
		if( ret == 0 ) {
			print(ctx);
		}
		return ret;
	}
}
