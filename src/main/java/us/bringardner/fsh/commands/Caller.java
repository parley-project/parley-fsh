package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Caller extends ShellCommand{
	static String name = "caller";
	static String help = "caller [n]\n"
			+ "	In a function, print where it was called from: with n, \"line function file\" for the call n\n"
			+ "	frames up (0 is the current function's caller); without, \"line file\". Status 1 outside a\n"
			+ "	function or past the outermost call."
			;

	public Caller() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		java.util.List<Object> lineno = list(ctx.getVariable("BASH_LINENO"));
		java.util.List<Object> source = list(ctx.getVariable("BASH_SOURCE"));
		java.util.List<Object> funcname = list(ctx.getVariable("FUNCNAME"));
		if( lineno.isEmpty() || source.isEmpty()) {
			return 1;
		}
		int first = 0;
		if( args.length > 0 ) {
			String a = ""+args[0].getValue(ctx);
			if( a.equals("--")) {
				first = 1;
			} else if( a.startsWith("-") && a.length() > 1 && !a.substring(1).matches("[0-9]+")) {
				ctx.error("caller: "+a.substring(0, 2)+": invalid option");
				ctx.stderr.println("caller: usage: caller [expr]");
				return 2;
			}
		}
		if( args.length <= first ) {
			// line file, of the call to the running function (NULL if there is none)
			Object l = lineno.size() > 0 ? lineno.get(0) : null;
			Object f = source.size() > 1 ? source.get(1) : null;
			ctx.stdout.println((l == null ? "NULL" : l)+" "+(f == null ? "NULL" : f));
			return 0;
		}
		if( funcname.isEmpty()) {
			return 1;
		}
		String a = (""+args[first].getValue(ctx)).trim();
		if( !a.matches("[-+]?[0-9]+")) {
			ctx.error("caller: "+a+": invalid number");
			ctx.stderr.println("caller: usage: caller [expr]");
			return 2;
		}
		long n = Long.parseLong(a.startsWith("+") ? a.substring(1) : a);
		if( n < 0 || n+1 >= Integer.MAX_VALUE ) {
			return 1;
		}
		Object l = n < lineno.size() ? lineno.get((int) n) : null;
		Object f = n+1 < source.size() ? source.get((int) n+1) : null;
		Object fn = n+1 < funcname.size() ? funcname.get((int) n+1) : null;
		if( l == null || f == null || fn == null ) {
			return 1;
		}
		ctx.stdout.println(l+" "+fn+" "+f);
		return 0;
	}

	private static java.util.List<Object> list(Object v) {
		java.util.List<Object> ret = new java.util.ArrayList<>();
		if( v instanceof java.util.List<?> l ) {
			ret.addAll(l);
		} else if( v != null ) {
			ret.add(v);
		}
		return ret;
	}
}
