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
				case 's': ctx.stdout.println(n+": "+usage(n, text)); break;
				case 'd': ctx.stdout.println(n+" - "+summary(n, text)); break;
				case 'm': {
					ctx.stdout.println("NAME\n    "+n+" - "+summary(n, text)+"\n");
					ctx.stdout.println("SYNOPSIS\n    "+usage(n, text)+"\n");
					ctx.stdout.println("DESCRIPTION\n"+indented(description(n, text))+"\n");
				} break;
				default: ctx.stdout.print(full(n, text)); break;
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

	/** fsh's own commands, in the order COMMANDS.md lists them */
	static final List<String> FSH_COMMANDS = List.of("connect", "disconnect", "cred");
	/** the file builtins: fsh's own versions, which work on every mounted file system */
	static final List<String> FILE_COMMANDS = List.of("ls", "cp", "mv", "rm", "mkdir", "touch", "ln", "find", "wc");
	/** builtins that are separate programs in bash */
	static final List<String> OTHER_COMMANDS = List.of("sleep", "expr", "clear");

	/**
	 * COMMANDS.md: the builtins' help, as Markdown. It is made from the same text help prints, so
	 * the two can't disagree; TestCommandsDoc checks the file is current.
	 */
	public static String markdown() {
		Map<String, String> topics = topics(null);
		StringBuilder md = new StringBuilder();
		md.append("# fsh commands\n\n")
		.append("<!-- Made from the builtins' help text; don't edit it by hand. After changing a help text, run\n")
		.append("     mvn test -Dtest=TestCommandsDoc -Dparley.fsh.writeCommandsDoc=true -->\n\n")
		.append("Everything here is also in the shell: `help NAME` shows a command's text, and `help` lists them all.\n\n")
		.append("- [fsh's own commands](#fshs-own-commands): connecting file systems and managing their passwords\n")
		.append("- [File commands](#file-commands): fsh's versions of ls, cp, mv and the rest, which work on every\n")
		.append("  mounted file system\n")
		.append("- [Other commands](#other-commands): commands that are separate programs in bash\n")
		.append("- [Bash builtins](#bash-builtins): the builtins that work as they do in bash\n\n");

		md.append("## fsh's own commands\n\n");
		for(String n : FSH_COMMANDS) {
			section(md, n, topics.get(n));
		}
		md.append("## File commands\n\n")
		.append("These are fsh's own versions, so they work on every mounted file system (see `connect`), and\n")
		.append("between them: `cp /prod/app.log /mem/` copies a file from one mounted file system to another. They take the options listed for\n")
		.append("each; any other option is an error. The system's command of the same name still runs by its path\n")
		.append("(`/bin/cp`), or after `enable -n cp`, and works only on the local disk.\n\n");
		for(String n : FILE_COMMANDS) {
			section(md, n, topics.get(n));
		}
		md.append("## Other commands\n\n")
		.append("In bash these are separate programs; fsh has them built in.\n\n");
		for(String n : OTHER_COMMANDS) {
			section(md, n, topics.get(n));
		}
		md.append("## Bash builtins\n\n")
		.append("These work as they do in bash; the [bash manual](https://www.gnu.org/software/bash/manual/bash.html#Shell-Builtin-Commands)\n")
		.append("describes them in full, and `help NAME` shows fsh's summary.\n\n")
		.append("| Command | Usage | What it does |\n")
		.append("|---|---|---|\n");
		for(Map.Entry<String, String> e : topics.entrySet()) {
			String n = e.getKey();
			if( FSH_COMMANDS.contains(n) || FILE_COMMANDS.contains(n) || OTHER_COMMANDS.contains(n)) {
				continue;
			}
			md.append("| `").append(cell(n)).append("` | `").append(cell(usage(n, e.getValue())))
			.append("` | ").append(cell(sentence(n, e.getValue(), topics.keySet()))).append(" |\n");
		}
		return md.toString();
	}

	private static void section(StringBuilder md, String n, String text) {
		if( text == null ) {
			throw new IllegalStateException("no help for "+n);
		}
		md.append("### ").append(n).append("\n\n```\n").append(usage(n, text)).append("\n```\n\n");
		String d = description(n, text);
		if( !d.isEmpty()) {
			md.append("```text\n");
			for(String line : d.split("\n")) {
				md.append(line.strip()).append('\n');
			}
			md.append("```\n\n");
		}
	}

	/**
	 * The first sentence of what it does, across line breaks, skipping usage lines (its own, or
	 * another builtin's that it shares, such as readarray's mapfile).
	 */
	private static String sentence(String n, String text, java.util.Set<String> names) {
		StringBuilder ret = new StringBuilder();
		for(String line : description(n, text).split("\n")) {
			String l = line.strip();
			if( l.startsWith("USAGE:") || l.equals("SYNOPSIS") || l.equals("DESCRIPTION")) {
				continue;
			}
			for(String dash : new String[] {" – ", " - "}) {
				if( l.startsWith(n+dash)) {
					// (echo – write arguments ...)
					l = l.substring(n.length()+dash.length()).strip();
				}
			}
			String first = l.split("[ \\[]", 2)[0];
			if( ret.length() == 0 && (l.isEmpty() || l.startsWith("-") || names.contains(first))) {
				continue;
			}
			if( l.isEmpty()) {
				break;
			}
			if( ret.length() > 0 ) {
				ret.append(' ');
			}
			ret.append(l);
			int dot = ret.indexOf(". ");
			if( dot > 0 ) {
				ret.setLength(dot+1);
				break;
			}
			if( ret.charAt(ret.length()-1) == '.' ) {
				break;
			}
		}
		String r = ret.toString().strip().replaceAll(" {2,}", " ");
		if( r.isEmpty()) {
			r = summary(n, text);
		}
		return Character.toUpperCase(r.charAt(0))+r.substring(1);
	}

	/** text for a table cell: one line, with | escaped */
	private static String cell(String s) {
		return s.replace("\n", " ").replace("|", "\\|").strip();
	}
}
