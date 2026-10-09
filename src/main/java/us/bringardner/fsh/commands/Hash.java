package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.Map;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * hash: the programs the shell remembers the paths of (it remembers each program it finds on
 * PATH), as bash's.
 */
public class Hash extends ShellCommand {
	static String name = "hash";
	static String help = "hash [-lr] [-p pathname] [-dt] [name ...]\n"
			+ "	Remember or display program locations. With no names, list the remembered programs and how\n"
			+ "	often each was used. -r forgets them all, -d forgets the names, -p pathname remembers name\n"
			+ "	as pathname, -t prints each name's path, -l prints them as hash commands.";

	static final String USAGE = "hash: usage: hash [-lr] [-p pathname] [-dt] [name ...]";

	public Hash() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean reset = false, delete = false, show = false, list = false;
		String pathname = null;
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
				char c = a.charAt(k);
				switch (c) {
				case 'r': reset = true; break;
				case 'd': delete = true; break;
				case 't': show = true; break;
				case 'l': list = true; break;
				case 'p':
					if( k+1 < a.length()) {
						pathname = a.substring(k+1);
					} else if( idx+1 < args.length ) {
						pathname = ""+args[++idx].getValue(ctx);
					} else {
						ctx.error("hash: -p: option requires an argument");
						ctx.stderr.println(USAGE);
						return 2;
					}
					k = a.length();
					break;
				default:
					ctx.error("hash: -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
			}
		}
		if( delete && idx >= args.length ) {
			ctx.error("hash: -d: option requires an argument");
			return 1;
		}
		if( !ctx.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Hashall)) {
			// set +o hashall
			ctx.error("hash: hashing disabled");
			return 1;
		}
		Map<String,Object []> table = ctx.console.hashTable;
		if( reset ) {
			table.clear();
		}
		if( idx >= args.length ) {
			if( reset ) {
				return 0;
			}
			if( table.isEmpty()) {
				if( !list ) {
					// (as bash, on standard output)
					ctx.stdout.println("hash: hash table empty");
				}
				return 0;
			}
			if( list ) {
				for(Map.Entry<String,Object []> e : table.entrySet()) {
					ctx.stdout.println("builtin hash -p "+e.getValue()[0]+" "+e.getKey());
				}
				return 0;
			}
			ctx.stdout.println("hits\tcommand");
			for(Object [] v : table.values()) {
				ctx.stdout.println(String.format("%4d\t%s", ((int []) v[1])[0], v[0]));
			}
			return 0;
		}
		int ret = 0;
		int names = args.length-idx;
		for(; idx < args.length; idx++) {
			String n = ""+args[idx].getValue(ctx);
			if( pathname != null ) {
				if( ctx.console.restricted && pathname.contains("/")) {
					// (a restricted shell: no path with a /)
					ctx.error("hash: "+pathname+": restricted");
					return 1;
				}
				if( ctx.console.restricted && us.bringardner.fsh.exec.Programs.which(pathname, ctx) == null ) {
					// (one it does not find in PATH)
					ctx.error("hash: "+pathname+": not found");
					ret = 1;
					continue;
				}
				if( ctx.getFileSource(pathname).isDirectory()) {
					ctx.error("hash: "+pathname+": Is a directory");
					ret = 1;
					continue;
				}
				table.put(n, new Object[] {pathname, new int[] {0}});
			} else if( delete ) {
				if( table.remove(n) == null ) {
					ctx.error("hash: "+n+": not found");
					ret = 1;
				}
			} else if( show || list ) {
				Object [] v = table.get(n);
				if( v == null ) {
					ctx.error("hash: "+n+": not found");
					ret = 1;
				} else if( list ) {
					ctx.stdout.println("builtin hash -p "+v[0]+" "+n);
				} else {
					ctx.stdout.println(names > 1 ? n+"\t"+v[0] : ""+v[0]);
				}
			} else if( n.contains("/")) {
				// (a path is not remembered)
			} else if( ctx.console.builtin(n) == null && ctx.getFunction(n) == null ) {
				FileSource file = us.bringardner.fsh.exec.Programs.which(n, ctx);
				if( file == null ) {
					ctx.error("hash: "+n+": not found");
					ret = 1;
				} else {
					table.put(n, new Object[] {file.getAbsolutePath(), new int[] {0}});
				}
			}
		}
		return ret;
	}
}
