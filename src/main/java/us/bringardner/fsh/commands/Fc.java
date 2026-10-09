package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.Console.HistoryEntry;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * fc [-e ename] [-lnr] [first] [last] or fc -s [pat=rep] [command]: list, edit and run again, or
 * run again, commands from the history, as bash's fc does.
 */
public class Fc extends ShellCommand{
	static String name = "fc";
	static String help = "fc [-e ename] [-lnr] [first] [last] or fc -s [pat=rep] [command]\n"
			+ "	List (-l, -n without numbers, -r in reverse) or edit and run again the commands from first\n"
			+ "	to last of the history (a number, a negative offset, or the start of a command). The editor\n"
			+ "	is ename, $FCEDIT, $EDITOR or vi. -s runs one command again, each pat=rep replaced in it."
			;

	static final String USAGE = "fc: usage: fc [-e ename] [-lnr] [first] [last] or fc -s [pat=rep] [command]";

	/** fc_gethnum's answers that are not places in the list */
	private static final int INVALID = -2, NOTFOUND = -3;

	public Fc() {
		super(name, help);
	}

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
		List<String> words = new ArrayList<>();
		for(us.bringardner.fsh.Argument a : args) {
			words.add(String.valueOf(a.getValue(ctx)));
		}
		boolean numbering = true, reverse = false, listing = false, execute = false;
		String ename = null;
		int i = 0;
		for(; i < words.size(); i++) {
			String w = words.get(i);
			if( w.equals("--")) {
				i++;
				break;
			}
			// (-1 is a history number, not an option)
			if( !w.startsWith("-") || w.length() < 2 || w.substring(1).matches("[0-9]+")) {
				break;
			}
			for (int k = 1; k < w.length(); k++) {
				char c = w.charAt(k);
				switch (c) {
				case 'n': numbering = false; break;
				case 'l': listing = true; break;
				case 'r': reverse = true; break;
				case 's': execute = true; break;
				case 'e':
					if( k+1 < w.length()) {
						ename = w.substring(k+1);
					} else if( i+1 < words.size()) {
						ename = words.get(++i);
					} else {
						ctx.error("fc: -e: option requires an argument");
						ctx.stderr.println(USAGE);
						return 2;
					}
					k = w.length();
					break;
				default:
					ctx.error("fc: -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
			}
		}
		List<String> rest = new ArrayList<>(words.subList(i, words.size()));
		if( "-".equals(ename)) {
			execute = true;
		}
		List<HistoryEntry> hist = ctx.console.history;
		if( execute ) {
			// fc -s [pat=rep ...] [command]
			List<String[]> subs = new ArrayList<>();
			while( !rest.isEmpty() && rest.get(0).indexOf('=') >= 0 ) {
				String r = rest.remove(0);
				int eq = r.indexOf('=');
				subs.add(new String[] {r.substring(0, eq), r.substring(eq+1)});
			}
			int n = number(ctx, rest.isEmpty() ? null : rest.get(0), false, false);
			if( n < 0 ) {
				ctx.error("fc: no command found");
				return 1;
			}
			String command = hist.get(n).command;
			for(String [] r : subs) {
				if( !r[0].isEmpty()) {
					command = command.replace(r[0], r[1]);
				}
			}
			ctx.stderr.println(command);
			// (the command takes the place of fc -s in the history)
			ctx.console.deleteLastHistory();
			ctx.console.rememberCommand(command);
			return ctx.console.runCode(ctx, command);
		}
		if( hist.isEmpty()) {
			return 0;
		}
		int count = hist.size();
		int lastHist = lastHist(ctx);
		int realLast = count-1;
		int beg, end;
		if( !rest.isEmpty()) {
			beg = number(ctx, rest.get(0), listing, true);
			if( rest.size() > 1 ) {
				end = number(ctx, rest.get(1), listing, false);
			} else if( beg == realLast ) {
				end = listing ? realLast : beg;
			} else {
				end = listing ? lastHist : beg;
			}
		} else if( listing ) {
			// the last 16
			end = lastHist;
			beg = Math.max(0, end-16+1);
		} else {
			beg = end = lastHist;
		}
		if( beg == INVALID || end == INVALID ) {
			ctx.error("fc: history specification out of range");
			return 1;
		}
		if( beg == NOTFOUND || end == NOTFOUND ) {
			ctx.error("fc: no command found");
			return 1;
		}
		beg = Math.max(0, beg);
		end = Math.max(0, end);
		if( !listing && ctx.console.historyLastLineAdded ) {
			// (the fc command itself is not kept when editing)
			ctx.console.deleteLastHistory();
			int last = hist.size()-1;
			if( end > last ) {
				end = last;
			}
			if( beg > last ) {
				beg = last;
			}
		}
		if( end < beg ) {
			int t = end;
			end = beg;
			beg = t;
			reverse = true;
		}
		StringBuilder out = new StringBuilder();
		for (int k = reverse ? end : beg; reverse ? k >= beg : k <= end; k += reverse ? -1 : 1) {
			if( k < 0 || k >= hist.size()) {
				continue;
			}
			if( listing ) {
				if( numbering ) {
					out.append(ctx.console.historyBase+k);
				}
				out.append(ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix) ? "\t" : "\t ");
			}
			out.append(hist.get(k).command).append('\n');
		}
		if( listing ) {
			ctx.stdout.print(out);
			return 0;
		}
		// edit them, then run what the file has
		java.io.File tmpDir = tmpDir(ctx);
		java.io.File file = java.io.File.createTempFile("fsh-fc.", ".sh", tmpDir);
		try {
			java.nio.file.Files.writeString(file.toPath(), out.toString());
			String editor = ename;
			if( editor == null ) {
				Object fcedit = ctx.getVariable("FCEDIT");
				Object editorVar = ctx.getVariable("EDITOR");
				editor = fcedit != null && !fcedit.toString().isEmpty() ? fcedit.toString()
						: editorVar != null && !editorVar.toString().isEmpty() ? editorVar.toString() : "vi";
			}
			int status = ctx.console.runCode(ctx, editor+" '"+file.getAbsolutePath().replace("'", "'\\''")+"'");
			if( status != 0 ) {
				return 1;
			}
			String code = java.nio.file.Files.readString(file.toPath());
			// (the commands are shown as they are read, as with set -v)
			ctx.stderr.print(code);
			ctx.stderr.flush();
			for(String line : code.split("\n")) {
				if( !line.isBlank()) {
					ctx.console.rememberCommand(line);
				}
			}
			return ctx.console.runCode(ctx, code);
		} finally {
			file.delete();
		}
	}

	private static java.io.File tmpDir(ShellContext ctx) {
		Object t = ctx.getVariable("TMPDIR");
		if( t != null && new java.io.File(t.toString()).isDirectory()) {
			return new java.io.File(t.toString());
		}
		return new java.io.File(System.getProperty("java.io.tmpdir"));
	}

	/** the last entry this deals with: the one before this fc command if that was kept */
	private static int lastHist(ShellContext ctx) {
		int count = ctx.console.history.size();
		boolean remember = ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.History);
		int last = count-(remember ? 1 : 0)-(remember && ctx.console.historyLastLineAdded ? 1 : 0);
		if( !remember ) {
			last = count-1;
		}
		return Math.max(0, Math.min(last, count-1));
	}

	/**
	 * bash's fc_gethnum: a history number, a negative offset from the last, or the start of a
	 * command, as a place in the list (or INVALID or NOTFOUND); null is the last.
	 */
	private static int number(ShellContext ctx, String spec, boolean listing, boolean first) {
		List<HistoryEntry> hist = ctx.console.history;
		if( hist.isEmpty()) {
			return -1;
		}
		int last = lastHist(ctx);
		int realLast = hist.size()-1;
		if( spec == null ) {
			return last;
		}
		String s = spec;
		int sign = 1;
		if( s.startsWith("-")) {
			sign = -1;
			s = s.substring(1);
		}
		if( !s.isEmpty() && Character.isDigit(s.charAt(0))) {
			int n;
			try {
				n = Integer.parseInt(s.replaceAll("[^0-9].*", ""))*sign;
			} catch (NumberFormatException e) {
				n = Integer.MAX_VALUE;
			}
			if( n < 0 ) {
				n += last+1;
				return n < 0 ? 0 : n;
			} else if( n == 0 ) {
				return sign == -1 ? (listing ? realLast : INVALID) : last;
			}
			n -= ctx.console.historyBase;
			if( n < 0 || n >= last ) {
				return first ? 0 : last;
			}
			return n;
		}
		for (int j = last; j >= 0; j--) {
			if( hist.get(j).command.startsWith(spec)) {
				return j;
			}
		}
		return NOTFOUND;
	}
}
