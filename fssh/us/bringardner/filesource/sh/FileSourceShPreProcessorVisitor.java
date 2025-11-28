// Generated from /Volumes/Data/eclipse-git/BjlShell/Antlr4/FileSourceShPreProcessor.g4 by ANTLR 4.13.2
package us.bringardner.filesource.sh;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link FileSourceShPreProcessorParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface FileSourceShPreProcessorVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#ppcode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPpcode(FileSourceShPreProcessorParser.PpcodeContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#ppexpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPpexpr(FileSourceShPreProcessorParser.PpexprContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#ppcommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPpcommand(FileSourceShPreProcessorParser.PpcommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_backtick_command}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPp_backtick_command(FileSourceShPreProcessorParser.Pp_backtick_commandContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_dollar_command}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPp_dollar_command(FileSourceShPreProcessorParser.Pp_dollar_commandContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#pp_parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPp_parameter(FileSourceShPreProcessorParser.Pp_parameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#ppvariable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPpvariable(FileSourceShPreProcessorParser.PpvariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorParser#pptext}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPptext(FileSourceShPreProcessorParser.PptextContext ctx);
}