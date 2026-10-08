package us.bringardner.fsh.commands;


import java.io.IOException;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

public class Set extends ShellCommand{
	/*
	 * re write to conform to https://pubs.opengroup.org/onlinepubs/9699919799/utilities/V3_chap02.html#tag_18_25
	 * Excluding the -o option
	 */
	static String name = "set";
	static String help = "If no options or arguments are supplied, set displays the names and values of all shell variables and functions, sorted according to the current locale, in a format that may be reused as input for setting or resetting the currently-set variables. Read-only variables cannot be reset.\n"
			+ "In POSIX mode, only shell variables are listed.\n"
			+ "When options are supplied, they set(-) or unset(+) shell attributes."
			+ ""
			+ "set [-abCefhmnuvx] [argument...]\n"
			+ "set [+abCefhmnuvx] [argument...]\n"
			+ "set -- [argument...]\n"
			+ "set -o\n"
			+ "set +o\n";

	public Set() {
		super(name, help);
	}

	/*
	-- If no arguments follow this option, then the positional parameters are unset. 
		Otherwise, the positional parameters are set to the arguments, 
		even if some of them begin with a ‘-’.

	- Signal the end of options, cause all remaining arguments to be assigned to the positional parameters. 
		The -x and -v options are turned off. 
		If there are no arguments, the positional parameters remain unchanged.

		, PrintLinesAsRead ("v")
		, PrintCommandTrace ("x")


	 */
	@Override
	public int process(ShellContext ctx) throws IOException {

		int ret = 0;
		if( args == null || args.length == 0) {
			throw new IOException("Args are not availible");
		}
		boolean isMain = false;
		int idx = 0;
		String val= null;
		for (; idx < args.length; idx++) {
			Argument a = args[idx];
			val = ""+a.getValue(ctx);
			if( val.equals("-main")) {
				isMain = true;
			} else if( val.equals("-")) {
				ctx.console.setOption(Option.PrintLinesAsRead, false);
				ctx.console.setOption(Option.PrintCommandTrace, false);
				// the words after - are the positional parameters
				idx++;
				break;
			} else if(val.equals("--")) {
				// the words after -- are the positional parameters, even if they start with -
				idx++;
				break;
			} else if(val.startsWith("-") || val.startsWith("+")) {
				boolean set = val.startsWith("-");
				String val1 = val.substring(1);
				Option o1 = Option.find(val1);
				if( o1 == Option.Option ) {
					// -o name is read below, one letter at a time (this turned on the option named o)
					o1 = Option.Unsupported;
				}
				if( o1 != Option.Unsupported) {
					ctx.console.setOption(o1, set);	
				} else {
					for(char c : val.substring(1).toCharArray()) {
						Option o = Option.find(""+c);

						if( o == Option.Unsupported) {
							// as bash says it
							String tmp = "set: "+(set?"-":"+")+c+": invalid option";
							ctx.error(tmp);
							return 1;
						}
						if( o == Option.Option) {
							if(idx < args.length-1) {
								Argument a2 = args[++idx];
								val = ""+a2.getValue(ctx);
								Option o2 = Option.find(val);
								if( o2 == null || o2 == Option.Unsupported || o2 == Option.Option ) {
									ctx.error("set: "+val+": invalid option name");
									return 2;
								}
								ctx.console.setOption(o2, set);							
							} else {
								for(Option oo : Console.Option.values()) {
									if(oo != Option.Option) {
										if( set ) {
											ctx.stdout.printf("%-15s\t%s\n",oo.longName, (ctx.console.isOptionEnabled(oo)?"on":"off"));
										} else {
											ctx.stdout.printf("set %so %s\n",ctx.console.isOptionEnabled(oo)?"-":"+",oo.longName);
										}
									}
								}
							}
						} else {
							ctx.console.setOption(o, set);
						}
					}
				}
			} else {
				break;
			}
		}
		if( idx < args.length) {
			// set them
			FshList pp = new FshList();

			for (; idx < args.length; idx++) {
				Object val2 = args[idx].getValue(ctx);
				pp.add(val2);
			}			
			setPositionalParameters(ctx, isMain, pp);
		} else if( val.equals("--")) {
			setPositionalParameters(ctx, isMain, new FshList());
		}

		return ret;
	}

	private static void setPositionalParameters(ShellContext ctx, boolean isMain, FshList pp) {
		if( isMain ) {
			ctx.console.setPositionalParameters(true, pp);
		} else {
			// in a function this sets the function's parameters
			ctx.setPositionalParameterValues(pp);
		}
	}
}
