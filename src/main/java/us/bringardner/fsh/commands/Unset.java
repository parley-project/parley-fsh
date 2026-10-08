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
		// unset -n ref: the reference, not what it names
		boolean reference = false;
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
					reference |= c == 'n';
				} else {
					ctx.error("unset: -"+c+": invalid option");
					ctx.error("unset: usage: unset [-f] [-v] [name ...]");
					return 2;
				}
			}
		}
		if( functions && variables ) {
			ctx.error("unset: cannot simultaneously unset a function and a variable");
			return 1;
		}

		int ret = 0;
		for(; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			boolean viaRef = false;
			if( !functions && !reference && us.bringardner.fsh.exec.Executor.isName(text)) {
				// a nameref to an element (r=a[1]): unset r unsets that element
				String target = ctx.resolveName(text);
				if( !target.equals(text) && ELEMENT.matcher(target).matches()) {
					text = target;
					viaRef = true;
				}
			}
			String ro = reference || functions ? text : ctx.readonlyName(text);
			if( ctx.console.isReadonly(ro) && !functions ) {
				ctx.error("unset: "+ro+": cannot unset: readonly variable");
				ret = 1;
				continue;
			}
			java.util.regex.Matcher m = ELEMENT.matcher(text);
			if( !functions && m.matches()) {
				// unset 'a[1]' or 'm[key]': one element
				Object val = ctx.getVariable(m.group(1));
				boolean all = m.group(2).equals("@") || m.group(2).equals("*");
				if( all && val instanceof java.util.List<?> ) {
					// unset 'a[@]': every element (a stays an array; for an associative one, bash 5.3
					// unsets the key @ or *)
					((java.util.List<?>) val).clear();
				} else if( val != null && !(val instanceof java.util.Map<?,?>) && !(val instanceof java.util.List<?>)) {
					// a scalar is its element 0
					if( !all && us.bringardner.fsh.expand.Arithmetic.evaluate(m.group(2), ctx).longValue() == 0 ) {
						ctx.unSetVariable(m.group(1), true);
					} else if( !viaRef ) {
						ctx.error("unset: "+m.group(1)+": not an array variable");
						ret = 1;
					}
				} else if( val instanceof java.util.Map<?,?> ) {
					((java.util.Map<?,?>) val).remove(m.group(2));
				} else if( val instanceof java.util.List<?> ) {
					int index = us.bringardner.fsh.expand.Arithmetic.evaluate(m.group(2), ctx).intValue();
					if( index < 0 ) {
						// unset 'a[-1]': from the end, past the highest index
						java.util.List<Object> keys = keys(val);
						int last = keys.isEmpty() ? -1 : ((Number) keys.get(keys.size()-1)).intValue();
						index += last+1;
					}
					if( index >= 0 ) {
						((java.util.List<?>) val).remove(index);
					} else {
						// unset 'a[-10]' past the start
						ctx.error("unset: ["+m.group(2)+"]: bad array subscript");
						ret = 1;
					}
				}
			} else if( functions ) {
				ctx.removeFunction(text);
			} else {
				if( text.equals("RANDOM") || text.equals("SRANDOM") || text.equals("SECONDS") || text.equals("BASH_ALIASES") || text.equals("BASH_CMDS")) {
					// as bash: no longer special
					ctx.console.unsetSpecials.add(text);
				}
				if( reference && ctx.rawVariable(text) != null && !(ctx.rawVariable(text) instanceof ShellContext.NameRef)) {
					// unset -n of a variable that is no nameref: nothing (bash 5.3's)
					continue;
				}
				ctx.console.pendingExports.remove(text);
				boolean declared = ctx.console.declaredUnset.remove(text);
				if( !ctx.unSetVariable(text, !reference) && !declared ) {
					if( !variables ) {
						// as in bash, a name that is no variable may be a function
						ctx.removeFunction(text);
					}
				} else if( ctx.getVariable(text) == null ) {
					// gone: so are its attributes (-i, -l, -u, -c)
					ctx.console.setInteger(text, false);
					ctx.console.setCaseAttribute(text, null);
				}
			}
		}
		// an unset name is not an error (a readonly one is)
		return ret;
	}

	/** the indexes of an array (or the keys of an associative one), in order */
	private static java.util.List<Object> keys(Object val) {
		java.util.List<Object> ret = new java.util.ArrayList<>();
		if( val instanceof us.bringardner.fsh.FshList list ) {
			ret.addAll(list.getIndexes());
		} else if( val instanceof java.util.Map<?,?> map ) {
			ret.addAll(map.keySet());
		} else if( val instanceof java.util.List<?> list ) {
			for (int i = 0; i < list.size(); i++) {
				ret.add(i);
			}
		}
		return ret;
	}
}
