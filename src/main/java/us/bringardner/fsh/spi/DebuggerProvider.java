package us.bringardner.fsh.spi;

import java.io.IOException;

import us.bringardner.fsh.Console;

/**
 * A debugger a script can wait for: what {@code fsh --wait-for-debugger [address:]port script}
 * uses. fsh has no debugger protocol of its own; a jar on its class path or in a plugin folder
 * (fsh-loupe is one) provides this, listed in
 * {@code META-INF/services/us.bringardner.fsh.spi.DebuggerProvider}.
 */
public interface DebuggerProvider {

	/** a short name, for messages (such as loupe) */
	String name();

	/**
	 * Listens at where, waits until a debugger has connected and attached, and gives console the
	 * debugger's {@link us.bringardner.fsh.DebugContext}: the script the caller then runs with
	 * console is debugged. It says on console's standard error where it is waiting.
	 *
	 * @param where "port" (this machine only) or "address:port"
	 * @param script the script's path as the command line names it
	 * @param code the script's text
	 * @throws IllegalArgumentException if where isn't a port or address:port
	 * @throws IOException if it can't listen there, or the wait ended without a debugger
	 */
	Session waitForDebugger(Console console, String where, String script, String code) throws IOException;

	/** A debugger attached to a script. */
	interface Session {
		/** The script ended with this status: the debugger is told, and its connection closed. Only the first call counts. */
		void ended(int status);
	}
}
