package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestReadStatement extends AbstractConsoleTest {

	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	@Test
	public void testRead01() throws Exception{
		String cmd = "read\n"
				+ "echo $REPLY"
				;

		String stdIn = "tony";
		String expect = "tony\n";
		int exitCode = 0;
		
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}
	
	@Test
	public void testRead02() throws Exception{
		String cmd = "read name\n"
				+ "echo $name"
				;

		String stdIn = "tony";
		String expect = "tony\n";
		int exitCode = 0;
		
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	@Test
	public void testRead03() throws Exception{
		String cmd = "read word word2\n"
				+ "echo $word $word2"
				;

		String stdIn = "one two";
		String expect = "one two\n";
		int exitCode = 0;
		
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	@Test
	public void testRead04() throws Exception{
		String cmd = "read word word2\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				;

		String stdIn = "one two three four";
		String expect = "one\ntwo three four\n";
		int exitCode = 0;
		
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	@Test
	public void testRead05() throws Exception{
		String cmd = "read word word2 word3 word4\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				+ "echo $word3\n"
				+ "echo $word4\n"
				;

		String stdIn = "one two";
		String expect = 
				"one\n"
				+ "two\n"
				+ "\n"
				+ "\n";
		int exitCode = 0;
		
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	@Test
	public void testRead06() throws Exception{
		String cmd = "read -N 5 word word2\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				;

		String stdIn = "one two";
		String expect = 
				"one\n"
				+ "t\n"
				;
		int exitCode = 0;
		
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	@Test
	public void testRead07() throws Exception{
		String cmd = "read -N 5 word word2\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				;

		String stdIn = "one\ntwo";
		String expect = 
				"one\n"
				+ "t\n"
				;
		int exitCode = 0;
		//System.out.println(cmd);
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	@Test
	public void testRead08() throws Exception{
		String cmd = "read -n 5 word word2\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				;

		String stdIn = "one\ntwo";
		String expect = 
				"one\n\n"				
				;
		int exitCode = 0;
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}
	
	@Test
	public void testRead09() throws Exception{
		String cmd = "read -n 5 word word2\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				;

		String stdIn = "one two\n";
		String expect = 
				"one\nt\n"				
				;
		int exitCode = 0;
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	@Test
	public void testRead10() throws Exception{
		String cmd = "read -a array\n"
				+ "echo $array[0]\n"
				+ "echo $array[1]\n"
				;
		//System.out.println(cmd);
		String stdIn = "one two\n";
		String expect = 
				"one\ntwo\n"				
				;
		int exitCode = 0;
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}
	
	@Test
	public void testRead11() throws Exception{
		String cmd = "read -p 'Enter name:' name\n"
				+ "echo $name\n"
				;
		//System.out.println(cmd);
		String stdIn = "tony\n";
		String expect = 
				"Enter name:tony\n"				
				;
		int exitCode = 0;
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}
	@Test
	public void testRead12() throws Exception{
		String cmd = "read -r word word2\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				;

		String stdIn = "one\\\ntwo";
		String expect = 
				"one\\\n\n"				
				;
		int exitCode = 0;
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}

	
	@Test
	public void testRead13() throws Exception{
		String cmd = "read -r word word2\n"
				+ "echo $word\n"
				+ "echo $word2\n"
				;

		String stdIn = "123\\456 ok\n";
		String expect = 
				"123\\456\n"
				+ "ok\n"				
				;
		int exitCode = 0;
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String actual = new String(ret.bao.toByteArray());
		assertEquals(expect, actual);
		
		//String err = new String(ret.bae.toByteArray());
		
	}
}
