package us.bringardner.fsh;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import us.bringardner.fsh.commands.Trap;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobState;
import us.bringardner.parley.files.FileSource;

/**
 * Tab completion, as bash's: the word at the cursor is a command (an alias, function, builtin,
 * keyword or program on PATH) at the start of a command, a variable after $, a user after ~,
 * what the command's complete spec gives (complete -F, -W, -A ...), or else a file name.
 */
public class Completion implements LineEditor.Completer {

	/** a possibility: the text that replaces the word, how a list shows it, and what follows it alone */
	public static final class Candidate {
		public final String text;
		public final String display;
		public final String suffix;

		public Candidate(String text, String display, String suffix) {
			this.text = text;
			this.display = display;
			this.suffix = suffix;
		}

		@Override
		public String toString() {
			return text+suffix;
		}
	}

	/** what the word starting at start can become */
	public static final class Result {
		public final int start;
		public final List<Candidate> candidates;

		public Result(int start, List<Candidate> candidates) {
			this.start = start;
			this.candidates = candidates;
		}

		/** the start all the candidates have */
		public String commonPrefix() {
			String ret = null;
			for(Candidate c : candidates) {
				if( ret == null ) {
					ret = c.text;
				} else {
					int n = 0;
					while( n < ret.length() && n < c.text.length() && ret.charAt(n) == c.text.charAt(n)) {
						n++;
					}
					ret = ret.substring(0, n);
				}
			}
			return ret == null ? "" : ret;
		}
	}

	/** in bash's order (its word_token_alist) */
	private static final String [] KEYWORDS = {"if", "then", "else", "elif", "fi", "case", "esac", "for", "select", "while",
			"until", "do", "done", "in", "function", "time", "{", "}", "!", "[[", "]]", "coproc"};

	/** bash's help topics (the names in its builtin table) */
	private static final String [] HELP_TOPICS = {"!", "%", "(( ... ))", ".", ":", "[", "[[ ... ]]", "alias", "bg", "bind",
			"break", "builtin", "caller", "case", "cd", "command", "compgen", "complete", "compopt", "continue", "coproc",
			"declare", "dirs", "disown", "echo", "enable", "eval", "exec", "exit", "export", "false", "fc", "fg", "for", "for ((",
			"function", "getopts", "hash", "help", "history", "if", "jobs", "kill", "let", "local", "logout", "mapfile", "popd",
			"printf", "pushd", "pwd", "read", "readarray", "readonly", "return", "select", "set", "shift", "shopt", "source",
			"suspend", "test", "time", "times", "trap", "true", "type", "typeset", "ulimit", "umask", "unalias", "unset", "until",
			"variables", "wait", "while", "{ ... }"};

	/** the builtins' names ([ for the test one, and those the executor runs itself) */
	static java.util.SortedSet<String> builtinNames() {
		java.util.SortedSet<String> ret = new TreeSet<>();
		for(String n : Console.commands.keySet()) {
			ret.add(n.equals("__bracket_test") ? "[" : n);
		}
		ret.addAll(List.of("break", "continue", "eval", "declare", "typeset", "local", "readonly", "export", "exec"));
		return ret;
	}

	/** after these a command starts */
	private static final Set<String> COMMAND_KEYWORDS = Set.of("if", "then", "else", "elif", "do", "while", "until",
			"!", "time", "{", "exec", "command", "builtin", "nohup", "sudo", "env", "xargs");

	private final Console console;

	public Completion(Console console) {
		this.console = console;
	}

	// ------------------------------------------------------------------ the line

	/** the line up to the cursor, read as the shell would: the words and where the last one starts */
	static final class Parsed {
		/** the words of the command the cursor is in, before the current one (quotes removed) */
		final List<String> words = new ArrayList<>();
		/** where the current word starts in the line */
		int start;
		/** the current word as typed, and without its quotes */
		String raw = "";
		String value = "";
		/** the quote the current word is in at the cursor (' or "), or 0 */
		char quote;
		/** the current word is a command name / the file of a redirect */
		boolean command = true;
		boolean redirect;
	}

	static Parsed parse(String line, int cursor) {
		Parsed p = new Parsed();
		String text = line.substring(0, Math.min(cursor, line.length()));
		StringBuilder raw = new StringBuilder();
		StringBuilder value = new StringBuilder();
		char quote = 0;
		boolean commandNext = true;
		boolean redirectNext = false;
		int wordStart = 0;
		boolean inWord = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if( quote != 0 ) {
				raw.append(c);
				if( c == quote ) {
					quote = 0;
				} else if( c == '\\' && quote == '"' && i+1 < text.length()) {
					char n = text.charAt(++i);
					raw.append(n);
					value.append(n);
				} else {
					value.append(c);
				}
				continue;
			}
			if( !inWord ) {
				if( Character.isWhitespace(c)) {
					continue;
				}
				if( ";&|()".indexOf(c) >= 0 ) {
					commandNext = true;
					redirectNext = false;
					p.words.clear();
					continue;
				}
				if( c == '<' || c == '>' ) {
					redirectNext = true;
					continue;
				}
				inWord = true;
				wordStart = i;
				raw.setLength(0);
				value.setLength(0);
			}
			if( Character.isWhitespace(c) || ";&|()<>".indexOf(c) >= 0 ) {
				// the word ends
				endWord(p, value.toString(), commandNext, redirectNext);
				commandNext = p.command;
				redirectNext = false;
				inWord = false;
				i--;
				continue;
			}
			raw.append(c);
			if( c == '\'' || c == '"' ) {
				quote = c;
			} else if( c == '\\' && i+1 < text.length()) {
				char n = text.charAt(++i);
				raw.append(n);
				value.append(n);
			} else {
				value.append(c);
			}
		}
		if( inWord ) {
			p.start = wordStart;
			p.raw = raw.toString();
			p.value = value.toString();
			p.quote = quote;
		} else {
			p.start = text.length();
		}
		p.command = commandNext && !redirectNext;
		p.redirect = redirectNext;
		return p;
	}

	/** a word before the cursor ends: it is the command's, and says if the next is a command */
	private static void endWord(Parsed p, String word, boolean wasCommand, boolean wasRedirect) {
		if( wasRedirect ) {
			// a redirect's file: not one of the command's words
			p.command = wasCommand;
			return;
		}
		p.words.add(word);
		// after an assignment or a keyword like then, a command still comes
		p.command = wasCommand && (word.matches("[A-Za-z_][A-Za-z0-9_]*=.*") || COMMAND_KEYWORDS.contains(word));
		if( p.command && COMMAND_KEYWORDS.contains(word)) {
			p.words.clear();
		}
	}

	// ------------------------------------------------------------------ completing

	@Override
	public Result complete(String line, int cursor) {
		try {
			return complete0(line, cursor);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private Result complete0(String line, int cursor) {
		Parsed p = parse(line, cursor);
		ShellContext sc = new ShellContext(console);
		List<Candidate> ret = new ArrayList<>();

		if( p.quote != '\'' && p.raw.startsWith("$") && !p.raw.contains("/")) {
			// a variable
			boolean brace = p.raw.startsWith("${");
			String name = p.raw.substring(brace ? 2 : 1);
			for(String v : variableNames(sc)) {
				if( v.startsWith(name)) {
					ret.add(new Candidate((brace ? "${" : "$")+v, v, brace ? "}" : " "));
				}
			}
			return new Result(p.start, ret);
		}
		if( p.value.startsWith("~") && !p.value.contains("/") && p.quote == 0 ) {
			for(String user : users()) {
				if( user.startsWith(p.value.substring(1))) {
					ret.add(new Candidate("~"+user, "~"+user, "/"));
				}
			}
			return new Result(p.start, ret);
		}
		if( !p.command && !p.redirect && !p.words.isEmpty()) {
			Spec spec = console.getCompletion(p.words.get(0));
			if( spec != null ) {
				Result r = programmable(spec, sc, p, line, cursor);
				if( r != null ) {
					return r;
				}
			}
		}
		if( p.command && !p.value.contains("/")) {
			for(String name : commandNames(sc, p.value)) {
				ret.add(new Candidate(quoted(name, p.quote), name, closing(p.quote)+" "));
			}
			return new Result(p.start, ret);
		}
		return new Result(p.start, files(sc, p, false));
	}

	/** what a complete spec gives for the word; null to go on as if there were none */
	private Result programmable(Spec spec, ShellContext sc, Parsed p, String line, int cursor) {
		String prev = p.words.get(p.words.size()-1);
		List<String> words = spec.generate(sc, p.value, p.words.get(0), prev, p.words, line, cursor);
		List<Candidate> ret = new ArrayList<>();
		boolean filenames = spec.options.contains("filenames");
		boolean nospace = spec.options.contains("nospace");
		for(String w : words) {
			if( filenames ) {
				boolean dir = isDirectory(sc, w);
				String name = w.endsWith("/") ? w : w;
				int slash = name.lastIndexOf('/', name.length()-2);
				ret.add(new Candidate(quoted(w, p.quote), slash >= 0 ? name.substring(slash+1) : name,
						dir ? "/" : nospace ? "" : closing(p.quote)+" "));
			} else {
				ret.add(new Candidate(w, w, nospace ? "" : " "));
			}
		}
		if( spec.options.contains("plusdirs")) {
			ret.addAll(files(sc, p, true));
		}
		if( ret.isEmpty()) {
			if( spec.options.contains("dirnames")) {
				return new Result(p.start, files(sc, p, true));
			}
			if( spec.options.contains("default") || spec.options.contains("bashdefault")) {
				return new Result(p.start, files(sc, p, false));
			}
		}
		return new Result(p.start, ret);
	}

	private static String closing(char quote) {
		return quote == 0 ? "" : String.valueOf(quote);
	}

	/** file names that start with the word (directories only: dirsOnly) */
	private List<Candidate> files(ShellContext sc, Parsed p, boolean dirsOnly) {
		List<Candidate> ret = new ArrayList<>();
		String word = p.value;
		int slash = word.lastIndexOf('/');
		String dirPart = slash >= 0 ? word.substring(0, slash+1) : "";
		String namePart = word.substring(slash+1);
		String dir = dirPart.isEmpty() ? "." : expandTilde(sc, dirPart);
		try {
			FileSource d = sc.getFileSource(dir);
			FileSource[] kids = d.isDirectory() ? d.listFiles() : null;
			if( kids == null ) {
				return ret;
			}
			Set<String> names = new TreeSet<>();
			Map<String, Boolean> isDir = new java.util.HashMap<>();
			for(FileSource k : kids) {
				String name = k.getName();
				if( !name.startsWith(namePart) || name.startsWith(".") && !namePart.startsWith(".")) {
					continue;
				}
				boolean directory = k.isDirectory();
				if( dirsOnly && !directory ) {
					continue;
				}
				names.add(name);
				isDir.put(name, directory);
			}
			for(String name : names) {
				boolean directory = isDir.get(name);
				String text = keepTilde(dirPart)+quoted(name, p.quote);
				if( !dirPart.startsWith("~")) {
					text = quoted(dirPart+name, p.quote);
				}
				ret.add(new Candidate(text, name+(directory ? "/" : ""), directory ? "/" : closing(p.quote)+" "));
			}
		} catch (Exception e) {
			// nothing there
		}
		return ret;
	}

	/** ~ and ~user/ stay as typed; the rest is quoted */
	private static String keepTilde(String dirPart) {
		if( !dirPart.startsWith("~")) {
			return dirPart;
		}
		int slash = dirPart.indexOf('/');
		return dirPart.substring(0, slash+1)+quoted(dirPart.substring(slash+1), (char) 0);
	}

	private static String expandTilde(ShellContext sc, String path) {
		if( !path.startsWith("~")) {
			return path;
		}
		int slash = path.indexOf('/');
		String user = slash < 0 ? path.substring(1) : path.substring(1, slash);
		String rest = slash < 0 ? "" : path.substring(slash);
		String home;
		if( user.isEmpty()) {
			Object h = sc.getVariable("HOME");
			home = h == null ? System.getProperty("user.home") : h.toString();
		} else {
			home = new File("/Users/"+user).isDirectory() ? "/Users/"+user : "/home/"+user;
		}
		return home+rest;
	}

	private static boolean isDirectory(ShellContext sc, String path) {
		try {
			return sc.getFileSource(expandTilde(sc, path)).isDirectory();
		} catch (Exception e) {
			return false;
		}
	}

	/** the characters a completed word has a backslash before, as bash quotes it */
	private static final String SPECIAL = " \t\n\"'\\$`&;|()<>*?[]!{}#=,";

	/** text as typed in the word: with backslashes, or as it is inside the quote the word opened */
	static String quoted(String text, char quote) {
		if( quote == '\'' ) {
			return "'"+text.replace("'", "'\\''");
		}
		if( quote == '"' ) {
			StringBuilder ret = new StringBuilder("\"");
			for(char c : text.toCharArray()) {
				if( "\"\\$`".indexOf(c) >= 0 ) {
					ret.append('\\');
				}
				ret.append(c);
			}
			return ret.toString();
		}
		StringBuilder ret = new StringBuilder();
		for(char c : text.toCharArray()) {
			if( SPECIAL.indexOf(c) >= 0 && c != '=' && c != ',' ) {
				ret.append('\\');
			}
			ret.append(c);
		}
		return ret.toString();
	}

	// ------------------------------------------------------------------ names

	/** aliases, functions, builtins, keywords and the programs on PATH that start with word */
	Set<String> commandNames(ShellContext sc, String word) {
		Set<String> ret = new TreeSet<>();
		add(ret, console.getAliases().keySet(), word);
		add(ret, console.getFunctions().keySet(), word);
		add(ret, Console.commands.keySet(), word);
		add(ret, List.of(KEYWORDS), word);
		ret.addAll(pathCommands(sc, word));
		return ret;
	}

	private static void add(Set<String> to, Collection<String> names, String word) {
		for(String n : names) {
			if( n.startsWith(word)) {
				to.add(n);
			}
		}
	}

	/** the programs on $PATH whose names start with word */
	static Set<String> pathCommands(ShellContext ctx, String word) {
		Set<String> ret = new TreeSet<>();
		Object path = ctx.getVariable("PATH");
		if( path == null ) {
			return ret;
		}
		for(String dir : path.toString().split(File.pathSeparator)) {
			File[] kids = new File(dir.isEmpty() ? "." : dir).listFiles();
			if( kids != null ) {
				for(File f : kids) {
					if( f.getName().startsWith(word) && f.isFile() && f.canExecute()) {
						ret.add(f.getName());
					}
				}
			}
		}
		return ret;
	}

	static Set<String> variableNames(ShellContext sc) {
		Set<String> ret = new TreeSet<>();
		for(String n : sc.getVariables().keySet()) {
			if( n.matches("[A-Za-z_][A-Za-z0-9_]*")) {
				ret.add(n);
			}
		}
		for(String n : sc.getEnvironmentVariables().keySet()) {
			if( n.matches("[A-Za-z_][A-Za-z0-9_]*")) {
				ret.add(n);
			}
		}
		return ret;
	}

	/** the users (their home directories' names, and this user) */
	static Set<String> users() {
		Set<String> ret = new TreeSet<>();
		ret.add(System.getProperty("user.name"));
		for(String dir : new String[] {"/Users", "/home"}) {
			File[] kids = new File(dir).listFiles();
			if( kids != null ) {
				for(File f : kids) {
					if( f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equals("Shared")) {
						ret.add(f.getName());
					}
				}
			}
		}
		ret.add("root");
		return ret;
	}

	// ------------------------------------------------------------------ specs

	/**
	 * What complete and compgen are told to give: actions (-A, -a -b -c ...), words (-W), a
	 * function (-F) or a command (-C), with a prefix (-P), a suffix (-S), a filter (-X) and
	 * options (-o filenames, nospace, default, dirnames, plusdirs, bashdefault, nosort).
	 */
	public static final class Spec {
		public final List<String> actions = new ArrayList<>();
		public String wordList;
		public String function;
		public String command;
		public String prefix = "";
		public String suffix = "";
		public String filter;
		public final Set<String> options = new LinkedHashSet<>();

		/** the words for word: the command's (cmd), the word before it (prev) and the line's words */
		public List<String> generate(ShellContext sc, String word, String cmd, String prev, List<String> words, String line, int point) {
			Set<String> found = new LinkedHashSet<>();
			Console console = sc.console;
			for(String action : actions) {
				// (in bash's order: its tables are sorted, its keywords are not)
				Set<String> names = action.equals("keyword") ? new LinkedHashSet<>() : new TreeSet<>();
				switch (action) {
				case "alias": names.addAll(console.getAliases().keySet()); break;
				case "arrayvar":
					for(Map.Entry<String,Object> e : sc.getVariables().entrySet()) {
						if( e.getValue() instanceof List<?> || e.getValue() instanceof Map<?,?>) {
							names.add(e.getKey());
						}
					}
					break;
				case "builtin":
				case "enabled":
				case "disabled":
					for(String b : builtinNames()) {
						if( action.equals("builtin") || console.disabledBuiltins.contains(b) == action.equals("disabled")) {
							names.add(b);
						}
					}
					break;
				case "command": names.addAll(new Completion(console).commandNames(sc, word)); break;
				case "directory":
				case "file":
					names.addAll(fileWords(sc, word, action.equals("directory")));
					break;
				case "export": names.addAll(sc.getEnvironmentVariables().keySet()); break;
				case "function": names.addAll(console.getFunctions().keySet()); break;
				case "keyword": names.addAll(List.of(KEYWORDS)); break;
				case "variable": names.addAll(variableNames(sc)); break;
				case "user": names.addAll(users()); break;
				case "helptopic": names.addAll(List.of(HELP_TOPICS)); break;
				case "group": case "hostname": case "service": case "binding": break;
				case "job":
				case "running":
				case "stopped":
					for(IJob j : console.jobManager.getJobs()) {
						if( action.equals("job") || (j.getState() == JobState.Suspended) == action.equals("stopped")) {
							String c = j.getCommandLine();
							names.add(c.split("\\s+")[0]);
						}
					}
					break;
				case "setopt":
					// (the ones set -o lists)
					for(Console.Option o : us.bringardner.fsh.commands.Set.listed()) {
						names.add(o.longName);
					}
					break;
				case "shopt": names.addAll(console.getShellOptions().keySet()); break;
				case "signal":
					for(String s : Trap.getLocalSignals().values()) {
						names.add("SIG"+s);
					}
					break;
				default:
					throw new IllegalArgumentException(action+": invalid action name");
				}
				for(String n : names) {
					if( n.startsWith(word)) {
						found.add(n);
					}
				}
			}
			if( wordList != null ) {
				// the words, expanded, in the order given
				Object ifs = sc.getVariable(Console.IFS);
				String expanded = wordList;
				try {
					expanded = us.bringardner.fsh.exec.Executor.expandWord(sc, wordList);
				} catch (RuntimeException e) {
				}
				for(String w : us.bringardner.fsh.commands.Read.split(expanded, ifs == null ? " \t\n" : ifs.toString(), Integer.MAX_VALUE)) {
					if( w.startsWith(word)) {
						found.add(w);
					}
				}
			}
			if( function != null ) {
				found.addAll(runFunction(sc, word, cmd, prev, words, line, point));
			}
			if( command != null ) {
				found.addAll(runCommand(sc, word, cmd, prev, line, point));
			}
			List<String> ret = new ArrayList<>();
			for(String f : found) {
				if( filter != null && filtered(f, word)) {
					continue;
				}
				ret.add(prefix+f+suffix);
			}
			return ret;
		}

		/** -X: a word that matches the pattern is left out (!pattern: one that does not) */
		private boolean filtered(String f, String word) {
			boolean not = filter.startsWith("!");
			String pattern = (not ? filter.substring(1) : filter).replace("&", word);
			boolean matches = GlobPattern.compile(pattern).matches(f);
			return not ? !matches : matches;
		}

		private List<String> runFunction(ShellContext sc, String word, String cmd, String prev, List<String> words, String line, int point) {
			Console console = sc.console;
			FshList compWords = new FshList();
			compWords.addAll(words);
			compWords.add(word);
			sc.setVariable("COMP_WORDS", compWords);
			sc.setVariable("COMP_CWORD", String.valueOf(words.size()));
			sc.setVariable("COMP_LINE", line == null ? "" : line);
			sc.setVariable("COMP_POINT", String.valueOf(point));
			sc.setVariable("COMP_TYPE", "9");
			sc.setVariable("COMP_KEY", "9");
			sc.unSetVariable("COMPREPLY");
			List<String> ret = new ArrayList<>();
			try {
				console.runCode(sc, function+" "+q(cmd)+" "+q(word)+" "+q(prev));
				Object reply = sc.getVariable("COMPREPLY");
				if( reply instanceof List<?> list ) {
					for(Object o : list) {
						ret.add(String.valueOf(o));
					}
				} else if( reply instanceof Map<?,?> map ) {
					for(Object o : map.values()) {
						ret.add(String.valueOf(o));
					}
				} else if( reply != null && !reply.toString().isEmpty()) {
					ret.add(reply.toString());
				}
			} catch (Exception e) {
				// the function failed: nothing
			} finally {
				for(String v : new String[] {"COMP_WORDS", "COMP_CWORD", "COMP_LINE", "COMP_POINT", "COMP_TYPE", "COMP_KEY"}) {
					sc.unSetVariable(v);
				}
			}
			return ret;
		}

		private List<String> runCommand(ShellContext sc, String word, String cmd, String prev, String line, int point) {
			List<String> ret = new ArrayList<>();
			try {
				sc.setVariable("COMP_LINE", line == null ? "" : line);
				sc.setVariable("COMP_POINT", String.valueOf(point));
				String out = us.bringardner.fsh.exec.Executor.expandWord(sc, "$("+command+" "+q(cmd)+" "+q(word)+" "+q(prev)+")");
				for(String l : out.split("\n")) {
					if( !l.isEmpty()) {
						ret.add(l);
					}
				}
			} catch (Exception e) {
			} finally {
				sc.unSetVariable("COMP_LINE");
				sc.unSetVariable("COMP_POINT");
			}
			return ret;
		}

		private static String q(String s) {
			return "'"+(s == null ? "" : s).replace("'", "'\\''")+"'";
		}

		/** the file (or directory) names that start with word, with the word's directory */
		private static List<String> fileWords(ShellContext sc, String word, boolean dirsOnly) {
			List<String> ret = new ArrayList<>();
			int slash = word.lastIndexOf('/');
			String dirPart = slash >= 0 ? word.substring(0, slash+1) : "";
			String namePart = word.substring(slash+1);
			try {
				FileSource d = sc.getFileSource(dirPart.isEmpty() ? "." : expandTilde(sc, dirPart));
				FileSource[] kids = d.isDirectory() ? d.listFiles() : null;
				if( kids != null ) {
					Set<String> names = new TreeSet<>();
					for(FileSource k : kids) {
						String n = k.getName();
						if( n.startsWith(namePart) && !(n.startsWith(".") && !namePart.startsWith(".")) && (!dirsOnly || k.isDirectory())) {
							names.add(dirPart+n);
						}
					}
					ret.addAll(names);
				}
			} catch (Exception e) {
			}
			return ret;
		}

		/** as complete -p prints it */
		public String toCommand(String name) {
			StringBuilder ret = new StringBuilder("complete");
			// (in bash's order: its options, its one-letter actions, then -A ones)
			for(String o : List.of("bashdefault", "default", "dirnames", "filenames", "fullquote", "noquote", "nosort", "nospace", "plusdirs")) {
				if( options.contains(o)) {
					ret.append(" -o ").append(o);
				}
			}
			String [][] acts = {{"alias", "a"}, {"arrayvar", null}, {"binding", null}, {"builtin", "b"}, {"command", "c"},
					{"directory", "d"}, {"disabled", null}, {"enabled", null}, {"export", "e"}, {"file", "f"}, {"function", null},
					{"helptopic", null}, {"hostname", null}, {"group", "g"}, {"job", "j"}, {"keyword", "k"}, {"running", null},
					{"service", "s"}, {"setopt", null}, {"shopt", null}, {"signal", null}, {"stopped", null}, {"user", "u"},
					{"variable", "v"}};
			for(String [] a : acts) {
				if( a[1] != null && actions.contains(a[0])) {
					ret.append(" -").append(a[1]);
				}
			}
			for(String [] a : acts) {
				if( a[1] == null && actions.contains(a[0])) {
					ret.append(" -A ").append(a[0]);
				}
			}
			if( wordList != null ) {
				ret.append(" -W ").append(q(wordList));
			}
			if( !prefix.isEmpty()) {
				ret.append(" -P ").append(q(prefix));
			}
			if( !suffix.isEmpty()) {
				ret.append(" -S ").append(q(suffix));
			}
			if( filter != null ) {
				ret.append(" -X ").append(q(filter));
			}
			if( command != null ) {
				ret.append(" -C ").append(q(command));
			}
			if( function != null ) {
				ret.append(" -F ").append(function);
			}
			return ret.append(' ').append(name).toString();
		}
	}
}
