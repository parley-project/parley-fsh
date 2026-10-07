package us.bringardner.shell.antlr.statement;

import java.io.IOException;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;

/**
 * ! statement: status 0 if the statement fails, 1 if it succeeds.
 */
public class NegateStatement extends Statement {

	private final Statement statement;

	public NegateStatement(ParserRuleContext context, Statement statement) {
		super(context);
		this.statement = statement;
	}

	@Override
	protected int execute(ShellContext ctx) throws IOException {
		ctx.conditionDepth++;
		try {
			return statement.process(ctx) == 0 ? 1 : 0;
		} finally {
			ctx.conditionDepth--;
		}
	}
}
