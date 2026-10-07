package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Colon extends ShellCommand{
	static String name = ":";
	static String help = ": [arguments]\n"
			+ "	Do nothing, successfully (the arguments are expanded and ignored). Exit status: 0."
			;

	public Colon() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		return 0;
	}
}
