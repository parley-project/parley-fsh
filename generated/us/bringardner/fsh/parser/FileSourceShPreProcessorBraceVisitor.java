// Generated from /Volumes/Data/eclipse-workspace-jmail/BjlFileSystemConsole/Antlr4/FileSourceShPreProcessorBrace.g4 by ANTLR 4.13.2
package us.bringardner.fsh.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link FileSourceShPreProcessorBraceParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface FileSourceShPreProcessorBraceVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#expansion_list}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpansion_list(FileSourceShPreProcessorBraceParser.Expansion_listContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#brace_expantion}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBrace_expantion(FileSourceShPreProcessorBraceParser.Brace_expantionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#first}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFirst(FileSourceShPreProcessorBraceParser.FirstContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#range_value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRange_value(FileSourceShPreProcessorBraceParser.Range_valueContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#number}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumber(FileSourceShPreProcessorBraceParser.NumberContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#word}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWord(FileSourceShPreProcessorBraceParser.WordContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShPreProcessorBraceParser#text}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitText(FileSourceShPreProcessorBraceParser.TextContext ctx);
}