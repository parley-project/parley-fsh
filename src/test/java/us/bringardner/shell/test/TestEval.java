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
public class TestEval  extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	
	@Test
	public void testEval01() throws Exception{
		String cmd = "var=\"Test value\"\n"
				+ "command=\"echo $var\"\n"
				+ "eval \"$command\"\n"
				+ ""
				;

		String stdIn = "";
		int exitCode = 0;
		String expect = "Test value\n";
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String out = ret.getStdOut();
		String err = ret.getStdErr();;
		if( !err.isEmpty()) {
			System.out.println(cmd);
			System.out.println(err);
		}
		assertEquals(expect, out);
		assertEquals("", err);
	}
	//									   Sun            Nov              9             20      : 53       :28             EST      2025
	//                                    "Sun            Aug              3             09      : 49       :01             EDT      2025"
	Pattern datePattern = Pattern.compile("[a-zA-Z]{3}\\s+[a-zA-Z]{3}\\s+[0-9]{1,2}\\s+[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}\\s+[A-Z]{3}\\s+[0-9]{4}\\s*");
	//Windows date 
	// 																			Thu              12           /    11         /     2025
	Pattern winDatePattern = Pattern.compile("[a-zA-Z]{3}\\s+[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}\\s*");
	
	@Test
	public void testEval02() throws Exception{

		String cmd = "command=\"echo $(date)\"\n"
				+ "eval \"$command\""
				;
		if(getOs()==OperatingSystem.Windows) {
			 cmd = "command=\"echo $(date /T)\"\n"
						+ "eval \"$command\""
						;
		}
		String stdIn = "";
		int exitCode = 0;
		//String expect = "Test value\n";
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		
		String out = ret.getStdOut();;
		String err = ret.getStdErr();;
		if( !err.isEmpty()) {
			System.out.println(cmd);
			System.out.println(err);
		}
		assertEquals("", err);
		Matcher m = datePattern.matcher(out);
		if(getOs()==OperatingSystem.Windows) {
			m = winDatePattern.matcher(out);
		}
		assertTrue(m.matches());
		
	}
	
	@SuppressWarnings("unused")
	@Test
	public void testEval03() throws Exception{
		// make sure we fail in the same way bash does
		String cmd = "command=\"$(date)\"\n"
				+ "eval \"$command\""
				;

		String stdIn = "";
		int exitCode = 1;
		String expect = "";
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		String out = ret.getStdOut();;
		String err = ret.getStdErr();;
		//assertTrue(err.startsWith("java.io.IOException: command not found:"));
		//assertEquals(expect, out);
		
		
	}
}
	
