package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * pushd, popd and dirs: the directory stack, as bash keeps it (the current directory and, after
 * it, the stack, the most recent first; $DIRSTACK shows it).
 */
public abstract class DirStack extends ShellCommand{

	public static final String DIRSTACK="DIRSTACK";

	public DirStack(String name, String help) {
		super(name, help);
	}

	/** the stack below the current directory, oldest first (bash's pushd_directory_list) */
	static List<String> stack(ShellContext ctx) {
		return ctx.console.dirStack;
	}

	static String cwd(ShellContext ctx) throws IOException {
		return ctx.console.getCurrentDirectory().getAbsolutePath();
	}

	/** ~/x for a directory under $HOME (not -l), as bash's polite_directory_format */
	static String polite(ShellContext ctx, String dir) {
		Object h = ctx.getVariable("HOME");
		String home = h == null ? "" : h.toString();
		if( home.length() > 1 && dir.startsWith(home) && (dir.length() == home.length() || dir.charAt(home.length()) == '/')) {
			return "~"+dir.substring(home.length());
		}
		return dir;
	}

	/** +N or -N: N, or null if it is not a number */
	static Long number(String word) {
		String n = word.substring(1);
		return n.matches("[0-9]+") && n.length() < 18 ? Long.parseLong(n) : null;
	}

	void usage(ShellContext ctx, String text) {
		ctx.stderr.println(getName()+": usage: "+text);
	}

	/** the error for an index out of the stack: directory stack empty, or ...index out of range */
	void indexError(ShellContext ctx, String word) {
		if( stack(ctx).isEmpty()) {
			ctx.error(getName()+": directory stack empty");
		} else {
			ctx.error(getName()+": "+word+": directory stack index out of range");
		}
	}

	/** dirs with no arguments: the whole stack on one line */
	static void print(ShellContext ctx) throws IOException {
		StringBuilder ret = new StringBuilder(polite(ctx, cwd(ctx)));
		List<String> stack = stack(ctx);
		for (int i = stack.size()-1; i >= 0; i--) {
			ret.append(' ').append(polite(ctx, stack.get(i)));
		}
		ctx.stdout.println(ret);
	}

	static int cd(ShellContext ctx, String who, String dir) throws IOException {
		return Cd.change(ctx, who, dir, false, false);
	}
}
