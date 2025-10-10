package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import sun.misc.Signal;
import us.bringardner.shell.Console;
import us.bringardner.shell.Console.Job;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Kill extends ShellCommand{
	static String name = "kill";
	static String help = "kill [-s sigspec] [-n signum] [-sigspec] id [...]\n"
			+ "kill -l|-L [exit_status]\n\n"
			+ "Send a signal specified by sigspec or signum to the processes named by each id. "
			+ "Each id may be a job specification jobspec or process ID pid. sigspec is either a case-insensitive signal "
			+ "name such as SIGINT (with or without the SIG prefix) or a signal number; signum is a signal number. "
			+ "If sigspec and signum are not present, kill sends SIGTERM.\n"
			+ "\n"
			+ "The -l option lists the signal names."
			+ " If any arguments are supplied when -l is supplied, kill lists the names of the signals corresponding"
			+ " to the arguments, and the return status is zero."
			+ " exit_status is a number specifying a signal number or the exit status of a process terminated by a signal;"
			+ " if it is supplied, kill prints the name of the signal that caused the process to terminate."
			+ " kill assumes that process exit statuses are greater than 128; anything less than that is a signal number."
			+ " The -L option is equivalent to -l.\n"
			+ "\n"
			+ "The return status is zero if at least one signal was successfully sent,"
			+ " or non-zero if an error occurs or an invalid option is encountered."
			;

	public Kill() {
		super(name, help);
	}

	

	@Override
	public int process(ShellContext ctx) throws IOException {
		Map<Integer, String> signals = Trap.getLocalSignals();
		int ret = 0;
		Integer signum = null;
		List<Integer> ids = new ArrayList<>();
		boolean list = false;
		Integer exitStatus= null;

		// parse all the args
		for (int idx = 0; idx < args.length; idx++) {
			Argument a = args[idx];
			String val = ""+a.getValue(ctx);
			//[-s sigspec] [-n signum] [-sigspec] id [...]
			if( val.equals("-l") || val.equals("-L")) {
				list = true;
				if( idx < args.length-1) {
					String tmp = ""+args[++idx].getValue(ctx);
					exitStatus = parseSigNum(tmp);
				}
			} else if( val.equals("-s") || val.equals("-n")) {
				signum = parseSigNum(""+args[++idx].getValue(ctx));								
			} else if( val.startsWith("-")) {				
				signum = parseSigNum(val.substring(1));				
			} else {
				if( val.startsWith("%")) {
					val = val.substring(1);
				} 
				int id = Integer.parseInt(val);
				ids.add(id);


			}
		}

		if( list ) {

			if( exitStatus!=null) {
				String name = signals.get(exitStatus);
				ctx.stdout.println(""+name);
			} else {
				List<String> tmp = new ArrayList<>();
				for(Entry<Integer, String> e : signals.entrySet()) {
					tmp.add(e.getValue());					
				}
				ctx.stdout.println(toColumns(ctx, tmp).trim());
			}
		} else {
			if( signum == null ) {
				Signal signal = new Signal("TERM");
				signum = signal.getNumber();
			}
			for(Integer id : ids) {
				Job ct = null;
				int sz = ctx.console.jobs.size();
				if(id<= sz) {
					ct = ctx.console.jobs.get(id-1);
				} else {
					for(Job c : ctx.console.jobs) {
						if( c.pid == id) {
							ct = c;
							break;
						}
					}
				}
				
				if( ct == null ) {
					//  is this an external process???
					Process p = new ProcessBuilder("kill","-"+signum,""+id).start();
					try {
						p.waitFor(1000, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
					}
					if( !p.isAlive()) {
						int exitCode = p.exitValue();
						if( exitCode!=0) {
							return ret;
						}
					}
				} else {
					// this is my process
					Console.raiseSignal(signum);
					Thread.yield();
				}
			}
		}




		return ret;
	}

	private int parseSigNum(String val) {
		int ret = -1;		
		if(Character.isDigit(val.charAt(0))) {
			ret = Integer.parseInt(val);					
		} else {
			val = val.toUpperCase();
			if( val.startsWith("SIG") ) {
				val = val.substring(3);
			}			
			Signal s = new Signal(val);
			ret = s.getNumber();
		}

		return ret;
	}

}
