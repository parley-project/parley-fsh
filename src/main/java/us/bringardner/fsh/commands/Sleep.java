package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

public class Sleep extends ShellCommand{
	static String name = "sleep";
	static String help = "sleep NUMBER[UNIT] ...\n"
			+ "\tWait for the sum of the intervals. NUMBER may have a fraction (0.5); UNIT is s for seconds (the\n"
			+ "\tdefault), m minutes, h hours, d days or M milliseconds.\n"
			+ "\tExample:\n"
			+ "\tsleep 1.5m";

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
				switch (m.group(2)) {
				case "m": multiplyer = Minute; break;
				case "h": multiplyer = Hour; break;
				case "d": multiplyer = Day; break;
				case "M": multiplyer = MilliSecond; break;
				default: multiplyer = Second; break;
				}
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
			if( timeToSleep > 0 ) {
				// in a subshell, bash's sleep is a process of the subshell's process: so is this
				// (ps lists it, and killing it ends the sleep)
				ctx.standIn();
			}
			while( timeToSleep > 0 ) {
				if( ctx.getException()!=null) {
					throw  ctx.getException();
				}
				if( ctx.hasPendingSignal()) {
					// a trapped signal: as an external sleep it would get it (and end), then the trap runs
					return 130;
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
