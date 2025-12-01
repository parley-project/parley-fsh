package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
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
		boolean nl = true;

		StringBuilder buf = new StringBuilder();
		if( context !=null) {
			ParserRuleContext cc = context;
			List<ParseTree> kids = cc.children;
			//  match up args with WS
			int aidx=0;				
			for (int idx = 0; idx < kids.size(); idx++) {
				ParseTree kid = kids.get(idx);
				if (kid instanceof ArgumentContext	) {
					//ArgumentContext ac = (ArgumentContext) kid;
					Argument a = args[aidx];
					String val =""+a.getValue(ctx);

					if( val.equals("-n") ) {
						nl = false;
					} else {
						buf.append(val);
					}						
					aidx++;
				} else if (aidx<args.length && !buf.isEmpty() && kid instanceof TerminalNode) {
					String val = kid.getText();
					buf.append(val);
				}
			}

		} else {
			throw new IOException("No context in Echo");
		}



		String result = buf.toString();
		if( nl ) {
			ctx.stdout.println(result);
		} else {
			ctx.stdout.print(result);
		}

		return ret;
	}

}
