package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Pwd extends ShellCommand{
	static String name = "pwd";
	static String help = "pwd [-LP]\n\tPrint the current directory.\n"
			+ "\t [-L] (default) Display the logical current working directory\n"
			+ "\t -P Display the physical current working directory (all symbolic links resolved).";
	
	public Pwd() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		FileSource dir = ctx.console.getCurrentDirectory();
		if( args.length>0 && args[0].getValue(ctx).toString().equals("-P")) {
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
