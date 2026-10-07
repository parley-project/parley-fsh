package us.bringardner.fsh.commands;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Console.HistoryEntry;

public class History extends ShellCommand{
	static String name = "history";
	static String help = "history [n]\n"
			+ "history -c\n"
			+ "history -d offset\n"
			+ "history -d start-end\n"
			+ "history [-anrw] [filename]\n"
			+ "history -ps arg\n"
			+ "With no options, display the history list with line numbers. Lines prefixed with a ‘*’ have been modified. \n"
			+ "An argument of n lists only the last n lines. If the shell variable HISTTIMEFORMAT is set and not null,\n"
			+ "it is used as a format string for strftime to display the time stamp associated with each displayed history entry.\n"
			+ "No intervening blank is printed between the formatted time stamp and the history line."
			;

	static int cnt = 0;
	public History() {
		super(name, help);
	}

	enum HistoryOption {c,d,a,n,r,w,p,s};
	
	@Override
	public int process(ShellContext ctx) throws IOException {		
		int ret = 0;
		if( args.length==0) {
			print(ctx,ctx.console.history.size());
		} else {
			String option = ""+args[0].getValue(ctx);
			StringBuilder buf = new StringBuilder();
			for (int idx = 1; idx < args.length; idx++) {
				buf.append(args[idx].getValue(ctx).toString());
			}
			String cmdArg = buf.toString();
			
			if( option.equals("-c")) {
				ctx.console.history.clear();
			} else if( option.equals("-d")) {
				if( !cmdArg.isEmpty()) {
					int start = -2;
					int end = -1;
					int idx = cmdArg.indexOf('-', 1);
					if( idx > 0 ) {
						String tmp = cmdArg.substring(0,idx);
						start = Integer.parseInt(tmp);
						tmp = cmdArg.substring(idx+1);
						end = Integer.parseInt(tmp);
					} else {
						start = Integer.parseInt(cmdArg);
						end = start;
					}
					if( start< 0 ) {
						start = ctx.console.history.size()+start-1;						
					}
					if( end< 0 ) {
						end = ctx.console.history.size()+end-1;						
					}
					if(end >= ctx.console.history.size()) {
						end = ctx.console.history.size();
					}
					
					ctx.stdout.printf("%s -> %d %d\n",cmdArg,start,end);
					for(idx=end; idx >= start; idx--) {
						ctx.console.history.remove(idx);
					}
				} 
				
			} else if( option.equals("-a") || option.equals("-n") || option.equals("-w")) {
				if(!cmdArg.isEmpty()) {
					ctx.console.saveHistory(cmdArg);
				} else {
					ctx.console.saveHistory();
				}
			} else if( option.equals("-r")) {
				if(!cmdArg.isEmpty()) {
					ctx.console.readHistory(cmdArg);
				} else {
					ctx.console.readHistory();
				}
			} else if( option.equals("-p")) {
				ctx.stdout.println("find cmd");
			} else if( option.equals("-s")) {
				ctx.console.addHistory(cmdArg);
			} else {
				int lines = Integer.parseInt(option);
				print(ctx, lines);
			}
		}
			

		return ret;
	}

	private void print(ShellContext ctx, int lines) {
		SimpleDateFormat fmt = null;
		Object obj = ctx.getVariable(Console.VARIABLE_HISTTIMEFORMAT);
		if( obj !=null ) {
			fmt = new SimpleDateFormat(obj.toString());			
		}
		
		List<HistoryEntry> tmp = ctx.console.history;
		int sz = tmp.size();
		if( lines > sz) {
			lines = sz;
		}
		int start = ctx.console.history.size()-lines;
		if(start < 0 ) {
			start = 0;
		}
		//1072 tm history -d
		
		for(int idx=start; idx < sz; idx++) {
			HistoryEntry e = tmp.get(idx);
			String tm = "";
			if(fmt != null ) {
				tm = fmt.format(new Date(e.time));
			} 
			ctx.stdout.printf("%4d %s %s%s\n", idx,e.saved?"":"*",tm,e.command);			
		}

	}

}
