package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

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
		for(Argument arg : args) {
			String text = ""+arg.getValue(ctx);
			if( text.startsWith("-")) {
				if(text.equals("-a")) {
					ctx.console.clearAliases();
				} else {
					// invalid??
					throw new IOException("Don't know what to do for '"+text+"'");
				}
			} else  {
				ctx.console.removeAlias(text);
			}  
		}
		
		return ret;
	}

	
}
