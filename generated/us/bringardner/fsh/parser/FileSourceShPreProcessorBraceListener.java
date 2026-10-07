// Generated from /Volumes/Data/eclipse-workspace-jmail/BjlFileSystemConsole/Antlr4/FileSourceShPreProcessorBrace.g4 by ANTLR 4.13.2
package us.bringardner.fsh.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link FileSourceShPreProcessorBraceParser}.
 */
public interface FileSourceShPreProcessorBraceListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorBraceParser#expansion_list}.
	 * @param ctx the parse tree
	 */
	void enterExpansion_list(FileSourceShPreProcessorBraceParser.Expansion_listContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#expansion_list}.
	 * @param ctx the parse tree
	 */
	void exitExpansion_list(FileSourceShPreProcessorBraceParser.Expansion_listContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorBraceParser#brace_expantion}.
	 * @param ctx the parse tree
	 */
	void enterBrace_expantion(FileSourceShPreProcessorBraceParser.Brace_expantionContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#brace_expantion}.
	 * @param ctx the parse tree
	 */
	void exitBrace_expantion(FileSourceShPreProcessorBraceParser.Brace_expantionContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorBraceParser#first}.
	 * @param ctx the parse tree
	 */
	void enterFirst(FileSourceShPreProcessorBraceParser.FirstContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#first}.
	 * @param ctx the parse tree
	 */
	void exitFirst(FileSourceShPreProcessorBraceParser.FirstContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorBraceParser#range_value}.
	 * @param ctx the parse tree
	 */
	void enterRange_value(FileSourceShPreProcessorBraceParser.Range_valueContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#range_value}.
	 * @param ctx the parse tree
	 */
	void exitRange_value(FileSourceShPreProcessorBraceParser.Range_valueContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorBraceParser#number}.
	 * @param ctx the parse tree
	 */
	void enterNumber(FileSourceShPreProcessorBraceParser.NumberContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#number}.
	 * @param ctx the parse tree
	 */
	void exitNumber(FileSourceShPreProcessorBraceParser.NumberContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorBraceParser#word}.
	 * @param ctx the parse tree
	 */
	void enterWord(FileSourceShPreProcessorBraceParser.WordContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#word}.
	 * @param ctx the parse tree
	 */
	void exitWord(FileSourceShPreProcessorBraceParser.WordContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShPreProcessorBraceParser#text}.
	 * @param ctx the parse tree
	 */
	void enterText(FileSourceShPreProcessorBraceParser.TextContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#text}.
	 * @param ctx the parse tree
	 */
	void exitText(FileSourceShPreProcessorBraceParser.TextContext ctx);
}