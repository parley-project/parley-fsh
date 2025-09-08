package us.bringardner.shell.antlr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import us.bringardner.filesource.sh.FileSourceShLexer;
import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.BackgroundCommandContext;
import us.bringardner.filesource.sh.FileSourceShParser.CaseClauseContext;
import us.bringardner.filesource.sh.FileSourceShParser.CaseStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.CommandContext;
import us.bringardner.filesource.sh.FileSourceShParser.CommandStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.Command_substitutionContext;
import us.bringardner.filesource.sh.FileSourceShParser.CompareContext;
import us.bringardner.filesource.sh.FileSourceShParser.CompareStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.Compare_primeContext;
import us.bringardner.filesource.sh.FileSourceShParser.ConditionalStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.DoStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.ExpressionContext;
import us.bringardner.filesource.sh.FileSourceShParser.FactorContext;
import us.bringardner.filesource.sh.FileSourceShParser.ForStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.FunctionDefinitionContext;
import us.bringardner.filesource.sh.FileSourceShParser.IfStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.ListContext;
import us.bringardner.filesource.sh.FileSourceShParser.MathExpressionContext;
import us.bringardner.filesource.sh.FileSourceShParser.MathStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.Parameter1Context;
import us.bringardner.filesource.sh.FileSourceShParser.ParameterContext;
import us.bringardner.filesource.sh.FileSourceShParser.PathContext;
import us.bringardner.filesource.sh.FileSourceShParser.PipeOpContext;
import us.bringardner.filesource.sh.FileSourceShParser.PipeStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.PipeableStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.RedirectionOperatorContext;
import us.bringardner.filesource.sh.FileSourceShParser.ScriptContext;
import us.bringardner.filesource.sh.FileSourceShParser.SelectStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.Statement1Context;
import us.bringardner.filesource.sh.FileSourceShParser.StatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.Statement_blockContext;
import us.bringardner.filesource.sh.FileSourceShParser.Statement_group1Context;
import us.bringardner.filesource.sh.FileSourceShParser.Statement_groupContext;
import us.bringardner.filesource.sh.FileSourceShParser.Statement_or_statement1Context;
import us.bringardner.filesource.sh.FileSourceShParser.TermContext;
import us.bringardner.filesource.sh.FileSourceShParser.Until_statementContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.filesource.sh.FileSourceShParser.WhileStatementContext;
import us.bringardner.filesource.sh.FileSourceShParserBaseVisitor;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.statement.AssignStatement;
import us.bringardner.shell.antlr.statement.BackgroundStatement;
import us.bringardner.shell.antlr.statement.BreakStatement;
import us.bringardner.shell.antlr.statement.CaseStatement;
import us.bringardner.shell.antlr.statement.CommandStatement;
import us.bringardner.shell.antlr.statement.CommandSubstitutionStatement;
import us.bringardner.shell.antlr.statement.ContinueStatement;
import us.bringardner.shell.antlr.statement.DeclareAssociateArrayStatement;
import us.bringardner.shell.antlr.statement.ForStatement;
import us.bringardner.shell.antlr.statement.FunctionDefStatement;
import us.bringardner.shell.antlr.statement.IfStatement;
import us.bringardner.shell.antlr.statement.LogicStatement;
import us.bringardner.shell.antlr.statement.MathStatement;
import us.bringardner.shell.antlr.statement.PipeStatement;
import us.bringardner.shell.antlr.statement.SelectStatement;
import us.bringardner.shell.antlr.statement.StatementGroup;
import us.bringardner.shell.antlr.statement.StatementGroup1;
import us.bringardner.shell.antlr.statement.UntilStatement;
import us.bringardner.shell.antlr.statement.WhileStatement;

public class FileSourceShVisitorImpl extends FileSourceShParserBaseVisitor<Object> {

	public static class StatementWrapper {
		ShellCommand cmd;
		ShellContext ctx;
	}

	public static String ManageHereDocument(String code) {
		String ret = code;

		return ret;
	}


	@Override
	public List<Statement> visitScript(ScriptContext ctx) {
		//bashScript: SHEBANG? statement+ EOF;
		List<Statement> ret = new ArrayList<>();

		for(StatementContext stmt : ctx.statement()) {
			Statement s = visitStatement(stmt);
			if( s != null ) {
				ret.add(s);
			}
		}

		return ret;

	}

	/*
		
statement
	: white* statement1 WS* (NL|SEMI|EOF)
	| conditionalStatement (NL|SEMI|EOF) 
    
		;
	 */

	@Override
	public Statement visitStatement(StatementContext ctx) {
		Statement ret = null;
		if( ctx.statement1()!=null ) {
			ret = visitStatement1(ctx.statement1());
		} else if( ctx.conditionalStatement()!=null) {
			ret = visitConditionalStatement(ctx.conditionalStatement());
		}
			
		return ret;
	}
	
	@Override
	public Statement visitConditionalStatement(ConditionalStatementContext ctx) {
		Statement ret = null;
		Statement left = null;
		if( ctx.left!=null  ) {
			left = visitStatement1(ctx.left);
		} else {
			left = visitConditionalStatement(ctx.conditionalStatement());			
		}
		
		Statement right = visitStatement1(ctx.right);
		
		ret = new LogicStatement(ctx,left,ctx.op,right);

		return ret;
	}
	
	@Override
	public Statement visitStatement1(Statement1Context ctx) {
		Statement ret = null;
		//String txt = ctx.getText();
		if(ctx.commandStatement()!=null ) {
			ret = visitCommandStatement(ctx.commandStatement());
		} else if(ctx.pipeStatement()!=null ) {
			ret = visitPipeStatement(ctx.pipeStatement());
		} else if(ctx.mathStatement()!=null ) {
			ret = visitMathStatement(ctx.mathStatement());
		}else if(ctx.ifStatement()!=null ) {
			ret = visitIfStatement(ctx.ifStatement());
		}else if(ctx.whileStatement()!=null ) {
			ret = visitWhileStatement(ctx.whileStatement());
		}else if(ctx.forStatement()!=null ) {
			ret = visitForStatement(ctx.forStatement());
		}else if(ctx.until_statement()!=null ) {
			ret = visitUntil_statement(ctx.until_statement());
		}else if(ctx.assignStatement()!=null ) {
			ret = visitAssignStatement(ctx.assignStatement());
		}else if(ctx.declareAssociativeArrayStatement()!=null ) {
			ret = new DeclareAssociateArrayStatement(ctx.declareAssociativeArrayStatement());
		} else if(ctx.functionDefinition()!=null ) {
			ret = visitFunctionDefinition(ctx.functionDefinition());		
		} else if(ctx.loop_controll_statement()!=null  ) {
			if( ctx.loop_controll_statement().CONTINUE()!=null) {
				ret = new ContinueStatement(ctx.loop_controll_statement());	
			} else if(ctx.loop_controll_statement().BREAK()!=null ) {
				ret = new BreakStatement(ctx.loop_controll_statement());				
			} else {
				throw new RuntimeException("No valid break or continue statement '"+ctx.getText()+"'");
			}
		}  else if (ctx.boolean_statement()!=null) {
			ret = new LogicStatement(ctx,ctx.boolean_statement());
		} else if (ctx.caseStatement()!=null) {
			ret = visitCaseStatement(ctx.caseStatement());
		} else if (ctx.statement_group()!=null) {
			ret = visitStatement_group(ctx.statement_group());
		} else if (ctx.command_substitution()!=null) {
			ret = new CommandSubstitutionStatement(ctx.command_substitution());
		} else if (ctx.compareStatement()!=null) {			
			ret = visitCompareStatement(ctx.compareStatement());
		} else if (ctx.backgroundCommand()!=null) {
			ret = visitBackgroundCommand(ctx.backgroundCommand());
		}else if(ctx.selectStatement()!=null ) {
			ret = visitSelectStatement(ctx.selectStatement());
		}  else  {
			throw new RuntimeException("No known statement '"+ctx.getText()+"'");
		} 

		return ret;
	}
	/*

backgroundCommand:
				statement_group AMP
				| pipeStatement AMP
				| commandStatement AMP
				;
	 */
	@Override
	public BackgroundStatement visitBackgroundCommand(BackgroundCommandContext ctx) {
		Statement stmt = null;
		if( ctx.statement_group()!=null) {
			stmt = visitStatement_group(ctx.statement_group());
		} else if( ctx.pipeStatement()!=null) {
			stmt = visitPipeStatement(ctx.pipeStatement());
		} else if( ctx.commandStatement() !=null) {
			stmt = visitCommandStatement(ctx.commandStatement());
		} else {
			throw new RuntimeException("No known background statement ");
		}

		return new BackgroundStatement(ctx,stmt);
	}

	//compareStatement:  LSQUARE simpleCompare=compare RSQUARE statement?;
	@Override
	public Statement visitCompareStatement(CompareStatementContext ctx) {
		Compare compare = new Compare(ctx.compare());
		List<List<Statement>> stmts = new ArrayList<>();
		if( ctx.statement()!=null) {
			List<Statement> list = new ArrayList<>();
			stmts.add(list);
			list.add(visitStatement(ctx.statement()));
		}

		IfStatement ret = new IfStatement(ctx, Arrays.asList(compare), stmts);

		return ret;
	}


	@Override
	public CommandSubstitutionStatement visitCommand_substitution(Command_substitutionContext ctx) {
		CommandSubstitutionStatement ret = new CommandSubstitutionStatement(ctx);

		return ret;
	}

	@Override
	public Statement visitPipeableStatement(PipeableStatementContext ctx) {
		if( ctx.commandStatement() !=null ) {
			return visitCommandStatement(ctx.commandStatement());
		} else if( ctx.statement_group() !=null ) {
			return visitStatement_group(ctx.statement_group());
		} else {
			throw new RuntimeException("No option in pipable");
		}		
	}

	@Override
	public CommandStatement visitCommandStatement(CommandStatementContext ctx) {
		/*

commandStatement
    :	redirect? command (argument)* hereDocument redirect? CMD_TERMINATOR?
    | 	redirect? command (argument)* redirect? CMD_TERMINATOR?    
    ;
		 */
		CommandStatement ret = new CommandStatement(ctx);	

		String name = visitCommand(ctx.command());
		ret.setName(name);
		if( ctx.argument()!=null) {
			List<ArgumentContext> argsCtx = ctx.argument();
			if( argsCtx.size()>0) {
				List<Argument> allArgs = new ArrayList<>();
				if( argsCtx != null  ) {
					for (ArgumentContext a : argsCtx) {
						allArgs.add(visitArgument(a));
					}			
				} 
				Argument [] args = allArgs.toArray(new Argument[allArgs.size()]);
				ret.setArgs(args);
			}
		}
		if( ctx.hereDocument()!=null) {
			ret.setHereDocument(ctx.hereDocument());
		}
		if( ctx.redirect2!=null) {
			ret.setRedirect(ctx.redirect2);
			if( ctx.redirect2.file_address()!=null ) {
				ret.setFileAddress(ctx.redirect2.file_address());
			}
		} else if( ctx.redirect1!=null) {
			ret.setRedirect(ctx.redirect1);
			if( ctx.redirect1.file_address()!=null ) {
				ret.setFileAddress(ctx.redirect1.file_address());
			}
		}


		return ret;
	}

	/*

assignStatement
    : LOCAL? ID EQ STRING
    | LOCAL? ID EQ ID
    | LOCAL? ID EQ variable
    | LOCAL? ID EQ expression
    | LOCAL? ID EQ mathExpression


	 */
	@Override
	public AssignStatement visitAssignStatement(AssignStatementContext ctx) {
		AssignStatement ret = new AssignStatement(ctx);

		return ret;
	}

	/*


pipeStatement
    : TIME? parg=argument? NOT? commandStatement (pipeOp commandStatement)+
    ;

pipeOp:
	PIPE AMP?
	;    	 */
	@Override
	public Statement visitPipeStatement(PipeStatementContext ctx) {
		if( ctx.parg !=null ) {
			String tmp = ctx.parg.getText();
			if( !tmp.equals("-p")) {
				throw new RuntimeException("Invalid pipe argument "+tmp);
			}
		}

		boolean doTime = ctx.TIME()!=null;
		String [] ops = new String[ctx.pipeOp().size()];
		for (int idx = 0; idx < ops.length; idx++) {
			PipeOpContext op = ctx.pipeOp(idx);
			String opS = op.getText();
			ops[idx] = opS;
		}


		Statement[] stmts = new CommandStatement[ctx.pipeableStatement().size()];
		for (int idx = 0; idx < stmts.length; idx++) {
			stmts[idx] = visitPipeableStatement(ctx.pipeableStatement(idx));
		}

		if( ops.length != stmts.length-1) {
			throw new RuntimeException("Invaid pipstatement. wrong numebr of ops="+ops.length+" should be "+(stmts.length-1));
		}

		return new PipeStatement(ctx,doTime,stmts,ops);
	}

	@Override
	public Statement visitMathStatement(MathStatementContext ctx) {
		MathExpressionContext mx = ctx.mathExpression();
		if( mx !=null && mx.expression()!=null) {
			Expression ret = visitExpression(mx.expression());
			return new MathStatement(ctx,ret);
		}
		ParameterContext p = ctx.parameter();
		if( p != null ) {
			Parameter pp = new Parameter(p);
			return new MathStatement(ctx,pp);
		}
		throw new RuntimeException("Invalie math statement");
	}

	@Override
	public Expression visitMathExpression(MathExpressionContext ctx) {
		return visitExpression(ctx.expression());
	}

	@Override
	public Compare visitCompare(CompareContext ctx) {
		return new Compare(ctx);
	}

	@Override
	public Object visitCompare_prime(Compare_primeContext ctx) {		
		return new ComparePrime(ctx);
	}

	@Override
	public Expression visitExpression(ExpressionContext ctx) {

		return new Expression(ctx);
	}

	/*

ifStatement
    : IF LSQUARE compare RSQUARE SEMI THEN 
    		statement+ 
    	(ELIF LSQUARE compare RSQUARE SEMI THEN statement+)* 
    	(ELSE statement+)? 
      FI
    ;


	 */
	@Override
	public Statement visitIfStatement(IfStatementContext ctx) {

		List<CompareContext> compareList = ctx.compare();
		int cs = compareList.size(); 
		List<Statement_blockContext> stmtsList = ctx.statement_block();
		int ss = stmtsList.size();
		if( ss != cs && ss != (cs+1) ) {
			throw new RuntimeException("Logic error' cs="+cs+" ss="+ss);
		}
		List<Compare> compare = new ArrayList<>();
		for(int idx = 0; idx < cs; idx++) {
			compare.add(visitCompare(compareList.get(idx)));
		}
		List<List<Statement>> stmtList = new ArrayList<>();
		for(Statement_blockContext sb : stmtsList) {
			List<Statement> stmts = new ArrayList<>();
			stmtList.add(stmts);
			for(Statement_or_statement1Context s: sb.statement_or_statement1()) {
				Statement tmp = visitStatement_or_statement1(s);
				if( tmp != null) {
					stmts.add(tmp);
				}
			}
		}

		return new IfStatement(ctx,compare,stmtList);
	}

	@Override
	public StatementGroup visitStatement_group(Statement_groupContext ctx) {
		StatementGroup1 g1 = visitStatement_group1(ctx.statement_group1());

		return new StatementGroup(ctx, g1);
	}

	@Override
	public Statement visitStatement_or_statement1(Statement_or_statement1Context ctx) {
		if( ctx.statement()!=null) {
			return visitStatement(ctx.statement());
		}
		if( ctx.statement1()!=null) {
			return visitStatement1(ctx.statement1());
		}
		
		return null;
	}
	
	@Override
	public StatementGroup1 visitStatement_group1(Statement_group1Context ctx) {

		List<Statement> ret = new ArrayList<>();
		for(Statement_or_statement1Context s: ctx.statement_or_statement1()) {
			Statement tmp = visitStatement_or_statement1(s);
			if( tmp != null) {
				ret.add(tmp);
			}
		}

		return new StatementGroup1(ctx, ret);
	}

	@Override
	public List<Statement> visitStatement_block(Statement_blockContext ctx) {
		List<Statement> ret = new ArrayList<>();
		for(Statement_or_statement1Context s: ctx.statement_or_statement1()) {
			Statement tmp = visitStatement_or_statement1(s);
			if( tmp != null) {
				ret.add(tmp);
			}
		}

		return ret;
	}

	/*

caseStatement
    :   CASE expression IN NL caseClause+ ESAC    
    ;
caseClause
    :   patternList NL? RPAREN NL? commandList NL? op=';;' NL?
    |   patternList NL? RPAREN NL? commandList NL? op=';&' NL?
    |   patternList NL? RPAREN NL? commandList NL? op=';;&' NL?


	 */
	@Override
	public CaseStatement visitCaseStatement(CaseStatementContext ctx) {

		List<CaseStatement.CaseClause> clouses = new ArrayList<>();

		for(CaseClauseContext cc : ctx.caseClause()) {
			CaseStatement.CaseClause clouse = new CaseStatement.CaseClause();
			clouse.stmts = visitStatement_block(cc.statement_block());
			clouse.pattarns = cc.patternList().pattern() ;
			String op = cc.op.getText();;
			if( op.equals(";;")) {
				clouse.op = CaseStatement.Operator.Stop;
			} else if( op.equals(";&")) {
				clouse.op = CaseStatement.Operator.FallThrough;
			} else if( op.equals(";;&")) {
				clouse.op = CaseStatement.Operator.Continue;
			}
			clouses.add(clouse);			
		}
		CaseStatement ret =new CaseStatement(ctx,clouses);

		return ret;
	}

	@Override
	public List<Statement> visitDoStatement(DoStatementContext ctx) {
		List<Statement> ret = new ArrayList<>();
		for(StatementContext s:ctx.statement()) {
			Statement stmt = visitStatement(s);
			if( stmt !=null) {
				ret.add(stmt);	
			}			
		}
		
		return ret;
	}
	
	@Override
	public Statement visitWhileStatement(WhileStatementContext ctx) {
		//|'while' compare DO statement+ DONE
		Compare compare = visitCompare(ctx.compare());
		List<Statement> stmts = visitDoStatement(ctx.doStatement());
		
		return new WhileStatement(ctx,compare,stmts);
	}

	/*

forStatement
    : FOR ID IN argument+ SEMI? DO loop_statement+ DONE
    ;
	 */
	@Override
	public Statement visitForStatement(ForStatementContext ctx) {
		ForStatement ret = new ForStatement(ctx);
		if( ctx.for_loop_control() !=null ) {
			Compare fc =visitCompare(ctx.for_loop_control().for_compare().compare());
			ret.setLoopControl(
					visitAssignStatement(ctx.for_loop_control().assignStatement()),
					fc,
					visitExpression(ctx.for_loop_control().expression())
					);
		} else if( ctx.ID() !=null){
			ret.setVarName(ctx.ID().getText());
		} else {
			throw new RuntimeException("Invalid for statement");
		}
		List<Argument> args = new ArrayList<>();
		ListContext list = ctx.list();
		if( list !=null) {
			for(ArgumentContext actx : list.argument()) {
				Argument a = visitArgument(actx);
				if( a != null ) {
					args.add(a);
				}
			}
			ret.setArgs(args.toArray(new Argument[args.size()]));
		}
		List<Statement> stmts = visitDoStatement(ctx.doStatement());
		ret.setStmts(stmts);

		return ret;
	}

	@Override
	public Statement visitSelectStatement(SelectStatementContext ctx) {
		SelectStatement ret = new SelectStatement(ctx);
		if( ctx.ID() !=null){
			ret.setVarName(ctx.ID().getText());
		} else {
			throw new RuntimeException("Invalid for statement");
		}
		List<Argument> args = new ArrayList<>();
		ListContext list = ctx.list();
		
		if( list !=null) {
			for(ArgumentContext actx : list.argument()) {
				Argument a = visitArgument(actx);
				if( a != null ) {
					args.add(a);
				}
			}
			ret.setArgs(args.toArray(new Argument[args.size()]));
		}
		List<Statement> stmts = visitDoStatement(ctx.doStatement());
		ret.setStmts(stmts);

		return ret;
	}

	@Override
	public Statement visitFunctionDefinition(FunctionDefinitionContext ctx) {
		String name = ctx.ID().getText();
		List<Statement> stmts = new ArrayList<>();
		for(StatementContext ss : ctx.compoundCommand().statement()) {
			Statement tmp = visitStatement(ss);
			if( tmp !=null ) {
				stmts.add(tmp);
			}
		}

		return new FunctionDefStatement(ctx,name,stmts);
	}

	/*
argument
    : STRING
    | MINUS? ID
    | variable
    | NUMBER
    | file_name
    | mathExpression
    ;

	 */
	@Override
	public Argument visitArgument(ArgumentContext ctx) {
		Argument ret = new Argument(ctx);

		return ret;

	}

	@Override
	public String  visitCommand(CommandContext ctx) {
		//		command: ID ;
		if( ctx.ID()!=null ) {
			return ctx.ID().getText();
		} else if( ctx.path()!=null ) {
			return ctx.path().getText();
		} else {
			throw new RuntimeException("No id or path in command");
		}

	}

	@Override
	public Object visitVariable(VariableContext ctx) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Object visitTerm(TermContext ctx) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Statement visitUntil_statement(Until_statementContext ctx) {
		//  : 'until' compare NL? DO statement+ DONE	    		  
		Compare compare = visitCompare(ctx.compare());
		List<Statement> stmts = visitDoStatement(ctx.doStatement());
		
		return new UntilStatement(ctx,compare,stmts);

	}

	@Override
	public Object visitFactor(FactorContext ctx) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Object visitPath(PathContext ctx) {
		throw new RuntimeException("Not implemented");
	}


	@Override
	public Object visitRedirectionOperator(RedirectionOperatorContext ctx) {
		throw new RuntimeException("Not implemented");
	}

	public static Argument parseAurgument(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
		ArgumentContext a = parser.argument();
		Argument ret = visitor.visitArgument(a);

		return ret;
	}

	public static Parameter1Context parseParameter1(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		Parameter1Context ret = parser.parameter1();

		return ret;
	}

	public static Compare parseCompare(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
		CompareContext a = parser.compare();
		Compare ret = visitor.visitCompare(a);

		return ret;
	}

	public static List<Statement> parse(String code) throws Exception {
		AtomicReference<Exception> error = new AtomicReference<>();
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		lexer.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,int charPositionInLine, String msg, RecognitionException e) {
				error.set(e);
			}
		});
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		//parser.removeErrorListeners();
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,int charPositionInLine, String msg, RecognitionException e) {
				if( e == null ) {
					error.set(new RuntimeException(msg));
				} else {
					error.set(e);
				}
			}

		});

		FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
		List<Statement> stmts = visitor.visitScript(parser.script());
		Exception e = error.get();
		if( e != null ) {
			throw e;
		}

		return stmts;
	}



}
