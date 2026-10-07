package us.bringardner.shell.antlr.statement;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.RerdirectImpl;
import us.bringardner.shell.antlr.Statement;

/**
 * A compound command with redirects: while ...; done < file, for ...; done > out, if ...; fi 2>&1.
 */
public class RedirectedStatement extends Statement {

	private final Statement statement;
	private final RerdirectImpl redirect;

	public RedirectedStatement(ParserRuleContext context, Statement statement, RerdirectImpl redirect) {
		super(context);
		this.statement = statement;
		this.redirect = redirect;
	}

	@Override
	protected int execute(ShellContext ctx) throws IOException {
		InputStream in = ctx.stdin;
		PrintStream out = ctx.stdout;
		PrintStream err = ctx.stderr;
		List<Closeable> redirected = configureRedirect(ctx, redirect);
		try {
			return statement.process(ctx);
		} finally {
			ctx.stdin = in;
			ctx.stdout = out;
			ctx.stderr = err;
			closeRedirects(redirected);
		}
	}
}
