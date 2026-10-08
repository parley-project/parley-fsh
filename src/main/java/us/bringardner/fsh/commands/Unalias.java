package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

public class Unalias extends ShellCommand{
	static String name = "unalias";
	static String help = "unalias [-a] [name …]\n"
			+"Remove one or more aliases or all with -a option."
			;
	
	static int cnt = 0;
	public Unalias() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		if( args.length == 0 ) {
			ctx.stderr.println("unalias: usage: unalias [-a] name [name ...]");
			return 2;
		}
		boolean options = true;
		for(Argument arg : args) {
			String text = ""+arg.getValue(ctx);
			if( options && text.equals("--")) {
				options = false;
			} else if( options && text.startsWith("-") && text.length() > 1 ) {
				for(char c : text.substring(1).toCharArray()) {
					if( c != 'a' ) {
						ctx.error("unalias: -"+c+": invalid option");
						ctx.stderr.println("unalias: usage: unalias [-a] name [name ...]");
						return 2;
					}
				}
				ctx.console.clearAliases();
				return 0;
			} else if( ctx.console.getAlias(text) == null ) {
				options = false;
				ctx.error("unalias: "+text+": not found");
				ret = 1;
			} else {
				options = false;
				ctx.console.removeAlias(text);
			}
		}
		
		return ret;
	}

	
}
