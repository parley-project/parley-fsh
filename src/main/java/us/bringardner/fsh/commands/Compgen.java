package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.Completion;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * compgen [-abcdefgjksuv] [-o option] [-A action] [-W words] [-F function] [-C command]
 * [-P prefix] [-S suffix] [-X filter] [word]: the completions of word, one a line, as bash's.
 */
public class Compgen extends ShellCommand{

	static String name = "compgen";
	static String help = "compgen [-abcdefgjksuv] [-o option] [-A action] [-W wordlist] [-F function] [-C command] [-P prefix] [-S suffix] [-X filterpat] [word]\n"
			+ "The completions of word, one a line, as complete's options say (see complete). Status 1 if there are none.";

	public Compgen() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> words = new ArrayList<>();
		for(int i = 0; i < args.length; i++) {
			words.add(""+args[i].getValue(ctx));
		}
		Complete.Parsed p;
		try {
			p = Complete.parse(name, words);
		} catch (IllegalArgumentException e) {
			ctx.error(e.getMessage());
			return 2;
		}
		String word = p.names.isEmpty() ? "" : p.names.get(0);
		List<String> found;
		try {
			found = p.spec.generate(ctx, word, name, "", List.of(), "", 0);
		} catch (IllegalArgumentException e) {
			ctx.error(name+": "+e.getMessage());
			return 2;
		}
		if( found.isEmpty() && (p.spec.options.contains("default") || p.spec.options.contains("bashdefault"))) {
			Completion.Spec files = new Completion.Spec();
			files.actions.add("file");
			found = files.generate(ctx, word, "", "", List.of(), "", 0);
		} else if( found.isEmpty() && p.spec.options.contains("dirnames")) {
			Completion.Spec dirs = new Completion.Spec();
			dirs.actions.add("directory");
			found = dirs.generate(ctx, word, "", "", List.of(), "", 0);
		}
		for(String f : found) {
			ctx.stdout.println(f);
		}
		return found.isEmpty() ? 1 : 0;
	}
}
