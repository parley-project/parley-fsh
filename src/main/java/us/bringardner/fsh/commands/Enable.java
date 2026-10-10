package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * enable: turn builtins off (-n) and on again; with no names, list them.
 */
public class Enable extends ShellCommand{
	static String name = "enable";
	static String help = "enable [-a] [-dnps] [-f filename] [name ...]\n"
			+ "	Turn on the builtins named, or turn them off with -n (the name then runs a program\n"
			+ "	of that name). With no names, print the enabled builtins (-n: the disabled ones,\n"
			+ "	-a: all). -f loads the names from filename as bash does (looked for on\n"
			+ "	BASH_LOADABLES_PATH without a /); fsh cannot run bash's loadable builtins, so this\n"
			+ "	reports why the file cannot be loaded, or that it cannot be used."
			;

	/** posix's special builtins (and source), as enable -s lists them */
	private static final java.util.Set<String> SPECIAL = java.util.Set.of(".", ":", "break", "continue", "eval", "exec",
			"exit", "export", "readonly", "return", "set", "shift", "source", "times", "trap", "unset");

	static final String USAGE = "enable: usage: enable [-a] [-dnps] [-f filename] [name ...]";

	public Enable() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean disable = false;
		boolean all = false;
		boolean special = false;
		boolean dynamic = false;
		String file = null;
		List<String> names = new ArrayList<>();
		for(int idx = 0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.startsWith("-") && a.length() > 1 && names.isEmpty()) {
				char [] cs = a.substring(1).toCharArray();
				for(int k = 0; k < cs.length; k++) {
					char c = cs[k];
					switch (c) {
					case 'n': disable = true; break;
					case 'a': all = true; break;
					case 'p': break;
					case 's': special = true; break;
					case 'd': dynamic = true; break;
					case 'f':
						// -f file, -ffile
						if( k+1 < cs.length ) {
							file = a.substring(k+2);
						} else if( idx+1 < args.length ) {
							file = ""+args[++idx].getValue(ctx);
						} else {
							ctx.error("enable: -f: option requires an argument");
							ctx.stderr.println(USAGE);
							return 2;
						}
						k = cs.length;
						break;
					default:
						ctx.error("enable: -"+c+": invalid option");
						ctx.stderr.println(USAGE);
						return 2;
					}
				}
			} else {
				names.add(a);
			}
		}
		if( file != null && !names.isEmpty()) {
			return load(ctx, file, names);
		}
		TreeSet<String> known = new TreeSet<>(Console.commands.keySet());
		// (the ones the shell runs itself)
		known.addAll(List.of("break", "continue", "declare", "typeset", "local", ".", "["));
		if( names.isEmpty()) {
			for(String n : known) {
				if( n.startsWith("__") || special && !SPECIAL.contains(n)) {
					continue;
				}
				boolean off = ctx.console.disabledBuiltins.contains(n.equals("[") ? Console.BRACKET_TEST : n);
				if( all || off == disable ) {
					ctx.stdout.println("enable "+(off ? "-n " : "")+n);
				}
			}
			return 0;
		}
		int ret = 0;
		for(String n : names) {
			if( !known.contains(n)) {
				ctx.error("enable: "+n+": not a shell builtin");
				ret = 1;
			} else if( dynamic ) {
				// (fsh loads no builtins)
				ctx.error("enable: "+n+": not dynamically loaded");
				ret = 1;
			} else if( disable ) {
				ctx.console.disabledBuiltins.add(n.equals("[") ? Console.BRACKET_TEST : n);
			} else {
				ctx.console.disabledBuiltins.remove(n.equals("[") ? Console.BRACKET_TEST : n);
			}
		}
		return ret;
	}

	/**
	 * enable -f file names: as bash loads them (dlopen, then each name_struct), in fsh's helper,
	 * with bash's errors. A file that does load still cannot be used: its builtins are bash's C code.
	 */
	private static int load(ShellContext ctx, String file, List<String> names) throws IOException {
		String path = file;
		if( !file.contains("/")) {
			// on BASH_LOADABLES_PATH, as bash looks (not found: no dlopen, and its error is "(null)")
			Object lp = ctx.getVariable("BASH_LOADABLES_PATH");
			if( lp != null ) {
				path = null;
				for(String d : String.valueOf(lp).split(":", -1)) {
					String candidate = (d.isEmpty() ? "." : d)+"/"+file;
					if( ctx.getFileSource(candidate).exists()) {
						path = candidate;
						break;
					}
				}
				if( path == null ) {
					ctx.error("enable: cannot open shared object "+file+": (null)");
					return 1;
				}
			}
		}
		String helper = us.bringardner.fsh.NativeKeyboard.helper();
		String answer = null;
		if( helper != null ) {
			List<String> cmd = new ArrayList<>(List.of(helper, "-l", path));
			cmd.addAll(names);
			ProcessBuilder b = new ProcessBuilder(cmd).redirectErrorStream(true);
			us.bringardner.parley.files.FileSource dir = ctx.console.getCurrentDirectory();
			if( dir instanceof us.bringardner.parley.files.fileproxy.FileProxy ) {
				b.directory(((us.bringardner.parley.files.fileproxy.FileProxy) dir).getTarget());
			}
			Process p = b.start();
			try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
				answer = r.readLine();
			}
			try {
				p.waitFor();
			} catch (InterruptedException e) {
			}
		}
		if( answer == null ) {
			ctx.error("enable: cannot open shared object "+file+": (null)");
		} else if( answer.startsWith("O")) {
			ctx.error("enable: cannot open shared object "+file+": "+answer.substring(1));
		} else if( answer.startsWith("S")) {
			int tab = answer.indexOf('\t');
			ctx.error("enable: cannot find "+answer.substring(1, tab)+"_struct in shared object "+file+": "+answer.substring(tab+1));
		} else {
			// (bash's builtins are C code for bash: there is nothing fsh can run)
			ctx.error("enable: "+names.get(0)+": cannot load bash's loadable builtins into fsh ("+file+")");
		}
		return 1;
	}
}
