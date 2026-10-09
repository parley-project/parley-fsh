package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import us.bringardner.fsh.ConsoleFrame;
import us.bringardner.fsh.KeyboardReader;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.exec.Programs;

public class Clear extends ShellCommand{
	static String name = "clear";
	// physical 
	static String help = "clear\n\tClear the terminal screen.";
	
	public Clear() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		KeyboardReader kb = ctx.console.getKeyboadReader(false);	
		if (kb instanceof ConsoleFrame) {
			ConsoleFrame frame = (ConsoleFrame) kb;
			frame.clear();			
		} else {
			List<String> cmd = Arrays.asList("clear");
			ret = Programs.execute(cmd, ctx);			
		}
		
		return ret;
	}

}
