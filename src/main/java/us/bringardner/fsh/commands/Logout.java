package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * logout [n]: exit a login shell (with n, or the last status); another shell says to use exit.
 */
public class Logout extends ShellCommand{
	static String name = "logout";
	static String help = "logout [n]\n"
			+ "	Exit a login shell with status n (the last command's if n is not given). A shell that is\n"
			+ "	not a login shell says to use exit."
			;

	public Logout() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( !ctx.console.isLogin ) {
			ctx.error("logout: not login shell: use `exit'");
			return 1;
		}
		Exit exit = new Exit();
		exit.setArgs(args);
		return exit.process(ctx);
	}

}
