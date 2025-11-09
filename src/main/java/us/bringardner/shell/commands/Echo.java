package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Echo extends ShellCommand{
	static String name = "echo";
	static String help = "echo – write arguments to the standard output\n"
			+ "\n"
			+ "USAGE:  echo [-n] [string ...]\n"
			;

	public Echo() {
		super(name, help);
	}

	
	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		int start = 0;
		boolean nl = true;
		String val1 = null;
		ParserRuleContext cc = context;
		List<ParseTree> kids = cc.children;
		
		StringBuilder buf = new StringBuilder();
		if( args.length>0 ) {
			for(ParseTree kid : kids) {
				if (kid instanceof ArgumentContext) {
					ArgumentContext actx = (ArgumentContext) kid;
					Argument a = new Argument(actx);
					String val =""+a.getValue(ctx);
					if( val.equals("-n") ) {
						nl = false;
					} else {
						buf.append(val);
					}
				} else if (kid instanceof TerminalNode) {
					String val = kid.getText();
					buf.append(val);
				}
			}
		}

		if( nl ) {
			ctx.stdout.println(buf);
		} else {
			ctx.stdout.print(buf);
		}

		return ret;
	}

}
