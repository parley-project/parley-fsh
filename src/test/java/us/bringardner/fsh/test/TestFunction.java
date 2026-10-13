package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestFunction extends AbstractConsoleTest{

	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	@Test
	public void testFunction01() throws Exception{
		String cmd = "function f () {\n"
				+ "	echo hello\n"
				+ "}\n"
				+ "f"
				;

		String expect = 
				"hello\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
	@Test
	public void testFunction02() throws Exception{
		String cmd = "function f () {\n"
				+ "	echo \"count = $#\"\n"
				+ "	echo  $@\n"
				+ "}\n"
				+ "\n"
				+ "f one two\n"
				+ ""
				;

		String expect = 
				"count = 2\n"
				+ "one two\n"
				;
		//System.out.println(cmd);
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
			

	@Test
	public void testFunction03() throws Exception{
		String cmd = ""
				+ "var=0\n"
				+ "function f() {\n"
				+ "	echo $var\n"
				+ "	(( var++ ))\n"
				+ "	if [ $var -lt 3 ]; then\n"
				+ "		f\n"
				+ "	fi\n"
				+ "}\n"
				+ "f\n"
				+ "\n"
				+ ""
				;
		//System.out.println(cmd);
		String expect = 
				"0\n"
				+ "1\n"
				+ "2\n"
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
	@Test
	public void testFunction04() throws Exception{
		String cmd = ""
				+ "export FUNCNEST=4\n"
				+ "var=0\n"
				+ "function f() {\n"
				+ "	echo $var\n"
				+ "	(( var++ ))\n"
				+ "	if [ $var -lt 3 ]; then\n"
				+ "		f\n"
				+ "	fi\n"
				+ "}\n"
				+ "f\n"
				+ "\n"
				+ ""
				;

		String expect = 
				"0\n"
				+ "1\n"
				+ "2\n"
				
				;
		//System.out.println(cmd);
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		if( !err.isEmpty()) {
			System.out.println(err);
		}
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testFunction05() throws Exception{
		String cmd = "function f1(){\n"
				+ "  return 34\n"
				+ "}\n"
				+ "\n"
				+ "function f2(){\n"
				+ "  f1\n"
				+ "  local res=$?\n"
				+ "  echo $res\n"
				+ "}\n"
				+ "\n"
				+ "f2"
				;

		String expect = 
				"34\n"
				;
		
		//System.out.println(cmd);
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		if( !err.isEmpty()) {
			System.out.println(err);
		}
		
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
	@Test
	public void testVarInc() throws Exception{
		String cmd = ""
				+ "var=0\n"
				+ "	echo $var\n"
				+ "	(( var++ ))\n"
				+ "	echo $var\n"
				+ ""
				;
		//System.out.println(cmd);
		String expect = 
				"0\n"
				+ "1\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
	
	@Test
	public void testFunction06() throws Exception{
		String cmd = "function fun() {\n"
				+ "	echo $1\n"
				+ "	echo two\n"
				+ "}\n"
				+ "\n"
				+ "fun $1\n"
				+ "fun $2\n";
		
		String expect = 
				"arg1\n"
				+ "two\n"
				+ "arg2\n"
				+ "two\n"
				;
		//System.out.println(cmd);
		ExecuteResult res = executeCommand(cmd,"","arg1","arg2");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testFunctionReturnZeroEndsFunction() throws Exception{
		String cmd = "f() { echo one; return 0; echo two; }\n"
				+ "f\n"
				+ "echo rc=$?\n"
				;

		ExecuteResult res = executeCommand(cmd,"");
		assertEquals("", res.getStdErr());
		assertEquals("one\nrc=0\n", res.getStdOut());
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testFunctionReturnFromLoop() throws Exception{
		String cmd = "g() { for i in 1 2 3; do if [ $i == 2 ]; then return 5; fi; echo i=$i; done; echo notreached; }\n"
				+ "g\n"
				+ "echo rc=$?\n"
				;

		ExecuteResult res = executeCommand(cmd,"");
		assertEquals("", res.getStdErr());
		assertEquals("i=1\nrc=5\n", res.getStdOut());
		assertEquals(0, res.exitCode);
	}

	/** A function's local variables are among the shell's variables while it runs, as in bash. */
	@Test
	public void testLocalsAreListedWhileTheFunctionRuns() throws Exception{
		String cmd = "myglobal=g\n"
				+ "shadow=global\n"
				+ "gone=global\n"
				+ "f() {\n"
				+ "  local mylocal=1\n"
				+ "  local shadow=inner\n"
				+ "  local gone\n"
				+ "  unset gone\n"
				+ "  g\n"
				+ "}\n"
				+ "g() {\n"
				+ "  local myinner=2\n"
				+ "  set | grep -E '^(my|shadow|gone)'\n"
				+ "  echo \"names: ${!my*}\"\n"
				+ "  declare -p | grep -E ' (mylocal|myinner|shadow)='\n"
				+ "}\n"
				+ "f\n"
				+ "echo \"after: ${!my*}\"\n"
				+ "set | grep -E '^(my|shadow|gone)'\n"
				;

		ExecuteResult res = executeCommand(cmd,"");
		assertEquals("", res.getStdErr());
		// (what bash 5 prints)
		assertEquals("myglobal=g\n"
				+ "myinner=2\n"
				+ "mylocal=1\n"
				+ "shadow=inner\n"
				+ "names: myglobal myinner mylocal\n"
				+ "declare -- myinner=\"2\"\n"
				+ "declare -- mylocal=\"1\"\n"
				+ "declare -- shadow=\"inner\"\n"
				+ "after: myglobal\n"
				+ "gone=global\n"
				+ "myglobal=g\n"
				+ "shadow=global\n", res.getStdOut());
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testPositionalParametersSeenWhileAFunctionRuns() throws IOException {
		// what a debugger sees (getVariables, getAllPositionalParameters) in a function: $0 is
		// the script's, as $0 expands; $1 ... are the function's, and the script's aren't mixed in
		java.util.List<java.util.Map<String,Object>> seen = new java.util.ArrayList<>();
		java.util.List<java.util.List<Object>> all = new java.util.ArrayList<>();
		us.bringardner.fsh.DebugContext before = console.getDebugContext();
		console.setDebugContext(new us.bringardner.fsh.DebugContext() {
			@Override
			public synchronized void before(us.bringardner.fsh.syntax.Ast.Node node, String source, us.bringardner.fsh.ShellContext ctx) {
				if( node.text(source).startsWith("echo \"f:") && seen.isEmpty()) {
					seen.add(ctx.getVariables());
					all.add(ctx.getAllPositionalParameters());
				}
			}
		});
		ExecuteResult res;
		try {
			res = executeCommand("set -- a b c\n"
					+ "f() {\n"
					+ "  echo \"f: $0 $1 $#\"\n"
					+ "}\n"
					+ "f world\n"
					+ "echo \"main: $@\"\n", "");
		} finally {
			console.setDebugContext(before);
		}
		String[] lines = res.getStdOut().split("\n");
		assertEquals(2, lines.length, res.getStdOut());
		String zero = lines[0].substring(3, lines[0].length()-" world 1".length());
		assertEquals("f: "+zero+" world 1", lines[0]);
		assertEquals("main: a b c", lines[1]);

		java.util.Map<String,Object> vars = seen.get(0);
		assertEquals(zero, String.valueOf(vars.get("$0")));
		assertEquals("world", String.valueOf(vars.get("$1")));
		assertEquals(false, vars.containsKey("$2"), "the script's $2 isn't the function's");
		assertEquals(java.util.Arrays.asList(zero, "world"), strings(all.get(0)));
	}

	@Test
	public void testLeadingBlankLinesCountForLineNumbers() throws IOException {
		// code run as a string (as fsh-ide and fshdb run scripts) keeps its leading blank lines
		ExecuteResult res = executeCommand("\n\n  \necho \"line $LINENO\"\nf() { echo \"from $BASH_LINENO\"; }\nf\n", "");
		assertEquals("line 4\nfrom 6\n", res.getStdOut());
	}

	private static java.util.List<String> strings(java.util.List<Object> list) {
		java.util.List<String> ret = new java.util.ArrayList<>();
		for(Object o : list) {
			ret.add(String.valueOf(o));
		}
		return ret;
	}
}
