package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import us.bringardner.shell.Console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionFunction {

	public static class ExecuteResult {
		int exitCode=0;		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();			
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn) {
		Console console = new Console();
		PrintStream stdout= System.out;
		PrintStream stderr= System.err;
		InputStream stdin = System.in;
		
		ExecuteResult ret = new ExecuteResult();
		System.setOut(new PrintStream(ret.bao));
		System.setErr(new PrintStream(ret.bae));
		System.setIn(new ByteArrayInputStream(stdIn.getBytes()));
		
		ret.exitCode=console.executeUsingAntlr(command);
		System.setIn(stdin);
		System.setOut(stdout);
		System.setErr(stderr);
		
		if( ret.bae.size() !=0) {
			System.out.println(new String(ret.bae.toByteArray()));
		}
		return ret;
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
				+ "	$((var++))\n"
				+ "	if [ $var < 3]; then\n"
				+ "		f\n"
				+ "	fi\n"
				+ "}\n"
				+ "f\n"
				+ "\n"
				+ ""
				;
		System.out.println(cmd);
		String expect = 
				"0\n"
				+ "1\n"
				+ "2\n"
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
				+ "	$((var++))\n"
				+ "	if [ $var < 3]; then\n"
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		
		System.out.println(cmd);
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
				+ "	$((var++))\n"
				+ "	echo $var\n"
				+ ""
				;
		//System.out.println(cmd);
		String expect = 
				"0\n"
				+ "1\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
}
