package us.bringardner.fsh.test;

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
		if( windowsDate() ) {
			 cmd = "command=\"echo $(date /T)\"\n"
						+ "eval \"$command\""
						;
		}
		String stdIn = "";
		int exitCode = 0;
		
		boolean tmp = showError;
		showError=false;
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		showError = tmp;
		String out = ret.getStdOut();;
		String err = ret.getStdErr();;
		if( !err.isEmpty()) {
			System.out.println(cmd);
			System.out.println(err);
		}
		assertEquals("", err);
		Matcher m = datePattern.matcher(out);
		if( windowsDate() ) {
			m = winDatePattern.matcher(out);
		}
		assertTrue(m.matches(), out);

	}

	/**
	 * Whether date is Windows's own (date /T): not where a Unix date.exe (Git for Windows, Cygwin) is
	 * on the PATH, which is what the shell runs then.
	 */
	private static boolean windowsDate() {
		if( getOs() != OperatingSystem.Windows ) {
			return false;
		}
		String path = System.getenv("PATH");
		if( path != null ) {
			for(String dir : path.split(java.io.File.pathSeparator)) {
				if( !dir.isEmpty() && new java.io.File(dir, "date.exe").isFile() ) {
					return false;
				}
			}
		}
		return true;
	}
	
	@SuppressWarnings("unused")
	@org.junit.jupiter.api.condition.DisabledOnOs(value = org.junit.jupiter.api.condition.OS.WINDOWS, disabledReason = "date is cmd.exe's own interactive command on Windows, not a program that is not found")
	@Test
	public void testEval03() throws Exception{
		// make sure we fail in the same way bash does
		String cmd = "command=\"$(date)\"\n"
				+ "eval \"$command\""
				;

		String stdIn = "";
		// the first word of the date is no command
		int exitCode = 127;
		String expect = "";
		boolean tmp = showError;
		showError=false;
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		showError=tmp;
		String out = ret.getStdOut();;
		String err = ret.getStdErr();;
		//assertTrue(err.startsWith("java.io.IOException: command not found:"));
		//assertEquals(expect, out);
		
		
	}
}
	
