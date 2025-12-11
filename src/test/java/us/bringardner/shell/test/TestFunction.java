package us.bringardner.shell.test;

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
				+ "	$((var++))\n"
				+ "	if [ $var < 3]; then\n"
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
	
}
