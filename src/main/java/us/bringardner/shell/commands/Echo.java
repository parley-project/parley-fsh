package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

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
		ParserRuleContext cc = context;
		List<ParseTree> kids = cc.children;
		//  match up args with WS
		List<String> white = new ArrayList<String>();
		List<String> wsl = new ArrayList<String>();
		for (int idx = 2; idx < kids.size(); idx++) {
			ParseTree kid = kids.get(idx);
			if (kid instanceof TerminalNode) {
				String val = kid.getText();
				white.add(val);
				wsl.add(""+val.length());
			}
		}
		
		if( white.isEmpty()) {
			white.add(" ");
			wsl.add("0");
		}
		
		StringBuilder buf = new StringBuilder();
		if( args.length>0 ) {
			for (int idx = 0; idx < args.length; idx++) {
				Argument a = args[idx];
				String val =""+a.getValue(ctx);
				
				if( val.equals("-n") ) {
					nl = false;
				} else {
					if(!buf.isEmpty()) {
						//String ws = "~"+white.getLast()+"~"+wsl.getLast();
						if( idx >= white.size()) {
						//	ws = "~"+white.getLast()+"~"+wsl.getLast();
							buf.append(white.getLast());
						} else {
						//	ws = "~"+white.get(idx-1)+"~"+wsl.get(idx-1);
							buf.append(white.get(idx-1));
						}
					}
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
