package us.bringardner.fsh.antlr.statement;

import java.io.IOException;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.parser.FileSourceShParser.Loop_controll_statementContext;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellContext.LoopControl;
import us.bringardner.fsh.antlr.Statement;

public class ContinueStatement extends Statement{

	public ContinueStatement(ParserRuleContext context) {
		super(context);		 
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		Loop_controll_statementContext ctx = (Loop_controll_statementContext)getContext();
		int n = 1;
		if( ctx.NUMBER()!=null) {
			n = Integer.parseInt(ctx.NUMBER().getText());
		}
		
		throw new LoopStatement.LoopControlException(LoopControl.Continue, n);
		
	}

}
