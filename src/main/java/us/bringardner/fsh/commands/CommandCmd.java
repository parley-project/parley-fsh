package us.bringardner.fsh.commands;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.exec.Programs;

/**
 * command [-v|-V] name [args]: run name as a builtin or program, not a function.
 */
public class CommandCmd extends ShellCommand{
	static String name = "command";
	static String help = "command [-v|-V] name [args]\n"
			+ "	Run name, skipping functions. -v prints how name would be run (its path, or its name for a\n"
			+ "	builtin or function), -V says it in words."
			;

	public CommandCmd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int idx = 0;
		String mode = null;
		while( idx < args.length ) {
			String a = ""+args[idx].getValue(ctx);
			if( a.equals("--")) {
				idx++;
				break;
			} else if( a.matches("-[pvV]+")) {
				if( a.contains("p") && ctx.console.restricted ) {
					// (a restricted shell: no -p)
					ctx.error("command: -p: restricted");
					return 1;
				}
				// (-pv, -Vp ...)
				if( a.contains("V")) {
					mode = "-V";
				} else if( a.contains("v")) {
					mode = "-v";
				}
				idx++;
			} else if( a.startsWith("-") && a.length() > 1 ) {
				char bad = a.substring(1).chars().filter(c -> "pvV".indexOf(c) < 0).mapToObj(c -> (char) c).findFirst().orElse('?');
				ctx.error("command: -"+bad+": invalid option");
				ctx.stderr.println("command: usage: command [-pVv] command [arg ...]");
				return 2;
			} else {
				break;
			}
		}
		if( idx >= args.length ) {
			return 0;
		}
		String n = ""+args[idx].getValue(ctx);
		if( mode != null ) {
			// what n is, as type says it (-V), or briefly (-v)
			List<String[]> ways = Type.describe(ctx, n, false, false, false);
			if( ways.isEmpty()) {
				if( mode.equals("-V")) {
					ctx.error("command: "+n+": not found");
				}
				return 1;
			}
			String [] w = ways.get(0);
			String out = mode.equals("-V") ? w[1]
					: w[0].equals("alias") ? "alias "+n+"='"+String.valueOf(ctx.console.getAlias(n)).replace("'", "'\\''")+"'"
					: w[0].equals("file") ? w[2] : n;
			ctx.stdout.println(out);
			return 0;
		}
		Constructor<? extends ShellCommand> con = ctx.console.builtin(n);
		if( con != null ) {
			// (command makes a special builtin's failure not end the shell)
			ctx.viaCommand++;
			try {
				return Builtin.run(con, Arrays.copyOfRange(args, idx+1, args.length), ctx);
			} finally {
				ctx.viaCommand--;
			}
		}
		// a program
		List<String> cmd = new ArrayList<>();
		for(int i = idx; i < args.length; i++) {
			cmd.add(""+args[i].getValue(ctx));
		}
		FileSource file = Programs.which(n, ctx);
		if( file != null ) {
			cmd.set(0, file.getAbsolutePath());
		}
		return Programs.execute(cmd, ctx);
	}
}
