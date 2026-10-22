package us.bringardner.fsh.spi;

import java.util.List;

import us.bringardner.fsh.ShellCommand;

/**
 * Commands a plugin adds to fsh. A plugin jar lists its provider in
 * META-INF/services/us.bringardner.fsh.spi.CommandProvider and has Parley-Plugin-Api: 1 in its
 * manifest; fsh finds it in its plugin folders (parley-core's Plugins) as it starts.
 *
 * A builtin of the same name, or a command an earlier plugin added, wins: the later one is listed
 * as shadowed by the plugins command.
 */
public interface CommandProvider {

	/**
	 * The commands: ShellCommands with a public constructor without arguments, as fsh makes a new
	 * one each time a command runs.
	 */
	List<ShellCommand> commands();
}
