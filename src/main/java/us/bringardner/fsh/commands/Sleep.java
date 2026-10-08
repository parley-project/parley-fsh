package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

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
			boolean any = false;
			// the last number and unit (for -db)
			String ival = null;
			long multiplyer = Second;
			for(Argument a :args) {
				String val = (""+a.getValue(ctx)).trim();
				if(val.isEmpty()) {
					continue;
				}
				if( val.equals("-db")) {
					debug = true;
					continue;
				}
				// a number, maybe with a fraction (0.5), and an optional unit; several add up
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+\\.?\\d*|\\.\\d+)([smhdM]?)").matcher(val);
				if( !m.matches()) {
					ctx.stderr.println("sleep: invalid time interval '"+val+"'");
					return 1;
				}
				ival = m.group(1);
				multiplyer = switch (m.group(2)) {
				case "m" -> Minute;
				case "h" -> Hour;
				case "d" -> Day;
				case "M" -> MilliSecond;
				default -> Second;
				};
				timeToSleep += Math.round(Double.parseDouble(m.group(1)) * multiplyer);
				any = true;
			}
			
			if( !any ) {
				ctx.stderr.println("sleep: missing operand");
				return 1;
			}
			if( debug ) {
				//System.out.println("ival= "+ival+" multiplyer="+multiplyer+" timeToSleep="+timeToSleep);
				ctx.stdout.println("ival= "+ival+" multiplyer="+multiplyer+" timeToSleep="+timeToSleep);
			}
			while( timeToSleep > 0 ) {
				if( ctx.getException()!=null) {
					throw  ctx.getException();
				}
				if(ctx.isPaused()) {
					// time spent suspended doesn't count
					ctx.waitWhilePaused();
				} else {
					long start = System.currentTimeMillis();
					ctx.sleep(timeToSleep);
					long end = System.currentTimeMillis();
					timeToSleep -= (end-start);
				}
			}			
		}


		return ret;
	}

}
