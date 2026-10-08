package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

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
		Map<String,Boolean> options = ctx.console.getShellOptions();
		java.util.List<String> names = new java.util.ArrayList<>();
		for(int idx = 0; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			switch (text) {
			case "-s": set = true; break;
			case "-u": set = false; break;
			case "-p": print = true; break;
			case "-q": quiet = true; break;
			default:
				if( text.startsWith("-")) {
					ctx.error("shopt: "+text+": invalid option");
					return 2;
				}
				names.add(text);
			}
		}
		int ret = 0;
		if( set != null ) {
			for(String n : names) {
				if( !options.containsKey(n)) {
					ctx.error("shopt: "+n+": invalid shell option name");
					ret = 1;
					continue;
				}
				options.put(n, set);
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
					ctx.error("shopt: "+n+": invalid shell option name");
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
			for(Map.Entry<String,Boolean> e : show.entrySet()) {
				if( print ) {
					ctx.stdout.println("shopt "+(e.getValue() ? "-s " : "-u ")+e.getKey());
				} else {
					ctx.stdout.printf("%-20s\t%s\n", e.getKey(), e.getValue() ? "on" : "off");
				}
			}
		}
		return ret;
	}
}
