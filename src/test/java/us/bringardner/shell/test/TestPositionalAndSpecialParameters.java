package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;

public class TestPositionalAndSpecialParameters extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}


	@Test
	public void testPositionalParameters01() {
		String expect = 
				"fssh\n"+
						"one\n"+
						"two\n"
						;
		String code = ""				
				+ "echo $0\n"
				+ "echo $1\n"
				+ "echo $2\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "","one","two");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testSpecialParameters01() {
		String expect = 

				"one two\n"
				+ "2\n"
				+ "0\n"
				+ "B\n"
				+ "0\n"
				+ "0\n"
				+ ""
						;
		String code = ""				
				+ "echo $*\n"
				+ "echo $#\n"
				+ "echo $?\n"
				+ "echo $-\n"
				+ "echo $$\n"
				+ "echo $!\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "","one","two");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}
	
	@Test
	public void testPositionalParameters02() {
		String expect = 
				"_one two\n"
						;
		String code = ""				
				+ "echo ${@#tony}\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "","tony_one","tonytwo");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}
	
	@Test
	public void testPositionalParameter03() {
		AbstractConsoleTest.console = new Console();
		
		String expect = "hello_world_of\n"
				+ "hello\n";
		String code = "a='hello_world_of_tomorrow'\n"
				+ "echo \"${a%_*}\"\n"
				+ "echo \"${a%%_*}\"\n"
				
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "","hello_world_of_tomorrow");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}
	
	@Test
	public void testPositionalParameters04() {
		String expect = 
				"345\n"
				+ "nyt\n"
						;
		String code = ""				
				+ "echo ${1:2:3}\n"
				+ "echo ${2:2:3}\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "","123456789","tonytwo");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}
	
	@Test
	public void testPositionalParameters05() {
		String expect = 
				"5\n"
				+ "one two three four five\n"
				+ "4\n"
				+ "two three four five\n"
				+ "3\n"
				+ "three four five\n"
				+ "2\n"
				+ "four five\n"
				+ "1\n"
				+ "five\n"
				+ ""
						;
		String code = ""				
				+ "until [ $# -eq 0 ] \n"
				+ "do\n"
				+ "	echo $#\n"
				+ "	echo $*\n"
				+ "	shift\n"
				+ "done\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code,"", "one","two","three","four","five");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}
	@Test
	public void testPositionalParameters06() {
		String expect = 
				"7\n"
				+ "one two three four five six seven\n"
				+ "5\n"
				+ "three four five six seven\n"
				+ "3\n"
				+ "five six seven\n"
				+ ""
						;
		String code = ""				
				+ "until [ $# -le 2 ] \n"
				+ "do\n"
				+ "	echo $#\n"
				+ "	echo $*\n"
				+ "	shift 2\n"
				+ "done\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code,"", "one","two","three","four","five","six","seven");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testSpeacialParameters02() {
		String expect = 
				"two three four\n"
				
						;
		String code = ""				
				+ "echo ${*:2:3}\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "","one","two","three","four","five");
		String actual = res.getStdOut();
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}
}
