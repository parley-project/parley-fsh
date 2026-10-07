package us.bringardner.shell.antlr.statement;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentPartContext;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.filesource.sh.FileSourceShParser.CaseStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.PatternContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
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
pattern: argument ;
	 */
	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;
		CaseStatementContext ctx = (CaseStatementContext) getContext();
		String val = ""+new Argument(ctx.subject).getValue(sc);

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
		// as in bash, a failed command does not stop the rest
		for(Statement s : stmts) {
			ret = s.process(sc);
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

pattern: argument ;
	 */
	private boolean matches(String val,PatternContext p, ShellContext sc) throws IOException {
		// the pattern is a word: its quoted parts are text, the rest ($x too) is a glob
		StringBuilder rx = new StringBuilder();
		StringBuilder glob = new StringBuilder();
		for(ArgumentPartContext part : p.argument().argumentPart()) {
			if( part.string() != null ) {
				rx.append(ShellCommand.prepWildCards(glob.toString(), true));
				glob.setLength(0);
				rx.append(Pattern.quote(sc.expandString(part.string())));
			} else {
				// the unquoted parts together ([A-Z] is several parts)
				glob.append(Argument.getValue(part, sc));
			}
		}
		rx.append(ShellCommand.prepWildCards(glob.toString(), true));
		Boolean nocase = sc.console.getShellOptions().get("nocasematch");
		Pattern pattern = Pattern.compile(rx.toString(), Pattern.DOTALL | (Boolean.TRUE.equals(nocase) ? Pattern.CASE_INSENSITIVE : 0));
		Matcher m = pattern.matcher(val);

		return m.matches();
	}

}
