package us.bringardner.shell.antlr.statement;

import java.io.IOException;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.MathStatementContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.Parameter;
import us.bringardner.shell.antlr.Statement;

public class MathStatement extends Statement{

	Expression expr;
	Parameter  parameter;
	
	public MathStatement(ParserRuleContext context) {
		super(context);
	}

	public MathStatement(MathStatementContext ctx, Expression expr) {
		this(ctx);
		this.expr = expr;
	}

	public MathStatement(MathStatementContext ctx, Parameter pp) {
		this(ctx);
		parameter = pp;
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;
		MathStatementContext ctx = ((MathStatementContext)getContext());
		if( parameter != null ) {
			parameter.evaluate(sc);
		} else if( expr != null ) {
			expr.evaluate(sc);
		} else if(ctx.mathExpression()!=null ) {
			expr = new Expression(ctx.mathExpression().expression());
			expr.evaluate(sc);
		} else {
			throw new RuntimeException("Invalide math expression "+ctx.getText());
		}


		return ret;
	}


}
