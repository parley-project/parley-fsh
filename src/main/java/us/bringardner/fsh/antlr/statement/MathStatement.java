package us.bringardner.fsh.antlr.statement;

import java.io.IOException;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.parser.FileSourceShParser.MathStatementContext;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Arithmetic;
import us.bringardner.fsh.antlr.Parameter;
import us.bringardner.fsh.antlr.Statement;

public class MathStatement extends Statement{

	Parameter  parameter;
	
	public MathStatement(ParserRuleContext context) {
		super(context);
	}

	public MathStatement(MathStatementContext ctx, Parameter pp) {
		this(ctx);
		parameter = pp;
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		MathStatementContext ctx = ((MathStatementContext)getContext());
		if( ctx.ARITH_COMMAND() != null ) {
			// (( expression )): 0 if the value is not 0, 1 if it is (or the expression is invalid)
			try {
				return Arithmetic.isTrue(Arithmetic.expandAndEvaluate(Arithmetic.body(ctx.ARITH_COMMAND().getText()), sc)) ? 0 : 1;
			} catch (Arithmetic.ArithmeticError e) {
				sc.stderr.println("((: "+e.getMessage());
				return 1;
			}
		} else if( ctx.mathExpression() != null ) {
			Arithmetic.expansion(ctx.mathExpression().getText(), sc);
			return 0;
		} else if( parameter != null ) {
			parameter.evaluate(sc);
			return 0;
		}
		throw new RuntimeException("Invalide math statement "+ctx.getText());
	}

	@Override
	protected boolean errexitApplies() {
		return true;
	}
}
