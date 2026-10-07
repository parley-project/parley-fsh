package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class False extends ShellCommand{
	static String name = "false";
	static String help = "false [arguments]\n"
			+ "	Do nothing, unsuccessfully. Exit status: 1."
			;

	public False() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		return 1;
	}
}
