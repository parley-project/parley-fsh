package us.bringardner.shell.antlr;

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
	public boolean evaluate(ShellContext sc) {
		if( ctx.simpleCompare!=null) {
			Compare tmp = new Compare(ctx.simpleCompare);
			return tmp.evaluate(sc);
		}
		
		if( ctx.compare_prime()!=null) {
			ComparePrime tmp = new ComparePrime(ctx.compare_prime());
			return tmp.evaluate(sc);
		} 
		if( ctx.NOT()!=null) {
			Compare tmp = new Compare(ctx.notCompare);
			return !tmp.evaluate(sc);
		}
		
		Compare left = new Compare(ctx.left);
		Compare right = new Compare(ctx.right);
		if( ctx.AND()!=null) {
			return left.evaluate(sc) && right.evaluate(sc);
		}
		
		if( ctx.OR()!=null) {
			boolean l1 = left.evaluate(sc);
			boolean l2 = right.evaluate(sc);
			return l1 || l2;
		}
		
		
		throw new RuntimeException("Invalide compare"+ctx.getText());
	}
	
	
}
