package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * enable: turn builtins off (-n) and on again; with no names, list them.
 */
public class Enable extends ShellCommand{
	static String name = "enable";
	static String help = "enable [-a] [-n] [-p] [name ...]\n"
			+ "	Turn on the builtins named, or turn them off with -n (the name then runs a program\n"
			+ "	of that name). With no names, print the enabled builtins (-n: the disabled ones,\n"
			+ "	-a: all)."
			;

	public Enable() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean disable = false;
		boolean all = false;
		List<String> names = new ArrayList<>();
		for(int idx = 0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.startsWith("-") && a.length() > 1 && names.isEmpty()) {
				for(char c : a.substring(1).toCharArray()) {
					switch (c) {
					case 'n': disable = true; break;
					case 'a': all = true; break;
					case 'p': break;
					default:
						ctx.error("enable: -"+c+": invalid option");
						return 2;
					}
				}
			} else {
				names.add(a);
			}
		}
		if( names.isEmpty()) {
			for(String n : new TreeSet<>(Console.commands.keySet())) {
				if( n.startsWith("__")) {
					continue;
				}
				boolean off = ctx.console.disabledBuiltins.contains(n);
				if( all || off == disable ) {
					ctx.stdout.println("enable "+(off ? "-n " : "")+n);
				}
			}
			return 0;
		}
		int ret = 0;
		for(String n : names) {
			if( !Console.commands.containsKey(n)) {
				ctx.error("enable: "+n+": not a shell builtin");
				ret = 1;
			} else if( disable ) {
				ctx.console.disabledBuiltins.add(n);
			} else {
				ctx.console.disabledBuiltins.remove(n);
			}
		}
		return ret;
	}
}
