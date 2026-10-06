package us.bringardner.shell.antlr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.Arg_command_substitutionContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentPartContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssociativeArrayValueContext;
import us.bringardner.filesource.sh.FileSourceShParser.BraceExpansionContext;
import us.bringardner.filesource.sh.FileSourceShParser.BraceRangeContext;
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
argument: argumentPart+ ;

argumentPart:
      literal=(ID | NUMBER | ARG_ID | TEXT | SLASH | ... )
    | string
    | argVariable
    | parameter
    | mathExpression
    | arg_command_substitution
    | braceExpansion
    ;
	 */
	public boolean hasValue() {
		return value !=null;
	}
	
	/**
	 * @return the value of the word: the value of its only part (which may be a number or a list),
	 * or the text of all its parts joined together
	 */
	public Object getValue(ShellContext ctx)  {
		if( value !=null) {
			return value;
		}

		List<ArgumentPartContext> parts = context.argumentPart();
		if( parts.size() == 1) {
			return getValue(parts.get(0), ctx);
		}
		StringBuilder ret = new StringBuilder();
		for(ArgumentPartContext part : parts) {
			ret.append(getValue(part, ctx));
		}
		return ret.toString();
	}

	public static Object getValue(ArgumentPartContext part, ShellContext ctx)  {
		Object ret;
		if( part.literal != null) {
			ret = part.literal.getText();
		} else if( part.argVariable()!= null) {			
			ret = ""+ctx.getVariable(part.argVariable()); 
		} else if(part.string()!=null) {
			ret = ctx.expandString(part.string());			
		} else if(part.parameter()!=null) {
			ret = visit(part.parameter(),ctx);
		} else if( part.mathExpression()!= null ) {
			ret = visit(part.mathExpression(),ctx);
		} else if(part.arg_command_substitution()!=null) {
			ret = visit(part.arg_command_substitution(),ctx);
		} else if( part.braceExpansion()!=null) {
			throw new RuntimeException("brace expantion must be done before calling getValue");
		} else {
			throw new RuntimeException("Not a valid argument "+part.getText());
		}
		return ret;
	}

	/**
	 * @return true if the word has an unquoted * or ?, so it names files
	 */
	public boolean hasUnquotedWildcard() {
		if( context != null ) {
			for(ArgumentPartContext part : context.argumentPart()) {
				if( part.literal != null ) {
					int type = part.literal.getType();
					if( type == FileSourceShParser.STAR || type == FileSourceShParser.QUESTION) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Brace expansion of a word: prefix{a,b}suffix gives prefixasuffix and prefixbsuffix.
	 * 
	 * @return the words, or null if the word has no braces
	 */
	public static List<String> expandBraces(ArgumentContext word, ShellContext ctx) throws IOException {
		List<ArgumentPartContext> parts = word.argumentPart();
		int braceIdx = -1;
		for (int idx = 0; idx < parts.size() && braceIdx < 0; idx++) {
			if( parts.get(idx).braceExpansion() != null ) {
				braceIdx = idx;
			}
		}
		if( braceIdx < 0 ) {
			return null;
		}
		StringBuilder prefix = new StringBuilder();
		for (int idx = 0; idx < braceIdx; idx++) {
			prefix.append(getValue(parts.get(idx), ctx));
		}
		StringBuilder suffix = new StringBuilder();
		for (int idx = braceIdx+1; idx < parts.size(); idx++) {
			ArgumentPartContext part = parts.get(idx);
			// only the first braces are expanded
			suffix.append(part.braceExpansion() != null ? part.getText() : getValue(part, ctx));
		}
		List<String> ret = new ArrayList<>();
		for(String val : expandBraces(parts.get(braceIdx).braceExpansion(), ctx)) {
			ret.add(prefix+val+suffix);
		}
		return ret;
	}

	//	braceExpansion: LCURLY (braceRange|braceArgList) RCURLY
	private static List<String> expandBraces(BraceExpansionContext exp, ShellContext ctx) throws IOException {
		if( exp.braceRange()!=null) {
			return expandBraces(exp.braceRange(),ctx);
		} else if(exp.braceArgList()!=null) {
			List<String> ret = new ArrayList<>();
			for(AssociativeArrayValueContext arg : exp.braceArgList().associativeArrayValue()) {
				ret.add(braceItem(arg, ctx));
			}
			return ret;
		} else {
			throw new IOException("Invalid brace expantion "+exp.getText());
		}
	}

	/**
	 * A bare name in braces is text, as in bash ({a,b} and {a..z} do not read variables a and b).
	 */
	private static String braceItem(AssociativeArrayValueContext item, ShellContext ctx) throws IOException {
		if( item.variable() != null && item.variable().idOnly != null ) {
			return item.getText();
		}
		return visit(item, ctx);
	}

	//	braceRange: start=associativeArrayValue DOT_DOT end=associativeArrayValue (DOT_DOT incr=associativeArrayValue);
	private static List<String> expandBraces(BraceRangeContext range, ShellContext ctx) throws IOException {
		List<String>  ret = new ArrayList<>();
		String startStr = braceItem(range.start, ctx);
		String endStr   = braceItem(range.end, ctx);
		boolean isChar = Character.isLetter(startStr.charAt(0));
		int start = isChar?startStr.charAt(0): Integer.parseInt( startStr);
		int end = isChar?endStr.charAt(0): Integer.parseInt( endStr);

		int inc = 1;
		if( !isChar ) {
			if( range.incr !=null) {
				String tmp = visit(range.incr, ctx);
				inc = Math.abs(Integer.parseInt(tmp));
				if( inc == 0 ) {
					// bash treats an increment of 0 as 1
					inc = 1;
				}
			}
		}

		if(start < end) {
			for(int idx=start; idx <=end; idx += inc) {
				ret.add(isChar ? ""+((char)idx) : ""+idx);
			}
		} else {
			for(int idx=start; idx >=end; idx -= inc) {
				ret.add(isChar ? ""+((char)idx) : ""+idx);
			}
		}

		return ret;
	}

	public static Object visit(AssignStatementContext assignStatement, ShellContext ctx)  {
		String ret = assignStatement.getText();
		if( ret.indexOf('$')>=0) {
			ret = FileSourceShPreProcessorVisitorImpl.processString(ret, ctx);
		}
		return ret;
	}

	public static Object visit(Arg_command_substitutionContext arg_command_substitution, ShellContext ctx)  {
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

	public static Object visit(ParameterContext parameter, ShellContext ctx)  {
		Parameter p = new Parameter(parameter);
		return p.evaluate(ctx);
	}

	public static Object visit(MathExpressionContext mathExpression, ShellContext ctx)  {
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
	public static String visit(Path_segmentContext path_segment, ShellContext ctx) throws IOException {
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
	public static String visit(VariableContext variable, ShellContext ctx)  {
		Object obj = ctx.getVariable(variable);
		String ret = ""+obj;
		return ret;
	}

	//string : DQ_STRING | SQ_STRING | ESC;
	public static String visit(StringContext string, ShellContext ctx)  {
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
