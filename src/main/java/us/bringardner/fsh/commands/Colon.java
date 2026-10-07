package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

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
