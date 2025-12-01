package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Compare;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.Statement;

public class ForStatement extends LoopStatement{
	//   : FOR ID IN argument+ SEMI? DO loop_statement+ DONE
	String varName;
	List<Statement> stmts;
	AssignStatement assign;
	Compare compare;
	Expression expr;


	public AssignStatement getAssign() {
		return assign;
	}

	public void setAssign(AssignStatement assign) {
		this.assign = assign;
	}

	public Compare getCompare() {
		return compare;
	}

	public void setCompare(Compare compare) {
		this.compare = compare;
	}

	public Expression getExpr() {
		return expr;
	}

	public void setExpr(Expression expr) {
		this.expr = expr;
	}

	public String getVarName() {
		return varName;
	}

	public void setVarName(String varName) {
		this.varName = varName;
	}

	public List<Statement> getStmts() {
		return stmts;
	}

	public void setStmts(List<Statement> stmts) {
		this.stmts = stmts;
	}



	public ForStatement(ParserRuleContext context) {
		super(context);
	}

	protected int execute(ShellContext sc) throws IOException {
		if( assign == null) {
			return executeShellStyle(sc);
		} else {
			return executeCStyle(sc);
		}
	}

	private List<String> argsToString(ShellContext ctx) throws IOException {
		List<String> ret = new ArrayList<>();

		for (int idx = 0; idx < args.length; idx++) {

			Argument a = args[idx];
			String val = ""+a.getValue(ctx);

			ArgumentContext actx = a.getContext();
			//TODO: probably need a more comprehensive way to generate file lists
			if( actx !=null && actx.path() !=null) {
				List<FileSource> list = ShellCommand.getFiles(ctx, val);
				if( list.size()>0) {
					for(FileSource file : list) {
						//String name = val.replaceAll("\\*", file.getName());
						String name = file.getName();
						ret.add(name);						
					}		
				} 				
			} else {
				ret.add(val);
			}
		}
		return ret;

	}
	protected int executeShellStyle(ShellContext sc) throws IOException {
		int ret = 0;
		ShellContext.LoopControl tmp = null;
		List<String> sargs = argsToString(sc);
		for(String arg : sargs ) {
			if(ShellContext.LoopControl.Break.equals(tmp)) {
				break;
			}
			Object val = arg;
			sc.setLocalVariable(varName, val);	


			for(Statement stmt : stmts) {
				try {
					ret = stmt.process(sc);
				} catch(LoopControlException e) {
					if(e.howFar>1) {
						throw new LoopControlException(e.type, e.howFar-1);
					}
					tmp = e.type;
					break;
				}					 				
			}						
		}

		return ret;
	}

	public void setLoopControl(AssignStatement assign,Compare compare,Expression expr) {
		this.assign = assign;
		this.compare = compare;
		this.expr = expr;
	}

	protected int executeCStyle(ShellContext sc) throws IOException {
		int ret = 0;
		ShellContext.LoopControl tmp = null;
		assign.execute(sc);
		while(compare.evaluate(sc)) {
			if(ShellContext.LoopControl.Break.equals(tmp)) {
				break;
			}

			for(Statement stmt : stmts) {
				try {
					ret = stmt.process(sc);
				} catch(LoopControlException e) {
					if(e.howFar>1) {
						throw new LoopControlException(e.type, e.howFar-1);
					}
					tmp = e.type;
					break;
				}					 				
			}
			expr.evaluate(sc);
		}

		return ret;
	}
}
