package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * suspend [-f]: stop the shell until it gets SIGCONT, as bash's (only with job control; a login
 * shell only with -f).
 */
public class Suspend extends ShellCommand{
	static String name = "suspend";
	static String help = "suspend [-f]\n"
			+ "	Suspend the shell until it receives a SIGCONT. A login shell, or one without job control,\n"
			+ "	cannot be suspended; -f suspends a login shell anyway."
			;

	private static final String USAGE = "suspend: usage: suspend [-f]";

	public Suspend() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean force = false;
		for(us.bringardner.fsh.Argument a : args) {
			String w = ""+a.getValue(ctx);
			if( w.equals("--")) {
				break;
			}
			if( !w.startsWith("-") || w.length() < 2 ) {
				ctx.stderr.println(USAGE);
				return 2;
			}
			for(char c : w.substring(1).toCharArray()) {
				if( c == 'f' ) {
					force = true;
				} else {
					ctx.error(name+": -"+c+": invalid option");
					ctx.stderr.println(USAGE);
					return 2;
				}
			}
		}
		if( !ctx.console.jobControl()) {
			ctx.error(name+": cannot suspend: no job control");
			return 1;
		}
		if( ctx.console.isLogin && !force ) {
			ctx.error(name+": cannot suspend a login shell");
			return 1;
		}
		// the shell stops itself (it goes on when it gets SIGCONT)
		try {
			new ProcessBuilder("kill", "-STOP", String.valueOf(ProcessHandle.current().pid())).inheritIO().start().waitFor();
		} catch (InterruptedException e) {
		}
		return 0;
	}
}
