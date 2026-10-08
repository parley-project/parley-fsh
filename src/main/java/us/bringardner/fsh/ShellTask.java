package us.bringardner.fsh;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.Console.CommandThread;

/**
 * Something a CommandThread runs (a pipe stage, a background job) for the new executor.
 */
public interface ShellTask {

	/** run it in ctx; its status */
	int run(ShellContext ctx) throws IOException;

	/** the command as written, as jobs shows it */
	String text();

	/** the threads it started (a pipeline's stages), which get the signals it gets */
	default List<CommandThread> children() {
		return List.of();
	}
}
