// Generated from /Volumes/Data/eclipse-git/BjlShell/Antlr4/FileSourceShParser.g4 by ANTLR 4.13.2
package us.bringardner.filesource.sh;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link FileSourceShParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface FileSourceShParserVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#script}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitScript(FileSourceShParser.ScriptContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#conditionalStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionalStatement(FileSourceShParser.ConditionalStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(FileSourceShParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#statement1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement1(FileSourceShParser.Statement1Context ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#backgroundCommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBackgroundCommand(FileSourceShParser.BackgroundCommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#loop_controll_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLoop_controll_statement(FileSourceShParser.Loop_controll_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#assignStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignStatement(FileSourceShParser.AssignStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#assignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment(FileSourceShParser.AssignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#boolean}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBoolean(FileSourceShParser.BooleanContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#path_segment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPath_segment(FileSourceShParser.Path_segmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#path}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPath(FileSourceShParser.PathContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#argument_list}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgument_list(FileSourceShParser.Argument_listContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#argument}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgument(FileSourceShParser.ArgumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#signed_number}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSigned_number(FileSourceShParser.Signed_numberContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#operator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperator(FileSourceShParser.OperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#commandStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCommandStatement(FileSourceShParser.CommandStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#redirect}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRedirect(FileSourceShParser.RedirectContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#file_address}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFile_address(FileSourceShParser.File_addressContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#command}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCommand(FileSourceShParser.CommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#pipeStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPipeStatement(FileSourceShParser.PipeStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#pipeableStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPipeableStatement(FileSourceShParser.PipeableStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#pipeOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPipeOp(FileSourceShParser.PipeOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#compareStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompareStatement(FileSourceShParser.CompareStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#mathStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMathStatement(FileSourceShParser.MathStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#mathExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMathExpression(FileSourceShParser.MathExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#boolean_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBoolean_statement(FileSourceShParser.Boolean_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#compare}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompare(FileSourceShParser.CompareContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#compare_prime}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompare_prime(FileSourceShParser.Compare_primeContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#file_test}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFile_test(FileSourceShParser.File_testContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#associative_index}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssociative_index(FileSourceShParser.Associative_indexContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(FileSourceShParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#term}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTerm(FileSourceShParser.TermContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#caseStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCaseStatement(FileSourceShParser.CaseStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#caseClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCaseClause(FileSourceShParser.CaseClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#patternList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPatternList(FileSourceShParser.PatternListContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#pattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPattern(FileSourceShParser.PatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#regex}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRegex(FileSourceShParser.RegexContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#factor}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFactor(FileSourceShParser.FactorContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#redirectionOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRedirectionOperator(FileSourceShParser.RedirectionOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#white}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhite(FileSourceShParser.WhiteContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#ifStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStatement(FileSourceShParser.IfStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#statement_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement_block(FileSourceShParser.Statement_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#whileStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileStatement(FileSourceShParser.WhileStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#until_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUntil_statement(FileSourceShParser.Until_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#doStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDoStatement(FileSourceShParser.DoStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#forStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForStatement(FileSourceShParser.ForStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#selectStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelectStatement(FileSourceShParser.SelectStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#for_loop_control}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFor_loop_control(FileSourceShParser.For_loop_controlContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#for_compare}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFor_compare(FileSourceShParser.For_compareContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#variable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariable(FileSourceShParser.VariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#array_index}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArray_index(FileSourceShParser.Array_indexContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#hereDocument}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHereDocument(FileSourceShParser.HereDocumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#functionDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDefinition(FileSourceShParser.FunctionDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#string}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitString(FileSourceShParser.StringContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#arrayInitializer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayInitializer(FileSourceShParser.ArrayInitializerContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#list}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitList(FileSourceShParser.ListContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#statement_or_statement1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement_or_statement1(FileSourceShParser.Statement_or_statement1Context ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#statement_group}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement_group(FileSourceShParser.Statement_groupContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#statement_group1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement_group1(FileSourceShParser.Statement_group1Context ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#compoundCommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompoundCommand(FileSourceShParser.CompoundCommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#command_substitution}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCommand_substitution(FileSourceShParser.Command_substitutionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#arg_command_substitution}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArg_command_substitution(FileSourceShParser.Arg_command_substitutionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#exprStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprStatement(FileSourceShParser.ExprStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr(FileSourceShParser.ExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameter(FileSourceShParser.ParameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#parameter1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameter1(FileSourceShParser.Parameter1Context ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#parameter_index}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameter_index(FileSourceShParser.Parameter_indexContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#parameter_body}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameter_body(FileSourceShParser.Parameter_bodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#pattern_string}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPattern_string(FileSourceShParser.Pattern_stringContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#replacement_string}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReplacement_string(FileSourceShParser.Replacement_stringContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#pbody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPbody(FileSourceShParser.PbodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#declareAssociativeArrayStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclareAssociativeArrayStatement(FileSourceShParser.DeclareAssociativeArrayStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#associativeArrayInitializer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssociativeArrayInitializer(FileSourceShParser.AssociativeArrayInitializerContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#associativeArrayElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssociativeArrayElement(FileSourceShParser.AssociativeArrayElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FileSourceShParser#associativeArrayValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssociativeArrayValue(FileSourceShParser.AssociativeArrayValueContext ctx);
}