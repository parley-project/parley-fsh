package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.ShellContext;

/** dirs [-clpv] [+N] [-N], as bash's */
public class Dirs extends DirStack {
	static String name = "dirs";
	static String help = "dirs [-clpv] [+N] [-N]\n"
			+ "	Print the directory stack, the current directory first: -l without ~, -p one per line, -v\n"
			+ "	numbered, +N or -N one entry (counted from the left or right); -c clears it.";
	static final String USAGE = "dirs [-clpv] [+N] [-N]";

	public Dirs() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> stack = stack(ctx);
		boolean longForm = false, clear = false;
		int vflag = 0;
		int index = -1;
		int indexFlag = 0;
		String w = "";
		for(int idx = 0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.equals("-l")) {
				longForm = true;
			} else if( a.equals("-c")) {
				clear = true;
			} else if( a.equals("-v")) {
				vflag |= 2;
			} else if( a.equals("-p")) {
				vflag |= 1;
			} else if( a.equals("--")) {
				break;
			} else if( a.startsWith("+") || a.startsWith("-")) {
				Long n = number(a);
				w = a.substring(1);
				if( n == null ) {
					ctx.error("dirs: "+a+": invalid number");
					usage(ctx, USAGE);
					return 2;
				}
				boolean plus = a.startsWith("+");
				// bash's get_dirstack_index
				indexFlag = plus ? 1 : 2;
				int size = stack.size();
				if( n == 0 && plus ) {
					index = 0;
				} else if( n == size ) {
					indexFlag = plus ? 2 : 1;
					index = 0;
				} else if( n >= 0 && n <= size ) {
					index = (int) (plus ? size-n : n);
				} else {
					index = -1;
				}
			} else {
				ctx.error("dirs: "+a+": invalid option");
				usage(ctx, USAGE);
				return 2;
			}
		}
		if( clear ) {
			stack.clear();
			return 0;
		}
		int size = stack.size();
		if( indexFlag != 0 && (index < 0 || index > size)) {
			if( stack.isEmpty()) {
				ctx.error("dirs: directory stack empty");
			} else {
				ctx.error("dirs: "+w+": directory stack index out of range");
			}
			return 1;
		}
		StringBuilder out = new StringBuilder();
		if( indexFlag == 0 || (indexFlag == 1 && index == 0)) {
			String here = format(ctx, cwd(ctx), longForm);
			out.append((vflag & 2) != 0 ? String.format("%2d  %s", 0, here) : here);
			if( indexFlag != 0 ) {
				ctx.stdout.println(out);
				return 0;
			}
		}
		if( indexFlag != 0 ) {
			String e = format(ctx, stack.get(index), longForm);
			out.append((vflag & 2) != 0 ? String.format("%2d  %s", size-index, e) : e);
		} else {
			for (int i = size-1; i >= 0; i--) {
				String e = format(ctx, stack.get(i), longForm);
				if( vflag >= 2 ) {
					out.append(String.format("\n%2d  %s", size-i, e));
				} else {
					out.append((vflag & 1) != 0 ? "\n" : " ").append(e);
				}
			}
		}
		ctx.stdout.println(out);
		return 0;
	}

	private static String format(ShellContext ctx, String dir, boolean longForm) {
		return longForm ? dir : polite(ctx, dir);
	}
}
