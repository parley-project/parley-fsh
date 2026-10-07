package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Unset extends ShellCommand{
	static String name = "unset";
	static String help = "unset [-f] [-v] [name ...]\n"
			+ "	Remove each variable or function name.\n"
			+ "	-f  the names are functions\n"
			+ "	-v  the names are variables\n"
			+ "	With neither, a name is a variable, or a function if there is no such variable.\n"
			+ "	Exit status: 0 unless an option is invalid."
			;

	private static final java.util.regex.Pattern ELEMENT = java.util.regex.Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)\\[(.+)\\]");

	public Unset() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean functions = false;
		boolean variables = false;
		int idx = 0;
		for(; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( text.equals("--")) {
				idx++;
				break;
			}
			if( !text.startsWith("-") || text.length() < 2 ) {
				break;
			}
			for(char c : text.substring(1).toCharArray()) {
				if( c == 'f' ) {
					functions = true;
				} else if( c == 'v' || c == 'n') {
					variables = true;
				} else {
					ctx.stderr.println("unset: -"+c+": invalid option");
					ctx.stderr.println("unset: usage: unset [-f] [-v] [name ...]");
					return 2;
				}
			}
		}
		if( functions && variables ) {
			ctx.stderr.println("unset: cannot simultaneously unset a function and a variable");
			return 1;
		}

		int ret = 0;
		for(; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( ctx.console.isReadonly(text) && !functions ) {
				ctx.stderr.println("unset: "+text+": cannot unset: readonly variable");
				ret = 1;
				continue;
			}
			java.util.regex.Matcher m = ELEMENT.matcher(text);
			if( !functions && m.matches()) {
				// unset 'a[1]' or 'm[key]': one element
				Object val = ctx.getVariable(m.group(1));
				if( val instanceof java.util.Map<?,?> ) {
					((java.util.Map<?,?>) val).remove(m.group(2));
				} else if( val instanceof java.util.List<?> ) {
					((java.util.List<?>) val).remove(us.bringardner.fsh.antlr.Arithmetic.expandAndEvaluate(m.group(2), ctx).intValue());
				}
			} else if( functions ) {
				ctx.removeFunction(text);
			} else if( !ctx.unSetVariable(text) && !variables ) {
				// as in bash, a name that is no variable may be a function
				ctx.removeFunction(text);
			}
		}
		// an unset name is not an error (a readonly one is)
		return ret;
	}
}
