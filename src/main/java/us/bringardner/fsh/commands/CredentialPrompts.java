package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.NativeKeyboard;
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

	private CredentialPrompts() {
	}

	/**
	 * Makes fsh the prompter for references, keeping the earlier one for other threads. It is
	 * installed again if something has replaced it since (Credentials.restoreDefaults() puts
	 * parley-files' console prompter back), so a shell that can't ask never falls through to
	 * that one: on Java 22 and later System.console() isn't null even without a terminal, and
	 * it would wait for an answer that never comes.
	 */
	public static synchronized void install() {
		if( !(Credentials.getPrompter() instanceof FshPrompter)) {
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
		if( ctx == null || ctx.console == null ) {
			return false;
		}
		// an interactive shell asks at its terminal; one that isn't (Eclipse's console, a GUI
		// window) can still ask with a dialog when there's a display
		return ctx.console.isInteractive || dialogAvailable();
	}

	/**
	 * Whether a password dialog may be shown: there's a display, and dialogs haven't been
	 * turned off (-Dfsh.credentials.dialog=false; the tests do, so they never wait on one).
	 */
	static boolean dialogAvailable() {
		if( "false".equalsIgnoreCase(System.getProperty("fsh.credentials.dialog"))) {
			return false;
		}
		try {
			return !java.awt.GraphicsEnvironment.isHeadless();
		} catch (Throwable e) {
			// no AWT (a native image built without it, say)
			return false;
		}
	}

	/**
	 * Reads one line without echo, as read -s does, and ends the line on standard error.
	 *
	 * @return the answer, or null at end of input
	 */
	static char[] readSecret(ShellContext ctx, String prompt) throws IOException {
		if( !ctx.console.isInteractive && dialogAvailable()) {
			// standard input isn't a terminal, so it can't be read without echo: ask in a dialog
			return askInDialog(prompt.trim().endsWith(":") ? prompt.trim().substring(0, prompt.trim().length()-1) : prompt.trim());
		}
		char mask = secretMask(ctx);
		if( NativeKeyboard.isAvailible() && us.bringardner.fsh.Console.isKeyboard(ctx.stdin) || ctx.stdin instanceof NativeKeyboard ) {
			// a terminal: a spinner, or a mask (*) for each character typed
			NativeKeyboard kb = new NativeKeyboard();
			kb.setLineTerminator('\n');
			kb.setPrompt(prompt);
			char[] ret = mask == SPIN ? kb.readSpinning() : mask == 0 ? kb.readSilent() : kb.readMasked(mask);
			return ret;
		}
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

	/** The variable that sets what a secret's characters show as at a terminal. */
	public static final String MASK_VARIABLE = "FSH_SECRET_MASK";

	/** secretMask's answer for the spinner. */
	static final char SPIN = '\uFFFF';

	/**
	 * What a secret shows as while it's typed at a terminal: a spinner that turns with each key
	 * (SPIN) when FSH_SECRET_MASK isn't set or is "spin", so typing is seen to land without the
	 * length showing; its first character for each character typed (such as *); or nothing (0)
	 * when it is set but empty, as sudo, ssh and passwd show nothing.
	 */
	static char secretMask(ShellContext ctx) {
		Object v = ctx.getVariable(MASK_VARIABLE);
		if( v == null || v.toString().equals("spin")) {
			return SPIN;
		}
		String s = v.toString();
		return s.isEmpty() ? 0 : s.charAt(0);
	}

	/** A password dialog; null if it was cancelled. */
	static char[] askInDialog(String label) throws IOException {
		char[][] answer = new char[1][];
		Runnable ask = ()->{
			javax.swing.JPasswordField field = new javax.swing.JPasswordField(24);
			javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));
			panel.add(new javax.swing.JLabel(label+":"), java.awt.BorderLayout.NORTH);
			panel.add(field, java.awt.BorderLayout.CENTER);
			javax.swing.JOptionPane pane = new javax.swing.JOptionPane(panel, javax.swing.JOptionPane.PLAIN_MESSAGE,
					javax.swing.JOptionPane.OK_CANCEL_OPTION);
			javax.swing.JDialog dialog = pane.createDialog("fsh");
			dialog.setAlwaysOnTop(true);
			dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowGainedFocus(java.awt.event.WindowEvent e) {
					field.requestFocusInWindow();
				}
			});
			field.addActionListener(e->pane.setValue(javax.swing.JOptionPane.OK_OPTION));
			dialog.setVisible(true);
			dialog.dispose();
			Object v = pane.getValue();
			if( v instanceof Integer && (Integer) v == javax.swing.JOptionPane.OK_OPTION ) {
				answer[0] = field.getPassword();
			}
			field.setText("");
		};
		try {
			if( javax.swing.SwingUtilities.isEventDispatchThread()) {
				ask.run();
			} else {
				javax.swing.SwingUtilities.invokeAndWait(ask);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw new IOException("Can't show the password dialog: "+e.getCause(), e.getCause());
		}
		return answer[0];
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
			} else if( c == '#' && !in ) {
				// a comment runs to the end of the line
				break;
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
