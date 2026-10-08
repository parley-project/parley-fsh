package us.bringardner.fsh;

import java.io.IOException;

import us.bringardner.fsh.antlr.Argument;

/**
 * A shell function, however it was read: what the shell keeps in its function table and calls.
 */
public interface ShellFunction {

	String getName();

	/** the function as declare -f and type print it, in bash's layout */
	String declaration();

	/** call it with these arguments ($1 ...); its status */
	int invoke(Argument[] args, ShellContext ctx) throws IOException;

	boolean isExported();

	void setExported(boolean exported);
}
