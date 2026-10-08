package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.signal.ReturnException;

public class Return extends ShellCommand{
	static String name = "return";
	static String help = "set the exit code \n"
			;
	
	public Return() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( !ctx.isInFunction() && ctx.sourceDepth == 0 ) {
			ctx.error("return: can only `return' from a function or sourced script");
			return 2;
		}
		// with no number, the status of the last command (as in bash)
		int ret = ctx.console.getLastExitCode();
		if( args.length == 0 && ctx.trapStatus != null && ctx.functionDepth() == ctx.trapFunctionDepth ) {
			// return in a trap's action: the status from before the trap (bash's)
			ret = ctx.trapStatus;
		}
		if( args.length>0) {
			try {
				ret = Integer.parseInt(args[0].getValue(ctx).toString());
			} catch (Exception e) {
			}
		}
		
		// a status is 0 to 255: return 300 is 44, return -1 is 255
		throw new ReturnException(ctx,this,((ret % 256)+256) % 256);
	//return ret;
	}

}
