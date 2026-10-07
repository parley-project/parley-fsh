package us.bringardner.fsh.antlr.statement;

import java.io.IOException;

import us.bringardner.fsh.parser.FileSourceShParser.Statement1Context;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.DoubleBracket;
import us.bringardner.fsh.antlr.Statement;

/**
 * [[ expression ]]: 0 if true, 1 if false, 2 if invalid.
 */
public class DoubleBracketStatement extends Statement {

	public DoubleBracketStatement(Statement1Context context) {
		super(context);
	}

	@Override
	protected int execute(ShellContext ctx) throws IOException {
		return DoubleBracket.test(((Statement1Context) getContext()).DBL_TEST().getText(), ctx);
	}
	@Override
	protected boolean errexitApplies() {
		return true;
	}
}
