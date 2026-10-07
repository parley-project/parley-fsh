package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.CompareStatementContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Compare;
import us.bringardner.shell.antlr.Statement;

public class IfStatement extends Statement{

	private List<Compare> compare;
	private List<List<Statement>> stmtsList;

	
	public List<Compare> getCompare() {
		return compare;
	}

	public void setCompare(List<Compare> compare) {
		this.compare = compare;
	}

	public List<List<Statement>> getStmtsList() {
		return stmtsList;
	}

	public void setStmtsList(List<List<Statement>> stmtsList) {
		this.stmtsList = stmtsList;
	}

	public IfStatement(ParserRuleContext ctx, List<Compare> compare, List<List<Statement>> stmtsList) {
		super(ctx);
		this.compare = compare;
		this.stmtsList = stmtsList;
	}

/*
ifStatement
    : IF LSQUARE compare RSQUARE SEMI THEN statement_block
    	(ELIF LSQUARE compare RSQUARE SEMI THEN statement_block)* 
    	(ELSE statement_block)? 
      FI
    ;
	
 */
	@Override
	protected int execute(ShellContext sc) throws IOException {
		int ret = 0;
		int numCmp = compare.size();
		int numStm = stmtsList.size();
		// [ test ] alone: its status is the result
		boolean test = getContext() instanceof CompareStatementContext;
		if( numCmp >0) {
			int matched = numCmp;
			boolean failed = false;
			
			for(int idx=0,sz=compare.size(); idx < sz ; idx++) {
				Compare c = compare.get(idx);
				if( c.evaluate(sc)) {
					matched = idx;
					break;
				}
				failed |= c.failed;
			}
			if( matched < numStm) {
				// as in bash, a failed command does not stop the rest; the status is the last one's
				for(Statement s : stmtsList.get(matched)) {
					ret = s.process(sc);
				}
			} else if( test && matched == numCmp ) {
				ret = failed ? 2 : 1;
			}
			
		}
		return ret;
	}


}
