package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import us.bringardner.filesource.sh.FileSourceShParser.Until_statementContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Compare;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.statement.LoopStatement.LoopControlException;

public class UntilStatement extends Statement{

	Compare compare;
	List<Statement> stmts;
	
	public UntilStatement(Until_statementContext context) {
		super(context);
	}

	public UntilStatement(Until_statementContext ctx, Compare compare, List<Statement> stmts) {
		this(ctx);
		this.compare = compare;
		this.stmts = stmts;
	}

	public Compare getCompare() {
		return compare;
	}

	public void setCompare(Compare compare) {
		this.compare = compare;
	}

	public List<Statement> getStmts() {
		return stmts;
	}

	public void setStmts(List<Statement> stmts) {
		this.stmts = stmts;
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;

		Until_statementContext ctx = ((Until_statementContext)getContext());
		Compare test = new Compare(ctx.compare());
		ShellContext.LoopControl trigger = null;
		// (break is checked first: the condition is not evaluated again after it)
		while( !ShellContext.LoopControl.Break.equals(trigger) && !test.evaluate(sc)) {

			for(Statement stmt : stmts) {
				try {
					ret = stmt.process(sc);
				} catch(LoopControlException e) {
					// break and continue have status 0
					ret = 0;
					if(e.howFar>1) {
						throw new LoopControlException(e.type, e.howFar-1);
					}
					trigger = e.type;
					break;
				}
				// as in bash, a failed command does not end the loop; the status is the last command's

			}						
		}

		return ret;
	}

}
