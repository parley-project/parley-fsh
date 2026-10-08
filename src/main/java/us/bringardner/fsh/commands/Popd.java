package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.ShellContext;

/** popd [-n] [+N | -N], as bash's */
public class Popd extends DirStack {
	static String name = "popd";
	static String help = "popd [-n] [+N | -N]\n"
			+ "	Take the top directory off the stack and go to the new top, or take off entry N\n"
			+ "	(counted from the left or right of dirs' list). -n changes the stack only.";
	static final String USAGE = "popd [-n] [+N | -N]";

	public Popd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> stack = stack(ctx);
		boolean nocd = false;
		long which = 0;
		char direction = '+';
		String whichWord = null;
		for(int idx = 0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.equals("-n")) {
				nocd = true;
			} else if( a.equals("--")) {
				if( idx+1 < args.length ) {
					String next = ""+args[idx+1].getValue(ctx);
					if( next.startsWith("+") || next.startsWith("-")) {
						Long n = number(next);
						if( n == null ) {
							ctx.error("popd: "+next+": invalid number");
							usage(ctx, USAGE);
							return 2;
						}
						which = n;
						direction = next.charAt(0);
						whichWord = next;
					}
				}
				break;
			} else if( a.startsWith("+") || a.startsWith("-")) {
				Long n = number(a);
				if( n == null ) {
					ctx.error("popd: "+a+": invalid number");
					usage(ctx, USAGE);
					return 2;
				}
				which = n;
				direction = a.charAt(0);
				whichWord = a;
			} else if( !a.isEmpty()) {
				ctx.error("popd: "+a+": invalid argument");
				usage(ctx, USAGE);
				return 2;
			}
		}
		int size = stack.size();
		if( which > size || (stack.isEmpty() && which == 0)) {
			indexError(ctx, whichWord == null ? "" : whichWord);
			return 1;
		}
		if( (direction == '+' && which == 0) || (direction == '-' && which == size)) {
			// the top: go there
			if( !nocd ) {
				int ret = cd(ctx, "popd", stack.get(size-1));
				if( ret != 0 ) {
					return ret;
				}
			}
			stack.remove(size-1);
		} else {
			int i = (int) (direction == '+' ? size-which : which);
			if( i < 0 || i >= size ) {
				indexError(ctx, whichWord == null ? "" : whichWord);
				return 1;
			}
			stack.remove(i);
		}
		print(ctx);
		return 0;
	}
}
