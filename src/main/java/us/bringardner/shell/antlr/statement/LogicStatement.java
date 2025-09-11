package us.bringardner.shell.antlr.statement;

import java.io.IOException;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import us.bringardner.filesource.sh.FileSourceShParser.Boolean_statementContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;

public class LogicStatement extends Statement{

	Statement left;
	Statement right;
	Token op;
	Boolean_statementContext boolean_statement;
	
	public LogicStatement(ParserRuleContext context,Statement left,Token op ,Statement right) {
		super(context);		 
		this.left = left;
		this.op = op;
		this.right = right;
	}

	public LogicStatement(ParserRuleContext ctx, Boolean_statementContext boolean_statement) {
		super(ctx);
		this.boolean_statement = boolean_statement;
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		
		
		if( boolean_statement!=null) {
			int ret = boolean_statement.boolean_().TRUE() != null?0:1;
			return ret;
		}
		
		int ret = left.process(sc);
		
		if( op.getText().equals("||")) {
			if( ret!=0) {
				ret = right.process(sc); 
			}
		} else if( op.getText().equals("&&")) {
			if( ret==0) {
				ret = right.process(sc); 
			}
		} else {
			sc.stderr.println("Invalid logic statement="+getContext().getText());
			return 1;
		}
		
		return ret;
	}

}
