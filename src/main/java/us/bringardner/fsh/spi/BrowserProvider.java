package us.bringardner.fsh.spi;

import java.io.IOException;

import us.bringardner.fsh.NativeKeyboard;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * A file browser fsh's browse command can open: a window, or a full-screen terminal one. A plugin
 * jar lists its provider in META-INF/services/us.bringardner.fsh.spi.BrowserProvider.
 *
 * browse uses the provider of the highest priority that is available where fsh runs (browse
 * --with NAME chooses one; browse --list lists them).
 */
public interface BrowserProvider {

	/** a window: before a terminal browser when both can run */
	int PRIORITY_WINDOW = 100;
	/** a full-screen terminal browser */
	int PRIORITY_TERMINAL = 50;

	/** a short name for browse --with: swing, terminal, lanterna */
	String name();

	/** one line for browse --list */
	String description();

	/** higher first */
	int priority();

	/**
	 * It can run here, for this command: a window needs a display, a terminal browser needs the
	 * command's input and output to be the terminal (see {@link #onTerminal}, {@link #hasDisplay}).
	 */
	boolean available(ShellContext ctx);

	/**
	 * Browse until the user is done.
	 * @return the folder the browser was showing when it ended (for browse -c), or null
	 */
	FileSource browse(ShellContext ctx, BrowseRequest request) throws IOException;

	/** the command's output is fsh's own, and that is a terminal fsh can work */
	static boolean onTerminal(ShellContext ctx) {
		return ctx.stdout == System.out && NativeKeyboard.isAvailible() && NativeKeyboard.terminal();
	}

	/**
	 * There is a display for a window: not in an SSH session; on Linux and other Unix systems,
	 * DISPLAY or WAYLAND_DISPLAY is set.
	 */
	static boolean hasDisplay() {
		if( System.getenv("SSH_CONNECTION") != null || System.getenv("SSH_TTY") != null ) {
			return false;
		}
		String os = System.getProperty("os.name", "").toLowerCase();
		if( os.startsWith("windows") || os.startsWith("mac")) {
			return true;
		}
		return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
	}
}
