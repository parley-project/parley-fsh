package us.bringardner.shell.commands;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.Console;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.CommandStatement;

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
			if( a.equals("-v") || a.equals("-V") || a.equals("-p")) {
				if( !a.equals("-p")) {
					mode = a;
				}
				idx++;
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
			FileSource file = Console.commands.containsKey(n) || ctx.getFunction(n) != null || ctx.console.getAlias(n) != null ? null : CommandStatement.which(n, ctx);
			String out = null;
			if( ctx.console.getAlias(n) != null ) {
				out = mode.equals("-v") ? "alias "+n+"='"+ctx.console.getAlias(n)+"'" : n+" is aliased to `"+ctx.console.getAlias(n)+"'";
			} else if( ctx.getFunction(n) != null ) {
				out = mode.equals("-v") ? n : n+" is a function";
			} else if( Console.commands.containsKey(n)) {
				out = mode.equals("-v") ? n : n+" is a shell builtin";
			} else if( file != null ) {
				out = mode.equals("-v") ? file.getAbsolutePath() : n+" is "+file.getAbsolutePath();
			}
			if( out == null ) {
				if( mode.equals("-V")) {
					ctx.stderr.println("command: "+n+": not found");
				}
				return 1;
			}
			ctx.stdout.println(out);
			return 0;
		}
		Constructor<? extends ShellCommand> con = Console.commands.get(n);
		if( con != null ) {
			return Builtin.run(con, Arrays.copyOfRange(args, idx+1, args.length), ctx);
		}
		// a program
		List<String> cmd = new ArrayList<>();
		for(int i = idx; i < args.length; i++) {
			cmd.add(""+args[i].getValue(ctx));
		}
		FileSource file = CommandStatement.which(n, ctx);
		if( file != null ) {
			cmd.set(0, file.getAbsolutePath());
		}
		return CommandStatement.execute(cmd, ctx);
	}
}
