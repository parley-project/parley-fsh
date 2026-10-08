package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

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
		boolean options = true;
		for(Argument arg : args) {
			String text = ""+arg.getValue(ctx);
			if( options && text.equals("--")) {
				options = false;
			} else if( options && text.startsWith("-") && text.length() > 1 ) {
				for(char c : text.substring(1).toCharArray()) {
					if( c != 'p' ) {
						ctx.error("alias: -"+c+": invalid option");
						ctx.stderr.println("alias: usage: alias [-p] [name[=value] ... ]");
						return 2;
					}
				}
				for(String name : new java.util.TreeSet<>(ctx.console.getAliases().keySet())) {
					printAlias(ctx,name, ctx.console.getAlias(name));
				}
			} else {
				options = false;
				int eq = text.indexOf('=');
				if( eq > 0 ) {
					// name=value
					String name = text.substring(0, eq);
					if( name.chars().anyMatch(c -> " \t\n|&;()<>/$`=\\'\"".indexOf(c) >= 0)) {
						// as bash's legal_alias_name
						ctx.error("alias: `"+name+"': invalid alias name");
						ret = 1;
						continue;
					}
					ctx.console.setAlias(name, text.substring(eq+1));
				} else {
					Object val = ctx.console.getAlias(text);
					if( val == null ) {
						ctx.error("alias: "+text+": not found");
						ret = 1;
					} else {
						printAlias(ctx,text,val);
					}
				}
			}
		}
		
		return ret;
	}

	private void printAlias(ShellContext ctx, String name, Object val) {
		ctx.stdout.println("alias "+name+"='"+String.valueOf(val).replace("'", "'\\''")+"'");		
	}

}
