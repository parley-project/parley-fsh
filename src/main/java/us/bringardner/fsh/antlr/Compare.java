package us.bringardner.fsh.antlr;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

import us.bringardner.fsh.parser.FileSourceShParser.CompareContext;
import us.bringardner.fsh.ShellContext;

public class Compare {

	public CompareContext ctx;

	public Compare(CompareContext ctx) {
		this.ctx = ctx;
	}

	/** [ words ]: the text between the brackets, run as test's arguments */
	private String bracketText;
	private Statement bracketTest;

	/**
	 * A compare that is [ text ]: the words of text are expanded like any command's and tested by
	 * test, as in bash (where [ is a command).
	 */
	public Compare(CompareContext ctx, String bracketText) {
		this.ctx = ctx;
		this.bracketText = bracketText;
	}

	/**
	 * @return the text between [ and ] (in the source), or null if it is not a plain [ words ]
	 * (a [ inside it groups, which is this shell's own syntax: [ [ a ] || [ b ] ])
	 */
	public static String bracketText(TerminalNode open, TerminalNode close) {
		if( open == null || close == null ) {
			return null;
		}
		int start = open.getSymbol().getStopIndex()+1;
		int stop = close.getSymbol().getStartIndex()-1;
		String text = stop >= start ? open.getSymbol().getInputStream().getText(Interval.of(start, stop)) : "";
		return text.trim().startsWith("[") || text.trim().endsWith("]") ? null : text;
	}

	private boolean bracketTest(ShellContext sc) throws IOException {
		if( bracketTest == null ) {
			List<Statement> stmts;
			try {
				stmts = FileSourceShVisitorImpl.parse("__bracket_test "+bracketText);
			} catch (Exception e) {
				throw new TestSyntaxException("syntax error in [ "+bracketText+" ]");
			}
			if( stmts.size() != 1 ) {
				throw new TestSyntaxException("syntax error in [ "+bracketText+" ]");
			}
			bracketTest = stmts.get(0);
		}
		int status = bracketTest.process(sc);
		// 2: not a valid test (the status of [ ] is 2)
		failed |= status == 2;
		return status == 0;
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
		sc.conditionDepth++;
		try {
			return evaluate0(sc);
		} catch (TestSyntaxException e) {
			sc.stderr.println("[: "+e.getMessage());
			failed = true;
			return false;
		} finally {
			sc.conditionDepth--;
		}
	}

	private boolean evaluate0(ShellContext sc) throws IOException {
		if( bracketText != null ) {
			return bracketTest(sc);
		}
		if( ctx.LSQUARE() != null ) {
			String text = bracketText(ctx.LSQUARE(), ctx.RSQUARE());
			if( text != null ) {
				Compare c = new Compare(ctx, text);
				boolean ret = c.bracketTest(sc);
				failed |= c.failed;
				return ret;
			}
		}
		if( ctx.DBL_TEST() != null ) {
			return DoubleBracket.test(ctx.DBL_TEST().getText(), sc) == 0;
		}
		if( ctx.declare != null ) {
			Statement s = new us.bringardner.fsh.antlr.statement.DeclareAssociateArrayStatement(ctx.declare);
			RerdirectImpl r = RerdirectImpl.find(ctx.declare.children);
			if( r != null ) {
				s = new us.bringardner.fsh.antlr.statement.RedirectedStatement(ctx.declare, s, r);
			}
			return s.process(sc) == 0;
		}
		if( ctx.group != null ) {
			// if ( ... ); then: the subshell's (or { ...; }'s) status
			return new FileSourceShVisitorImpl().visitStatement_group1(ctx.group).process(sc) == 0;
		}
		if( ctx.ARITH_COMMAND() != null ) {
			try {
				return Arithmetic.isTrue(Arithmetic.expandAndEvaluate(Arithmetic.body(ctx.ARITH_COMMAND().getText()), sc));
			} catch (Arithmetic.ArithmeticError e) {
				sc.stderr.println("((: "+e.getMessage());
				return false;
			}
		}
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
