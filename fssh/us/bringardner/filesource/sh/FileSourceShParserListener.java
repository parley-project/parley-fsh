// Generated from /Volumes/Data/eclipse-git/BjlShell/Antlr4/FileSourceShParser.g4 by ANTLR 4.13.2
package us.bringardner.filesource.sh;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link FileSourceShParser}.
 */
public interface FileSourceShParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#script}.
	 * @param ctx the parse tree
	 */
	void enterScript(FileSourceShParser.ScriptContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#script}.
	 * @param ctx the parse tree
	 */
	void exitScript(FileSourceShParser.ScriptContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#conditionalStatement}.
	 * @param ctx the parse tree
	 */
	void enterConditionalStatement(FileSourceShParser.ConditionalStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#conditionalStatement}.
	 * @param ctx the parse tree
	 */
	void exitConditionalStatement(FileSourceShParser.ConditionalStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(FileSourceShParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(FileSourceShParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#statement1}.
	 * @param ctx the parse tree
	 */
	void enterStatement1(FileSourceShParser.Statement1Context ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#statement1}.
	 * @param ctx the parse tree
	 */
	void exitStatement1(FileSourceShParser.Statement1Context ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#backgroundCommand}.
	 * @param ctx the parse tree
	 */
	void enterBackgroundCommand(FileSourceShParser.BackgroundCommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#backgroundCommand}.
	 * @param ctx the parse tree
	 */
	void exitBackgroundCommand(FileSourceShParser.BackgroundCommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#loop_controll_statement}.
	 * @param ctx the parse tree
	 */
	void enterLoop_controll_statement(FileSourceShParser.Loop_controll_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#loop_controll_statement}.
	 * @param ctx the parse tree
	 */
	void exitLoop_controll_statement(FileSourceShParser.Loop_controll_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#assignStatement}.
	 * @param ctx the parse tree
	 */
	void enterAssignStatement(FileSourceShParser.AssignStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#assignStatement}.
	 * @param ctx the parse tree
	 */
	void exitAssignStatement(FileSourceShParser.AssignStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#assignment}.
	 * @param ctx the parse tree
	 */
	void enterAssignment(FileSourceShParser.AssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#assignment}.
	 * @param ctx the parse tree
	 */
	void exitAssignment(FileSourceShParser.AssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#boolean}.
	 * @param ctx the parse tree
	 */
	void enterBoolean(FileSourceShParser.BooleanContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#boolean}.
	 * @param ctx the parse tree
	 */
	void exitBoolean(FileSourceShParser.BooleanContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#id_star}.
	 * @param ctx the parse tree
	 */
	void enterId_star(FileSourceShParser.Id_starContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#id_star}.
	 * @param ctx the parse tree
	 */
	void exitId_star(FileSourceShParser.Id_starContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#path_segment}.
	 * @param ctx the parse tree
	 */
	void enterPath_segment(FileSourceShParser.Path_segmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#path_segment}.
	 * @param ctx the parse tree
	 */
	void exitPath_segment(FileSourceShParser.Path_segmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#path_segment_list}.
	 * @param ctx the parse tree
	 */
	void enterPath_segment_list(FileSourceShParser.Path_segment_listContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#path_segment_list}.
	 * @param ctx the parse tree
	 */
	void exitPath_segment_list(FileSourceShParser.Path_segment_listContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#path}.
	 * @param ctx the parse tree
	 */
	void enterPath(FileSourceShParser.PathContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#path}.
	 * @param ctx the parse tree
	 */
	void exitPath(FileSourceShParser.PathContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#argument_list}.
	 * @param ctx the parse tree
	 */
	void enterArgument_list(FileSourceShParser.Argument_listContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#argument_list}.
	 * @param ctx the parse tree
	 */
	void exitArgument_list(FileSourceShParser.Argument_listContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(FileSourceShParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(FileSourceShParser.ArgumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#signed_number}.
	 * @param ctx the parse tree
	 */
	void enterSigned_number(FileSourceShParser.Signed_numberContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#signed_number}.
	 * @param ctx the parse tree
	 */
	void exitSigned_number(FileSourceShParser.Signed_numberContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#commandStatement}.
	 * @param ctx the parse tree
	 */
	void enterCommandStatement(FileSourceShParser.CommandStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#commandStatement}.
	 * @param ctx the parse tree
	 */
	void exitCommandStatement(FileSourceShParser.CommandStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#redirect}.
	 * @param ctx the parse tree
	 */
	void enterRedirect(FileSourceShParser.RedirectContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#redirect}.
	 * @param ctx the parse tree
	 */
	void exitRedirect(FileSourceShParser.RedirectContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#file_address}.
	 * @param ctx the parse tree
	 */
	void enterFile_address(FileSourceShParser.File_addressContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#file_address}.
	 * @param ctx the parse tree
	 */
	void exitFile_address(FileSourceShParser.File_addressContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#command}.
	 * @param ctx the parse tree
	 */
	void enterCommand(FileSourceShParser.CommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#command}.
	 * @param ctx the parse tree
	 */
	void exitCommand(FileSourceShParser.CommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#pipeStatement}.
	 * @param ctx the parse tree
	 */
	void enterPipeStatement(FileSourceShParser.PipeStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#pipeStatement}.
	 * @param ctx the parse tree
	 */
	void exitPipeStatement(FileSourceShParser.PipeStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#pipeableStatement}.
	 * @param ctx the parse tree
	 */
	void enterPipeableStatement(FileSourceShParser.PipeableStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#pipeableStatement}.
	 * @param ctx the parse tree
	 */
	void exitPipeableStatement(FileSourceShParser.PipeableStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#pipeOp}.
	 * @param ctx the parse tree
	 */
	void enterPipeOp(FileSourceShParser.PipeOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#pipeOp}.
	 * @param ctx the parse tree
	 */
	void exitPipeOp(FileSourceShParser.PipeOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#compareStatement}.
	 * @param ctx the parse tree
	 */
	void enterCompareStatement(FileSourceShParser.CompareStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#compareStatement}.
	 * @param ctx the parse tree
	 */
	void exitCompareStatement(FileSourceShParser.CompareStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#mathStatement}.
	 * @param ctx the parse tree
	 */
	void enterMathStatement(FileSourceShParser.MathStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#mathStatement}.
	 * @param ctx the parse tree
	 */
	void exitMathStatement(FileSourceShParser.MathStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#mathExpression}.
	 * @param ctx the parse tree
	 */
	void enterMathExpression(FileSourceShParser.MathExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#mathExpression}.
	 * @param ctx the parse tree
	 */
	void exitMathExpression(FileSourceShParser.MathExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#boolean_statement}.
	 * @param ctx the parse tree
	 */
	void enterBoolean_statement(FileSourceShParser.Boolean_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#boolean_statement}.
	 * @param ctx the parse tree
	 */
	void exitBoolean_statement(FileSourceShParser.Boolean_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#compare}.
	 * @param ctx the parse tree
	 */
	void enterCompare(FileSourceShParser.CompareContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#compare}.
	 * @param ctx the parse tree
	 */
	void exitCompare(FileSourceShParser.CompareContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#compare_prime}.
	 * @param ctx the parse tree
	 */
	void enterCompare_prime(FileSourceShParser.Compare_primeContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#compare_prime}.
	 * @param ctx the parse tree
	 */
	void exitCompare_prime(FileSourceShParser.Compare_primeContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#file_test}.
	 * @param ctx the parse tree
	 */
	void enterFile_test(FileSourceShParser.File_testContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#file_test}.
	 * @param ctx the parse tree
	 */
	void exitFile_test(FileSourceShParser.File_testContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#associative_index}.
	 * @param ctx the parse tree
	 */
	void enterAssociative_index(FileSourceShParser.Associative_indexContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#associative_index}.
	 * @param ctx the parse tree
	 */
	void exitAssociative_index(FileSourceShParser.Associative_indexContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#regular_expression}.
	 * @param ctx the parse tree
	 */
	void enterRegular_expression(FileSourceShParser.Regular_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#regular_expression}.
	 * @param ctx the parse tree
	 */
	void exitRegular_expression(FileSourceShParser.Regular_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(FileSourceShParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(FileSourceShParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#term}.
	 * @param ctx the parse tree
	 */
	void enterTerm(FileSourceShParser.TermContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#term}.
	 * @param ctx the parse tree
	 */
	void exitTerm(FileSourceShParser.TermContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#caseStatement}.
	 * @param ctx the parse tree
	 */
	void enterCaseStatement(FileSourceShParser.CaseStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#caseStatement}.
	 * @param ctx the parse tree
	 */
	void exitCaseStatement(FileSourceShParser.CaseStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#caseClause}.
	 * @param ctx the parse tree
	 */
	void enterCaseClause(FileSourceShParser.CaseClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#caseClause}.
	 * @param ctx the parse tree
	 */
	void exitCaseClause(FileSourceShParser.CaseClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#patternList}.
	 * @param ctx the parse tree
	 */
	void enterPatternList(FileSourceShParser.PatternListContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#patternList}.
	 * @param ctx the parse tree
	 */
	void exitPatternList(FileSourceShParser.PatternListContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#rx_pattern}.
	 * @param ctx the parse tree
	 */
	void enterRx_pattern(FileSourceShParser.Rx_patternContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#rx_pattern}.
	 * @param ctx the parse tree
	 */
	void exitRx_pattern(FileSourceShParser.Rx_patternContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#pattern}.
	 * @param ctx the parse tree
	 */
	void enterPattern(FileSourceShParser.PatternContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#pattern}.
	 * @param ctx the parse tree
	 */
	void exitPattern(FileSourceShParser.PatternContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#char_class_list}.
	 * @param ctx the parse tree
	 */
	void enterChar_class_list(FileSourceShParser.Char_class_listContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#char_class_list}.
	 * @param ctx the parse tree
	 */
	void exitChar_class_list(FileSourceShParser.Char_class_listContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#char_class}.
	 * @param ctx the parse tree
	 */
	void enterChar_class(FileSourceShParser.Char_classContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#char_class}.
	 * @param ctx the parse tree
	 */
	void exitChar_class(FileSourceShParser.Char_classContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#char_class_a}.
	 * @param ctx the parse tree
	 */
	void enterChar_class_a(FileSourceShParser.Char_class_aContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#char_class_a}.
	 * @param ctx the parse tree
	 */
	void exitChar_class_a(FileSourceShParser.Char_class_aContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#char_class_b}.
	 * @param ctx the parse tree
	 */
	void enterChar_class_b(FileSourceShParser.Char_class_bContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#char_class_b}.
	 * @param ctx the parse tree
	 */
	void exitChar_class_b(FileSourceShParser.Char_class_bContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#char_class_body}.
	 * @param ctx the parse tree
	 */
	void enterChar_class_body(FileSourceShParser.Char_class_bodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#char_class_body}.
	 * @param ctx the parse tree
	 */
	void exitChar_class_body(FileSourceShParser.Char_class_bodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#char_class_range}.
	 * @param ctx the parse tree
	 */
	void enterChar_class_range(FileSourceShParser.Char_class_rangeContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#char_class_range}.
	 * @param ctx the parse tree
	 */
	void exitChar_class_range(FileSourceShParser.Char_class_rangeContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#char_class_chars}.
	 * @param ctx the parse tree
	 */
	void enterChar_class_chars(FileSourceShParser.Char_class_charsContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#char_class_chars}.
	 * @param ctx the parse tree
	 */
	void exitChar_class_chars(FileSourceShParser.Char_class_charsContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#regex}.
	 * @param ctx the parse tree
	 */
	void enterRegex(FileSourceShParser.RegexContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#regex}.
	 * @param ctx the parse tree
	 */
	void exitRegex(FileSourceShParser.RegexContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#factor}.
	 * @param ctx the parse tree
	 */
	void enterFactor(FileSourceShParser.FactorContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#factor}.
	 * @param ctx the parse tree
	 */
	void exitFactor(FileSourceShParser.FactorContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#redirectionOperator}.
	 * @param ctx the parse tree
	 */
	void enterRedirectionOperator(FileSourceShParser.RedirectionOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#redirectionOperator}.
	 * @param ctx the parse tree
	 */
	void exitRedirectionOperator(FileSourceShParser.RedirectionOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#white}.
	 * @param ctx the parse tree
	 */
	void enterWhite(FileSourceShParser.WhiteContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#white}.
	 * @param ctx the parse tree
	 */
	void exitWhite(FileSourceShParser.WhiteContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void enterIfStatement(FileSourceShParser.IfStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void exitIfStatement(FileSourceShParser.IfStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#statement_block}.
	 * @param ctx the parse tree
	 */
	void enterStatement_block(FileSourceShParser.Statement_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#statement_block}.
	 * @param ctx the parse tree
	 */
	void exitStatement_block(FileSourceShParser.Statement_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#whileStatement}.
	 * @param ctx the parse tree
	 */
	void enterWhileStatement(FileSourceShParser.WhileStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#whileStatement}.
	 * @param ctx the parse tree
	 */
	void exitWhileStatement(FileSourceShParser.WhileStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#until_statement}.
	 * @param ctx the parse tree
	 */
	void enterUntil_statement(FileSourceShParser.Until_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#until_statement}.
	 * @param ctx the parse tree
	 */
	void exitUntil_statement(FileSourceShParser.Until_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#doStatement}.
	 * @param ctx the parse tree
	 */
	void enterDoStatement(FileSourceShParser.DoStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#doStatement}.
	 * @param ctx the parse tree
	 */
	void exitDoStatement(FileSourceShParser.DoStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#forStatement}.
	 * @param ctx the parse tree
	 */
	void enterForStatement(FileSourceShParser.ForStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#forStatement}.
	 * @param ctx the parse tree
	 */
	void exitForStatement(FileSourceShParser.ForStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#selectStatement}.
	 * @param ctx the parse tree
	 */
	void enterSelectStatement(FileSourceShParser.SelectStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#selectStatement}.
	 * @param ctx the parse tree
	 */
	void exitSelectStatement(FileSourceShParser.SelectStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#for_loop_control}.
	 * @param ctx the parse tree
	 */
	void enterFor_loop_control(FileSourceShParser.For_loop_controlContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#for_loop_control}.
	 * @param ctx the parse tree
	 */
	void exitFor_loop_control(FileSourceShParser.For_loop_controlContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#for_compare}.
	 * @param ctx the parse tree
	 */
	void enterFor_compare(FileSourceShParser.For_compareContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#for_compare}.
	 * @param ctx the parse tree
	 */
	void exitFor_compare(FileSourceShParser.For_compareContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#variable}.
	 * @param ctx the parse tree
	 */
	void enterVariable(FileSourceShParser.VariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#variable}.
	 * @param ctx the parse tree
	 */
	void exitVariable(FileSourceShParser.VariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#array_index}.
	 * @param ctx the parse tree
	 */
	void enterArray_index(FileSourceShParser.Array_indexContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#array_index}.
	 * @param ctx the parse tree
	 */
	void exitArray_index(FileSourceShParser.Array_indexContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#hereDocument}.
	 * @param ctx the parse tree
	 */
	void enterHereDocument(FileSourceShParser.HereDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#hereDocument}.
	 * @param ctx the parse tree
	 */
	void exitHereDocument(FileSourceShParser.HereDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#functionDefinition}.
	 * @param ctx the parse tree
	 */
	void enterFunctionDefinition(FileSourceShParser.FunctionDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#functionDefinition}.
	 * @param ctx the parse tree
	 */
	void exitFunctionDefinition(FileSourceShParser.FunctionDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#string}.
	 * @param ctx the parse tree
	 */
	void enterString(FileSourceShParser.StringContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#string}.
	 * @param ctx the parse tree
	 */
	void exitString(FileSourceShParser.StringContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#arrayInitializer}.
	 * @param ctx the parse tree
	 */
	void enterArrayInitializer(FileSourceShParser.ArrayInitializerContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#arrayInitializer}.
	 * @param ctx the parse tree
	 */
	void exitArrayInitializer(FileSourceShParser.ArrayInitializerContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#list}.
	 * @param ctx the parse tree
	 */
	void enterList(FileSourceShParser.ListContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#list}.
	 * @param ctx the parse tree
	 */
	void exitList(FileSourceShParser.ListContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#statement_or_statement1}.
	 * @param ctx the parse tree
	 */
	void enterStatement_or_statement1(FileSourceShParser.Statement_or_statement1Context ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#statement_or_statement1}.
	 * @param ctx the parse tree
	 */
	void exitStatement_or_statement1(FileSourceShParser.Statement_or_statement1Context ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#statement_group}.
	 * @param ctx the parse tree
	 */
	void enterStatement_group(FileSourceShParser.Statement_groupContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#statement_group}.
	 * @param ctx the parse tree
	 */
	void exitStatement_group(FileSourceShParser.Statement_groupContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#statement_group1}.
	 * @param ctx the parse tree
	 */
	void enterStatement_group1(FileSourceShParser.Statement_group1Context ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#statement_group1}.
	 * @param ctx the parse tree
	 */
	void exitStatement_group1(FileSourceShParser.Statement_group1Context ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#compoundCommand}.
	 * @param ctx the parse tree
	 */
	void enterCompoundCommand(FileSourceShParser.CompoundCommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#compoundCommand}.
	 * @param ctx the parse tree
	 */
	void exitCompoundCommand(FileSourceShParser.CompoundCommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#command_substitution}.
	 * @param ctx the parse tree
	 */
	void enterCommand_substitution(FileSourceShParser.Command_substitutionContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#command_substitution}.
	 * @param ctx the parse tree
	 */
	void exitCommand_substitution(FileSourceShParser.Command_substitutionContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#arg_command_substitution}.
	 * @param ctx the parse tree
	 */
	void enterArg_command_substitution(FileSourceShParser.Arg_command_substitutionContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#arg_command_substitution}.
	 * @param ctx the parse tree
	 */
	void exitArg_command_substitution(FileSourceShParser.Arg_command_substitutionContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#exprStatement}.
	 * @param ctx the parse tree
	 */
	void enterExprStatement(FileSourceShParser.ExprStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#exprStatement}.
	 * @param ctx the parse tree
	 */
	void exitExprStatement(FileSourceShParser.ExprStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpr(FileSourceShParser.ExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpr(FileSourceShParser.ExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#parameter}.
	 * @param ctx the parse tree
	 */
	void enterParameter(FileSourceShParser.ParameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#parameter}.
	 * @param ctx the parse tree
	 */
	void exitParameter(FileSourceShParser.ParameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#parameter1}.
	 * @param ctx the parse tree
	 */
	void enterParameter1(FileSourceShParser.Parameter1Context ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#parameter1}.
	 * @param ctx the parse tree
	 */
	void exitParameter1(FileSourceShParser.Parameter1Context ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#parameter_index}.
	 * @param ctx the parse tree
	 */
	void enterParameter_index(FileSourceShParser.Parameter_indexContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#parameter_index}.
	 * @param ctx the parse tree
	 */
	void exitParameter_index(FileSourceShParser.Parameter_indexContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#parameter_body}.
	 * @param ctx the parse tree
	 */
	void enterParameter_body(FileSourceShParser.Parameter_bodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#parameter_body}.
	 * @param ctx the parse tree
	 */
	void exitParameter_body(FileSourceShParser.Parameter_bodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#pattern_string}.
	 * @param ctx the parse tree
	 */
	void enterPattern_string(FileSourceShParser.Pattern_stringContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#pattern_string}.
	 * @param ctx the parse tree
	 */
	void exitPattern_string(FileSourceShParser.Pattern_stringContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#replacement_string}.
	 * @param ctx the parse tree
	 */
	void enterReplacement_string(FileSourceShParser.Replacement_stringContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#replacement_string}.
	 * @param ctx the parse tree
	 */
	void exitReplacement_string(FileSourceShParser.Replacement_stringContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#pbody}.
	 * @param ctx the parse tree
	 */
	void enterPbody(FileSourceShParser.PbodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#pbody}.
	 * @param ctx the parse tree
	 */
	void exitPbody(FileSourceShParser.PbodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#declareAssociativeArrayStatement}.
	 * @param ctx the parse tree
	 */
	void enterDeclareAssociativeArrayStatement(FileSourceShParser.DeclareAssociativeArrayStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#declareAssociativeArrayStatement}.
	 * @param ctx the parse tree
	 */
	void exitDeclareAssociativeArrayStatement(FileSourceShParser.DeclareAssociativeArrayStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#associativeArrayInitializer}.
	 * @param ctx the parse tree
	 */
	void enterAssociativeArrayInitializer(FileSourceShParser.AssociativeArrayInitializerContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#associativeArrayInitializer}.
	 * @param ctx the parse tree
	 */
	void exitAssociativeArrayInitializer(FileSourceShParser.AssociativeArrayInitializerContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#associativeArrayElement}.
	 * @param ctx the parse tree
	 */
	void enterAssociativeArrayElement(FileSourceShParser.AssociativeArrayElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#associativeArrayElement}.
	 * @param ctx the parse tree
	 */
	void exitAssociativeArrayElement(FileSourceShParser.AssociativeArrayElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FileSourceShParser#associativeArrayValue}.
	 * @param ctx the parse tree
	 */
	void enterAssociativeArrayValue(FileSourceShParser.AssociativeArrayValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link FileSourceShParser#associativeArrayValue}.
	 * @param ctx the parse tree
	 */
	void exitAssociativeArrayValue(FileSourceShParser.AssociativeArrayValueContext ctx);
}