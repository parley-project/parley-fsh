package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.filesource.sh.FileSourceShParser.CaseStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.PatternContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.Statement;

public class CaseStatement extends Statement{
	//              ;;     ;&         ;;&
	public enum Operator {Stop,FallThrough,Continue}



	public static class CaseClause {
		public List<PatternContext> pattarns;
		public List<Statement> stmts;
		public Operator op;
	}

	private List<CaseClause> clouses;

	public CaseStatement(CaseStatementContext ctx, List<CaseClause> clouses) {		
		super(ctx);
		this.clouses = clouses;
	}



	/*

caseClause
    :   patternList NL? RPAREN NL? commandList NL? op=';;' NL?
    |   patternList NL? RPAREN NL? commandList NL? op=';&' NL?
    |   patternList NL? RPAREN NL? commandList NL? op=';;&' NL?
    ;
commandList: commandStatement+ ;
patternList
    :   pattern (PIPE pattern)*
    ;
pattern
    :   ID
    | expression
    |  '*'
    ;
	 */
	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;
		CaseStatementContext ctx = (CaseStatementContext) getContext();
		Expression expr = new Expression(ctx.expression());
		String val = ""+expr.evaluate(sc);

		for(int idx=0,sz=clouses.size(); idx < sz; idx++ ) {
			CaseClause cc = clouses.get(idx);

			if( matches(val,cc.pattarns,sc)) {
				int ev = execute(cc.stmts,sc);
				if(ev !=0 || cc.op == Operator.Stop) {
					return ev;
				}
				if( cc.op == Operator.FallThrough) {
					if( idx < (sz-1)) {
						CaseClause cc2 = clouses.get(idx+1);
						int ev2 = execute(cc2.stmts,sc);
						if( ev2 !=0 ) {
							return ev2;
						}
					}
					return 0;
				} else {
					//  continue and test next
				}
			}			
		}

		return ret;
	}



	private int execute(List<Statement> stmts, ShellContext sc) throws IOException {
		int ret = 0;
		for(Statement s : stmts) {
			if((ret=s.process(sc))!=0) {
				break;
			}
		}
		return ret;
	}



	private boolean matches(String val, List<PatternContext> patterns, ShellContext sc) throws IOException {
		boolean ret = false;
		for(PatternContext p : patterns) {
			if((ret=matches(val,p,sc))) {
				break;
			}
		}
		return ret;
	}


	/*

pattern
    :   ID
    | expression
    |  '*'
    ;
	 */
	private boolean matches(String val,PatternContext p, ShellContext sc) throws IOException {
		String pat = p.getText();
		if( p.expression()!=null) {
			Expression e = new Expression(p.expression());
			pat = ""+e.evaluate(sc);
		}
		String preped = ShellCommand.prepWildCards(pat,false);
		Pattern rx = Pattern.compile(preped);
		Matcher m = rx.matcher(val);

		return m.matches();
	}

}
