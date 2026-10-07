package us.bringardner.shell.antlr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRErrorStrategy;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import us.bringardner.filesource.sh.FileSourceShLexer;
import us.bringardner.filesource.sh.FileSourceShParser;
import org.antlr.v4.runtime.ParserRuleContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.CommandWordContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentPartContext;
import us.bringardner.filesource.sh.FileSourceShParser.Argument_listContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.CaseClauseContext;
import us.bringardner.filesource.sh.FileSourceShParser.CaseStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.CommandContext;
import us.bringardner.filesource.sh.FileSourceShParser.CommandStatementContext;
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
import us.bringardner.filesource.sh.FileSourceShParser.Job_control_statementContext;
import us.bringardner.filesource.sh.FileSourceShParser.JobspecContext;
import us.bringardner.filesource.sh.FileSourceShParser.ListContext;
import us.bringardner.filesource.sh.FileSourceShParser.MathStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.Parameter1Context;
import us.bringardner.filesource.sh.FileSourceShParser.ParameterContext;
import us.bringardner.filesource.sh.FileSourceShParser.PipeOpContext;
import us.bringardner.filesource.sh.FileSourceShParser.PipeStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.PipeableStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.RedirectContext;
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
import us.bringardner.shell.antlr.statement.ContinueStatement;
import us.bringardner.shell.antlr.statement.DeclareAssociateArrayStatement;
import us.bringardner.shell.antlr.statement.ForStatement;
import us.bringardner.shell.antlr.statement.FunctionDefStatement;
import us.bringardner.shell.antlr.statement.IfStatement;
import us.bringardner.shell.antlr.statement.JobControlStatement;
import us.bringardner.shell.antlr.statement.LogicStatement;
import us.bringardner.shell.antlr.statement.DoubleBracketStatement;
import us.bringardner.shell.antlr.statement.MathStatement;
import us.bringardner.shell.antlr.statement.NegateStatement;
import us.bringardner.shell.antlr.statement.RedirectedStatement;
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
			if( ctx.bg != null ) {
				ret = new BackgroundStatement(ctx, ret);
			}
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
		if( ctx.negated != null ) {
			return new NegateStatement(ctx, visitStatement1(ctx.negated));
		}
		Statement ret = null;
		//String txt = ctx.getText();
		if( ctx.DBL_TEST() != null ) {
			ret = new DoubleBracketStatement(ctx);
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
		} else if (ctx.compareStatement()!=null) {			
			ret = visitCompareStatement(ctx.compareStatement());
		}else if(ctx.selectStatement()!=null ) {
			ret = visitSelectStatement(ctx.selectStatement());
		} else if(ctx.job_control_statement() !=null) {
			ret = visitJob_control_statement(ctx.job_control_statement());
		}  else  {
			throw new RuntimeException("No known statement '"+ctx.getText()+"'");
		} 

		return ret;
	}
	//compareStatement:  LSQUARE simpleCompare=compare RSQUARE statement?;
	@Override
	public Statement visitCompareStatement(CompareStatementContext ctx) {
		// [ words ]: test's arguments (unless a [ inside groups)
		String text = Compare.bracketText(ctx.LSQUARE(), ctx.RSQUARE());
		Compare compare = text != null ? new Compare(ctx.compare(), text) : new Compare(ctx.compare());
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
	public Statement visitPipeableStatement(PipeableStatementContext ctx) {
		Statement ret = visitPipeable(ctx);
		RerdirectImpl redirect = ctx.commandStatement() == null && ctx.statement_group() == null
				? RerdirectImpl.find(ctx.children) : null;
		return redirect == null ? ret : new RedirectedStatement(ctx, ret, redirect);
	}

	private Statement visitPipeable(PipeableStatementContext ctx) {
		if( ctx.commandStatement() !=null ) {
			return visitCommandStatement(ctx.commandStatement());
		} else if( ctx.statement_group() !=null ) {
			return visitStatement_group(ctx.statement_group());
		} else if( ctx.whileStatement() !=null ) {
			return visitWhileStatement(ctx.whileStatement());
		} else if( ctx.until_statement() !=null ) {
			return visitUntil_statement(ctx.until_statement());
		} else if( ctx.forStatement() !=null ) {
			return visitForStatement(ctx.forStatement());
		} else if( ctx.ifStatement() !=null ) {
			return visitIfStatement(ctx.ifStatement());
		} else if( ctx.caseStatement() !=null ) {
			return visitCaseStatement(ctx.caseStatement());
		} else {
			throw new RuntimeException("No option in pipable");
		}		
	}

	@Override
	public JobControlStatement visitJob_control_statement(Job_control_statementContext ctx) {
		JobControlStatement ret = new JobControlStatement(ctx);
		ret.setArgs(visitArgument_list(ctx.argument()), null);
		ret.setJobSpecs(visitJobspec(ctx.jobspec()));
		return ret;
	}

	private List<String> visitJobspec(List<JobspecContext> ctx) {
		List<String> ret = new ArrayList<>();
		if( ctx != null ) {
			for(JobspecContext js : ctx) {
				ret.add(visitJobspec(js));
			}
		}
		return ret;
	}

	@Override
	public String visitJobspec(JobspecContext ctx) {
		if( ctx == null ) {
			return "null";
		}

		return ""+ctx.getText();
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

		if( ctx.command().cmdWord != null ) {
			// the name comes from expanding the word when the command runs
			ret.setCommandWord(toArgument(ctx.command().cmdWord));
		} else {
			ret.setName(visitCommand(ctx.command()));
		}
		ret.setPrefixAssignments(ctx.prefix);
		Argument[] args = null;
		if( ctx.argument()!=null) {
			
			List<ArgumentContext> argsCtx = ctx.argument();
			if( argsCtx.size()>0) {
				args = visitArgument_list(argsCtx);
				ret.setArgs(args,ctx.argument());
			}
		}
		if( ctx.hereDocument()!=null) {
			ret.setHereDocument(ctx.hereDocument());
		}
		// before and after the command: < in sort > out
		ret.setRedirect(RerdirectImpl.find(ctx.children));

		return ret;
	}

	/**
	 * A command word as an argument (a word), so it is expanded like one. The grammar has its own
	 * rule because a plain word there would also take keywords such as done and fi.
	 */
	public static ArgumentContext toArgument(CommandWordContext word) {
		ArgumentContext ret = new ArgumentContext(word, word.invokingState);
		ArgumentPartContext first = new ArgumentPartContext(ret, word.invokingState);
		ParserRuleContext start = (ParserRuleContext) word.commandWordStart().getChild(0);
		first.addChild(start);
		first.start = start.start;
		first.stop = start.stop;
		ret.addChild(first);
		for(ArgumentPartContext part : word.argumentPart()) {
			ret.addChild(part);
		}
		ret.start = word.start;
		ret.stop = word.stop;
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
			String tmp = ctx.parg.getText().trim();
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


		Statement[] stmts = new Statement[ctx.pipeableStatement().size()];
		for (int idx = 0; idx < stmts.length; idx++) {
			stmts[idx] = visitPipeableStatement(ctx.pipeableStatement(idx));
		}

		if( ops.length != stmts.length-1) {
			throw new RuntimeException("Invaid pipstatement. wrong numebr of ops="+ops.length+" should be "+(stmts.length-1));
		}

		if( stmts.length == 1 && !doTime && ctx.NOT() == null ) {
			// a plain command or group
			return stmts[0];
		}
		return new PipeStatement(ctx,doTime,stmts,ops);
	}

	@Override
	public Statement visitMathStatement(MathStatementContext ctx) {
		if( ctx.ARITH_COMMAND() != null || ctx.mathExpression() != null ) {
			return new MathStatement(ctx);
		}
		ParameterContext p = ctx.parameter();
		if( p != null ) {
			Parameter pp = new Parameter(p);
			return new MathStatement(ctx,pp);
		}
		throw new RuntimeException("Invalie math statement");
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
		// { ...; } > out and ( ... ) 2>&1: the redirects around the braces, in order
		List<ParseTree> kids = new ArrayList<>();
		for(ParseTree kid : ctx.children) {
			if( kid == ctx.statement_group1() ) {
				kids.addAll(ctx.statement_group1().children);
			} else {
				kids.add(kid);
			}
		}
		return new StatementGroup(ctx, g1,RerdirectImpl.find(kids));
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
			// the last clause may leave out ;;
			String op = cc.op == null ? ";;" : cc.op.getText();
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
			ret.setLoopControl(Arithmetic.body(ctx.for_loop_control().ARITH_COMMAND().getText()));
		} else if( ctx.ID() !=null){
			ret.setVarName(ctx.ID().getText());
			// for x; do ...: the positional parameters
			ret.setPositional(ctx.IN() == null);
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
			ret.setArgs(args.toArray(new Argument[args.size()]), null);
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
			ret.setArgs(args.toArray(new Argument[args.size()]), null);
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
	public Argument[] visitArgument_list(Argument_listContext ctx) {
		Argument[] ret = visitArgument_list(ctx.argument());

		return ret;
	}

	private Argument[] visitArgument_list(List<ArgumentContext> ctx) {
		List<Argument> ret = new ArrayList<>();
		if( ctx != null) {
			for(ArgumentContext a : ctx) {
				ret.add(visitArgument(a));
			}
		}
		Argument [] args = ret.toArray(new Argument[ret.size()]);
		return args;
	}

	@Override
	public String  visitCommand(CommandContext ctx) {
		//		command: ID ;
		if( ctx.ID()!=null ) {
			return ctx.ID().getText();
		} else if( ctx.TRUE()!=null || ctx.FALSE()!=null ) {
			return ctx.getText();
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
	public Object visitRedirectionOperator(RedirectionOperatorContext ctx) {
		throw new RuntimeException("Not implemented");
	}

	/**
	 * Run a parser rule with ANTLR's fast SLL prediction first. Only when that fails (a syntax error,
	 * or input that needs full context) is the input parsed again with full LL prediction, which
	 * reports errors as before. Full LL on every parse made a single command take seconds.
	 * 
	 * @param parser a new parser; its error listeners are used for the LL pass
	 * @param rule the rule to run, for example FileSourceShParser::script
	 * @return the parse tree
	 */
	public static <P extends Parser,T> T parseFast(P parser, Function<P,T> rule) {
		List<? extends ANTLRErrorListener> listeners = new ArrayList<>(parser.getErrorListeners());
		ANTLRErrorStrategy handler = parser.getErrorHandler();
		parser.removeErrorListeners();
		parser.setErrorHandler(new BailErrorStrategy());
		parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
		try {
			return rule.apply(parser);
		} catch (ParseCancellationException e) {
			parser.reset();
			listeners.forEach(parser::addErrorListener);
			parser.setErrorHandler(handler);
			parser.getInterpreter().setPredictionMode(PredictionMode.LL);
			return rule.apply(parser);
		}
	}

	public static Argument parseAurgument(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
		ArgumentContext a = parseFast(parser, FileSourceShParser::argument);
		Argument ret = visitor.visitArgument(a);

		return ret;
	}

	public static Parameter1Context parseParameter1(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		Parameter1Context ret = parseFast(parser, FileSourceShParser::parameter1);

		return ret;
	}

	public static Compare parseCompare(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
		CompareContext a = parseFast(parser, FileSourceShParser::compare);
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
		List<Statement> stmts = visitor.visitScript(parseFast(parser, FileSourceShParser::script));
		Exception e = error.get();
		if( e != null ) {
			throw e;
		}

		return stmts;
	}



}
