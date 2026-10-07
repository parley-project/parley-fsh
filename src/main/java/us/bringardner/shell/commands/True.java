package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class True extends ShellCommand{
	static String name = "true";
	static String help = "true [arguments]\n"
			+ "	Do nothing, successfully. Exit status: 0."
			;

	public True() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		return 0;
	}
}
