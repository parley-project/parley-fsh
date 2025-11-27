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



	static final long MilliSecond = 1;
	static final long Second = 1000*MilliSecond;
	static final long Minute = Second*60;
	static final long Hour = Minute*60;
	static final long Day = Hour*24;

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean debug = false;
		int ret = 0;
		if(args.length==0) {
			ctx.stdout.println("Unit can be 's' (seconds, the default), m (minutes), h (hours), d (days) or M (millisecond).");
			ret = 1;
		} else {
			long timeToSleep = 0;
			Integer ival = null;
			Long multiplyer=null;

			/**
			 * an argument in the form of 1s is parsed as two args. a number and a path segment
			 */
			
			for(Argument a :args) {
				String val = (""+a.getValue(ctx)).trim();
				if(val.isEmpty()) {
					continue;
				}
				if( Character.isDigit(val.charAt(0))) {
					ival = Integer.parseInt(val);
				} else if( val.equals("s")) {
					multiplyer = Second;
				} else if( val.equals("m")) {
					multiplyer = Minute;
				} else if( val.equals("h")) {
					multiplyer = Hour;
				} else if( val.equals("d")) {
					multiplyer = Day;
				} else if( val.equals("M")) {
					multiplyer = MilliSecond;
				} else if( val.equals("-db")) {
					debug = true;											
				} 
			}
			
			if( ival == null ) {
				ctx.stderr.println("No valid tiem ");
				return 1;
			}
			if( multiplyer==null) {
				multiplyer = Second;
			}
			timeToSleep = ival.longValue() * multiplyer;
			if( debug ) {
				//System.out.println("ival= "+ival+" multiplyer="+multiplyer+" timeToSleep="+timeToSleep);
				ctx.stdout.println("ival= "+ival+" multiplyer="+multiplyer+" timeToSleep="+timeToSleep);
			}
			while( timeToSleep > 0 ) {
				if( ctx.getException()!=null) {
					throw new IOException( ctx.getException());
				}
				if(ctx.isPaused()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
				} else {
					long start = System.currentTimeMillis();
					try {
						Thread.sleep(timeToSleep);
					} catch (InterruptedException e) {
						// reset flag interrupted 
						Thread.currentThread().isInterrupted();
						if( debug) {
							//System.out.println("Interupted sleep time="+timeToSleep);
						}
						// give time for exceptions to be set
						try {
							Thread.sleep(10);
						} catch (InterruptedException e1) {
						}
					}
					long end = System.currentTimeMillis();
					timeToSleep -= (end-start);
				}
			}			
		}


		return ret;
	}

}
