package us.bringardner.shell.antlr;

import java.io.IOException;

import us.bringardner.filesource.sh.FileSourceShParser.CompareContext;
import us.bringardner.shell.ShellContext;

public class Compare {

	public CompareContext ctx;

	public Compare(CompareContext ctx) {
		this.ctx = ctx;
	}

	/*
compare : LSQUARE compare_prime RSQUARE
		| NOT compare
		| left=compare AND right=compare
		| left=compare OR right=compare
		;
		
	 */
	/** a [ ] that is not a valid test (as in bash: a message and status 2) */
	public static class TestSyntaxException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public TestSyntaxException(String msg) {
			super(msg);
		}
	}

	/** true if the last evaluate found a [ ] that is not a valid test */
	public boolean failed;

	public boolean evaluate(ShellContext sc) throws IOException {
		failed = false;
		try {
			return evaluate0(sc);
		} catch (TestSyntaxException e) {
			sc.stderr.println("[: "+e.getMessage());
			failed = true;
			return false;
		}
	}

	private boolean evaluate0(ShellContext sc) throws IOException {
		if( ctx.simpleCompare!=null) {
			return new Compare(ctx.simpleCompare).evaluate0(sc);
		}
		
		if( ctx.compare_prime()!=null) {
			ComparePrime tmp = new ComparePrime(ctx.compare_prime());
			return tmp.evaluate(sc);
		} 
		if( ctx.NOT()!=null) {
			return !new Compare(ctx.notCompare).evaluate0(sc);
		}
		
		// the right side runs only if it decides the result (true || cmd does not run cmd)
		if( ctx.AND()!=null) {
			return new Compare(ctx.left).evaluate0(sc) && new Compare(ctx.right).evaluate0(sc);
		}
		
		if( ctx.OR()!=null) {
			return new Compare(ctx.left).evaluate0(sc) || new Compare(ctx.right).evaluate0(sc);
		}
		
		throw new RuntimeException("Invalide compare"+ctx.getText());
	}
}
