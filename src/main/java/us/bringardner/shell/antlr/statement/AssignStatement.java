package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignmentContext;
import us.bringardner.shell.FsshList;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.Parameter;
import us.bringardner.shell.antlr.Statement;

public class AssignStatement extends Statement{

	String name ;
	public AssignStatement(ParserRuleContext context) {
		super(context);
	}

	public String getName() {
		return name;
	}

	/*


assignStatement
    : LOCAL? id1=ID EQ boolean
    | LOCAL? id1=ID EQ string
    | LOCAL? id1=ID EQ id2=ID
    | LOCAL? id1=ID EQ variable
    | LOCAL? id1=ID EQ expression
    | LOCAL? id1=ID EQ mathExpression
    | LOCAL? id1=ID EQ parameter
    
	 */
	@Override
	protected int execute(ShellContext ctx) throws IOException {
		int ret = 0;
		AssignStatementContext actx = (AssignStatementContext) getContext();
		AssignmentContext assignment = actx.assignment();
		name = assignment.id1.getText();
		Object val = getValue(ctx);
		
		
		
		if( assignment.LOCAL()!=null) {
			ctx.setLocalVariable(name, val);
		} else {
			ctx.setVariable(name, val);
		}
		return ret;
	}

	public Object getValue(ShellContext ctx) throws IOException {
		AssignStatementContext aactx = (AssignStatementContext) getContext();
		AssignmentContext actx = aactx.assignment();
		
		Object val = null;
		
		if(actx.id2 !=null) {
			String vname = actx.id2.getText();
			// TODO:  is this id a variable name or value.  go with value for now.
			val = vname;
		} else if( actx.string()!=null) {
			val =  ctx.expandString(actx.string());		
		} else if( actx.variable()!=null) {
			val =  ctx.getVariable(actx.variable());			
		} else if( actx.expression()!=null) {
			Expression e = new Expression(actx.expression());
			val = e.evaluate(ctx);
		} else if( actx.mathExpression()!=null) {
			Expression e = new Expression(actx.mathExpression().expression());
			val = e.evaluate(ctx);
		} else if( actx.boolean_()!=null) {
			val = actx.boolean_().TRUE() !=null;
		} else if( actx.path()!=null) {
			val = Argument.visit(actx.path(), ctx);
		} else if( actx.parameter()!=null) {
			Parameter p = new Parameter(actx.parameter());
			val= p.evaluate(ctx);
		} else if( actx.arrayInitializer()!=null) {
			List<Object> list = new FsshList();
			for(ArgumentContext ac : actx.arrayInitializer().argument_list().argument()) {
				Argument arg = new Argument(ac);
				Object v = arg.getValue(ctx);
				list.add(v);
			}
			val = list;
		/*
		} else if( actx.list()!=null) {
			List<Object> list = new FsshList();
			for(ArgumentContext ac : actx.list().argument()) {
				Argument arg = new Argument(ac);
				Object v = arg.getValue(ctx);
				list.add(v);
			}
			val = list;
			*/
		} else if( actx.command_substitution()!=null) {
			CommandSubstitutionStatement cs = new CommandSubstitutionStatement(actx.command_substitution());
			if( cs.process(ctx)==0) {
				val = cs.getStdout();
			} else {
				val = cs.getStderr();
			}
		} else {
			throw new RuntimeException("No assignment made "+actx.getText());
		}

		return val;
	}

}
