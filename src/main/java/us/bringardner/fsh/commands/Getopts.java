package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Getopts extends ShellCommand{
	static String name = "getopts";
	static String help = "getopts optstring name [arg ...]\n"
			+ "	Read the next option from the positional parameters (or the args) into name, its argument\n"
			+ "	(for a letter followed by : in optstring) into OPTARG, and the index of the next\n"
			+ "	parameter into OPTIND. A : at the start of optstring reports errors quietly (name is ?\n"
			+ "	or :, OPTARG the letter). Exit status 1 when there are no more options."
			;

	public Getopts() {
		super(name, help);
	}

	private static final String USAGE = "getopts: usage: getopts optstring name [arg ...]";

	/** name was no name: said (each time), and the status is 1 */
	private boolean badName;

	private void bind(ShellContext ctx, String var, String value) {
		if( !us.bringardner.fsh.exec.Executor.isName(var)) {
			ctx.error("getopts: `"+var+"': not a valid identifier");
			badName = true;
			return;
		}
		ctx.setVariable(var, value);
	}

	/** (readonly or not, as bash's unbind_variable_noref) */
	private static void unsetOptarg(ShellContext ctx) {
		ctx.console.clearReadonly("OPTARG");
		ctx.unSetVariable("OPTARG", false);
	}

	/** a readonly OPTARG is said, and getopts goes on */
	private static void setOptarg(ShellContext ctx, String value) {
		try {
			ctx.setVariable("OPTARG", value);
		} catch (ShellContext.ReadonlyException e) {
			ctx.error(e.getMessage());
		}
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		badName = false;
		int ret = options(ctx);
		return badName ? 1 : ret;
	}

	private int options(ShellContext ctx) throws IOException {
		// (options: none, -- ends them)
		int first = 0;
		if( args.length > 0 ) {
			String a0 = ""+args[0].getValue(ctx);
			if( a0.equals("--")) {
				first = 1;
			} else if( a0.startsWith("-") && a0.length() > 1 ) {
				ctx.error("getopts: "+a0.substring(0, 2)+": invalid option");
				ctx.stderr.println(USAGE);
				return 2;
			}
		}
		if( args.length-first < 2 ) {
			ctx.stderr.println(USAGE);
			return 2;
		}
		String optstring = ""+args[first].getValue(ctx);
		String var = ""+args[first+1].getValue(ctx);
		List<String> params = new ArrayList<>();
		if( args.length > first+2 ) {
			for(int idx = first+2; idx < args.length; idx++) {
				params.add(""+args[idx].getValue(ctx));
			}
		} else {
			for(Object o : ctx.getPositionalParameterValues()) {
				params.add(""+o);
			}
		}
		boolean quiet = optstring.startsWith(":");

		int optind = 1;
		try {
			optind = Integer.parseInt((""+ctx.getVariable("OPTIND")).trim());
		} catch (NumberFormatException e) {
		}
		// where in a word like -abc the next letter is (bash keeps this to itself; OPTIND set by the
		// script starts again at the word's first letter)
		int pos = ctx.console.getoptsPosition(optind);

		if( optind < 1 || optind > params.size()) {
			return end(ctx, var, optind);
		}
		String word = params.get(optind-1);
		if( pos == 0 ) {
			if( word.equals("--")) {
				return end(ctx, var, optind+1);
			}
			if( !word.startsWith("-") || word.equals("-")) {
				return end(ctx, var, optind);
			}
			pos = 1;
		}
		char c = word.charAt(pos++);
		boolean wordDone = pos >= word.length();
		int at = c == ':' ? -1 : optstring.indexOf(c);
		if( at < 0 ) {
			// (said before name is set, as bash's)
			if( quiet ) {
				bind(ctx, var, "?");
				setOptarg(ctx, ""+c);
			} else {
				unsetOptarg(ctx);
				if( !"0".equals(String.valueOf(ctx.getVariable("OPTERR")).trim())) {
					// (OPTERR=0: unsaid)
					ctx.stderr.println(ctx.getVariable("$0")+": illegal option -- "+c);
				}
				bind(ctx, var, "?");
			}
		} else if( at+1 < optstring.length() && optstring.charAt(at+1) == ':' ) {
			// the argument: the rest of this word, or the next word
			if( !wordDone ) {
				setOptarg(ctx, word.substring(pos));
			} else if( optind < params.size()) {
				optind++;
				setOptarg(ctx, params.get(optind-1));
			} else {
				if( quiet ) {
					bind(ctx, var, ":");
					setOptarg(ctx, ""+c);
				} else {
					unsetOptarg(ctx);
					if( !"0".equals(String.valueOf(ctx.getVariable("OPTERR")).trim())) {
						ctx.stderr.println(ctx.getVariable("$0")+": option requires an argument -- "+c);
					}
					bind(ctx, var, "?");
				}
				return next(ctx, optind+1, 0, 0);
			}
			bind(ctx, var, ""+c);
			return next(ctx, optind+1, 0, 0);
		} else {
			bind(ctx, var, ""+c);
			unsetOptarg(ctx);
		}
		return wordDone ? next(ctx, optind+1, 0, 0) : next(ctx, optind, pos, 0);
	}

	private static int next(ShellContext ctx, int optind, int pos, int ret) {
		ctx.setVariable("OPTIND", optind);
		ctx.console.setGetoptsPosition(optind, pos);
		return ret;
	}

	/** no more options: name is ?, status 1 */
	private int end(ShellContext ctx, String var, int optind) {
		// (no more options: OPTARG is unset too, as bash's)
		unsetOptarg(ctx);
		bind(ctx, var, "?");
		next(ctx, optind, 0, 1);
		return 1;
	}
}
