package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Shopt extends ShellCommand{
	static String name = "shopt";
	static String help = "shopt [-s|-u] [-pq] [optname ...]\n"
			+ "	Set (-s) or unset (-u) shell options, or show them (-p as shopt commands). -q only sets\n"
			+ "	the exit status: 0 if every optname is set. The options are recorded; which of them\n"
			+ "	change what the shell does is listed in help for each feature."
			;

	public Shopt() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Boolean set = null;
		boolean print = false;
		boolean quiet = false;
		boolean setOptions = false;
		java.util.List<String> names = new java.util.ArrayList<>();
		for(int idx = 0; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( text.startsWith("-") && text.length() > 1 && names.isEmpty()) {
				for(char c : text.substring(1).toCharArray()) {
					switch (c) {
					case 's': set = true; break;
					case 'u': set = false; break;
					case 'p': print = true; break;
					case 'q': quiet = true; break;
					// set -o's options
					case 'o': setOptions = true; break;
					default:
						ctx.error("shopt: -"+c+": invalid option");
						ctx.stderr.println("shopt: usage: shopt [-pqsu] [-o] [optname ...]");
						return 2;
					}
				}
				continue;
			}
			names.add(text);
		}
		// set -o's options, or shopt's
		Map<String,Boolean> options = new TreeMap<>();
		if( setOptions ) {
			for(Console.Option o : Set.listed()) {
				options.put(o.longName, ctx.console.isOptionEnabled(o));
			}
		} else {
			options.putAll(ctx.console.getShellOptions());
		}
		String kind = setOptions ? "option name" : "shell option name";
		int ret = 0;
		if( set != null ) {
			if( names.isEmpty()) {
				// shopt -s: the ones that are on
				print(ctx, options, set, setOptions, print, quiet);
				return 0;
			}
			for(String n : names) {
				if( !options.containsKey(n)) {
					ctx.error("shopt: "+n+": invalid "+kind);
					ret = 1;
					continue;
				}
				if( setOptions ) {
					ctx.console.setOption(Console.Option.find(n), set);
				} else {
					ctx.console.getShellOptions().put(n, set);
				}
			}
			return ret;
		}
		// all of them in name order; the names given in the order given
		Map<String,Boolean> show = names.isEmpty() ? new TreeMap<>() : new java.util.LinkedHashMap<>();
		if( names.isEmpty()) {
			show.putAll(options);
		} else {
			for(String n : names) {
				if( !options.containsKey(n)) {
					ctx.error("shopt: "+n+": invalid "+kind);
					ret = 1;
				} else {
					show.put(n, options.get(n));
					if( !options.get(n)) {
						ret = 1;
					}
				}
			}
		}
		if( !quiet ) {
			print(ctx, show, null, setOptions, print, false);
		}
		return ret;
	}

	/** the options (only those on or off: which), as shopt or set -o shows them */
	private static void print(ShellContext ctx, Map<String,Boolean> options, Boolean which, boolean setOptions, boolean print, boolean quiet) {
		if( quiet ) {
			return;
		}
		for(Map.Entry<String,Boolean> e : options.entrySet()) {
			if( which != null && e.getValue() != which ) {
				continue;
			}
			if( print ) {
				ctx.stdout.println(setOptions ? "set "+(e.getValue() ? "-o " : "+o ")+e.getKey() : "shopt "+(e.getValue() ? "-s " : "-u ")+e.getKey());
			} else {
				ctx.stdout.printf("%-20s\t%s\n", e.getKey(), e.getValue() ? "on" : "off");
			}
		}
	}
}
