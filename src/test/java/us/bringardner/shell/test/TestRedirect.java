package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;


@TestMethodOrder(OrderAnnotation.class)
public class TestRedirect {

	private static File testFilesDir;
	public static boolean debug = false;

	public static void setup(String home) throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		testFilesDir = new File(home).getCanonicalFile();
		System.setProperty("user.home", testFilesDir.getAbsolutePath());
	}

	public static class ExecuteResult {
		int exitCode=0;		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();			
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn,int exitCode,String expectOut,String expectErr) {
		Console console = new Console();
		
		ExecuteResult ret = new ExecuteResult();
		console.setStdOut(new PrintStream(ret.bao));
		console.setStdErr(new PrintStream(ret.bae));
		console.setStdIn(new ByteArrayInputStream(stdIn.getBytes()));
		
		ret.exitCode=console.executeUsingAntlr(command);
		
		String out = new String(ret.bao.toByteArray());
		String err = new String(ret.bae.toByteArray());
		if(debug && !err.isEmpty()) {
			System.out.println(command);
			System.out.println(err);
		}
		assertEquals(expectErr, err);
		assertEquals(expectOut, out);
		assertEquals(exitCode, ret.exitCode);
		
		return ret;
	}
	
	
	public static ExecuteResult executeCommand(String [] args,String stdIn,int exitCode,String expectOut,String expectErr) throws IOException {
		
		Console console = new Console();
		
		ExecuteResult ret = new ExecuteResult();
		console.setStdOut(new PrintStream(ret.bao));
		console.setStdErr(new PrintStream(ret.bae));
		console.setStdIn(new ByteArrayInputStream(stdIn.getBytes()));

		ret.exitCode = console.execute(args);
		
		
		String out = new String(ret.bao.toByteArray());
		String err = new String(ret.bae.toByteArray());
		if(debug && !err.isEmpty()) {
			System.out.println(args);
			System.out.println(err);
		}
		assertEquals(expectErr, err);
		assertEquals(expectOut, out);
		assertEquals(exitCode, ret.exitCode);
		
		return ret;
	}
	
	
	
	
	
	
	@Test
	public void testRedirect01() throws Exception{
		String cmd = "/usr/bin/wc <<EOF\n"
				+ "the quick brown fox jumped over the lasy dog\n"
				+ "EOF\n"
				;

		String expectOut = 
				"       1       9      45\n"
				;
		String stdIn = "the quick brown fox jumped over the lasy dog";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
	@Test
	public void testRedirect01_2() throws Exception{
		String cmd = "/usr/bin/wc <<EOF\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "EOF\n"
				;

		String expectOut = 
				"       1       9      46\n"
				;
		String stdIn = "the quick brown fox jumped over the lasy dog";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
	@Test
	public void testRedirect01_3() throws Exception{
		String cmd = "/usr/bin/wc <<-EOF\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "EOF\n"
				;

		String expectOut = 
				"       4      36     180\n"
				;
		String stdIn = "the quick brown fox jumped over the lasy dog";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}

	@Test
	public void testRedirect01_4() throws Exception{
		String cmd = "/usr/bin/wc <<EOF\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "\tthe quick brown fox jumped over the lasy dog\n"
				+ "EOF\n"
				;

		//System.out.println(cmd);
		String expectOut = 
				"       4      36     184\n"
				;
		String stdIn = "the quick brown fox jumped over the lasy dog";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
	@Test
	public void testRedirect01_5() throws Exception{
		String cmd = 
				"text=\"the quick brown fox jumped over the lasy dog\"\n"
				+ "/usr/bin/wc <<EOF\n"
				+ "\t$text\n"
				+ "EOF\n"
				;

		String expectOut = 
				"       1       9      46\n"
				;
		String stdIn = "the quick brown fox jumped over the lasy dog";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
	@Test
	public void testRedirect01_6() throws Exception{
		String cmd = "/usr/bin/wc <<EOF01\n"
				+ "the quick brown fox jumped over the lasy dog\n"
				+ "EOF01\n"
				;

		String expectOut = 
				"       1       9      45\n"
				;
		String stdIn = "the quick brown fox jumped over the lasy dog";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}



	@Test
	public void testRedirect02() throws Exception{
		setup("WcTestFiles");
		String cmd = "/usr/bin/wc -Lclw < AbcFile.js\n"
				;

		String expectOut = "      45     168    1547      79\n";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}

	@Test
	public void testRedirect03() throws Exception{
		setup("WcTestFiles");
		String cmd = "< AbcFile.js wc -Lclw \n"
				;

		String expectOut = "      45     168    1547      79\n";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}

	@Test
	public void testRedirect04() throws Exception{
		File dir = new File("target/logdir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs());
		}
		
		File logFile = new File(dir,"log.txt");
		if( logFile.exists()) {
			assertTrue(logFile.delete());
		}
		
		String cmd = "echo \"something to log\" > "+logFile.getCanonicalPath()+"\n";
				;

		String expectOut = "";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}

	public static ExecuteResult executeCommand(String cmd,String stdIn,int exitCode) throws IOException {
		Console console = new Console();
		
		ExecuteResult ret = new ExecuteResult();
		console.setStdOut(new PrintStream(ret.bao));
		console.setStdErr(new PrintStream(ret.bae));
		console.setStdIn(new ByteArrayInputStream(stdIn.getBytes()));
		
		
		
		ret.exitCode = console.executeUsingAntlr(cmd);
		
		
		
		String err = new String(ret.bae.toByteArray());
		if(debug && !err.isEmpty()) {
			System.out.println(cmd);
			System.out.println(err);
		}
		
		assertEquals(exitCode, ret.exitCode);
		
		return ret;
	}
	
}
