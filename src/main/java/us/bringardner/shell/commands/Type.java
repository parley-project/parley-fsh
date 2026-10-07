package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.Set;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.Console;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.FunctionDefStatement;

public class Type extends ShellCommand{
	static String name = "type";
	static String help = "type [-t|-p|-P] name ...\n"
			+ "	Say how each name would be run: alias, keyword, function, builtin or file.\n"
			+ "	-t prints only that word; -p and -P print the file's path. Exit status 1 if a name is not found."
			;

	private static final Set<String> KEYWORDS = Set.of("if", "then", "else", "elif", "fi", "case", "esac", "for",
			"select", "while", "until", "do", "done", "in", "function", "time", "{", "}", "!", "[[", "]]");

	public Type() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean terse = false;
		boolean path = false;
		int ret = 0;
		for(int idx = 0; idx < args.length; idx++) {
			String n = ""+args[idx].getValue(ctx);
			switch (n) {
			case "-t": terse = true; continue;
			case "-p":
			case "-P": path = true; continue;
			default:
			}
			String kind = null;
			String text = null;
			Object alias = ctx.console.getAlias(n);
			FunctionDefStatement function = ctx.getFunction(n);
			if( !path && alias != null ) {
				kind = "alias";
				text = n+" is aliased to `"+alias+"'";
			} else if( !path && KEYWORDS.contains(n)) {
				kind = "keyword";
				text = n+" is a shell keyword";
			} else if( !path && function != null ) {
				kind = "function";
				text = n+" is a function\n"+function.getContext().getText();
			} else if( !path && Console.commands.containsKey(n) && !n.startsWith("__")) {
				kind = "builtin";
				text = n+" is a shell builtin";
			} else {
				FileSource file = us.bringardner.shell.antlr.statement.CommandStatement.which(n, ctx);
				if( file != null ) {
					kind = "file";
					text = path ? file.getAbsolutePath() : n+" is "+file.getAbsolutePath();
				}
			}
			if( kind == null ) {
				if( !terse && !path ) {
					ctx.stderr.println("type: "+n+": not found");
				}
				ret = 1;
			} else {
				ctx.stdout.println(terse ? kind : text);
			}
		}
		return ret;
	}
}
