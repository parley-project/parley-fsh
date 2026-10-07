package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import us.bringardner.filesource.sh.FileSourceShParser.Statement_group1Context;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.ExitException;

/*
( list )
Placing a list of commands between parentheses forces the shell to create a subshell (see Command Execution Environment), 
	and each of the commands in list is executed in that subshell environment. 
	Since the list is executed in a subshell, variable assignments do not remain in effect after the subshell completes.


{ list; }
Placing a list of commands between curly braces causes the list to be executed in the current shell context. 
	No subshell is created. The semicolon (or newline) following list is required.
 */
public class StatementGroup1 extends Statement{
	List<Statement> stmts;

	public StatementGroup1(Statement_group1Context context, List<Statement> stmts) {
		super(context);
		this.stmts = stmts;		
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		Statement_group1Context ctx = (Statement_group1Context)getContext();
		if(ctx.LPAREN()!=null) {
			// ( list ): a subshell, so exit, set -- and local variables stay inside. (Global variables
			// and the directory are the console's, so x=1 and cd still leak out.)
			ShellContext sub = sc.subShell();
			try {
				return run(sub);
			} catch (ExitException e) {
				return e.exitCode;
			}
		}
		return run(sc);
	}

	/**
	 * As in bash, a failed command does not stop the rest: { false; echo hi; } prints hi.
	 */
	private int run(ShellContext sc) throws IOException {
		int ret = 0;
		for(Statement s : stmts) {
			ret = s.process(sc);
		}
		return ret;
	}

}
