package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Expression;

public class Let extends ShellCommand{
	static String name = "let";
	static String help = "let arg [arg ...]\n"
			+ "	Each arg is an arithmetic expression to be evaluated, as in $((arg)); name=expression\n"
			+ "	assigns the value to name. Quote an arg that has spaces: let \"x = 2 * 3\".\n"
			+ "	The return status is 1 if the last arg evaluates to 0, otherwise 0."
			;

	/** name=expression, but not name==value */
	private static final Pattern ASSIGNMENT = Pattern.compile("\\s*([a-zA-Z_][a-zA-Z_0-9]*)\\s*=(?!=)(.*)", Pattern.DOTALL);

	public Let() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( args.length == 0 ) {
			ctx.stderr.println("let: expression expected");
			return 1;
		}
		Object last = null;
		for(Argument arg : args) {
			String text = ""+arg.getValue(ctx);
			try {
				Matcher m = ASSIGNMENT.matcher(text);
				if( m.matches()) {
					last = Expression.evaluate(m.group(2), ctx);
					ctx.setVariable(m.group(1), last);
				} else {
					last = Expression.evaluate(text, ctx);
				}
			} catch (RuntimeException e) {
				ctx.stderr.println("let: "+e.getMessage());
				return 1;
			}
		}
		return ((Number)last).doubleValue() == 0 ? 1 : 0;
	}
}
