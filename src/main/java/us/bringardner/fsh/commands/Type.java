package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellFunction;

public class Type extends ShellCommand{
	static String name = "type";
	static String help = "type [-afptP] name [name ...]\n"
			+ "	Say how each name would be run: alias, keyword, function, builtin or file.\n"
			+ "	-t prints only that word; -p prints the file's path (if it is a file), -P looks on PATH\n"
			+ "	whatever the name is; -a prints every way, -f leaves out functions. Exit status 1 if a\n"
			+ "	name is not found."
			;

	static final String USAGE = "type: usage: type [-afptP] name [name ...]";

	/** builtins the shell runs itself (not commands of their own) */
	private static final Set<String> SHELL_BUILTINS = Set.of("break", "continue", "declare", "typeset", "local", ".");

	/** posix's special builtins */
	private static final Set<String> SPECIAL = Set.of("break", ":", ".", "continue", "eval", "exec", "exit", "export",
			"readonly", "return", "set", "shift", "times", "trap", "unset");

	static final Set<String> KEYWORDS = Set.of("if", "then", "else", "elif", "fi", "case", "esac", "for",
			"select", "while", "until", "do", "done", "in", "function", "time", "{", "}", "!", "[[", "]]", "coproc");

	public Type() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean terse = false, path = false, forcePath = false, all = false, noFunctions = false;
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
			for (int k = 1; k < a.length(); k++) {
				switch (a.charAt(k)) {
				case 't': terse = true; break;
				case 'p': path = true; break;
				case 'P': forcePath = true; break;
				case 'a': all = true; break;
				case 'f': noFunctions = true; break;
				default:
					ctx.error("type: -"+a.charAt(k)+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
			}
		}
		int ret = 0;
		for(; idx < args.length; idx++) {
			String n = ""+args[idx].getValue(ctx);
			List<String[]> ways = describe(ctx, n, all, noFunctions, forcePath);
			if( ways.isEmpty()) {
				if( !terse && !path && !forcePath ) {
					ctx.error("type: "+n+": not found");
				}
				ret = 1;
				continue;
			}
			for(String [] w : ways) {
				if( terse ) {
					ctx.stdout.println(w[0]);
				} else if( path || forcePath ) {
					if( w[0].equals("file")) {
						ctx.stdout.println(w[2]);
					}
				} else {
					ctx.stdout.println(w[1]);
				}
			}
		}
		return ret;
	}

	/**
	 * How name would be run: {kind, what type says, the path for a file}, the first way only
	 * unless all. noFunctions: type -f; searchPath: only as a program on PATH (type -P).
	 */
	static List<String[]> describe(ShellContext ctx, String n, boolean all, boolean noFunctions, boolean searchPath) throws IOException {
		List<String[]> ret = new ArrayList<>();
		if( !searchPath ) {
			Object alias = ctx.console.getAlias(n);
			if( alias != null ) {
				ret.add(new String[] {"alias", n+" is aliased to `"+alias+"'", null});
				if( !all ) {
					return ret;
				}
			}
			if( KEYWORDS.contains(n)) {
				ret.add(new String[] {"keyword", n+" is a shell keyword", null});
				if( !all ) {
					return ret;
				}
			}
			ShellFunction function = noFunctions ? null : ctx.getFunction(n);
			if( function != null ) {
				ret.add(new String[] {"function", n+" is a function\n"+function.declaration(), null});
				if( !all ) {
					return ret;
				}
			}
			if( ctx.console.builtin(n) != null && !n.startsWith("__") || SHELL_BUILTINS.contains(n)) {
				// (posix mode names its special builtins so)
				boolean special = ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix) && SPECIAL.contains(n);
				ret.add(new String[] {"builtin", n+" is a "+(special ? "special " : "")+"shell builtin", null});
				if( !all ) {
					return ret;
				}
			}
		}
		if( !n.contains("/")) {
			Object [] known = searchPath && !all ? null : ctx.console.hashTable.get(n);
			if( known != null && !all ) {
				// as bash: it is looked up (and counted) through the table
				((int []) known[1])[0]++;
				ret.add(new String[] {"file", n+" is hashed ("+known[0]+")", ""+known[0]});
				return ret;
			}
		}
		for(String f : files(ctx, n, all)) {
			ret.add(new String[] {"file", n+" is "+f, f});
		}
		return ret;
	}

	/** name as a program: its path as bash says it (PATH's directory, as written, then /name), or every one (all) */
	private static List<String> files(ShellContext ctx, String n, boolean all) throws IOException {
		List<String> ret = new ArrayList<>();
		if( n.contains("/")) {
			FileSource f = us.bringardner.fsh.exec.Programs.which(n, ctx);
			if( f != null ) {
				ret.add(n);
			}
			return ret;
		}
		Object p = ctx.getVariable("PATH");
		for(String dir : (p == null ? "" : p.toString()).split(":", -1)) {
			String d = dir.isEmpty() ? "." : dir;
			FileSource f = ctx.getFileSource(d+"/"+n);
			if( f.exists() && f.isFile() && f.canExecute()) {
				ret.add(d.endsWith("/") ? d+n : d+"/"+n);
				if( !all ) {
					break;
				}
			}
		}
		return ret;
	}
}
