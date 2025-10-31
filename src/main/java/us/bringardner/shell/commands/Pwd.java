package us.bringardner.shell.commands;

import java.io.IOException;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Pwd extends ShellCommand{
	static String name = "pwd";
	static String help = "print the current directory\n"
			+ "\t [-L] (default) Display the logical current working directory\n"
			+ "\t -P Display the physical current working directory (all symbolic links resolved).";
	
	public Pwd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		FileSource dir = ctx.console.getCurrentDirectory();
		if( args.length>1 && args[1].getValue(ctx).toString().equals("-P")) {
			FileSource link = dir.getLinkedTo();
			while(link !=null ) {
				dir = link;
				link = dir.getLinkedTo();
			}
		}
		String pwd = dir.getAbsolutePath();
		
		ctx.stdout.println(pwd);
		return ret;
	}

}
