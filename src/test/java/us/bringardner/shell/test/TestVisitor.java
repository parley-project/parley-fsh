package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.filesource.sh.FileSourceShLexer;
import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.MathExpressionContext;
import us.bringardner.filesource.sh.FileSourceShParser.ParameterContext;
import us.bringardner.filesource.sh.FileSourceShParser.PathContext;
import us.bringardner.filesource.sh.FileSourceShParser.RedirectContext;
import us.bringardner.filesource.sh.FileSourceShParser.Signed_numberContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.FileSourceShVisitorImpl;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.statement.AssignStatement;
import us.bringardner.shell.antlr.statement.CommandStatement;
import us.bringardner.shell.antlr.statement.ForStatement;
import us.bringardner.shell.antlr.statement.FunctionDefStatement;
import us.bringardner.shell.antlr.statement.IfStatement;
import us.bringardner.shell.antlr.statement.UntilStatement;
import us.bringardner.shell.antlr.statement.WhileStatement;

@TestMethodOrder(OrderAnnotation.class)
public class TestVisitor {

	public static boolean isEq(Object a, Object b) {
		String as = ""+a;
		String bs = ""+b;
		boolean ret = bs.equals(as);

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

	public static Argument parseAurgument(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		FileSourceShVisitorImpl visitor = new FileSourceShVisitorImpl();
		ArgumentContext a = parser.argument();
		Argument ret = visitor.visitArgument(a);

		return ret;
	}

	public static ParameterContext parseParameter(String code) {
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(code));
		FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(lexer));
		ParameterContext p = parser.parameter();
		//String tmp = p.getText();

		return p;
	}

	@Test
	@Order(1)
	public void testArgs() {
		/*

    : STRING
    | MINUS? ID
    | variable
    | NUMBER
    | file_name
    | mathExpression
    ;		
		 */
		Argument a = null;

		String string = "\"the quick brown fox jumpped over the lazy dog.\"";
		string = "'the quick brown fox jumpped over the lazy dog.'";
		a = parseAurgument(string);

		TerminalNode ctx = a.getContext().string().SQ_STRING();
		assertNotNull(ctx, "string");
		assertEquals(string, ctx.getText());

		a = parseAurgument("name");
		
		ctx = a.getContext().variable().ID();
		assertNotNull(ctx, "ID");
		assertEquals("name", ctx.getText());

		a = parseAurgument("-name");
		assertNotNull(a.getContext().ARG_ID(), "argId");

		ctx = a.getContext().ARG_ID();
		assertNotNull(ctx, "ARG_ID from -name");
		assertEquals("-name", ctx.getText());

		a = parseAurgument("$v");
		VariableContext ctx2 = a.getContext().variable();
		assertNotNull(ctx2, "variable");
		assertEquals("$v", ctx2.getText());

		string = "~/test/../\"test\"/'test'/?/*/2334/dd";
		a = parseAurgument(string);
		PathContext ctx3 = a.getContext().path();	
		assertNotNull(ctx3, "variable");
		assertEquals(string, ctx3.getText());

		a = parseAurgument("1234");
		Signed_numberContext ctx1 = a.getContext().signed_number();
		assertNotNull(ctx1, "number");
		assertEquals("1234", ctx1.getText());

		/*
mathExpression
    : DOLLAR LPAREN LPAREN expression RPAREN RPAREN
    | LPAREN LPAREN expression RPAREN RPAREN
    ;
$((12+32))
		 */

		string = "$((12+32))";
		a = parseAurgument(string);
		MathExpressionContext ctx4 = a.getContext().mathExpression();
		assertNotNull(ctx4, "math exp1");
		assertEquals(string, ctx4.getText());

		string = "((12+32))";
		a = parseAurgument(string);
		ctx4 = a.getContext().mathExpression();
		assertNotNull(ctx4, "math exp2");
		assertEquals(string, ctx4.getText());

	}



	@Test
	public void tstParameter() {
		// this just validates all variations compile
		//parameter:'${' NOT? ID array_index?  pbody '}';
		String values[] = {
				"${#parameter}",
				"${!var:=DEFAULT}",
				"${var:+var is set and not null}",
				"${var:?var is unset or null}",
				"${parameter:7}",
				"${parameter:7:6}",
				"${1:7}",
				"${1:2:7}",
				"${@:7}",
				"${@:7:0}",
				"${@:7:-2}",
				"${array[@]:7}",
				"${array[@]: -7:2}",
				"${!prefix*}",
				"${!prefix@}",
				"${!name[@]}",
				"${!name[*]}",
				"${#parameter}",
				"${parameter#word}",
				"${parameter##word}",
				"${parameter%word}",
				"${parameter%%word}",
				"${parameter/pattern/string}",
				"${parameter//pattern/string}",
				"${parameter/#pattern/string}",
				"${parameter/%pattern/string}",
				"${parameter^pattern}",
				"${parameter^^pattern}",
				"${parameter,pattern}",
				"${parameter,,pattern}",
				"${parameter@U}"
		};
		String expect[] = {
				"${|parameter}",
				"${!var:=DEFAULT}",
				"${var:+var is set and not null}",
				"${var:?var is unset or null}",
				"${parameter:7}",
				"${parameter:7:6}",
				"${1:7}",
				"${1:2:7}",
				"${@:7}",
				"${@:7:0}",
				"${@:7:-2}",
				"${array[@]:7}",
				"${array[@]: -7:2}",
				"${!prefix*}",
				"${!prefix@}",
				"${!name[@]}",
				"${!name[*]}",
				"${|parameter}",
				"${parameter|word}",
				"${parameter||word}",
				"${parameter%word}",
				"${parameter%%word}",
				"${parameter/pattern/string}",
				"${parameter//pattern/string}",
				"${parameter/|pattern/string}",
				"${parameter/%pattern/string}",
				"${parameter^pattern}",
				"${parameter^^pattern}",
				"${parameter,pattern}",
				"${parameter,,pattern}",
				"${parameter@U}"
		};

		Console console = new Console();
		ShellContext ctx = new ShellContext(console);

		for (int idx = 0; idx < values.length; idx++) {
			String code = console.preProcess(values[idx], ctx);
			ParameterContext p = parseParameter(code);
			String content = p.getText();
			assertEquals(expect[idx],content);
		}

		/*
		Argument a = parseAurgument(string);
		ParameterContext ctx1 = a.getContext().parameter();
		assertNotNull(ctx1.NOT());
		assertNotNull(ctx1.ID());
		assertNull(ctx1.array_index());
		assertNotNull(ctx1.pbody());


		assertEquals("var", ctx1.ID().getText());
		assertEquals(":=DEFAULT", ctx1.pbody().getText());
		 */
	}

	@Test
	@Order(3)
	public void testRedirect() throws Exception {
		class RedirectExpect {
			String name;
			String [] args;
			String rdop;
			String file;			
			String fromId;
			String toId;
			public RedirectExpect(String name, String[] args, String rdop, String file,String fromId,String toId) {
				this.name = name;
				this.args = args;
				this.rdop = rdop;
				this.file = file;
				this.fromId = fromId;
				this.toId = toId;
			}
			public RedirectExpect(String name, String[] args, String rdop, String file) {
				this.name = name;
				this.args = args;
				this.rdop = rdop;
				this.file = file;				
			}

		}
		String code = "cmd arg1 arg2 > file 2>&-\n" +
				"cmd arg > file 2>&1\n" +
				"cmd &>> /file\n" +
				"cmd >> ~/file\n" +
				"cmd &> file\n" +
				"cmd >& file\n" +
				"cmd >| file\n" +
				"cmd > file\n" +
				"cmd < file";
		//System.out.println(code);
		RedirectExpect [] expects = {
				new RedirectExpect("cmd", new String[]{"arg1","arg2"},">","file","2","-"),
				new RedirectExpect("cmd",new String[]{"arg"},">","file","2","1"),
				new RedirectExpect("cmd",new String[0],"&>>","/file"),
				new RedirectExpect("cmd",new String[0],">>","~/file"),
				new RedirectExpect("cmd",new String[0],"&>","file"),
				new RedirectExpect("cmd",new String[0],">&","file"),
				new RedirectExpect("cmd",new String[0],">|","file"),
				new RedirectExpect("cmd",new String[0],">","file"),
				new RedirectExpect("cmd",new String[0],"<","file")
		};
		List<Statement> stmts = parse(code);
		//System.out.println("stmts,size="+stmts.size());
		assertEquals(expects.length,stmts.size());
		for(int idx=0; idx < expects.length; idx++) {
			Statement s = stmts.get(idx);
			RedirectExpect expect = expects[idx];
			if( s instanceof CommandStatement){
				CommandStatement cs = (CommandStatement) s;

				assertEquals(expect.name,cs.getName(),"name not eq");
				Argument[] args = cs.getArgs();
				assertEquals(expect.args.length,args.length,"Arg lengths '"+idx+"'");
				for (int idx1 = 0; idx1 < args.length; idx1++) {
					assertEquals(expect.args[idx1],args[idx1].toString(),"idx="+idx1);
				}

				/*


commandStatement
    :	redirect1=redirect? command (argument)* hereDocument redirect2=redirect? CMD_TERMINATOR? 
    | 	redirect1=redirect? command (argument)* redirect2=redirect? CMD_TERMINATOR?    
    ;

redirect 
		: (redirectionOperator (path | ID))
		| file_address
		| (redirectionOperator (path | ID)) file_address
		;    
				 */
				RedirectContext redirect = cs.getRedirect();
				assertNotNull(redirect,"All  cmds in this test have redirection");
				//                                                       op   file  fromId  toId
				//new RedirectExpect("cmd", new String[]{"arg1","arg2"},">","file",   "2",  "-"),
				String op =  redirect.redirectionOperator().getText();
				assertEquals(expect.rdop, op);
				String path = null;
				if( redirect.path() !=null) {
					path = redirect.path().getText();
				} else if( redirect.ID() !=null) {
					path = redirect.ID().getText();
				} else {
					throw new RuntimeException("Error no path");
				}
				assertEquals(expect.file, path);
				
				/*
file_address:
        	fromId=NUMBER? REDIRECT_BOTH toId=NUMBER
         | fromId=NUMBER? REDIRECT_BOTH toId=MINUS
        ;

				 */

				FileSourceShParser.File_addressContext address = cs.getFileAddress();
				if( address != null ) {

					Token fromId = address.fromId;
					Token toId = address.toId;

					boolean ok = isEq(fromId==null?"null":fromId.getText(),expect.fromId);
					assertTrue(ok,"From id "+fromId+" "+expect.fromId);
					ok = isEq(toId==null?"null":toId.getText(),expect.toId);
					assertTrue(ok,"To id "+toId+" "+expect.toId);					
				}



				System.out.println();
				//assertEquals(expect.rdop,tmp,"rdop");
				// May not work
				//assertEquals(expect.file,cs.getRedirect().getText(),"file / target");
			}
		}
	}





	@Test
	public void testIfStatent03_4() throws Exception{
		String cmd = "if [ true ] ; then\n"
				+ "		wc -l\n"
				+ "elif [ true ] ; then\n"
				+ "		ls\n"
				+ "elif [ true ] ; then\n"
				+ "		ls\n"
				+ "else \n"
				+ "		pwd\n"
				+ "fi\n"
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(IfStatement.class, stmt.getClass(),"Expected ifStatmemt");

		if (stmt instanceof IfStatement	) {
			IfStatement ifs = (IfStatement) stmt;
			assertEquals(3, ifs.getCompare().size(),"Expected 3 compares");
			assertEquals(4, ifs.getStmtsList().size(),"Expected 4 stmt blocks");
			for(List<Statement> list : ifs.getStmtsList()) {
				assertEquals(1, list.size(),"Expected 1 statement in the block");
			}
		}

	}

	@Test
	public void testIfStatent03_3()throws Exception{
		String cmd = "if [ true ] ; then\n"
				+ "		wc -l\n"
				+ "elif [ true ] ; then\n"
				+ "		ls\n"
				+ "elif [ true ] ; then\n"
				+ "		ls\n"
				+ "fi\n"
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(IfStatement.class, stmt.getClass(),"Expected ifStatmemt");

		if (stmt instanceof IfStatement	) {
			IfStatement ifs = (IfStatement) stmt;
			assertEquals(3, ifs.getCompare().size(),"Expected 3 compares");
			assertEquals(3, ifs.getStmtsList().size(),"Expected 3 stmt blocks");
			for(List<Statement> list : ifs.getStmtsList()) {
				assertEquals(1, list.size(),"Expected 1 statement in the block");
			}
		}

	}

	@Test
	public void testIfStatent01_1_2() throws Exception{
		String cmd = "if [ true ] ; then\n"
				+ "		wc -l\n"
				+ "		ls -l\n"
				+ "fi\n"
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(IfStatement.class, stmt.getClass(),"Expected ifStatmemt");

		if (stmt instanceof IfStatement	) {
			IfStatement ifs = (IfStatement) stmt;
			assertEquals(1, ifs.getCompare().size(),"Expected 1 compares");
			assertEquals(1, ifs.getStmtsList().size(),"Expected 1 stmt blocks");
			for(List<Statement> list : ifs.getStmtsList()) {
				assertEquals(2, list.size(),"Expected 2 statement in the block");
			}
		}

	}

	@Test
	public void testIfStatent01_1() throws Exception{
		String cmd = "if [ true ] ; then\n"
				+ "		wc -l\n"
				+" else \n"
				+ "		ls -l\n"
				+ "fi\n"
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(IfStatement.class, stmt.getClass(),"Expected ifStatmemt");

		if (stmt instanceof IfStatement	) {
			IfStatement ifs = (IfStatement) stmt;
			assertEquals(1, ifs.getCompare().size(),"Expected 1 compares");
			assertEquals(2, ifs.getStmtsList().size(),"Expected 1 stmt blocks");
			for(List<Statement> list : ifs.getStmtsList()) {
				assertEquals(1, list.size(),"Expected 2 statement in the block");
			}
		}

	}

	@Test
	public void testFunctionDef01() throws Exception{
		String cmd = "greeting () {\n"
				+ "  echo \"Hello $1\"\n"
				+ "}"
				+ "\n"
				+ "greeting you"
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(2, stmts.size(),"Expected two stmt");
		Statement stmt = stmts.get(0);
		assertEquals(FunctionDefStatement.class, stmt.getClass(),"Expected FunctionDefStatement");

		if (stmt instanceof FunctionDefStatement	) {
			FunctionDefStatement ifs = (FunctionDefStatement) stmt;
			assertEquals("greeting", ifs.getName());
			assertEquals(1, ifs.getStmts().size(),"Expected 1 stmt blocks");			
		}

		stmt = stmts.get(1);
		assertEquals(CommandStatement.class, stmt.getClass(),"Expected CommandStatement");
		if (stmt instanceof CommandStatement	) {
			CommandStatement ifs = (CommandStatement) stmt;

			assertEquals("greeting", ifs.getName());
			assertEquals(1, ifs.getArgs().length,"Expected 1 arg");			
		}


	}

	@Test
	public void testForStatement01() throws Exception{
		String cmd = "for w in one two three do\n"
				+ "	echo $w\n"
				+ "done\n"
				+ ""
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(ForStatement.class, stmt.getClass());

		if (stmt instanceof ForStatement	) {
			ForStatement ifs = (ForStatement) stmt;
			assertEquals(3,ifs.getArgs().length);
			assertEquals(1, ifs.getStmts().size(),"Expected 1 loop stmt ");			
		}						
	}

	@Test
	public void testForStatement02()throws Exception{
		String cmd = "for w in one two three do\n"
				+ "	echo $w\n"
				+ "	continue \n"
				+ "	break \n"
				+ "done\n"
				+ ""
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(ForStatement.class, stmt.getClass());

		if (stmt instanceof ForStatement	) {
			ForStatement ifs = (ForStatement) stmt;
			assertEquals(3,ifs.getArgs().length);
			assertEquals(3, ifs.getStmts().size());			
		}						
	}

	@Test
	public void testForStatementCStyle()throws Exception{
		String cmd = "for (( i=1; i<=10; i++ ))\n"
				+ "do  \n"
				+ " echo \"Loop number:\" $i\n"
				+ "done"
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(ForStatement.class, stmt.getClass());

		if (stmt instanceof ForStatement	) {
			ForStatement ifs = (ForStatement) stmt;
			
			assertEquals(1, ifs.getStmts().size());
			assertNotNull(ifs.getAssign());
			assertNotNull(ifs.getCompare());
			assertNotNull(ifs.getExpr());
		}	
		
		
	}
	@Test
	public void testSpecialVartiables()throws Exception{
		String cmd = 
				"a=$*\n"
						+ "a=$@\n"
						+ "a=$?\n"
						+ "a=$-\n"
						+ "a=$$\n"
						+ "a=$!\n"
						+ "a=$0\n"
						+ "a=$9\n"
						+ "a=$10\n"
						+ ""
						;

		List<Statement> stmts = parse(cmd);
		assertEquals(9, stmts.size());

		for(int idx=0,sz=stmts.size(); idx<sz; idx++ ) {
			Statement stmt = stmts.get(idx);
			assertEquals(AssignStatement.class, stmt.getClass());
		}
	}

	@Test
	public void testWhileStatement01()throws Exception{
		String cmd = "while  $i <= 2 \n"
				+ "do\n"
				+ "  echo Number: $i\n"
				+ "  ((i++))\n"
				+ "done\n"
				+ ""
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size());
		Statement stmt = stmts.get(0);
		assertEquals(WhileStatement.class, stmt.getClass());

		if (stmt instanceof WhileStatement	) {
			WhileStatement ws = (WhileStatement) stmt;
			List<Statement> s2 = ws.getStmts();
			assertEquals(2, s2.size());
		}						
	}

	@Test
	public void testUntilStatement01()throws Exception{
		String cmd = "until [ $counter > 5 ]\n"
				+ "do\n"
				+ "  echo Counter: $counter\n"
				+ "  ((counter++))\n"
				+ "done"
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size());
		Statement stmt = stmts.get(0);
		assertEquals(UntilStatement.class, stmt.getClass());

		if (stmt instanceof UntilStatement	) {
			UntilStatement ws = (UntilStatement) stmt;
			List<Statement> s2 = ws.getStmts();
			assertEquals(2, s2.size());
		}						
	}

	@Test
	public void testIfStatentComplextCompare_01() throws Exception{
		String cmd = "if ![[true] && [true] ]; then \n"
				+ "	echo 'true and true'\n"
				+ "fi\n"
				+ ""
				;

		List<Statement> stmts = parse(cmd);
		assertEquals(1, stmts.size(),"Expected one stmt");
		Statement stmt = stmts.get(0);
		assertEquals(IfStatement.class, stmt.getClass(),"Expected ifStatmemt");

		if (stmt instanceof IfStatement	) {
			IfStatement ifs = (IfStatement) stmt;
			assertEquals(1, ifs.getCompare().size(),"Expected 1 compares");
			assertEquals(1, ifs.getStmtsList().size(),"Expected 1 stmt blocks");
			for(List<Statement> list : ifs.getStmtsList()) {
				assertEquals(1, list.size(),"Expected 2 statement in the block");
			}
		}

	}


	@Test
	public void testIfStatentFileTestOptions() throws Exception{
		String options = "abcdefghkprstuwxGLNOSovRzn";

		String cmd1 = "if [ -%s path ] ; then\n"
				+ "		echo\n"
				+ "fi\n"
				;

		for(char c : options.toCharArray()) {
			String cmd = String.format(cmd1, ""+c);
			List<Statement> stmts = parse(cmd);
			assertEquals(1, stmts.size(),"Expected one stmt c="+c);
			Statement stmt = stmts.get(0);
			assertEquals(IfStatement.class, stmt.getClass(),"Expected ifStatmemt c="+c);

			if (stmt instanceof IfStatement	) {
				IfStatement ifs = (IfStatement) stmt;
				assertEquals(1, ifs.getCompare().size(),"Expected 1 compares c="+c);
				assertEquals(1, ifs.getStmtsList().size(),"Expected 1 stmt blocks c="+c);
				for(List<Statement> list : ifs.getStmtsList()) {
					assertEquals(1, list.size(),"Expected 1 statement in the block c="+c);
				}
			}
		}
	}

}
