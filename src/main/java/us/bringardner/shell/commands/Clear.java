package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import us.bringardner.shell.ConsoleFrame;
import us.bringardner.shell.KeyboardReader;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.CommandStatement;

public class Clear extends ShellCommand{
	static String name = "clear";
	// physical 
	static String help = "Clear the terminal screen";
	
	public Clear() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		KeyboardReader kb = ctx.console.getKeyboadReader();	
		if (kb instanceof ConsoleFrame) {
			ConsoleFrame frame = (ConsoleFrame) kb;
			frame.clear();			
		} else {
			List<String> cmd = Arrays.asList("clear");
			ret = CommandStatement.execute(cmd, ctx);			
		}
		
		return ret;
	}

}
