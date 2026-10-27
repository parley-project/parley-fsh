package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.Map;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.spi.BrowserProvider;
import us.bringardner.parley.plugin.PluginReport;
import us.bringardner.parley.plugin.Plugins;

/** plugins: what fsh loaded from its plugin folders, and what that added */
public class PluginsCommand extends ShellCommand {
	static final String NAME = "plugins";
	static final String HELP = "plugins\n"
			+ "\tList fsh's plugin folders, in the order they are searched, and each jar in them: loaded\n"
			+ "\t(a plugin or a library), skipped and why, or shadowed by a jar of the same name found\n"
			+ "\tfirst. Then the commands and browsers the plugins added, and the commands not added\n"
			+ "\tbecause a builtin or an earlier plugin has the name.";

	public PluginsCommand() {
		super(NAME, HELP);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		if( args.length > 0 ) {
			ctx.error(NAME+": usage: "+NAME);
			return 2;
		}
		PluginReport r = Plugins.report();
		if( r == null ) {
			ctx.stdout.println("No plugins were loaded (fsh was started by another program).");
			return 0;
		}
		ctx.stdout.print(r.text());
		if( !Console.pluginCommands.isEmpty()) {
			ctx.stdout.println("commands:");
			for(Map.Entry<String, String> e : Console.pluginCommands.entrySet()) {
				ctx.stdout.println("  "+e.getKey()+" ("+e.getValue()+")");
			}
		}
		boolean first = true;
		for(BrowserProvider b : Browse.providers(ctx)) {
			if( b == Browse.BUILTIN ) {
				// (fsh's own: not a plugin's)
				continue;
			}
			if( first ) {
				ctx.stdout.println("browsers:");
				first = false;
			}
			ctx.stdout.println("  "+b.name()+" ("+Console.pluginSource(b.getClass())+")");
		}
		for(String note : Console.pluginNotes) {
			ctx.stdout.println("note: "+note);
		}
		return 0;
	}
}
