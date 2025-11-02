package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Sleep extends ShellCommand{
	static String name = "sleep";
	static String help = "  sleep – suspend execution for an interval of time\n"
			+ "\n"
			+ "SYNOPSIS\n"
			+ "     sleep number[unit] [...]\n"
			+ "\n"
			+ "DESCRIPTION\n"
			+ "  The sleep command suspends execution for a minimum of number seconds (the default, or unit s), "
			+ "		minutes (unit m), hours (unit h), or days (unit d).  If multiple intervals are given, they are added together.  If the final sum is zero or negative, sleep exits immediately.\n"
			+ "\n"
			+ " If the sleep command receives a signal, it takes the standard action.  "
			+ "		When the SIGINFO signal is received, the estimate of the amount of seconds left to sleep is printed on "
			+ "		the standard output.\n"
			+ ".";

	public Sleep() {
		super(name, help);
	}
	public static boolean debug = false;
	

	static final long S = 1000;
	static final long M = S*60;
	static final long H = M*60;
	static final long D = H*24;

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		if(args.length==0) {
			ctx.stdout.println("Unit can be 's' (seconds, the default), m (minutes), h (hours), or d (days).");
			ret = 1;
		} else {
			if( debug ) System.out.println("args len="+args.length);
			int amt = 0;
			long multiplyer = S;

			for(Argument a :args) {
				String val = ""+a.getValue(ctx);
				if( val.endsWith("s")) {
					val = val.substring(0,val.length()-1);
					multiplyer = S;
				} else if( val.endsWith("m")) {
					val = val.substring(0,val.length()-1);
					multiplyer = M;
				} else if( val.endsWith("h")) {
					val = val.substring(0,val.length()-1);
					multiplyer = H;
				} else if( val.endsWith("d")) {
					val = val.substring(0,val.length()-1);
					multiplyer = D;
				}
				long tmp = Integer.parseInt(val)*multiplyer;
				amt += tmp;
			}
			if( debug ) System.out.println("start amt="+amt);
			while( amt > 0 ) {
				if( ctx.getException()!=null) {
					return ret;
				}
				if(ctx.isPaused()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
				} else {
					try {
						int tmp = amt>10?10:amt;
						Thread.sleep(tmp);
						amt -= tmp;
						if( debug ) System.out.println("sleep amt="+amt);
					} catch (InterruptedException e) {
					}
				}
			}
			if( debug ) System.out.println("end amt="+amt);
		}


		return ret;
	}

}
