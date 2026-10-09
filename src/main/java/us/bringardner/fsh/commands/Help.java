package us.bringardner.fsh.commands;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.GlobPattern;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * help [-dms] [pattern ...]: what the builtins do (fsh's own words), found as bash's help finds
 * them: a pattern matches a name as a glob or as the start of one.
 */
public class Help extends ShellCommand{
	static String name = "help";
	static String help = "help [-dms] [pattern ...]\n"
			+ "\tShow what the builtins matching pattern (a glob, or the start of a name) do; with\n"
			+ "\tno pattern, list them. -d: a short description of each, -m: as a manual page,\n"
			+ "\t-s: only how each is used. The status is 1 if a pattern matches nothing.";

	static final String USAGE = "help: usage: help [-dms] [pattern ...]";

	/** the builtins the executor runs itself, which have no command of their own */
	private static final Map<String, String> OWN = Map.of(
			"[", "[ expression ]\n\tThe same as test, with a ] as its last argument.",
			"break", "break [n]\n\tLeave the innermost loop, or the n enclosing loops. The status is 0 unless n\n"
					+ "\tis not 1 or more.",
			"continue", "continue [n]\n\tGo on with the next pass of the innermost loop, or of the nth enclosing\n"
					+ "\tloop. The status is 0 unless n is not 1 or more.",
			"declare", declareHelp("declare"),
			"typeset", declareHelp("typeset"),
			"local", "local [option] name[=value] ...\n\tMake each name a variable of the function it is used in"
					+ " (and of the functions\n\tit calls), with declare's options. Only in a function.");

	private static String declareHelp(String name) {
		return name+" [-aAfFgiIlnrtux] [name[=value] ...] or "+name+" -p [-aAfFilnrtux] [name ...]\n"
				+ "\tSet variables and their attributes, or show them (-p). -a an indexed array, -A an\n"
				+ "\tassociative one, -i an integer, -l and -u lower and upper case, -n a reference to\n"
				+ "\tanother name, -r read-only, -t traced, -x exported; + in place of - turns an\n"
				+ "\tattribute off. -f and -F show functions, -g makes a global variable in a function,\n"
				+ "\t-I a local one with the value of the one it hides. In a function it makes local\n"
				+ "\tvariables (as local does) unless -g is given.";
	}

	public Help() {
		super(name, help);
	}

	/** every builtin's help text, by name */
	private static Map<String, String> topics(ShellContext ctx) {
		Map<String, String> ret = new TreeMap<>();
		for(Map.Entry<String, Constructor<? extends ShellCommand>> e : Console.commands.entrySet()) {
			if( e.getKey().startsWith("__")) {
				continue;
			}
			try {
				ret.put(e.getKey(), e.getValue().newInstance().getHelp());
			} catch (ReflectiveOperationException | RuntimeException x) {
				// (one that cannot be made has no help)
			}
		}
		ret.putAll(OWN);
		return ret;
	}

	/** the help of a builtin (name: usage, then what it does, indented), or null if there is none */
	public static String text(ShellContext ctx, String builtin) {
		String text = topics(ctx).get(builtin);
		return text == null ? null : full(builtin, text);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		char mode = 0;
		int idx = 0;
		for(; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.equals("--")) {
				idx++;
				break;
			}
			if( !a.startsWith("-") || a.length() == 1 ) {
				break;
			}
			for(char c : a.substring(1).toCharArray()) {
				if( "dms".indexOf(c) < 0 ) {
					ctx.error(name+": -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
				mode = c;
			}
		}
		Map<String, String> topics = topics(ctx);
		if( idx >= args.length ) {
			for(Map.Entry<String, String> e : topics.entrySet()) {
				ctx.stdout.println(e.getKey()+": "+usage(e.getKey(), e.getValue()));
			}
			return 0;
		}
		int ret = 0;
		String first = ""+args[idx].getValue(ctx);
		if( first.matches(".*[*?\\[].*")) {
			// (a pattern: what it is, first, as bash's)
			List<String> words = new ArrayList<>();
			for (int i = idx; i < args.length; i++) {
				words.add(""+args[i].getValue(ctx));
			}
			ctx.stdout.print((words.size() > 1 ? "Shell commands matching keywords `" : "Shell commands matching keyword `")
					+String.join(", ", words)+"'\n\n");
		}
		for(; idx < args.length; idx++) {
			String pattern = ""+args[idx].getValue(ctx);
			GlobPattern glob = GlobPattern.compile(pattern);
			List<String> found = new ArrayList<>();
			for(String n : topics.keySet()) {
				if( glob.matches(n, true) || n.startsWith(pattern)) {
					found.add(n);
				}
			}
			if( found.isEmpty()) {
				ctx.error(name+": no help topics match `"+pattern+"'.  Try `help help' or `man -k "+pattern+"' or `info "+pattern+"'.");
				ret = 1;
				continue;
			}
			for(String n : found) {
				String text = topics.get(n);
				switch (mode) {
				case 's' -> ctx.stdout.println(n+": "+usage(n, text));
				case 'd' -> ctx.stdout.println(n+" - "+summary(n, text));
				case 'm' -> {
					ctx.stdout.println("NAME\n    "+n+" - "+summary(n, text)+"\n");
					ctx.stdout.println("SYNOPSIS\n    "+usage(n, text)+"\n");
					ctx.stdout.println("DESCRIPTION\n"+indented(description(n, text))+"\n");
				}
				default -> ctx.stdout.print(full(n, text));
				}
			}
		}
		return ret;
	}

	/** name: usage, then what it does, indented */
	private static String full(String n, String text) {
		String d = description(n, text);
		return n+": "+usage(n, text)+"\n"+(d.isEmpty() ? "" : indented(d)+"\n");
	}

	/** how it is used: the first line of its text, or a line in it that starts with the name */
	private static String usage(String n, String text) {
		String first = text.strip().split("\n", 2)[0].strip();
		if( isUsage(n, first)) {
			return first;
		}
		for(String line : text.split("\n")) {
			String l = line.strip();
			if( l.startsWith("USAGE:")) {
				l = l.substring(6).strip();
			}
			if( !l.equals(n) && isUsage(n, l)) {
				return l;
			}
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-z]+( \\[.*)").matcher(first);
		if( m.matches()) {
			// (a text it shares, readarray's with mapfile: with its own name)
			return n+m.group(1);
		}
		return n;
	}

	/** a line that is how name is used: name, or name and its arguments (not "name - what it does") */
	private static boolean isUsage(String n, String line) {
		return line.equals(n) || line.startsWith(n+" ") && !line.startsWith(n+" –") && !line.startsWith(n+" - ");
	}

	/** what it does: its text after the usage line */
	private static String description(String n, String text) {
		String t = text.strip();
		String first = t.split("\n", 2)[0].strip();
		if( isUsage(n, first)) {
			return t.contains("\n") ? t.substring(t.indexOf('\n')+1).strip() : "";
		}
		return t;
	}

	/** its first sentence (or line): what it does */
	private static String summary(String n, String text) {
		for(String line : description(n, text).split("\n")) {
			String l = line.strip();
			for(String dash : new String[] {" – ", " - "}) {
				if( l.startsWith(n+dash)) {
					// (mkdir – make directories)
					l = l.substring(n.length()+dash.length()).strip();
				}
			}
			if( l.isEmpty() || l.startsWith("USAGE:") || l.equals("SYNOPSIS")) {
				continue;
			}
			int dot = l.indexOf(". ");
			return dot > 0 ? l.substring(0, dot+1) : l;
		}
		return usage(n, text);
	}

	private static String indented(String text) {
		StringBuilder ret = new StringBuilder();
		for(String line : text.split("\n")) {
			if( ret.length() > 0 ) {
				ret.append('\n');
			}
			ret.append("    ").append(line.strip());
		}
		return ret.toString();
	}
}
