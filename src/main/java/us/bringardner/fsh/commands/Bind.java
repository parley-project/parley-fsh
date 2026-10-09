package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.fsh.LineEditor;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * bind [-lpsvPSVX] [-m keymap] [-f filename] [-q name] [-u name] [-r keyseq] [-x keyseq:shell-command]
 * [keyseq:readline-function or readline-command]: what the line editor's keys do, as bash's bind
 * (for the keys and functions fsh's line editor has).
 */
public class Bind extends ShellCommand{
	static String name = "bind";
	static String help = "bind [-lpsvPSVX] [-m keymap] [-f filename] [-q name] [-u name] [-r keyseq] [-x keyseq:shell-command] [keyseq:readline-function or readline-command]\n"
			+ "\tSet what the line editor's keys do, or show it. A binding is \"keyseq\": function (or\n"
			+ "\t\"text\" to type, or set variable value). -l lists the functions, -p and -P the keys\n"
			+ "\tthey are on, -s and -S the keys that type text, -v and -V the variables, -X the -x\n"
			+ "\tbindings; -q says which keys do a function, -u unbinds a function, -r a key; -x binds a\n"
			+ "\tkey to a shell command (READLINE_LINE and READLINE_POINT are the line and cursor);\n"
			+ "\t-f reads bindings from a file. -m names a keymap (fsh's is emacs).";

	static final String USAGE = "bind: usage: bind [-lpsvPSVX] [-m keymap] [-f filename] [-q name] [-u name] [-r keyseq] [-x keyseq:shell-command] [keyseq:readline-function or readline-command]";

	public Bind() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> words = new ArrayList<>();
		for(us.bringardner.fsh.Argument a : args) {
			words.add(""+a.getValue(ctx));
		}
		if( !ctx.console.isInteractive ) {
			// as bash says it (and does what it is asked)
			ctx.error("bind: warning: line editing not enabled");
		}
		int ret = 0;
		int i = 0;
		for(; i < words.size(); i++) {
			String w = words.get(i);
			if( w.equals("--")) {
				i++;
				break;
			}
			if( !w.startsWith("-") || w.length() < 2 ) {
				break;
			}
			for (int k = 1; k < w.length(); k++) {
				char c = w.charAt(k);
				if( "mfqurx".indexOf(c) >= 0 ) {
					// an option with a word after it (in the same word, or the next)
					String arg = k+1 < w.length() ? w.substring(k+1) : i+1 < words.size() ? words.get(++i) : null;
					if( arg == null ) {
						ctx.error("bind: -"+c+": option requires an argument");
						ctx.stderr.println(USAGE);
						return 2;
					}
					int status = option(ctx, c, arg);
					if( status != 0 ) {
						ret = status;
					}
					break;
				}
				switch (c) {
				case 'l' -> {
					for(String f : LineEditor.FUNCTIONS.keySet()) {
						ctx.stdout.println(f);
					}
				}
				case 'p', 'P' -> listFunctions(ctx, c == 'P');
				case 's', 'S' -> listMacros(ctx, c == 'S');
				case 'v', 'V' -> {
					for(Map.Entry<String, String> e : LineEditor.VARIABLES.entrySet()) {
						ctx.stdout.println(c == 'v' ? "set "+e.getKey()+" "+e.getValue() : e.getKey()+" is set to `"+e.getValue()+"'");
					}
				}
				case 'X' -> {
					for(Map.Entry<String, String> e : LineEditor.commandBindings().entrySet()) {
						ctx.stdout.println("\""+LineEditor.keyText(e.getKey())+"\" \""+e.getValue().replace("\\", "\\\\").replace("\"", "\\\"")+"\"");
					}
				}
				default -> {
					ctx.error("bind: -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
				}
			}
		}
		for(; i < words.size(); i++) {
			bindLine(words.get(i));
		}
		// (set editing-mode vi: set -o vi)
		ctx.console.applyEditingMode();
		return ret;
	}

	/** -m, -f, -q, -u, -r, -x with its word */
	private int option(ShellContext ctx, char c, String arg) throws IOException {
		switch (c) {
		case 'm':
			// (fsh's line editor has the one keymap, emacs)
			return 0;
		case 'f': {
			java.io.File file = new java.io.File(arg);
			if( !file.isAbsolute()) {
				file = new java.io.File(ctx.console.getCurrentDirectory().getAbsolutePath(), arg);
			}
			if( !readFile(file)) {
				ctx.error("bind: "+arg+": cannot read: No such file or directory");
				return 1;
			}
			return 0;
		}
		case 'q': {
			if( !LineEditor.FUNCTIONS.containsKey(arg)) {
				ctx.error("bind: `"+arg+"': unknown function name");
				return 1;
			}
			List<String> keys = keysOf(arg);
			if( keys.isEmpty()) {
				ctx.stdout.println(arg+" is not bound to any keys.");
				return 1;
			}
			ctx.stdout.println(arg+" can be invoked via "+String.join(", ", keys)+".");
			return 0;
		}
		case 'u':
			if( !LineEditor.FUNCTIONS.containsKey(arg)) {
				ctx.error("bind: `"+arg+"': unknown function name");
				return 1;
			}
			LineEditor.unbindFunction(arg);
			return 0;
		case 'r': {
			// (the key sequence in bind's notation, quoted or not)
			String seq = arg.length() > 1 && arg.startsWith("\"") && arg.endsWith("\"") ? arg.substring(1, arg.length()-1) : arg;
			LineEditor.unbindKey(unescape(seq));
			return 0;
		}
		case 'x': {
			String[] parsed = keySpec(arg);
			if( parsed == null || parsed[1].isEmpty()) {
				ctx.error("bind: "+arg+": missing colon separator");
				return 1;
			}
			String command = parsed[1];
			if( command.length() > 1 && command.startsWith("\"") && command.endsWith("\"")) {
				command = command.substring(1, command.length()-1);
			}
			LineEditor.bindCommand(parsed[0], command);
			return 0;
		}
		default:
			return 0;
		}
	}

	/**
	 * An inputrc file's bindings (bind -f, ~/.inputrc): $if mode=emacs, term=, and Bash (fsh does
	 * as bash) are true, mode=vi and other applications are not; $include reads another file.
	 * @return false if it cannot be read
	 */
	public static boolean readFile(java.io.File file) {
		List<String> lines;
		try {
			lines = java.nio.file.Files.readAllLines(file.toPath());
		} catch (IOException | RuntimeException e) {
			return false;
		}
		java.util.Deque<Boolean> skipping = new java.util.ArrayDeque<>();
		for(String line : lines) {
			String l = line.strip();
			if( l.isEmpty() || l.startsWith("#")) {
				continue;
			}
			boolean skip = skipping.contains(true);
			if( l.startsWith("$if")) {
				String test = l.substring(3).strip();
				boolean on = test.startsWith("mode=") ? test.substring(5).strip().equals("emacs")
						: test.startsWith("term=") || test.equalsIgnoreCase("bash");
				skipping.push(!on);
			} else if( l.startsWith("$else")) {
				if( !skipping.isEmpty()) {
					skipping.push(!skipping.pop());
				}
			} else if( l.startsWith("$endif")) {
				if( !skipping.isEmpty()) {
					skipping.pop();
				}
			} else if( l.startsWith("$include")) {
				if( !skip ) {
					String name = l.substring(8).strip();
					if( name.startsWith("~/")) {
						name = System.getProperty("user.home")+name.substring(1);
					}
					readFile(new java.io.File(name));
				}
			} else if( !skip ) {
				bindLine(l);
			}
		}
		return true;
	}

	/** the keys a function is on, as bind writes them ("\C-a") */
	private static List<String> keysOf(String function) {
		List<String> ret = new ArrayList<>();
		for(Map.Entry<String, String> e : LineEditor.bindings().entrySet()) {
			if( e.getValue().equals(function)) {
				for(String t : LineEditor.keyTexts(e.getKey())) {
					ret.add("\""+t+"\"");
				}
			}
		}
		return ret;
	}

	/** bind -p (and -P): each function and its keys */
	private static void listFunctions(ShellContext ctx, boolean words) {
		for(String f : LineEditor.FUNCTIONS.keySet()) {
			List<String> keys = keysOf(f);
			if( words ) {
				ctx.stdout.println(keys.isEmpty() ? f+" is not bound to any keys" : f+" can be found on "+String.join(", ", keys)+".");
			} else if( keys.isEmpty()) {
				ctx.stdout.println("# "+f+" (not bound)");
			} else {
				for(String k : keys) {
					ctx.stdout.println(k+": "+f);
				}
			}
		}
	}

	/** bind -s (and -S): the keys that type text */
	private static void listMacros(ShellContext ctx, boolean words) {
		Map<String, String> macros = new TreeMap<>();
		for(Map.Entry<String, String> e : LineEditor.bindings().entrySet()) {
			if( e.getValue().startsWith("\"")) {
				macros.put(LineEditor.keyText(e.getKey()), e.getValue().substring(1, e.getValue().length()-1));
			}
		}
		for(Map.Entry<String, String> e : macros.entrySet()) {
			ctx.stdout.println(words ? e.getKey()+" outputs "+e.getValue() : "\""+e.getKey()+"\": \""+e.getValue()+"\"");
		}
	}

	/** one binding: "keyseq": function, "keyseq": "text", keyname: function, or set variable value */
	static void bindLine(String line) {
		String l = line.strip();
		if( l.startsWith("set ") || l.startsWith("set\t")) {
			String[] f = l.substring(4).strip().split("\\s+", 2);
			if( f.length == 2 ) {
				LineEditor.VARIABLES.put(f[0], f[1].strip());
			} else if( f.length == 1 && !f[0].isEmpty()) {
				LineEditor.VARIABLES.put(f[0], "on");
			}
			return;
		}
		String[] parsed = keySpec(l);
		if( parsed == null ) {
			return;
		}
		String target = parsed[1];
		if( target.startsWith("\"") || target.startsWith("'")) {
			// a macro: the text it types
			char q = target.charAt(0);
			int end = target.lastIndexOf(q);
			String text = end > 0 ? target.substring(1, end) : target.substring(1);
			LineEditor.bind(parsed[0], "\""+unescape(text)+"\"");
			return;
		}
		String function = target.split("\\s+")[0];
		// (an unknown function: the key does nothing, as readline's)
		LineEditor.bind(parsed[0], LineEditor.FUNCTIONS.containsKey(function) ? function : "");
	}

	/** "keyseq": rest, or keyname: rest: {the key's characters, rest}; null if there is no colon */
	static String[] keySpec(String l) {
		l = l.strip();
		String seq;
		String rest;
		if( l.startsWith("\"")) {
			int end = 1;
			while( end < l.length() && l.charAt(end) != '"' ) {
				end += l.charAt(end) == '\\' ? 2 : 1;
			}
			if( end >= l.length()) {
				return null;
			}
			seq = unescape(l.substring(1, end));
			rest = l.substring(end+1).strip();
			if( !rest.startsWith(":")) {
				return null;
			}
			rest = rest.substring(1).strip();
		} else {
			int colon = l.indexOf(':');
			if( colon < 0 ) {
				return null;
			}
			seq = keyName(l.substring(0, colon).strip());
			rest = l.substring(colon+1).strip();
			if( seq == null ) {
				return null;
			}
		}
		return new String[] {seq, rest};
	}

	/** an inputrc key name: Control-u, C-u, Meta-x, M-x, RET, TAB, ESC, DEL, SPC ... */
	private static String keyName(String n) {
		String lower = n.toLowerCase();
		for(String p : new String[] {"control-", "c-"}) {
			if( lower.startsWith(p) && n.length() > p.length()) {
				String k = keyName(n.substring(p.length()));
				return k == null || k.length() != 1 ? null : String.valueOf((char) (k.charAt(0) == '?' ? 127 : Character.toLowerCase(k.charAt(0)) & 0x1f));
			}
		}
		for(String p : new String[] {"meta-", "m-"}) {
			if( lower.startsWith(p) && n.length() > p.length()) {
				String k = keyName(n.substring(p.length()));
				return k == null ? null : "\u001b"+k;
			}
		}
		switch (lower) {
		case "rubout": case "del": return "\u007f";
		case "esc": case "escape": return "\u001b";
		case "lfd": case "newline": return "\n";
		case "ret": case "return": return "\r";
		case "spc": case "space": return " ";
		case "tab": return "\t";
		default: return n.length() == 1 ? n : null;
		}
	}

	/** bind's notation: \C-x, \M-x, \e, \\, \", \', \a \b \d \f \n \r \t \v, \nnn, \xHH */
	static String unescape(String s) {
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if( c != '\\' || i+1 >= s.length()) {
				ret.append(c);
				continue;
			}
			char n = s.charAt(++i);
			if( (n == 'C' || n == 'M') && i+2 < s.length() && s.charAt(i+1) == '-' ) {
				// \C-x, \M-x (and \C-\M-x, \M-\C-x)
				String after = unescape(s.substring(i+2));
				char k = after.isEmpty() ? 0 : after.charAt(0);
				String tail = after.length() > 1 ? after.substring(1) : "";
				if( n == 'C' ) {
					if( k == 27 && tail.length() > 0 ) {
						// \C-\M-x
						ret.append('\u001b').append((char) (Character.toLowerCase(tail.charAt(0)) & 0x1f)).append(tail.substring(1));
					} else {
						ret.append(k == '?' ? (char) 127 : (char) (Character.toLowerCase(k) & 0x1f)).append(tail);
					}
				} else {
					ret.append('\u001b').append(after);
				}
				return ret.toString();
			}
			switch (n) {
			case 'e': ret.append('\u001b'); break;
			case 'a': ret.append('\u0007'); break;
			case 'b': ret.append('\b'); break;
			case 'd': ret.append('\u007f'); break;
			case 'f': ret.append('\f'); break;
			case 'n': ret.append('\n'); break;
			case 'r': ret.append('\r'); break;
			case 't': ret.append('\t'); break;
			case 'v': ret.append('\u000b'); break;
			case 'x': {
				int end = i+1;
				while( end < s.length() && end < i+3 && Character.digit(s.charAt(end), 16) >= 0 ) {
					end++;
				}
				if( end > i+1 ) {
					ret.append((char) Integer.parseInt(s.substring(i+1, end), 16));
					i = end-1;
				} else {
					ret.append('x');
				}
				break;
			}
			default:
				if( n >= '0' && n <= '7' ) {
					int end = i;
					while( end < s.length() && end < i+3 && s.charAt(end) >= '0' && s.charAt(end) <= '7' ) {
						end++;
					}
					ret.append((char) Integer.parseInt(s.substring(i, end), 8));
					i = end-1;
				} else {
					// \\, \", \' and any other: the character
					ret.append(n);
				}
			}
		}
		return ret.toString();
	}
}
