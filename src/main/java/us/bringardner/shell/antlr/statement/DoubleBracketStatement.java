package us.bringardner.shell.antlr.statement;

import java.io.IOException;

import us.bringardner.filesource.sh.FileSourceShParser.Statement1Context;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.DoubleBracket;
import us.bringardner.shell.antlr.Statement;

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
