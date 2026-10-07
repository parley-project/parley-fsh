// Generated from ExprParser.g4 by ANTLR 4.13.2
package us.bringardner.fsh.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ExprParser}.
 */
public interface ExprParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpr(ExprParser.ExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpr(ExprParser.ExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#array_element}.
	 * @param ctx the parse tree
	 */
	void enterArray_element(ExprParser.Array_elementContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#array_element}.
	 * @param ctx the parse tree
	 */
	void exitArray_element(ExprParser.Array_elementContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterConstant(ExprParser.ConstantContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitConstant(ExprParser.ConstantContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#function_call}.
	 * @param ctx the parse tree
	 */
	void enterFunction_call(ExprParser.Function_callContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#function_call}.
	 * @param ctx the parse tree
	 */
	void exitFunction_call(ExprParser.Function_callContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#arguments}.
	 * @param ctx the parse tree
	 */
	void enterArguments(ExprParser.ArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#arguments}.
	 * @param ctx the parse tree
	 */
	void exitArguments(ExprParser.ArgumentsContext ctx);
}