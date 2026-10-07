// Generated from FileSourceShPreProcessor.g4 by ANTLR 4.13.2
package us.bringardner.fsh.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link FileSourceShPreProcessorParser}.
 */
public interface FileSourceShPreProcessorListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#ppcode}.
	 * @param ctx the parse tree
	 */
	void enterPpcode(FileSourceShPreProcessorParser.PpcodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#ppcode}.
	 * @param ctx the parse tree
	 */
	void exitPpcode(FileSourceShPreProcessorParser.PpcodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#ppescape}.
	 * @param ctx the parse tree
	 */
	void enterPpescape(FileSourceShPreProcessorParser.PpescapeContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#ppescape}.
	 * @param ctx the parse tree
	 */
	void exitPpescape(FileSourceShPreProcessorParser.PpescapeContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#ppexpr}.
	 * @param ctx the parse tree
	 */
	void enterPpexpr(FileSourceShPreProcessorParser.PpexprContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#ppexpr}.
	 * @param ctx the parse tree
	 */
	void exitPpexpr(FileSourceShPreProcessorParser.PpexprContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#ppcommand}.
	 * @param ctx the parse tree
	 */
	void enterPpcommand(FileSourceShPreProcessorParser.PpcommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#ppcommand}.
	 * @param ctx the parse tree
	 */
	void exitPpcommand(FileSourceShPreProcessorParser.PpcommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pp_backtick_command}.
	 * @param ctx the parse tree
	 */
	void enterPp_backtick_command(FileSourceShPreProcessorParser.Pp_backtick_commandContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_backtick_command}.
	 * @param ctx the parse tree
	 */
	void exitPp_backtick_command(FileSourceShPreProcessorParser.Pp_backtick_commandContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pp_dollar_command}.
	 * @param ctx the parse tree
	 */
	void enterPp_dollar_command(FileSourceShPreProcessorParser.Pp_dollar_commandContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_dollar_command}.
	 * @param ctx the parse tree
	 */
	void exitPp_dollar_command(FileSourceShPreProcessorParser.Pp_dollar_commandContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pp_nested}.
	 * @param ctx the parse tree
	 */
	void enterPp_nested(FileSourceShPreProcessorParser.Pp_nestedContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_nested}.
	 * @param ctx the parse tree
	 */
	void exitPp_nested(FileSourceShPreProcessorParser.Pp_nestedContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pp_dq}.
	 * @param ctx the parse tree
	 */
	void enterPp_dq(FileSourceShPreProcessorParser.Pp_dqContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_dq}.
	 * @param ctx the parse tree
	 */
	void exitPp_dq(FileSourceShPreProcessorParser.Pp_dqContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pp_parameter}.
	 * @param ctx the parse tree
	 */
	void enterPp_parameter(FileSourceShPreProcessorParser.Pp_parameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_parameter}.
	 * @param ctx the parse tree
	 */
	void exitPp_parameter(FileSourceShPreProcessorParser.Pp_parameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pp_param}.
	 * @param ctx the parse tree
	 */
	void enterPp_param(FileSourceShPreProcessorParser.Pp_paramContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_param}.
	 * @param ctx the parse tree
	 */
	void exitPp_param(FileSourceShPreProcessorParser.Pp_paramContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pp_param_dq}.
	 * @param ctx the parse tree
	 */
	void enterPp_param_dq(FileSourceShPreProcessorParser.Pp_param_dqContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_param_dq}.
	 * @param ctx the parse tree
	 */
	void exitPp_param_dq(FileSourceShPreProcessorParser.Pp_param_dqContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#ppvariable}.
	 * @param ctx the parse tree
	 */
	void enterPpvariable(FileSourceShPreProcessorParser.PpvariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#ppvariable}.
	 * @param ctx the parse tree
	 */
	void exitPpvariable(FileSourceShPreProcessorParser.PpvariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorParser#pptext}.
	 * @param ctx the parse tree
	 */
	void enterPptext(FileSourceShPreProcessorParser.PptextContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorParser#pptext}.
	 * @param ctx the parse tree
	 */
	void exitPptext(FileSourceShPreProcessorParser.PptextContext ctx);
}