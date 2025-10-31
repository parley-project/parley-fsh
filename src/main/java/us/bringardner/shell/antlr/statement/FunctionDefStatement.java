package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import us.bringardner.filesource.sh.FileSourceShParser.FunctionDefinitionContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.DebugContext.RunState;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Statement;

public class FunctionDefStatement extends Statement{

	private String name;
	private List<Statement> stmts;
	private boolean exported=false;
	

	public FunctionDefStatement(FunctionDefinitionContext ctx, String name, List<Statement> stmts) {
		super(ctx);
		this.name = name;
		this.stmts = stmts;
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
		ctx.addFunction(this);
		return ret;
	}

	public int invoke(Argument[] args, ShellContext ctx) throws IOException {
		int ret = 0;
		RunState debug = ctx.console.getDebugContext().getCurrentState();
		if( debug == RunState.StepOver) {
			ctx.console.getDebugContext().setCurrentState(RunState.Running);
		}
		ctx.enterFunction(args,this);
		try {
			for(Statement s : stmts) {				
				ret=s.process(ctx);
			}
		} finally {
			ctx.exitFunction(this);
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
