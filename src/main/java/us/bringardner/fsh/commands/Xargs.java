package us.bringardner.fsh.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** xargs, modeled on the GNU xargs: builds and runs commands from the items it reads. */
public class Xargs extends ShellCommand{
	static String name = "xargs";
	static String help = "xargs [OPTION]... [COMMAND [INITIAL-ARGS]...]\n"
			+ "\tRun COMMAND (echo by default) with the items read from standard input added as arguments.\n"
			+ "\tItems are separated by white space; quotes and backslashes group them or protect characters.\n"
			+ "\t-0, --null           the items are separated by NULs (and nothing is special in them)\n"
			+ "\t-d, --delimiter=C    the items are separated by the character C\n"
			+ "\t-a, --arg-file=FILE  read the items from FILE\n"
			+ "\t-n, --max-args=N     at most N items to a command\n"
			+ "\t-I REPL              one item to a command, put in the place of REPL in the INITIAL-ARGS\n"
			+ "\t-L, --max-lines=N    at most N input lines to a command\n"
			+ "\t-r, --no-run-if-empty  don't run COMMAND if there is no item\n"
			+ "\t-t, --verbose        print each command on standard error before running it\n"
			+ "\tExit status 0, 123 if a command exited with 1-125, 124 if one exited with 255, 125 if one was killed.";

	public Xargs() {
		super(name, help);
	}

	/** the items of the input, as the shell would split it */
	private static List<String> split(String text) {
		List<String> items = new ArrayList<>();
		StringBuilder cur = null;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if( c == '\'' || c == '"' ) {
				int end = text.indexOf(c, i+1);
				int nl = text.indexOf('\n', i+1);
				if( end < 0 || (nl >= 0 && nl < end)) {
					throw new IllegalArgumentException("unmatched "+(c == '"' ? "double" : "single")+" quote; by default quotes are special to xargs unless you use the -0 option");
				}
				if( cur == null ) {
					cur = new StringBuilder();
				}
				cur.append(text, i+1, end);
				i = end;
			} else if( c == '\\' && i+1 < text.length()) {
				if( cur == null ) {
					cur = new StringBuilder();
				}
				cur.append(text.charAt(++i));
			} else if( Character.isWhitespace(c)) {
				if( cur != null ) {
					items.add(cur.toString());
					cur = null;
				}
			} else {
				if( cur == null ) {
					cur = new StringBuilder();
				}
				cur.append(c);
			}
		}
		if( cur != null ) {
			items.add(cur.toString());
		}
		return items;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// the options end at the command: xargs -n 2 echo -n  (the -n belongs to echo)
		List<String> words = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			words.add(""+args[i].getValue(ctx));
		}
		int cut = 0;
		while( cut < words.size()) {
			String w = words.get(cut);
			if( !w.startsWith("-") || w.equals("-") ) {
				break;
			}
			cut++;
			if( w.equals("--")) {
				break;
			}
			if( w.matches("-[naIdLs]") || w.matches("--(max-args|arg-file|delimiter|max-lines|max-chars|replace)") ) {
				cut++;
			}
		}
		cut = Math.min(cut, words.size());
		Args a = Args.parseWords(new ArrayList<>(words.subList(0, cut)), "0rtpx", "naIdLsP", null, "null:0", "no-run-if-empty:r", "verbose:t", "arg-file=a",
				"max-args=n", "delimiter=d", "max-lines=L", "max-chars=s", "max-procs=P", "replace=I");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		List<String> command = new ArrayList<>(words.subList(cut, words.size()));
		long maxArgs = a.number('n', 0), maxLines = a.number('L', 0);
		if( a.error != null || maxArgs < 0 || maxLines < 0 ) {
			ctx.stderr.println("xargs: invalid number");
			return 1;
		}
		String repl = a.value('I');

		byte [] input;
		if( a.value('a') != null ) {
			input = TextFiles.readAll(ctx, name, a.value('a'));
			if( input == null ) {
				return 1;
			}
		} else {
			input = ctx.stdin.readAllBytes();
		}
		String text = new String(input, StandardCharsets.UTF_8);
		List<String> items;
		List<Integer> lineOf = new ArrayList<>();
		try {
			if( a.has('0')) {
				items = new ArrayList<>(java.util.Arrays.asList(text.split("\0", -1)));
				if( !items.isEmpty() && items.get(items.size()-1).isEmpty()) {
					items.remove(items.size()-1);
				}
			} else if( a.value('d') != null ) {
				String d = a.value('d').replace("\\n", "\n").replace("\\t", "\t").replace("\\0", "\0");
				items = new ArrayList<>(java.util.Arrays.asList(text.split(java.util.regex.Pattern.quote(d), -1)));
				if( !items.isEmpty() && items.get(items.size()-1).isEmpty()) {
					items.remove(items.size()-1);
				}
			} else if( repl != null ) {
				// -I: one item per line, blanks at the ends dropped
				items = new ArrayList<>();
				for (String line : text.split("\r?\n")) {
					if( !line.trim().isEmpty()) {
						items.add(line.trim());
					}
				}
			} else if( maxLines > 0 ) {
				items = new ArrayList<>();
				int lineNo = 0;
				for (String line : text.split("\r?\n")) {
					lineNo++;
					for (String it : split(line)) {
						items.add(it);
						lineOf.add(lineNo);
					}
				}
			} else {
				items = split(text);
			}
		} catch (IllegalArgumentException e) {
			ctx.stderr.println("xargs: "+e.getMessage());
			return 1;
		}

		if( command.isEmpty()) {
			command.add("echo");
		}
		if( items.isEmpty()) {
			if( a.has('r') || repl != null ) {
				return 0;
			}
		}

		int status = 0;
		List<List<String>> batches = new ArrayList<>();
		if( repl != null ) {
			for (String it : items) {
				List<String> one = new ArrayList<>();
				one.add(it);
				batches.add(one);
			}
		} else if( maxLines > 0 ) {
			List<String> cur = new ArrayList<>();
			int firstLine = -1;
			for (int i = 0; i < items.size(); i++) {
				int line = lineOf.get(i);
				if( firstLine >= 0 && line >= firstLine+maxLines ) {
					batches.add(cur);
					cur = new ArrayList<>();
					firstLine = -1;
				}
				if( firstLine < 0 ) {
					firstLine = line;
				}
				cur.add(items.get(i));
			}
			if( !cur.isEmpty()) {
				batches.add(cur);
			}
		} else if( maxArgs > 0 ) {
			for (int i = 0; i < items.size(); i += maxArgs) {
				batches.add(new ArrayList<>(items.subList(i, (int) Math.min(items.size(), i+maxArgs))));
			}
		} else {
			batches.add(items);
		}
		if( batches.isEmpty()) {
			batches.add(new ArrayList<>());
		}

		for (List<String> batch : batches) {
			StringBuilder script = new StringBuilder();
			for (int i = 0; i < command.size(); i++) {
				String w = command.get(i);
				if( repl != null && !batch.isEmpty()) {
					w = w.replace(repl, batch.get(0));
				}
				script.append(i == 0 ? w : Env.quote(w)).append(' ');
			}
			if( repl == null ) {
				for (String it : batch) {
					script.append(Env.quote(it)).append(' ');
				}
			}
			String line = script.toString().trim();
			if( a.has('t')) {
				ctx.stderr.println(line);
			}
			int rc = ctx.console.executeScript(line);
			if( rc == 255 ) {
				status = 124;
				break;
			}
			if( rc != 0 && status == 0 ) {
				status = rc > 125 ? 125 : 123;
			}
		}
		ctx.stdout.flush();
		return status;
	}
}
