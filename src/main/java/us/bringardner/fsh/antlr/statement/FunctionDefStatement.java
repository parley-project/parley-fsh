package us.bringardner.fsh.antlr.statement;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.parser.FileSourceShParser.FunctionDefinitionContext;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.DebugContext.RunState;
import us.bringardner.fsh.antlr.Argument;
import us.bringardner.fsh.antlr.Statement;
import us.bringardner.fsh.antlr.signal.ReturnException;

public class FunctionDefStatement extends Statement{

	private String name;
	private List<Statement> stmts;
	private boolean exported=false;
	

	public FunctionDefStatement(FunctionDefinitionContext ctx, String name, List<Statement> stmts) {
		super(ctx);
		this.name = name;
		this.stmts = stmts;
	}


	/**
	 * The function as declare -f and type print it, in bash's layout:
	 * <pre>
	 * f () 
	 * { 
	 *     echo hi;
	 *     local x=1
	 * }
	 * </pre>
	 */
	public String declaration() {
		us.bringardner.fsh.parser.FileSourceShParser.FunctionDefinitionContext fd =
				(us.bringardner.fsh.parser.FileSourceShParser.FunctionDefinitionContext) getContext();
		us.bringardner.fsh.parser.FileSourceShParser.CompoundCommandContext cc = fd.compoundCommand();
		List<String> lines = new java.util.ArrayList<>();
		if( cc.subshell != null ) {
			lines.add(text(cc.subshell, cc.RPAREN().getSymbol()));
		} else {
			for(us.bringardner.fsh.parser.FileSourceShParser.StatementContext s : cc.statement()) {
				String t = text(s.getStart(), s.getStop()).trim();
				while( t.endsWith(";")) {
					t = t.substring(0, t.length()-1).trim();
				}
				if( !t.isEmpty()) {
					lines.add(t);
				}
			}
		}
		StringBuilder ret = new StringBuilder(getName()+" () \n{ \n");
		for (int idx = 0; idx < lines.size(); idx++) {
			ret.append("    ").append(lines.get(idx).replace("\n", "\n    ")).append(idx < lines.size()-1 ? ";" : "").append('\n');
		}
		ret.append('}');
		if( cc.redirect2 != null ) {
			ret.append(' ').append(text(cc.redirect2.getStart(), cc.redirect2.getStop()).trim());
		}
		return ret.toString();
	}

	/** the source text from start to stop, as written */
	private static String text(org.antlr.v4.runtime.Token start, org.antlr.v4.runtime.Token stop) {
		if( start == null || stop == null || stop.getStopIndex() < start.getStartIndex()) {
			return "";
		}
		return start.getInputStream().getText(org.antlr.v4.runtime.misc.Interval.of(start.getStartIndex(), stop.getStopIndex()));
	}

	public String getName() {
		return name;
	}



	public void setName(String name) {
		this.name = name;
	}



	public List<Statement> getStmts() {
		return stmts;
	}



	public void setStmts(List<Statement> stmts) {
		this.stmts = stmts;
	}



	@Override
	protected int execute(ShellContext ctx) throws IOException {
		int ret = 0;
		if( ctx.console.isReadonlyFunction(getName())) {
			ctx.stderr.println(getName()+": readonly function");
			return 1;
		}
		ctx.addFunction(this);
		return ret;
	}

	public int invoke(Argument[] args, ShellContext ctx) throws IOException {
		int ret = 0;
		RunState debug = ctx.console.getDebugContext().getCurrentState();
		if( debug == RunState.StepOver) {
			ctx.console.getDebugContext().setCurrentState(RunState.Running);
		}
		//  functions args must be evaluated before function stack is updated
		Object [] tmp = new Object[args.length];
		for (int idx = 0; idx < args.length; idx++) {
			tmp[idx] = args[idx].getValue(ctx);
		}
		ctx.enterFunction(tmp,this);
		try {
			for(Statement s : stmts) {				
				ret=s.process(ctx);
			}
		} catch (ReturnException e) {
			ret = e.exitCode;
		} finally {
			try {
				ctx.console.setLastExitCode(ret);
				ctx.functionReturning();
			} finally {
				ctx.exitFunction(this);
			}
		}
		if( debug == RunState.StepOver) {
			ctx.console.getDebugContext().setCurrentState(RunState.StepOver);
		}
		
		return ret;
	}


	public boolean isExported() {
		return exported;
	}


	public void setExported(boolean exported) {
		this.exported = exported;
	}

}
