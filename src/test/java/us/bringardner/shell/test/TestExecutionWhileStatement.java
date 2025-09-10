package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import us.bringardner.shell.Console;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionWhileStatement extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	@Test
	public void testWhileStatent01_1() throws Exception{
		String cmd = "i=0\n"
				+ "while [ $i -le 2 ]\n"
				+ "do\n"
				+ "  echo Number: $i\n"
				+ "  ((i++))\n"
				+ "done"
				;

		String expect = 
				"Number : 0\n"
				+"Number : 1\n"
				+"Number : 2\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testWhileStatent01_2() throws Exception{
		String cmd = 
				  "i=0\n"
				+ "while [ true ]\n"
				+ "do\n"
				+ "  echo Number: $i\n"
				+ "  ((i++))\n"
				+ "	 if [ $i == 2 ] ; then\n"
				+ "		break\n"
				+ "	 fi\n"
				+ "done"
				;

		String expect = 
				"Number : 0\n"
				+"Number : 1\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}
	
	@Test
	public void testWhileStatent01_3() throws Exception{
		String cmd = 
				  "i=0\n"
				+ "while [ true ]\n"
				+ "do\n"
				+ "  ((i++))\n"
				+ "	 if [ $i == 2 ] ; then\n"
				+ "		continue\n"
				+ "	 fi\n"
				+ "  echo Number: $i\n"				
				+ "	 if [ $i == 4 ] ; then\n"
				+ "		break\n"
				+ "	 fi\n"
				+ "done"
				;

		String expect = 
				"Number : 1\n"
				+"Number : 3\n"
				+"Number : 4\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}
	
}
