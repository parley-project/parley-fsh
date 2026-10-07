package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

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
		for(int idx = 0; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( text.equals("-p") || text.equals("--")) {
				continue;
			}
			any = true;
			int eq = text.indexOf('=');
			String var = eq < 0 ? text : text.substring(0, eq);
			if( eq >= 0 ) {
				if( ctx.console.isReadonly(var)) {
					ctx.stderr.println("readonly: "+var+": readonly variable");
					ret = 1;
					continue;
				}
				ctx.setVariable(var, text.substring(eq+1));
			}
			ctx.console.setReadonly(var);
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
