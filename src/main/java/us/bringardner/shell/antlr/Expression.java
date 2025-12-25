package us.bringardner.shell.antlr;

import java.io.IOException;

import us.bringardner.filesource.sh.FileSourceShParser.ExpressionContext;
import us.bringardner.filesource.sh.FileSourceShParser.FactorContext;
import us.bringardner.filesource.sh.FileSourceShParser.TermContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.shell.ShellContext;

public class Expression {

	private ExpressionContext ctx;

	public Expression(ExpressionContext ctx) {
		this.ctx = ctx;
	}
/*

expression
    :
    | simpleTerm=term

    | variable postOp=('++'|'--')
    | preOp=('++'|'--') variable
    | variable op='+=' expression
    | variable op='-=' expression
    | variable op='*=' expression
    | variable op='/=' expression
    | variable op='%=' expression
    | expression op=('+' | '-'| '%') complexTerm=term
       ;

term
    : factor
    | term ('*' | '/' | '%') factor
    ;

factor
    : NUMBER
    | string
    | variable
    | parameter
    | LPAREN expression RPAREN
    ;


 */
	public static enum Operator {
		Add("+"),
		Subtract("-"),
		Multiply("*"),
		Power("**"),
		Divide(":^:"),
		Increment("++"),
		Decrement("--"),
		Modulo("%"),
		AddAssign("+="),
		SubtractAssign("-="),
		MultipleyAssign("*="),
		DivideAssign(":^:="),
		ModuloAssign("%="),
		Undefined("")
		;
		
	    public final String label;

	    private Operator(String label) {
	        this.label = label;
	    }
	}


	public Operator getOperator(String val) {
		for(Operator op : Operator.values()) {
			if(op.label.equals(val)) {
				return op;
			}
		}
		return Operator.Undefined;
	}
	/*
expression
    :
    | simpleTerm=term

    | variable postOp=('++'|'--')
    | preOp=('++'|'--') variable
    | variable op='+=' expression
    | variable op='-=' expression
    | variable op='*=' expression
    | variable op='/=' expression
    | variable op='%=' expression
    | expression op=('+' | '-'| '%') complexTerm=term
       ;
	 */
	public Object evaluate(ShellContext ctx2) throws IOException {
		Object ret = null;
		if( ctx.simpleTerm!=null) {
			ret = evaluateTerm(ctx.simpleTerm,ctx2);
		} else if( ctx.complexTerm!=null) {
			//| expression op=('+' | '-'| '%') term
			Expression e = new Expression(ctx.expression());
			Object v1 = e.evaluate(ctx2);
			Object v2 = evaluateTerm(ctx.complexTerm, ctx2);
			Operator op = getOperator(ctx.op.getText());
			switch (op) {
			case Add: ret = add(v1,v2);break;
			case Subtract: ret = subtract(v1, v2); break;
			case Modulo: ret = modulo(v1, v2);break;
			default:
				throw new IllegalArgumentException("Unexpected value: " + op);
			}
		} else if( ctx.preOp!=null || ctx.postOp !=null) {
			//| variable postOp=('++'|'--')
		    //| preOp=('++'|'--') variable
			String name = ctx.variable().getText();
			Object val1 = ctx2.getVariable(name);
			Object val2 = null;
			String opStr = ctx.preOp!=null?ctx.preOp.getText():ctx.postOp.getText();
			Operator op = getOperator(opStr);
			switch (op) {
			case Increment: val2 = add(val1,1);break;
			case Decrement:	val2 = subtract(val1,1);break;
				
			default:
				throw new IllegalArgumentException("Unexpected value: " + op);
			}
			ctx2.setVariable(ctx.variable(),val2);
			if( ctx.preOp!=null) {
				ret = val2;
			} else {
				ret = val1;
			}
		} else if( ctx.variable()!=null && ctx.op!=null && ctx.expression()!=null) {
/*
     | variable op='+=' expression
    | variable op='-=' expression
    | variable op='*=' expression
    | variable op='/=' expression
    | variable op='%=' expression

 */
			VariableContext ve = ctx.variable();
			//String name = ""+ctx2.getVariable(ve);
			Object v1 = ctx2.getVariable(ve);
			 
			Expression e = new Expression(ctx.expression());
			Object v2 = e.evaluate(ctx2);
			Operator op = getOperator(ctx.op.getText());
			
			switch (op) {
			case AddAssign: ret = add(v1, v2);break;
			case SubtractAssign: ret = subtract(v1, v2);break;
			case MultipleyAssign: ret = multiply(v1, v2);break;
			case DivideAssign: ret = divide(v1, v2);break;
			case ModuloAssign: ret = modulo(v1, v2);break;
			default:
				throw new IllegalArgumentException("Unexpected value: " + op);
			}
			ctx2.setVariable(ctx.variable(), ret);
			
		} else {		
			throw new RuntimeException("invalid expression");
		}
		
		return ret;
	}

	private Object add(Object v1, Object v2) {
		Object ret = null;
		if (v1 instanceof Double || v2 instanceof Double) {
			ret = ((Number)v1).doubleValue()+((Number)v2).doubleValue();
		} else 	if (v1 instanceof Integer || v2 instanceof Integer) {
			ret = ((Number)v1).intValue()+((Number)v2).intValue();
		}  else {
			throw new RuntimeException("What do with "+v1.getClass());
		}
		
		return ret;
	}
	
	private Object subtract(Object v1, Object v2) {
		Object ret = null;
		if (v1 instanceof Double || v2 instanceof Double) {
			ret = ((Number)v1).doubleValue()-((Number)v2).doubleValue();
		} else 	if (v1 instanceof Integer || v2 instanceof Integer) {
			ret = ((Number)v1).intValue()-((Number)v2).intValue();
		}  else {
			throw new RuntimeException("What do with "+v1.getClass());
		}
		
		return ret;
	}
	
	
	/*
term
    : factor
    | term ('*' | '/' | '%') factor
    ;

factor
    : NUMBER
    | STRING
    | variable
    | LPAREN expression RPAREN
    ;

	 */

	private Object evaluateTerm(TermContext term,ShellContext ctx2) throws IOException {
		Object ret = null;
		Object factor = getValue(term.factor(),ctx2);
		if(term.term() ==null || term.op==null) {
			return factor;
		}
		Object term2 = evaluateTerm(term.term(), ctx2);
		Operator op = getOperator(term.op.getText());
		switch (op) {
		case Power:ret = power(term2,factor); break;
		case Multiply:ret = multiply(term2,factor); break;
		case Divide:	ret = divide(term2,factor); break;
		case Modulo:ret = modulo(term2,factor); break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + op);
		}
		
		return ret;
	}

	private Object modulo(Object v1, Object v2) {
		Object ret = null;
		if (v1 instanceof Double || v2 instanceof Double) {
			ret = ((Number)v1).doubleValue()%((Number)v2).doubleValue();
		} else 	if (v1 instanceof Integer || v2 instanceof Integer) {
			ret = ((Number)v1).intValue()%((Number)v2).intValue();
		}  else {
			throw new RuntimeException("mudulo What do with "+v1.getClass());
		}
		
		return ret;
	}
	
	private Object divide(Object v1, Object v2) {
		Object ret = null;
		if (v1 instanceof Double || v2 instanceof Double) {
			ret = ((Number)v1).doubleValue()/((Number)v2).doubleValue();
		} else 	if (v1 instanceof Integer || v2 instanceof Integer) {
			ret = ((Number)v1).intValue()/((Number)v2).intValue();
		}  else {
			throw new RuntimeException("divide What do with "+v1.getClass());
		}
		
		return ret;

	}
	
	private Object power(Object v1, Object v2) {
		Object ret = null;
		if (v1 instanceof Double || v2 instanceof Double) {
			ret = Math.pow(((Number)v1).doubleValue(), ((Number)v2).doubleValue());
		} else 	if (v1 instanceof Integer || v2 instanceof Integer) {
			ret = (int)Math.pow(((Number)v1).doubleValue(), ((Number)v2).doubleValue());
		}  else {
			throw new RuntimeException("power What do with "+v1.getClass());
		}
		
		return ret;

	}
	private Object multiply(Object v1, Object v2) {
		Object ret = null;
		if (v1 instanceof Double || v2 instanceof Double) {
			ret = ((Number)v1).doubleValue()*((Number)v2).doubleValue();
		} else 	if (v1 instanceof Integer || v2 instanceof Integer) {
			ret = ((Number)v1).intValue()*((Number)v2).intValue();
		}  else {
			throw new RuntimeException("multiply What do with "+v1.getClass());
		}
		
		return ret;

	}
	private Object getValue(FactorContext factor, ShellContext ctx2) throws IOException {
		Object ret = null;
		if(factor.NUMBER()!=null) {
			String str = factor.NUMBER().getText();
			if( str.indexOf('.')>=0) {
				ret = Double.parseDouble(str);	
			} else {
				ret = Integer.parseInt(str);
			}
			
		} else if(factor.string()!=null) {
			ret = ctx2.expandString(factor.string()) ;
		} else if(factor.variable()!=null) {
			ret = ctx2.getVariable(factor.variable());						
		} else if(factor.expression()!=null) {
			ExpressionContext ectx = factor.expression();
			
			
			Expression e = new Expression(ectx);
			ret = e.evaluate(ctx2);
			if( ret == null) {
				ret = ectx.getText();
			}
			
		} else if(factor.parameter()!=null) {
			Parameter p = new Parameter(factor.parameter());
			ret = p.evaluate(ctx2);
		} else {
			throw new RuntimeException("invalid factor="+factor.getText());
		}
		return ret;
	}

	
}
