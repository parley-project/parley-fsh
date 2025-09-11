package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionEval  extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	
	@Test
	public void testExport01() throws Exception{
		String cmd = "var=\"Test value\"\n"
				+ "command=\"echo $var\"\n"
				+ "eval \"$command\"\n"
				+ ""
				;

		String stdIn = "";
		int exitCode = 0;
		String expect = "Test value\n";
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String out = new String(ret.bao.toByteArray());
		String err = new String(ret.bae.toByteArray());
		if( !err.isEmpty()) {
			System.out.println(cmd);
			System.out.println(err);
		}
		assertEquals(expect, out);
		assertEquals("", err);
	}
	//                                    "Sun         Aug         3          09         : 49         : 01         EDT      2025"
	Pattern datePattern = Pattern.compile("[a-zA-Z]{3}\\s+[a-zA-Z]{3}\\s+[0-9]{1,2}\\s+[0-9]{1,2}\\s+:\\s+[0-9]{1,2}\\s+:\\s+[0-9]{1,2}\\s+[A-Z]{3}\\s+[0-9]{4}\\s*");
	
	@Test
	public void testExport02() throws Exception{
		String cmd = "command=\"echo $(date)\"\n"
				+ "eval \"$command\""
				;

		String stdIn = "";
		int exitCode = 0;
		//String expect = "Test value\n";
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String out = new String(ret.bao.toByteArray());
		String err = new String(ret.bae.toByteArray());
		if( !err.isEmpty()) {
			System.out.println(cmd);
			System.out.println(err);
		}
		assertEquals("", err);
		Matcher m = datePattern.matcher(out);
		assertTrue(m.matches());
		
	}
	
	@Test
	public void testExport03() throws Exception{
		// make sure we fail in the same way bash does
		String cmd = "command=\"$(date)\"\n"
				+ "eval \"$command\""
				;

		String stdIn = "";
		int exitCode = 1;
		String expect = "";
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String out = new String(ret.bao.toByteArray());
		String err = new String(ret.bae.toByteArray());
		assertTrue(err.startsWith("java.io.IOException: command not found:"));
		assertEquals(expect, out);
		
		
	}
}
	
