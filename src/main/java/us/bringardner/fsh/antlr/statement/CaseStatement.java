package us.bringardner.fsh.antlr.statement;

import us.bringardner.fsh.parser.FileSourceShParser.ArgumentPartContext;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.fsh.parser.FileSourceShParser.CaseStatementContext;
import us.bringardner.fsh.parser.FileSourceShParser.PatternContext;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Argument;
import us.bringardner.fsh.antlr.Statement;

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

		// ;; ends the case, ;& runs the next clause's commands as well, ;;& goes on testing the
		// next patterns; the status is that of the last commands run
		boolean fallInto = false;
		for(int idx=0,sz=clouses.size(); idx < sz; idx++ ) {
			CaseClause cc = clouses.get(idx);
			if( fallInto || matches(val,cc.pattarns,sc)) {
				ret = execute(cc.stmts,sc);
				if( cc.op == null || cc.op == Operator.Stop ) {
					return ret;
				}
				fallInto = cc.op == Operator.FallThrough;
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
