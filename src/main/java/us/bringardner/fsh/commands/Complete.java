package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import us.bringardner.fsh.Completion;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * complete [-abcdefgjksuv] [-o option] [-A action] [-W words] [-F function] [-C command]
 * [-P prefix] [-S suffix] [-X filter] [-DEI] [name ...]; complete -p [name ...]; complete -r
 * [name ...]: how Tab completes the words of a command, as bash's complete.
 */
public class Complete extends ShellCommand {

	static String name = "complete";
	static String help = "complete [-abcdefgjksuv] [-pr] [-DEI] [-o option] [-A action] [-F function] [-C command] [-W wordlist] [-P prefix] [-S suffix] [-X filterpat] [name ...]\n"
			+ "How Tab completes each name's arguments. -p prints them (all with no name), -r removes them.\n"
			+ "-D: commands with none of their own, -E: an empty line, -I: the command's name.";

	public Complete() {
		super(name, help);
	}

	/** what parse read: the spec, the names after it, and -p -r -D -E -I */
	public static final class Parsed {
		public final Completion.Spec spec = new Completion.Spec();
		public final List<String> names = new ArrayList<>();
		public boolean print, remove, any;
		/** compgen -V name: the words go in the array name */
		public String varName;
		public String special;
		public String word;
	}

	/**
	 * Read complete's (and compgen's) options from words.
	 * @throws IllegalArgumentException with bash's message for a bad one
	 */
	public static String usage(String command) {
		return command.equals("compgen")
				? "compgen: usage: compgen [-V varname] [-abcdefgjksuv] [-o option] [-A action] [-G globpat] [-W wordlist] [-F function] [-C command] [-X filterpat] [-P prefix] [-S suffix] [word]"
				: "complete: usage: complete [-abcdefgjksuv] [-pr] [-DEI] [-o option] [-A action] [-G globpat] [-W wordlist] [-F function] [-C command] [-X filterpat] [-P prefix] [-S suffix] [name ...]";
	}

	public static Parsed parse(String command, List<String> words) {
		Parsed ret = new Parsed();
		Completion.Spec s = ret.spec;
		int i = 0;
		for(; i < words.size(); i++) {
			String a = words.get(i);
			if( a.equals("--")) {
				i++;
				break;
			}
			if( !a.startsWith("-") || a.length() < 2 ) {
				break;
			}
			for(int c = 1; c < a.length(); c++) {
				char op = a.charAt(c);
				String arg = null;
				if( "oAWFCPSXG".indexOf(op) >= 0 ) {
					if( c+1 < a.length()) {
						arg = a.substring(c+1);
					} else if( i+1 < words.size()) {
						arg = words.get(++i);
					} else {
						throw new IllegalArgumentException(command+": -"+op+": option requires an argument");
					}
					c = a.length();
				}
				ret.any |= op != 'p' && op != 'r';
				switch (op) {
				case 'o': s.options.add(arg); break;
				case 'A':
					if( !List.of("alias", "arrayvar", "binding", "builtin", "command", "directory", "disabled", "enabled", "export",
							"file", "function", "helptopic", "hostname", "group", "job", "keyword", "running", "service", "setopt",
							"shopt", "signal", "stopped", "user", "variable").contains(arg)) {
						throw new IllegalArgumentException(command+": "+arg+": invalid action name");
					}
					s.actions.add(arg);
					break;
				case 'W': s.wordList = arg; break;
				case 'F': s.function = arg; break;
				case 'C': s.command = arg; break;
				case 'P': s.prefix = arg; break;
				case 'S': s.suffix = arg; break;
				case 'X': s.filter = arg; break;
				case 'G': break;
				case 'a': s.actions.add("alias"); break;
				case 'b': s.actions.add("builtin"); break;
				case 'c': s.actions.add("command"); break;
				case 'd': s.actions.add("directory"); break;
				case 'e': s.actions.add("export"); break;
				case 'f': s.actions.add("file"); break;
				case 'g': s.actions.add("group"); break;
				case 'j': s.actions.add("job"); break;
				case 'k': s.actions.add("keyword"); break;
				case 's': s.actions.add("service"); break;
				case 'u': s.actions.add("user"); break;
				case 'v': s.actions.add("variable"); break;
				case 'p': ret.print = true; break;
				case 'r': ret.remove = true; break;
				case 'D': ret.special = "-D"; break;
				case 'E': ret.special = "-E"; break;
				case 'I': ret.special = "-I"; break;
				case 'V':
					if( command.equals("compgen")) {
						if( c+1 < a.length()) {
							ret.varName = a.substring(c+1);
						} else if( i+1 < words.size()) {
							ret.varName = words.get(++i);
						} else {
							throw new IllegalArgumentException(command+": -V: option requires an argument");
						}
						c = a.length();
						break;
					}
					throw new IllegalArgumentException(command+": -"+op+": invalid option\n"+usage(command));
				default:
					throw new IllegalArgumentException(command+": -"+op+": invalid option\n"+usage(command));
				}
				if( command.equals("compgen") && "prDEI".indexOf(op) >= 0 ) {
					throw new IllegalArgumentException(command+": -"+op+": invalid option\n"+usage(command));
				}
			}
		}
		for(; i < words.size(); i++) {
			ret.names.add(words.get(i));
		}
		for(String o : s.options) {
			if( !List.of("bashdefault", "default", "dirnames", "filenames", "noquote", "nosort", "nospace", "plusdirs", "fullquote").contains(o)) {
				throw new IllegalArgumentException(command+": "+o+": invalid option name");
			}
		}
		return ret;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> words = new ArrayList<>();
		for(int i = 0; i < args.length; i++) {
			words.add(""+args[i].getValue(ctx));
		}
		Parsed p;
		try {
			p = parse(name, words);
		} catch (IllegalArgumentException e) {
			say(ctx, e.getMessage());
			return 2;
		}
		Map<String, Completion.Spec> specs = ctx.console.getCompletions();
		if( p.any && p.names.isEmpty() && p.special == null && !p.print && !p.remove ) {
			// (a specification for no command: the usage, as bash's)
			ctx.stderr.println(usage(name));
			return 2;
		}
		List<String> names = new ArrayList<>(p.names);
		if( p.special != null ) {
			names.add(p.special);
		}
		if( p.remove ) {
			if( names.isEmpty()) {
				specs.clear();
			}
			int ret = 0;
			for(String n : names) {
				if( specs.remove(n) == null ) {
					ctx.error(name+": "+n+": no completion specification");
					ret = 1;
				}
			}
			return ret;
		}
		if( p.print || (!p.any && names.isEmpty()) || (!p.any && !names.isEmpty())) {
			int ret = 0;
			List<String> show = names.isEmpty() ? new ArrayList<>(specs.keySet()) : names;
			for(String n : show) {
				Completion.Spec s = specs.get(n);
				if( s == null ) {
					ctx.error(name+": "+n+": no completion specification");
					ret = 1;
				} else {
					String shown = n.startsWith("-") ? s.toCommand("").stripTrailing().replaceFirst("^complete", "complete "+n) : s.toCommand(quoteName(n));
					ctx.stdout.println(shown);
				}
			}
			return ret;
		}
		for(String n : names) {
			specs.put(n, p.spec);
		}
		return 0;
	}

	/** an error, and a second line (the usage) as it is */
	public static void say(ShellContext ctx, String message) {
		int nl = message.indexOf('\n');
		ctx.error(nl < 0 ? message : message.substring(0, nl));
		if( nl >= 0 ) {
			ctx.stderr.println(message.substring(nl+1));
		}
	}

	private static String quoteName(String n) {
		return n.matches("[A-Za-z0-9_./:+-]+") ? n : "'"+n.replace("'", "'\\''")+"'";
	}
}
