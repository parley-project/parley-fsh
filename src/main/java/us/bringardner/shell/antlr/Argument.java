package us.bringardner.shell.antlr;

import java.io.IOException;

import us.bringardner.filesource.sh.FileSourceShParser.Arg_command_substitutionContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssociativeArrayValueContext;
import us.bringardner.filesource.sh.FileSourceShParser.MathExpressionContext;
import us.bringardner.filesource.sh.FileSourceShParser.ParameterContext;
import us.bringardner.filesource.sh.FileSourceShParser.PathContext;
import us.bringardner.filesource.sh.FileSourceShParser.Path_segmentContext;
import us.bringardner.filesource.sh.FileSourceShParser.StringContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.CommandSubstitutionStatement;

public class Argument {

	ArgumentContext context;
	String value;

	public Argument(String value) {
		this.value = value;
	}

	public Argument(ArgumentContext ctx) {
		context = ctx;
	}

	/*

	
argument:
      ARG_ID 
    | arg_command_substitution
    | signed_number
    | NUMBER    
   	| braceExpansion
   	| TEXT
    | string         
    | assignStatement            
    | mathExpression
    | parameter
	| path	
	| ID
	| variable
	| PERC

	 */
	public boolean hasValue() {
		return value !=null;
	}
	
	public Object getValue(ShellContext ctx) throws IOException {
		if( value !=null) {
			return value;
		}
		
		Object ret = context.getText().trim();

		if( context.braceExpansion()!=null) {
			throw new IOException("brace expantion must be done before calling getValue");
		}

		if( context.ARG_ID()!=null) {
			ret = context.ARG_ID().getText().trim();
		} else if( context.ID()!=null) {
			String name = context.ID().getText();
			ret = name;
		} else if( context.variable()!= null) {			
			ret = visit(context.variable(),ctx); 
		} else if( context.mathExpression()!= null ) {
			ret = visit(context.mathExpression(),ctx);
		} else if(context.string()!=null) {
			ret = ctx.expandString(context.string());			
		} else if(context.parameter()!=null) {
			ret = visit(context.parameter(),ctx);
		} else if(context.TEXT()!=null) {
			ret = context.TEXT().getText();
		} else if(context.signed_number()!=null) {
			ret = context.signed_number().getText();
		} else if(context.NUMBER()!=null) {
			ret = context.NUMBER().getText();
		} else if(context.PERC()!=null) {
			ret = context.PERC().getText();		
		} else if(context.path()!=null) {
			ret = visit(context.path(),ctx);						
		} else if(context.arg_command_substitution()!=null) {
			ret = visit(context.arg_command_substitution(),ctx);
		} else if(context.assignStatement()!=null ) {
			ret = visit(context.assignStatement(),ctx);			
		}  else {
			throw new RuntimeException("Not a valid argument "+context.getText());
		}
		

		return ret;
	}

	public static Object visit(AssignStatementContext assignStatement, ShellContext ctx) {
		String ret = assignStatement.getText();
		if( ret.indexOf('$')>=0) {
			ret = FileSourceShPreProcessorVisitorImpl.processString(ret, ctx);
		}
		return ret;
	}

	public Object visit(Arg_command_substitutionContext arg_command_substitution, ShellContext ctx) {
		Object ret  = null;
		CommandSubstitutionStatement cs = new CommandSubstitutionStatement(arg_command_substitution);
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
		return ret;
	}

	public static Object visit(ParameterContext parameter, ShellContext ctx) {
		Parameter p = new Parameter(parameter);
		return p.evaluate(ctx);
	}

	public static Object visit(MathExpressionContext mathExpression, ShellContext ctx) {
		Expression expr = new Expression(mathExpression.expression());
		return expr.evaluate(ctx);
		
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
		if( value !=null) {
			return value;
		}
		return context.getText();
	}

	/*

associativeArrayValue
    : string
    | NUMBER
    | boolean
    | variable
    | mathExpression
    | parameter
    ;
	 */
	public static String visit(AssociativeArrayValueContext context, ShellContext ctx) throws IOException {
		String ret = context.getText();
		
	
		if( context.variable()!= null) {
			ret = visit(context.variable(), ctx);
		} else if( context.NUMBER()!=null) {
			ret = context.getText();
		} else if( context.boolean_()!=null) {
			ret = context.getText();
		} else if( context.mathExpression()!=null) {
			ret = ""+visit(context.mathExpression(),ctx);
		} else if( context.parameter()!=null) {
			ret = ""+visit(context.parameter(),ctx);
		} else {
			throw new IOException("Invalid AssociativeArrayValueContext "+context);
		}
		
			
		return ret;
	}



}
