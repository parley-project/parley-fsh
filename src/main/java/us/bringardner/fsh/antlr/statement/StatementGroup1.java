package us.bringardner.fsh.antlr.statement;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.parser.FileSourceShParser.Statement_group1Context;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Statement;
import us.bringardner.fsh.antlr.signal.ExitException;

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
	/** ( list ) rather than { list; } */
	private final boolean subshell;

	public StatementGroup1(Statement_group1Context context, List<Statement> stmts) {
		this(context, stmts, context.LPAREN() != null);
	}

	/** a group from another rule: the ( ... ) body of f() ( ... ) */
	public StatementGroup1(org.antlr.v4.runtime.ParserRuleContext context, List<Statement> stmts, boolean subshell) {
		super(context);
		this.stmts = stmts;
		this.subshell = subshell;
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		if( subshell ) {
			// ( list ): a subshell, so its changes (x=1, cd, exit, set --, exec 3>f ...) stay inside
			ShellContext sub = sc.subShell();
			Console.Snapshot saved = sc.console.snapshot();
			List<String> trap = sc.console.beginSubshellTrap();
			int ret = 1;
			try {
				ret = run(sub);
			} catch (ExitException e) {
				ret = e.exitCode;
			} finally {
				// its EXIT trap runs as it ends
				sc.console.endSubshellTrap(trap, ret);
				sc.console.restore(saved);
			}
			return ret;
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
