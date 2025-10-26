package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import us.bringardner.filesource.sh.FileSourceShParser.RedirectContext;
import us.bringardner.filesource.sh.FileSourceShParser.Statement_groupContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;

/*
( list )
Placing a list of commands between parentheses forces the shell to create a subshell (see Command Execution Environment), 
	and each of the commands in list is executed in that subshell environment. 
	Since the list is executed in a subshell, variable assignments do not remain in effect after the subshell completes.


{ list; }
Placing a list of commands between curly braces causes the list to be executed in the current shell context. 
	No subshell is created. The semicolon (or newline) following list is required.
 */
public class StatementGroup extends Statement{
	StatementGroup1 g1;

	public StatementGroup(Statement_groupContext context, StatementGroup1 g1) {
		super(context);
		this.g1 = g1;		
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;
		Statement_groupContext ctx = (Statement_groupContext)getContext();
		InputStream in = sc.stdin;
		PrintStream out = sc.stdout;
		PrintStream err = sc.stderr;
		// these could be in front or behind but the result is the same
		RedirectContext redirect = null;
		
		if( ctx.redirect2!=null) {
			redirect = ctx.redirect2;
		} else if( ctx.redirect1!=null) {
			redirect = ctx.redirect1;
		}

		
		configureRedirect(sc, redirect);
		ret = g1.execute(sc);
		
		sc.stdin = in;
		sc.stdout = out;
		sc.stderr = err;
		
		return ret;
	}

}
