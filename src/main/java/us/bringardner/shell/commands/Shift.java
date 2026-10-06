package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

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
		int ret = 0;
		int n =1;
		if( args.length>0) {
			n = Integer.parseInt(""+args[0].getValue(ctx));
		}
		List<Object> tmp = ctx.getPositionalParameterValues();
		if( n > tmp.size()) {
			// as in bash, nothing is shifted and the status is 1
			return 1;
		}
		if( n > 0 ) {
			ctx.setPositionalParameterValues(new ArrayList<>(tmp.subList(n, tmp.size())));
		}
		return ret;
	}

	

}
