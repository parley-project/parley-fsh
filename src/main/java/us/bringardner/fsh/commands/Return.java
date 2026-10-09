package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.signal.ReturnException;

public class Return extends ShellCommand{
	static String name = "return";
	static String help = "return [n]\n\tEnd a function or a sourced file, with status n (the last command's status\n\twithout n).\n"
			;
	
	public Return() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// (the number is looked at first, as bash's get_exitstat)
		int first = args.length > 0 && "--".equals(""+args[0].getValue(ctx)) ? 1 : 0;
		// with no number, the status of the last command (as in bash)
		long ret = ctx.console.getLastExitCode();
		if( args.length == first && ctx.trapStatus != null && ctx.functionDepth() == ctx.trapFunctionDepth ) {
			// return in a trap's action: the status from before the trap (bash's)
			ret = ctx.trapStatus;
		}
		if( args.length > first ) {
			String n = (""+args[first].getValue(ctx)).trim();
			if( !n.matches("[-+]?[0-9]{1,18}")) {
				// (said, and it returns 2)
				ctx.error("return: "+n+": numeric argument required");
				ctx.specialUsageError();
				ret = 2;
			} else {
				if( args.length > first+1 ) {
					throw ctx.tooManyArguments("return");
				}
				ret = Long.parseLong(n.startsWith("+") ? n.substring(1) : n);
			}
		}
		if( !ctx.isInFunction() && ctx.sourceDepth == 0 ) {
			ctx.error("return: can only `return' from a function or sourced script");
			ctx.specialUsageError();
			return 2;
		}
		
		// a status is 0 to 255: return 300 is 44, return -1 is 255
		throw new ReturnException(ctx,this,(int) (((ret % 256)+256) % 256));
	//return ret;
	}

}
