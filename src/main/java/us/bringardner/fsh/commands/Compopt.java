package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import us.bringardner.fsh.Completion;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * compopt [-o|+o option] [-DEI] [name ...]: turn the completion options of each name on (-o) or
 * off (+o), or show them, as bash's compopt.
 */
public class Compopt extends ShellCommand {

	static String name = "compopt";
	static String help = "compopt [-o|+o option] [-DEI] [name ...]\n"
			+ "	Turn completion options of each name on (-o) or off (+o); with no option, show them.";

	private static final List<String> OPTIONS = List.of("bashdefault", "default", "dirnames", "filenames", "fullquote",
			"noquote", "nosort", "nospace", "plusdirs");
	private static final String USAGE = "compopt: usage: compopt [-o|+o option] [-DEI] [name ...]";

	public Compopt() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> on = new ArrayList<>();
		List<String> off = new ArrayList<>();
		List<String> names = new ArrayList<>();
		int i = 0;
		for(; i < args.length; i++) {
			String w = ""+args[i].getValue(ctx);
			if( w.equals("--")) {
				i++;
				break;
			}
			if( (w.startsWith("-") || w.startsWith("+")) && w.length() > 1 ) {
				boolean plus = w.startsWith("+");
				for (int k = 1; k < w.length(); k++) {
					char c = w.charAt(k);
					if( c == 'o' ) {
						String o;
						if( k+1 < w.length()) {
							o = w.substring(k+1);
						} else if( i+1 < args.length ) {
							o = ""+args[++i].getValue(ctx);
						} else {
							ctx.error(name+": -o: option requires an argument");
							ctx.stderr.println(USAGE);
							return 2;
						}
						if( !OPTIONS.contains(o)) {
							ctx.error(name+": "+o+": invalid option name");
							return 2;
						}
						(plus ? off : on).add(o);
						break;
					} else if( !plus && (c == 'D' || c == 'E' || c == 'I')) {
						names.add("-"+c);
					} else {
						ctx.error(name+": "+(plus ? "+" : "-")+c+": invalid option");
						ctx.stderr.println(USAGE);
						return 2;
					}
				}
				continue;
			}
			break;
		}
		for(; i < args.length; i++) {
			names.add(""+args[i].getValue(ctx));
		}
		if( names.isEmpty()) {
			// (only a completion function changes the completion running)
			ctx.error(name+": not currently executing completion function");
			return 1;
		}
		Map<String, Completion.Spec> specs = ctx.console.getCompletions();
		int ret = 0;
		for(String n : names) {
			Completion.Spec s = specs.get(n);
			if( s == null ) {
				ctx.error(name+": "+n+": no completion specification");
				ret = 1;
				continue;
			}
			if( on.isEmpty() && off.isEmpty()) {
				StringBuilder line = new StringBuilder("compopt ");
				for(String o : OPTIONS) {
					line.append(s.options.contains(o) ? "-o " : "+o ").append(o).append(' ');
				}
				ctx.stdout.println(line.append(n));
				continue;
			}
			s.options.addAll(on);
			s.options.removeAll(off);
		}
		return ret;
	}
}
