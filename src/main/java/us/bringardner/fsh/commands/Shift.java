package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Shift extends ShellCommand{
	static String name = "shift";
	static String help = "shift [n] \n"
			+" The positional parameters shall be shifted. Positional parameter 1\n"
			+ "       shall be assigned the value of parameter (1+n), parameter 2 shall\n"
			+ "       be assigned the value of parameter (2+n), and so on. The\n"
			+ "       parameters represented by the numbers \"$#\" down to \"$#-n+1\" shall\n"
			+ "       be unset, and the parameter '#' is updated to reflect the new\n"
			+ "       number of positional parameters.\n"
			+ "\n"
			+ "       The value n shall be an unsigned decimal integer less than or\n"
			+ "       equal to the value of the special parameter '#'.  If n is not\n"
			+ "       given, it shall be assumed to be 1. If n is 0, the positional and\n"
			+ "       special parameters are not changed."
			;
	
	public Shift() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int first = args.length > 0 && "--".equals(""+args[0].getValue(ctx)) ? 1 : 0;
		long n = 1;
		if( args.length > first ) {
			String text = (""+args[first].getValue(ctx)).trim();
			if( !text.matches("[-+]?[0-9]{1,18}")) {
				ctx.error("shift: "+text+": numeric argument required");
				ctx.specialUsageError();
				return 2;
			}
			if( args.length > first+1 ) {
				throw ctx.tooManyArguments("shift");
			}
			n = Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
		}
		List<Object> tmp = ctx.getPositionalParameterValues();
		if( n < 0 || n > tmp.size()) {
			// as in bash: nothing is shifted, the status is 1 (said for a negative count, or with
			// shopt -s shift_verbose)
			if( n < 0 || us.bringardner.fsh.Glob.option(ctx, "shift_verbose") || ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix)) {
				ctx.error("shift: "+n+": shift count out of range");
			}
			return 1;
		}
		if( n > 0 ) {
			ctx.setPositionalParameterValues(new ArrayList<>(tmp.subList((int) n, tmp.size())));
		}
		return 0;
	}

}
