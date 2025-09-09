package us.bringardner.shell.antlr;

import java.io.IOException;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.PathContext;
import us.bringardner.filesource.sh.FileSourceShParser.Path_segmentContext;
import us.bringardner.filesource.sh.FileSourceShParser.StringContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.CommandSubstitutionStatement;

public class Argument {

	ArgumentContext context;

	public Argument(ArgumentContext ctx) {
		context = ctx;
	}

	/*

argument
    : TEXT
    | string
    | MINUS? ID
    | variable
    | NUMBER
    | path
    | mathExpression
    | parameter
    ;


	 */
	public Object getValue(ShellContext ctx) throws IOException {
		Object ret = context.getText();

		if( context.ARG_ID()!=null) {
			ret = context.ARG_ID().getText();
		} else if( context.ID()!=null) {
			String name = context.ID().getText();
			//ret = ctx.getVariable(name);
			//if( ret == null ) {
			ret = name;
			//}
		} else if( context.variable()!= null) {
			ret = ctx.getVariable(context.variable());			
		} else if( context.mathExpression()!= null ) {
			Expression expr = new Expression(context.mathExpression().expression());
			ret = expr.evaluate(ctx);
		} else if(context.string()!=null) {
			ret = ctx.expandString(context.string());			
		} else if(context.parameter()!=null) {
			Parameter p = new Parameter(context.parameter());

			ret = p.evaluate(ctx);
		} else if(context.TEXT()!=null) {
			ret = context.TEXT().getText();
		} else if(context.signed_number()!=null) {
			ret = context.signed_number().getText();
		} else if(context.path()!=null) {
			ret = visit(context.path(),ctx);						
		} else if(context.arg_command_substitution()!=null) {
			CommandSubstitutionStatement cs = new CommandSubstitutionStatement(context.arg_command_substitution());
			try {
				if( cs.process(ctx)==0) {
					ret = cs.getStdout();
				} else {
					ret = cs.getStderr();
				}				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if(context.assignStatement()!=null ) {
			ret = context.getText();			
		} else if(context.operator()!=null) {			
			ret = context.getText();
		} else {

			throw new RuntimeException("Not a valid argument "+context.getText());
		}
		

		return ret;
	}

	//path:  (path_segment| SLASH)+
	public static String visit(PathContext path, ShellContext ctx) {
		return path.getText();
	}

	/*
	 * path_segment: 
		  TILDE 
		| ID
		| variable
        | DOT_DOT
        | DOT
        | STAR
        | QUESTION
        | string
        | MINUS
        | MINUS_MINUS
		;

	 */
	public static String visit(Path_segmentContext path_segment, ShellContext ctx) {
		if( path_segment.string() != null ) {
			return visit(path_segment.string(), ctx);
		} //else if( path_segment.variable()!=null ) {
			//return visit(path_segment.variable(),ctx);
		//} 
		return path_segment.getText();
	}

	/*
	 * variable:
        idOnly=ID ( associative_index | array_index)?
        |VARIABLE (associative_index | array_index)?

	 */
	public static String visit(VariableContext variable, ShellContext ctx) {
		Object obj = ctx.getVariable(variable);
		String ret = ""+obj;
		return ret;
	}

	//string : DQ_STRING | SQ_STRING | ESC;
	public static String visit(StringContext string, ShellContext ctx) {
		String ret = ctx.expandString(string);
		
		return ret;
	}

	public ArgumentContext getContext() {
		return context;
	}

	public void setContext(ArgumentContext Context) {
		this.context = Context;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Argument) {
			Argument other = (Argument) obj;
			return context.getText().equals(other.context.getText());
		}

		return false;
	}

	@Override
	public String toString() {
		return context.getText();
	}



}
