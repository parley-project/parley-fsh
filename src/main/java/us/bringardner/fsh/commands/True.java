package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

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
