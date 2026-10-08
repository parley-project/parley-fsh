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

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( args.length < 2 ) {
			ctx.error("getopts: usage: getopts optstring name [arg ...]");
			return 2;
		}
		String optstring = ""+args[0].getValue(ctx);
		String var = ""+args[1].getValue(ctx);
		List<String> params = new ArrayList<>();
		if( args.length > 2 ) {
			for(int idx = 2; idx < args.length; idx++) {
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
			ctx.setVariable(var, "?");
			if( quiet ) {
				ctx.setVariable("OPTARG", ""+c);
			} else {
				ctx.unSetVariable("OPTARG");
				ctx.stderr.println(ctx.getVariable("$0")+": illegal option -- "+c);
			}
		} else if( at+1 < optstring.length() && optstring.charAt(at+1) == ':' ) {
			// the argument: the rest of this word, or the next word
			if( !wordDone ) {
				ctx.setVariable("OPTARG", word.substring(pos));
			} else if( optind < params.size()) {
				optind++;
				ctx.setVariable("OPTARG", params.get(optind-1));
			} else {
				if( quiet ) {
					ctx.setVariable(var, ":");
					ctx.setVariable("OPTARG", ""+c);
				} else {
					ctx.setVariable(var, "?");
					ctx.unSetVariable("OPTARG");
					ctx.stderr.println(ctx.getVariable("$0")+": option requires an argument -- "+c);
				}
				return next(ctx, optind+1, 0, 0);
			}
			ctx.setVariable(var, ""+c);
			return next(ctx, optind+1, 0, 0);
		} else {
			ctx.setVariable(var, ""+c);
			ctx.unSetVariable("OPTARG");
		}
		return wordDone ? next(ctx, optind+1, 0, 0) : next(ctx, optind, pos, 0);
	}

	private static int next(ShellContext ctx, int optind, int pos, int ret) {
		ctx.setVariable("OPTIND", optind);
		ctx.console.setGetoptsPosition(optind, pos);
		return ret;
	}

	/** no more options: name is ?, status 1 */
	private static int end(ShellContext ctx, String var, int optind) {
		ctx.setVariable(var, "?");
		next(ctx, optind, 0, 1);
		return 1;
	}
}
