package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.credentials.CredentialRequest;
import us.bringardner.parley.files.credentials.Credentials;

/**
 * fsh's side of parley-files' credential references: {@code @prompt:} (and the keystore's
 * master password) asks on the shell's terminal, without echo, while a command such as
 * connect or cred runs; and a typed line that holds a plain password is kept out of the
 * history.
 * <p>
 * The prompter is JVM-wide, so it asks only for the shell whose command is running on this
 * thread ({@link #with}); anywhere else it hands over to the prompter that was there before
 * (fsh-ide's dialog, say). A shell that isn't interactive (a script, fsh -c, cron) can't be
 * asked: the prompt fails at once instead of waiting.
 */
public final class CredentialPrompts {

	/** Something a command does that may ask for a secret. */
	public interface IOAction<T> {
		T run() throws IOException;
	}

	private static final ThreadLocal<ShellContext> current = new ThreadLocal<>();
	private static boolean installed;

	private CredentialPrompts() {
	}

	/** Makes fsh the prompter for references (once), keeping the earlier one for other threads. */
	public static synchronized void install() {
		if( !installed ) {
			installed = true;
			Credentials.setPrompter(new FshPrompter(Credentials.getPrompter()));
		}
	}

	/** Runs action with ctx's shell as the one to ask. */
	public static <T> T with(ShellContext ctx, IOAction<T> action) throws IOException {
		install();
		ShellContext prev = current.get();
		current.set(ctx);
		try {
			return action.run();
		} finally {
			if( prev == null ) {
				current.remove();
			} else {
				current.set(prev);
			}
		}
	}

	/** Whether ctx's shell can ask the person at the keyboard. */
	static boolean canAsk(ShellContext ctx) {
		return ctx != null && ctx.console != null && ctx.console.isInteractive;
	}

	/**
	 * Reads one line without echo, as read -s does, and ends the line on standard error.
	 *
	 * @return the answer, or null at end of input
	 */
	static char[] readSecret(ShellContext ctx, String prompt) throws IOException {
		List<Read.Options> silent = new ArrayList<>(List.of(Read.Options.s, Read.Options.r));
		String line;
		try {
			line = new Read().readLine(ctx, prompt, '\n', -1, "", -1, -1, silent);
		} catch (java.io.EOFException e) {
			line = null;
		}
		ctx.stderr.println();
		return line == null ? null : line.toCharArray();
	}

	private static final class FshPrompter implements Credentials.Prompter {
		private final Credentials.Prompter before;

		FshPrompter(Credentials.Prompter before) {
			this.before = before;
		}

		@Override
		public char[] ask(String label, CredentialRequest request) {
			ShellContext ctx = current.get();
			if( ctx == null ) {
				return before == null ? null : before.ask(label, request);
			}
			if( !CredentialPrompts.canAsk(ctx)) {
				return null;
			}
			try {
				return readSecret(ctx, label+": ");
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public boolean canAsk(CredentialRequest request) {
			ShellContext ctx = current.get();
			if( ctx == null ) {
				return before != null && before.canAsk(request);
			}
			return CredentialPrompts.canAsk(ctx);
		}
	}

	/**
	 * Whether a typed line holds a plain secret that mustn't go into the history: a connect
	 * setting the factory calls secret whose value isn't a reference (@...) or an expansion
	 * ($...), or a cred set given the secret as an argument.
	 */
	public static boolean holdsPlainSecret(String line) {
		List<String> words = words(line);
		for(int i = 0; i < words.size(); i++) {
			String w = words.get(i);
			if( w.equals("connect") && i+1 < words.size()) {
				FileSourceFactory f = FileSourceFactory.getFileSourceFactory(words.get(i+1));
				for(int j = i+2; j < words.size() && !isSeparator(words.get(j)); j++) {
					String s = words.get(j);
					int eq = s.indexOf('=');
					if( eq <= 0 ) {
						continue;
					}
					String name = s.substring(0, eq);
					String value = s.substring(eq+1);
					if( value.isEmpty() || value.startsWith("$") || (value.startsWith("@") && !value.startsWith("@@"))) {
						continue;
					}
					if( f != null ? f.isSecretProperty(name) : FileSourceFactory.looksLikeSecret(name)) {
						return true;
					}
				}
			} else if( w.equals("cred") && i+2 < words.size() && words.get(i+1).equals("set")) {
				int n = 0;
				for(int j = i+2; j < words.size() && !isSeparator(words.get(j)); j++) {
					n++;
				}
				if( n > 1 ) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isSeparator(String w) {
		return w.equals(";") || w.equals("|") || w.equals("&") || w.equals("&&") || w.equals("||");
	}

	/** The line's words, quotes removed; ; | & are words of their own. Enough for the check above. */
	static List<String> words(String line) {
		List<String> ret = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean in = false;
		char quote = 0;
		for(int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if( quote != 0 ) {
				if( c == quote ) {
					quote = 0;
				} else {
					cur.append(c);
				}
			} else if( c == '\'' || c == '"' ) {
				quote = c;
				in = true;
			} else if( c == '\\' && i+1 < line.length()) {
				cur.append(line.charAt(++i));
				in = true;
			} else if( Character.isWhitespace(c) || c == ';' || c == '|' || c == '&' ) {
				if( in ) {
					ret.add(cur.toString());
					cur.setLength(0);
					in = false;
				}
				if( !Character.isWhitespace(c)) {
					if( (c == '|' || c == '&') && i+1 < line.length() && line.charAt(i+1) == c ) {
						ret.add(""+c+c);
						i++;
					} else {
						ret.add(""+c);
					}
				}
			} else {
				cur.append(c);
				in = true;
			}
		}
		if( in ) {
			ret.add(cur.toString());
		}
		return ret;
	}
}
