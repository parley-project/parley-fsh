package us.bringardner.fsh.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.Glob;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * compgen: the completions of a word, one per line, as bash's compgen prints them.
 */
public class Compgen extends ShellCommand{
	static String name = "compgen";
	static String help = "compgen [-abcdefkv] [-A action] [-W wordlist] [-P prefix] [-S suffix] [word]\n"
			+ "	Print the possible completions of word (all of them without word):\n"
			+ "	-A function|variable|alias|builtin|command|file|directory|export|keyword, or the short\n"
			+ "	forms -a (alias) -b (builtin) -c (command) -d (directory) -e (export) -f (file)\n"
			+ "	-k (keyword) -v (variable); -W the words of wordlist. Status 1 if there are none."
			;

	private static final String [] KEYWORDS = {"!", "[[", "]]", "case", "do", "done", "elif", "else", "esac",
			"fi", "for", "function", "if", "in", "select", "then", "time", "until", "while", "{", "}"};

	public Compgen() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> actions = new ArrayList<>();
		String wordList = null;
		String prefix = "";
		String suffix = "";
		String word = "";
		for(int idx = 0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.equals("--")) {
				if( idx+1 < args.length ) {
					word = ""+args[idx+1].getValue(ctx);
				}
				break;
			}
			if( a.startsWith("-") && a.length() > 1 ) {
				for(int c = 1; c < a.length(); c++) {
					char op = a.charAt(c);
					switch (op) {
					case 'A':
						actions.add(""+args[++idx].getValue(ctx));
						break;
					case 'W':
						wordList = ""+args[++idx].getValue(ctx);
						break;
					case 'P':
						prefix = ""+args[++idx].getValue(ctx);
						break;
					case 'S':
						suffix = ""+args[++idx].getValue(ctx);
						break;
					case 'a': actions.add("alias"); break;
					case 'b': actions.add("builtin"); break;
					case 'c': actions.add("command"); break;
					case 'd': actions.add("directory"); break;
					case 'e': actions.add("export"); break;
					case 'f': actions.add("file"); break;
					case 'k': actions.add("keyword"); break;
					case 'v': actions.add("variable"); break;
					default:
						ctx.stderr.println("compgen: -"+op+": invalid option");
						return 2;
					}
				}
			} else {
				word = a;
			}
		}

		Set<String> found = new LinkedHashSet<>();
		if( wordList != null ) {
			// the words in the order given
			Object ifs = ctx.getVariable(Console.IFS);
			for(String w : Read.split(wordList, ifs == null ? " \t\n" : ifs.toString(), Integer.MAX_VALUE)) {
				if( w.startsWith(word)) {
					found.add(w);
				}
			}
		}
		for(String action : actions) {
			Set<String> names = new TreeSet<>();
			switch (action) {
			case "function": names.addAll(ctx.console.getFunctions().keySet()); break;
			case "variable": names.addAll(ctx.getVariables().keySet()); break;
			case "export": names.addAll(ctx.getEnvironmentVariables().keySet()); break;
			case "alias": names.addAll(ctx.console.getAliases().keySet()); break;
			case "builtin": names.addAll(Console.commands.keySet()); break;
			case "keyword": names.addAll(List.of(KEYWORDS)); break;
			case "command":
				names.addAll(ctx.console.getAliases().keySet());
				names.addAll(ctx.console.getFunctions().keySet());
				names.addAll(Console.commands.keySet());
				names.addAll(List.of(KEYWORDS));
				names.addAll(pathCommands(ctx, word));
				break;
			case "file":
			case "directory":
				for(String path : Glob.expand(escape(word)+"*", ctx)) {
					if( action.equals("file") || ctx.getFileSource(path).isDirectory()) {
						names.add(path);
					}
				}
				break;
			default:
				ctx.stderr.println("compgen: "+action+": invalid action name");
				return 2;
			}
			for(String n : names) {
				if( n.startsWith(word) && !n.startsWith("$")) {
					found.add(n);
				}
			}
		}
		for(String f : found) {
			ctx.stdout.println(prefix+f+suffix);
		}
		return found.isEmpty() ? 1 : 0;
	}

	/** word as a pattern that matches only itself */
	private static String escape(String word) {
		StringBuilder ret = new StringBuilder();
		for(char c : word.toCharArray()) {
			if( "*?[]\\".indexOf(c) >= 0 ) {
				ret.append('\\');
			}
			ret.append(c);
		}
		return ret.toString();
	}

	/** the programs on $PATH whose names start with word */
	private static Set<String> pathCommands(ShellContext ctx, String word) {
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
}
