package us.bringardner.fsh.antlr.statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Arithmetic;
import us.bringardner.fsh.antlr.Statement;

public class ForStatement extends LoopStatement{
	//   : FOR ID IN argument+ SEMI? DO loop_statement+ DONE
	String varName;
	/** for x; do ... (no in): loop over the positional parameters */
	boolean positional;

	public void setPositional(boolean positional) {
		this.positional = positional;
	}
	List<Statement> stmts;
	/** for (( init; condition; step )): arithmetic, each may be empty; null for for name in ... */
	String init;
	String condition;
	String step;

	public String getInit() {
		return init;
	}

	public String getCondition() {
		return condition;
	}

	public String getStep() {
		return step;
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
		if( condition == null) {
			return executeShellStyle(sc);
		} else {
			return executeCStyle(sc);
		}
	}

	private List<String> argsToString(ShellContext ctx) throws IOException {
		List<String> ret = new ArrayList<>();

		for (int idx = 0; idx < args.length; idx++) {

			// the words are already expanded (braces, splitting, *.txt)
			ret.add(""+args[idx].getValue(ctx));
		}
		return ret;

	}
	protected int executeShellStyle(ShellContext sc) throws IOException {
		int ret = 0;
		ShellContext.LoopControl tmp = null;
		List<String> sargs = new ArrayList<>();
		if( positional ) {
			for(Object o : sc.getPositionalParameterValues()) {
				sargs.add(""+o);
			}
		} else {
			sargs = argsToString(sc);
		}
		for(String arg : sargs ) {
			if(ShellContext.LoopControl.Break.equals(tmp)) {
				break;
			}
			Object val = arg;
			// an ordinary variable, as in bash (it stays set after the loop, and read or x=
			// change it); a hidden local one shadowed later assignments
			sc.setVariable(varName, val);


			for(Statement stmt : stmts) {
				try {
					ret = stmt.process(sc);
				} catch(LoopControlException e) {
					// break and continue have status 0
					ret = 0;
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

	public void setLoopControl(String control) {
		String [] parts = Arithmetic.forParts(control);
		init = parts[0];
		condition = parts[1];
		step = parts[2];
	}

	protected int executeCStyle(ShellContext sc) throws IOException {
		int ret = 0;
		ShellContext.LoopControl tmp = null;
		try {
			Arithmetic.expandAndEvaluate(init, sc);
			// an empty condition is true
			while(condition.isBlank() || Arithmetic.isTrue(Arithmetic.expandAndEvaluate(condition, sc))) {
				if(ShellContext.LoopControl.Break.equals(tmp)) {
					break;
				}

				for(Statement stmt : stmts) {
					try {
						ret = stmt.process(sc);
					} catch(LoopControlException e) {
						// break and continue have status 0
						ret = 0;
						if(e.howFar>1) {
							throw new LoopControlException(e.type, e.howFar-1);
						}
						tmp = e.type;
						break;
					}					 				
				}
				if(ShellContext.LoopControl.Break.equals(tmp)) {
					break;
				}
				tmp = null;
				Arithmetic.expandAndEvaluate(step, sc);
			}
		} catch (Arithmetic.ArithmeticError e) {
			sc.stderr.println("((: "+e.getMessage());
			return 1;
		}

		return ret;
	}
	@Override
	protected boolean globWords() {
		return true;
	}
}
