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

	static final String USAGE = "history: usage: history [-c] [-d offset] [n] or history -anrw [filename] or history -ps arg [arg...]";

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( ctx.subshellLevel == 0 ) {
			return process0(ctx);
		}
		// (in a subshell, what it does to the history is the subshell's: fc -s | cat, as in bash)
		Object[] saved = ctx.console.saveHistoryState();
		try {
			return process0(ctx);
		} finally {
			ctx.console.restoreHistoryState(saved);
		}
	}

	private int process0(ShellContext ctx) throws IOException {
		List<HistoryEntry> list = ctx.console.history;
		List<String> words = new ArrayList<>();
		for(Argument a : args) {
			words.add(String.valueOf(a.getValue(ctx)));
		}
		boolean clear = false, print = false, store = false;
		char fileOp = 0;
		String delete = null;
		int i = 0;
		for(; i < words.size(); i++) {
			String w = words.get(i);
			if( w.equals("--")) {
				i++;
				break;
			}
			if( !w.startsWith("-") || w.length() < 2 || w.substring(1).matches("[0-9]+")) {
				break;
			}
			for (int k = 1; k < w.length(); k++) {
				char c = w.charAt(k);
				switch (c) {
				case 'c': clear = true; break;
				case 'p': print = true; break;
				case 's': store = true; break;
				case 'a': case 'n': case 'r': case 'w':
					if( fileOp != 0 && fileOp != c ) {
						ctx.error("history: cannot use more than one of -anrw");
						return 1;
					}
					fileOp = c;
					break;
				case 'd':
					if( k+1 < w.length()) {
						delete = w.substring(k+1);
					} else if( i+1 < words.size()) {
						delete = words.get(++i);
					} else {
						ctx.error("history: -d: option requires an argument");
						ctx.stderr.println(USAGE);
						return 2;
					}
					k = w.length();
					break;
				default:
					ctx.error("history: -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
			}
		}
		List<String> rest = words.subList(i, words.size());
		if( clear ) {
			list.clear();
			ctx.console.historyLinesThisSession = 0;
			// (numbers start from 1 again)
			ctx.console.historyBase = 1;
			if( delete == null && fileOp == 0 && !print && !store ) {
				return 0;
			}
		}
		if( delete != null ) {
			return delete(ctx, delete);
		}
		if( print || store ) {
			// the arguments: expanded and printed (-p), or one entry in place of this command (-s)
			if( ctx.console.historyLastLineAdded ) {
				ctx.console.deleteLastHistory();
			}
			if( store ) {
				if( !rest.isEmpty()) {
					ctx.console.addHistory(String.join(" ", rest));
				}
				return 0;
			}
			List<String> commands = new ArrayList<>();
			for(HistoryEntry e : list) {
				commands.add(e.command);
			}
			int ret = 0;
			for(String w : rest) {
				us.bringardner.fsh.HistoryExpansion.Result r = us.bringardner.fsh.HistoryExpansion.expand(w, commands, ctx.console.historyBase);
				if( r.error != null ) {
					// (bash says only that it failed)
					ctx.error("history: "+w+": history expansion failed");
					ret = 1;
				} else {
					ctx.stdout.println(r.line);
				}
			}
			return ret;
		}
		if( fileOp != 0 ) {
			String file = !rest.isEmpty() ? rest.get(0) : ctx.console.historyFile();
			if( file == null ) {
				return 0;
			}
			switch (fileOp) {
			case 'a': {
				// the entries of this session, after the file's
				int n = ctx.console.historyLinesThisSession;
				if( n > 0 && !ctx.console.writeHistoryFile(file, n, true)) {
					return 1;
				}
				ctx.console.historyLinesInFile += n;
				ctx.console.historyLinesThisSession = 0;
				return 0;
			}
			case 'w':
				if( !ctx.console.writeHistoryFile(file, -1, false)) {
					return 1;
				}
				ctx.console.historyLinesThisSession = 0;
				return 0;
			case 'r':
				ctx.console.historyLinesInFile = ctx.console.readHistoryFile(file, 0);
				return 0;
			default:
				// -n: the lines of the file not read yet
				ctx.console.historyLinesInFile = ctx.console.readHistoryFile(file, ctx.console.historyLinesInFile);
				return 0;
			}
		}
		if( rest.isEmpty()) {
			print(ctx, list.size());
			return 0;
		}
		int count;
		try {
			count = Integer.parseInt(rest.get(0));
		} catch (NumberFormatException e) {
			ctx.error("history: "+rest.get(0)+": numeric argument required");
			return 2;
		}
		if( rest.size() > 1 ) {
			ctx.error("history: too many arguments");
			return 2;
		}
		print(ctx, count);
		return 0;
	}

	/** -d offset (a history number, or negative from the end) or -d start-end, as bash's */
	private int delete(ShellContext ctx, String arg) {
		List<HistoryEntry> list = ctx.console.history;
		int base = ctx.console.historyBase;
		int length = list.size();
		int dash = arg.indexOf('-', arg.startsWith("-") ? 1 : 0);
		if( dash > 0 ) {
			String first = arg.substring(0, dash);
			String second = arg.substring(dash+1);
			Long s = number(first), e = number(second);
			if( s == null || e == null ) {
				ctx.error("history: "+arg+": history position out of range");
				return 1;
			}
			long start = first.startsWith("-") && s < 0 ? s+length : s > 0 ? s-base : s;
			if( start < 0 || start >= length ) {
				ctx.error("history: "+first+": history position out of range");
				return 1;
			}
			long end = second.startsWith("-") && e < 0 ? e+length : e > 0 ? e-base : e;
			if( end < 0 || end >= length ) {
				ctx.error("history: "+second+": history position out of range");
				return 1;
			}
			for (long i = end; i >= start; i--) {
				list.remove((int) i);
			}
			return 0;
		}
		Long n = number(arg);
		if( n == null ) {
			ctx.error("history: "+arg+": invalid number");
			return 1;
		}
		long index;
		if( arg.startsWith("-") && n < 0 ) {
			index = length+n;
			if( index < 0 ) {
				ctx.error("history: "+arg+": history position out of range");
				return 1;
			}
		} else if( n < base || n >= base+length ) {
			ctx.error("history: "+arg+": history position out of range");
			return 1;
		} else {
			index = n-base;
		}
		list.remove((int) index);
		return 0;
	}

	/** a whole number (bash's valid_number), or null */
	private static Long number(String text) {
		String t = text.trim();
		if( !t.matches("[-+]?[0-9]+")) {
			return null;
		}
		try {
			return Long.parseLong(t.startsWith("+") ? t.substring(1) : t);
		} catch (NumberFormatException e) {
			return null;
		}
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
			ctx.stdout.printf("%5d  %s%s%n", ctx.console.historyBase+idx, tm, e.command);
		}
	}
}
