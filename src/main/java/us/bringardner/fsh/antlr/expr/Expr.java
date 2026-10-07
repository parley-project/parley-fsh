package us.bringardner.fsh.antlr.expr;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.parser.ExprParser.ExprContext;

public class Expr {

	private ParserRuleContext context;
	
	public ParserRuleContext getContext() {
		return context;
	}

	public void setContext(ParserRuleContext context) {
		this.context = context;
	}

	public Expr(ExprContext ctx) {
		this.context = ctx;
	}

}
