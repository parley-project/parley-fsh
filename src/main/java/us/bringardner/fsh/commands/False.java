package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

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
