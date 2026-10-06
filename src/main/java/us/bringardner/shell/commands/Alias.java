package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Alias extends ShellCommand{
	static String name = "alias";
	static String help = "alias [-p] [name[=value] …]\n"
			+"Without arguments or with the -p option, alias prints the list of aliases on the standard output in a form that allows them to be reused as input.\n"
			+ "If arguments are supplied, an alias is defined for each name whose value is given.\n"
			+ "If no value is given, the name and value of the alias is printed. \n"
			+ "\tAliases allow a string to be substituted for a word when it is used as the ]\n\t"
			+ "first word of a simple command. The shell maintains a list of aliases that may be set and unset with \n\t"
			+ "the alias and unalias builtin commands."
			;
	
	public Alias() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		for(Argument arg : args) {
			String text = ""+arg.getValue(ctx);
			if( text.startsWith("-")) {
				if(text.equals("-p")) {
					for(String name : ctx.console.getAliases().keySet()) {
						printAlias(ctx,name, ctx.console.getAlias(name));
					}
				} else {
					// invalid??
					throw new IOException("Don't know what to do for '"+text+"'");
				}
			} else {
				int eq = text.indexOf('=');
				if( eq > 0 ) {
					// name=value
					ctx.console.setAlias(text.substring(0, eq), text.substring(eq+1));
				} else {
					Object val = ctx.console.getAlias(text);
					if( val == null ) {
						//alias: llll: not found
						ctx.stdout.println("alias: "+text+": not found");
					} else {
						printAlias(ctx,text,val);
					}
				}
			}
		}
		
		return ret;
	}

	private void printAlias(ShellContext ctx, String name, Object val) {
		ctx.stdout.println("alias "+name+"='"+val+"'");		
	}

}
