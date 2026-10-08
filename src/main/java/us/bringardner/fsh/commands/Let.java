package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.expand.Arithmetic;

public class Let extends ShellCommand{
	static String name = "let";
	static String help = "let arg [arg ...]\n"
			+ "	Each arg is an arithmetic expression to be evaluated, as in $((arg)); name=expression\n"
			+ "	assigns the value to name. Quote an arg that has spaces: let \"x = 2 * 3\".\n"
			+ "	The return status is 1 if the last arg evaluates to 0, otherwise 0."
			;

	public Let() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( args.length == 0 ) {
			ctx.stderr.println("let: expression expected");
			return 1;
		}
		Number last = 0L;
		for(Argument arg : args) {
			// each argument is one expression (already expanded): let x=2+3 "y = x * 2"
			try {
				last = Arithmetic.evaluate(""+arg.getValue(ctx), ctx);
			} catch (Arithmetic.ArithmeticError e) {
				ctx.stderr.println("let: "+e.getMessage());
				return 1;
			}
		}
		return Arithmetic.isTrue(last) ? 0 : 1;
	}
}
