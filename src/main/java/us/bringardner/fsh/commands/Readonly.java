package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Readonly extends ShellCommand{
	static String name = "readonly";
	static String help = "readonly [-p] [name[=value] ...]\n"
			+ "	Make each name readonly, after setting it to value if one is given. With no names,\n"
			+ "	or -p, list the readonly variables. Exit status: 0, or 1 if a name is already\n"
			+ "	readonly and has a value."
			;

	public Readonly() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		boolean any = false;
		// readonly -f name: a function that may not be redefined or unset
		boolean functions = false;
		for(int idx = 0; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( text.equals("-p") || text.equals("--")) {
				continue;
			}
			if( text.equals("-f")) {
				functions = true;
				continue;
			}
			if( functions ) {
				any = true;
				if( !ctx.console.getFunctions().containsKey(text)) {
					ctx.error("readonly: "+text+": not a function");
					ret = 1;
				} else {
					ctx.console.setReadonlyFunction(text);
				}
				continue;
			}
			any = true;
			int eq = text.indexOf('=');
			String var = eq < 0 ? text : text.substring(0, eq);
			if( eq >= 0 ) {
				if( ctx.console.isReadonly(var)) {
					ctx.error("readonly: "+var+": readonly variable");
					ret = 1;
					continue;
				}
				ctx.setVariable(var, text.substring(eq+1));
			}
			ctx.console.setReadonly(var);
		}
		if( !any && functions ) {
			// readonly -f: the readonly functions, as bash prints them
			for(String n : new java.util.TreeSet<>(ctx.console.getFunctions().keySet())) {
				if( ctx.console.isReadonlyFunction(n)) {
					ctx.stdout.println(ctx.console.getFunctions().get(n).declaration());
					ctx.stdout.println("declare -fr "+n);
				}
			}
			return ret;
		}
		if( !any ) {
			for(String var : ctx.console.getVariables().keySet()) {
				if( ctx.console.isReadonly(var)) {
					ctx.stdout.println("declare -r "+var+"=\""+ctx.getVariable(var)+"\"");
				}
			}
		}
		return ret;
	}
}
