package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** basename and dirname, modeled on the GNU commands. */
public class Basename extends ShellCommand{
	static String help = "basename NAME [SUFFIX]\n"
			+ "basename OPTION... NAME...\n"
			+ "\tPrint NAME without its leading directories and, if given, without SUFFIX.\n"
			+ "\t-a, --multiple    take several NAMEs\n"
			+ "\t-s, --suffix=SUF  remove SUF from each NAME (implies -a)\n"
			+ "\t-z, --zero        end each output line with a NUL, not a newline";

	private final boolean dir;

	public Basename() {
		this(false);
	}

	/** dirname is the other half of this class */
	Basename(boolean dirname) {
		super(dirname ? "dirname" : "basename", dirname ? "dirname [-z] NAME...\n"
				+ "\tPrint NAME without its last component: the directory it is in (. if it has none).\n"
				+ "\t-z, --zero   end each output line with a NUL, not a newline" : help);
		dir = dirname;
	}

	private static String stripSlashes(String s) {
		int end = s.length();
		while( end > 1 && (s.charAt(end-1) == '/' || s.charAt(end-1) == '\\')) {
			end--;
		}
		return s.substring(0, end);
	}

	static String base(String name) {
		String s = stripSlashes(name);
		if( s.length() == 1 && (s.equals("/") || s.equals("\\"))) {
			return s;
		}
		int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
		return s.substring(slash+1);
	}

	static String dirOf(String name) {
		String s = stripSlashes(name);
		if( s.isEmpty()) {
			return ".";
		}
		int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
		if( slash < 0 ) {
			return ".";
		}
		String d = stripSlashes(s.substring(0, slash+1));
		return d.isEmpty() ? s.substring(0, 1) : d;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = dir ? Args.parse(ctx, args, "z", "", false, "zero:z") : Args.parse(ctx, args, "az", "s", false, "multiple:a", "zero:z", "suffix=s");
		if( a.error != null ) {
			return a.fail(ctx, getName());
		}
		List<String> names = new ArrayList<>(a.operands);
		if( names.isEmpty()) {
			ctx.stderr.println(getName()+": missing operand");
			return 1;
		}
		String suffix = a.value('s');
		boolean multiple = dir || a.has('a') || suffix != null;
		if( !multiple ) {
			if( names.size() > 2 ) {
				ctx.stderr.println(getName()+": extra operand '"+names.get(2)+"'");
				return 1;
			}
			if( names.size() == 2 ) {
				suffix = names.remove(1);
			}
		}
		String end = a.has('z') ? "\0" : "\n";
		StringBuilder out = new StringBuilder();
		for (String n : names) {
			String r = dir ? dirOf(n) : base(n);
			if( !dir && suffix != null && !suffix.isEmpty() && r.endsWith(suffix) && !r.equals(suffix)) {
				r = r.substring(0, r.length()-suffix.length());
			}
			out.append(r).append(end);
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return 0;
	}
}
