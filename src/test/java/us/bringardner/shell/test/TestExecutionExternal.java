package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionExternal {

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
	public void testExternal01() throws Exception{
		String cmd = "/usr/bin/wc -w\n"
				;

		String expectOut = 
				"       2\n"
				;
		String stdIn = "one two";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
	@Test
	public void testExternal02() throws Exception{
		String cmd = "/usr/bin/wc\n"
				;

		String expectOut = 
				"       0       9      44\n"
				;
		String stdIn = "the quick brown fox jumped over the lasy dog";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}

	@Test
	public void testExternal03() throws Exception{
		setup("WcTestFiles");
		String path = testFilesDir.getAbsolutePath();
		
		String cmd = "/usr/bin/wc -Lclw *\n"
				;

		String expectOut = String.format(
				""
				+ "      45     168    1547      79 .*/WcTestFiles/AbcFile.js\n"
				+ "     156     537    3710      86 .*/WcTestFiles/AbcFile.php\n"
				+ "      45     314    2048      76 .*/WcTestFiles/AbcFile.properties\n"
				+ "     122     679    4958     126 .*/WcTestFiles/AbcFile.txt\n"
				+ "     368    1698   12263     126 total\n"
				+ ""
				,path,path,path,path);
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		String out = new String(ret.bao.toByteArray());
		Pattern p = Pattern.compile(expectOut);
		Matcher m = p.matcher(out);
		boolean ok = m.matches();
		assertTrue(ok);
		String err = new String(ret.bae.toByteArray());
		assertEquals(expectErr, err);
		
		
	}
	
	@Test
	public void testExternal04() throws Exception{
		setup("WcTestFiles");
		
		String cmd = "/usr/bin/wc -Lclw AbcFile.js\n"
				;

		String expectOut = "      45     168    1547      79 AbcFile.js\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
	@Test
	public void testExternal04_1() throws Exception{
		
		
		String cmd = "set one two\n"
				+ "echo $*\n"
				;

		String expectOut = "one two\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}

	@Test
	public void testExternal04_2() throws Exception{
		
		
		String cmd = "set one -two\n"
				+ "echo $*\n"
				;

		String expectOut = "one -two\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	@Test
	public void testExternal04_3() throws Exception{
		String cmd = "set -One -two\n"
				+ "echo $*\n"
				;

		String expectOut = "";
		String stdIn = "";
		String expectErr = "fssh 1,3: -O: invalid option\n";
		int exitCode = 1;
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
	}

	@Test
	public void testExternal04_4() throws Exception{
		String cmd = "set -o kbecho\n"
				
				;

		String expectOut = "";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
	}
	
	
	@Test
	public void testExternal05() throws Exception{
		setup("ExternalTestFiles");
		
		String cmd = "test01.sh\n"
				;

		String expectOut = "hello\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
	@Test
	public void testExternal06() throws Exception{
		setup("ExternalTestFiles");
		
		String cmd = "test01.sh dude\n"
				;

		String expectOut = "hello dude\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);		
	}

	@Test
	public void testExternal07() throws Exception{
		setup("ExternalTestFiles");
		
		String cmd = "test02.sh dude\n"
				;

		String expectErr = "java.io.IOException: permission denied: test02.sh\n";
		
		String stdIn = "";
		String expectOut = "";
		int exitCode = 1;
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);		
	}

	@Test
	public void testExternal08() throws Exception{
		setup("ExternalTestFiles");
		
		String cmd = "sh test02.sh dude\n";
		String expectOut = "hello dude\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		cmd = "sh ./test02.sh dude\n";
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
	}

	@Test
	public void testExternal09() throws Exception{
		setup("ExternalTestFiles");
		String [] args = {
				"-ea ",
				 "-e",
				 "+ea",
				 "echo",
				 "hello",
				  "dude\n"
				};
		
		String in="";
		String out="hello dude\n";
		String err="";
		
		executeCommand(args, in, 0, out, err);
		
		
		
		
	}

	@Test
	public void testExternal10() throws Exception{
		setup("ExternalTestFiles");
		
		String cmd[] = {"test03.fssh","dude"};
		String expectOut = "hello dude\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}

	@Test
	public void testExternalRedirect01() throws Exception{
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
	public void testExternalRedirect01_2() throws Exception{
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
	public void testExternalRedirect01_3() throws Exception{
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
	public void testExternalRedirect01_4() throws Exception{
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
	public void testExternalRedirect01_5() throws Exception{
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
	public void testExternalRedirect01_6() throws Exception{
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
	public void testExternalRedirect02() throws Exception{
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
	public void testExternalRedirect03() throws Exception{
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
	public void testExternalRedirect04() throws Exception{
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
