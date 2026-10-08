package us.bringardner.fsh.commands;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import us.bringardner.fsh.Argument;
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
		List<HistoryEntry> list = ctx.console.history;
		List<String> words = new ArrayList<>();
		for(Argument a : args) {
			words.add(String.valueOf(a.getValue(ctx)));
		}
		if( words.isEmpty()) {
			print(ctx, list.size());
			return 0;
		}
		String option = words.get(0);
		List<String> rest = words.subList(1, words.size());
		switch (option) {
		case "-c":
			list.clear();
			return 0;
		case "-d":
			return delete(ctx, rest.isEmpty() ? "" : rest.get(0));
		case "-a":
		case "-w":
			if( rest.isEmpty()) {
				ctx.console.saveHistory();
			} else {
				ctx.console.saveHistory(rest.get(0));
			}
			return 0;
		case "-r":
		case "-n":
			if( rest.isEmpty()) {
				ctx.console.readHistory();
			} else {
				ctx.console.readHistory(rest.get(0));
			}
			return 0;
		case "-s":
			// the arguments as one entry, in place of this history -s command
			if( !list.isEmpty() && list.get(list.size()-1).command.startsWith("history")) {
				list.remove(list.size()-1);
			}
			ctx.console.addHistory(String.join(" ", rest));
			return 0;
		case "-p": {
			// each argument history-expanded and printed, not run or kept (nor this command)
			if( !list.isEmpty() && list.get(list.size()-1).command.startsWith("history")) {
				list.remove(list.size()-1);
			}
			List<String> commands = new ArrayList<>();
			for(HistoryEntry e : list) {
				commands.add(e.command);
			}
			int ret = 0;
			for(String w : rest) {
				us.bringardner.fsh.HistoryExpansion.Result r = us.bringardner.fsh.HistoryExpansion.expand(w, commands);
				if( r.error != null ) {
					// (in a typed line it says why: !x: event not found)
					ctx.error("history: "+w+": history expansion failed");
					ret = 1;
				} else {
					ctx.stdout.println(r.line);
				}
			}
			return ret;
		}
		default:
			try {
				print(ctx, Integer.parseInt(option));
				return 0;
			} catch (NumberFormatException e) {
				ctx.error("history: "+option+": numeric argument required");
				return 1;
			}
		}
	}

	/** -d n (1 is the first; negative counts from the end) or -d start-end */
	private int delete(ShellContext ctx, String arg) {
		List<HistoryEntry> list = ctx.console.history;
		try {
			int dash = arg.indexOf('-', 1);
			int start = position(dash > 0 ? arg.substring(0, dash) : arg, list.size());
			int end = dash > 0 ? position(arg.substring(dash+1), list.size()) : start;
			if( start < 1 || end > list.size() || start > end ) {
				ctx.error("history: "+arg+": history position out of range");
				return 1;
			}
			for (int i = end; i >= start; i--) {
				list.remove(i-1);
			}
			return 0;
		} catch (NumberFormatException e) {
			ctx.error("history: "+arg+": numeric argument required");
			return 1;
		}
	}

	private static int position(String text, int size) {
		int n = Integer.parseInt(text);
		return n < 0 ? size+n+1 : n;
	}

	/** the last lines entries, as bash shows them: "%5d  command" (HISTTIMEFORMAT before it) */
	private void print(ShellContext ctx, int lines) {
		SimpleDateFormat fmt = null;
		Object obj = ctx.getVariable(Console.VARIABLE_HISTTIMEFORMAT);
		if( obj != null && !obj.toString().isEmpty()) {
			try {
				fmt = new SimpleDateFormat(ctx.console.strftimeToJava(obj.toString()));
			} catch (RuntimeException e) {
				fmt = null;
			}
		}
		List<HistoryEntry> list = ctx.console.history;
		int sz = list.size();
		int start = Math.max(0, sz-Math.max(0, lines));
		for (int idx = start; idx < sz; idx++) {
			HistoryEntry e = list.get(idx);
			String tm = fmt == null ? "" : fmt.format(new Date(e.time));
			ctx.stdout.printf("%5d  %s%s%n", idx+1, tm, e.command);
		}
	}
}
