package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** env and printenv, modeled on the GNU commands. */
public class Env extends ShellCommand{
	static String help = "env [-0] [-u NAME] [NAME=VALUE]... [COMMAND [ARG]...]\n"
			+ "\tWith no COMMAND print the environment, one NAME=VALUE a line (with NAME=VALUE operands added to it).\n"
			+ "\tWith a COMMAND run it with the NAME=VALUE operands in its environment.\n"
			+ "\t-0, --null        end each line with a NUL, not a newline\n"
			+ "\t-u, --unset=NAME  remove NAME from the environment first";

	private final boolean printenv;

	public Env() {
		this(false);
	}

	Env(boolean printenv) {
		super(printenv ? "printenv" : "env", printenv ? "printenv [-0] [NAME...]\n"
				+ "\tPrint the value of each environment variable NAME, or with none the whole environment.\n"
				+ "\tExit status 1 if a NAME isn't set.\n"
				+ "\t-0, --null   end each line with a NUL, not a newline" : help);
		this.printenv = printenv;
	}

	/** quote a word for the shell */
	static String quote(String w) {
		if( w.matches("[A-Za-z0-9_@%+=:,./-]+") && !w.startsWith("-")) {
			// (nothing in it is special to the shell)
			return w;
		}
		return "'"+w.replace("'", "'\\''")+"'";
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Map<String, String> env = new TreeMap<>();
		for (Map.Entry<String, Object> e : ctx.getEnvironmentVariables().entrySet()) {
			env.put(e.getKey(), String.valueOf(e.getValue()));
		}
		String end;
		if( printenv ) {
			Args a = Args.parse(ctx, args, "0", "", false, "null:0");
			if( a.error != null ) {
				return a.fail(ctx, getName());
			}
			end = a.has('0') ? "\0" : "\n";
			StringBuilder out = new StringBuilder();
			int ret = 0;
			if( a.operands.isEmpty()) {
				for (Map.Entry<String, String> e : env.entrySet()) {
					out.append(e.getKey()).append('=').append(e.getValue()).append(end);
				}
			}
			for (String n : a.operands) {
				String v = env.get(n);
				if( v == null ) {
					ret = 1;
				} else {
					out.append(v).append(end);
				}
			}
			ctx.stdout.print(out);
			ctx.stdout.flush();
			return ret;
		}

		// env: options, then NAME=VALUE operands, then the command
		boolean nul = false;
		List<String> unset = new ArrayList<>();
		List<String> words = new ArrayList<>();
		boolean optionsOver = false;
		for (int idx = 0; idx < args.length; idx++) {
			String arg = ""+args[idx].getValue(ctx);
			if( !optionsOver && (arg.equals("-0") || arg.equals("--null"))) {
				nul = true;
			} else if( !optionsOver && (arg.equals("-u") || arg.equals("--unset"))) {
				if( idx+1 >= args.length ) {
					ctx.stderr.println("env: option requires an argument -- 'u'");
					return 125;
				}
				unset.add(""+args[++idx].getValue(ctx));
			} else if( !optionsOver && arg.startsWith("--unset=")) {
				unset.add(arg.substring(8));
			} else if( !optionsOver && arg.equals("-i")) {
				env.clear();
			} else if( !optionsOver && arg.startsWith("-") && arg.length() > 1 && !arg.equals("--")) {
				ctx.stderr.println("env: invalid option -- '"+arg+"'");
				return 125;
			} else {
				optionsOver = true;
				words.add(arg);
			}
		}
		for (String u : unset) {
			env.remove(u);
		}
		List<String> assignments = new ArrayList<>();
		Iterator<String> it = words.iterator();
		while( it.hasNext()) {
			String w = it.next();
			int eq = w.indexOf('=');
			if( eq > 0 && w.substring(0, eq).matches("[A-Za-z_][A-Za-z0-9_]*")) {
				env.put(w.substring(0, eq), w.substring(eq+1));
				assignments.add(w);
				it.remove();
			} else {
				break;
			}
		}
		if( words.isEmpty()) {
			end = nul ? "\0" : "\n";
			StringBuilder out = new StringBuilder();
			for (Map.Entry<String, String> e : env.entrySet()) {
				out.append(e.getKey()).append('=').append(e.getValue()).append(end);
			}
			ctx.stdout.print(out);
			ctx.stdout.flush();
			return 0;
		}
		StringBuilder script = new StringBuilder();
		for (String as : assignments) {
			int eq = as.indexOf('=');
			script.append(as, 0, eq+1).append(quote(as.substring(eq+1))).append(' ');
		}
		for (int i = 0; i < words.size(); i++) {
			script.append(i == 0 ? words.get(i) : quote(words.get(i))).append(' ');
		}
		return ctx.console.executeScript(script.toString().trim());
	}
}
