package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestRedirect extends AbstractConsoleTest {

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn,int exitCode,String expectOut,String expectErr) throws IOException {
		
		ExecuteResult ret = executeCommand(command, stdIn, exitCode);
		
		String out = new String(ret.bao.toByteArray());
		String err = new String(ret.bae.toByteArray());
		assertEquals(expectErr, err);
		assertEquals(expectOut, out);
		
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
	public void testRedirect01_7() throws Exception{
		String cmd = "echo here 2>&1/dev/null"
				;

		String expectOut = 
				""
				;
		String stdIn = "";
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
		
		String expectData = "something to log";
		String cmd = "echo \""+expectData+"\" > "+logFile.getCanonicalPath()+"\n";
				;

		String expectOut = "";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);

		assertTrue(logFile.exists());
		
		try (InputStream in = new FileInputStream(logFile)) {
			String tmp = new String(in.readAllBytes());
			assertEquals(expectData+"\n", tmp);
		}
		
		assertTrue(logFile.delete());
		
	}

	@Test
	public void testRedirect04_01() throws Exception{
		File dir = new File("target/logdir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs());
		}
		
		File logFile = new File(dir,"log.txt");
		if( logFile.exists()) {
			assertTrue(logFile.delete());
		}
		
		String expectData = "something to log";
		/*
		 * exec 3<> /tmp/foo  #open fd 3.
echo "test" >&3
exec 3>&- #close fd 3.
		 */
		String cmd = "exec 3<> "+logFile+"\n"
				+ "echo \""+expectData+"\" >&3\n"
				+ "exec 3>&-\n";
				;
		String expectOut = "";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);

		assertTrue(logFile.exists());
		
		try (InputStream in = new FileInputStream(logFile)) {
			String tmp = new String(in.readAllBytes());
			assertEquals(expectData+"\n", tmp);
		}
		
		assertTrue(logFile.delete());
		
	}

	@Test
	public void testRedirect04_02() throws Exception{
		String stdIn = "";
		int exitCode = 0;
		String expectErr="";
		
		File dir = new File("target/logdir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs());
		}
		File dataFile = new File(dir,"data.txt");
		if( dataFile.exists()) {
			assertTrue(dataFile.delete());
		}
		
		String cmd = "exec 3>"+dataFile;
		String expectOut = "";
		// open fd3 for write
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
		assertTrue(dataFile.exists());
		
		// write 10 lines
		cmd = ""
				+ "for((  idx=0; idx < 10; idx++ ))\n"
				+ "do\n"
				+ "	echo \"Data file line $idx\" >&3\n"
				+ "done"
				;		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
		// close output
		cmd = "exec >&3-";
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		// open for input
		cmd = "exec 3<"+dataFile;
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
		// read 10 lines
		cmd = ""
				+ "while read -r line <&3; do\n"
				+ "    echo \"GOT: ${line}\"\n"
				+ "done"
				
				;
		expectOut = "GOT: Data file line 0\n"
				+ "GOT: Data file line 1\n"
				+ "GOT: Data file line 2\n"
				+ "GOT: Data file line 3\n"
				+ "GOT: Data file line 4\n"
				+ "GOT: Data file line 5\n"
				+ "GOT: Data file line 6\n"
				+ "GOT: Data file line 7\n"
				+ "GOT: Data file line 8\n"
				+ "GOT: Data file line 9\n"
				;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);

		// close input
		cmd = "exec <&3-";
		executeCommand(cmd,stdIn,exitCode,"",expectErr);

		// open for append
		cmd = "exec 3>>"+dataFile;
		executeCommand(cmd,stdIn,exitCode,"",expectErr);

		// write lines 10 - 19
		cmd = ""
				+ "for((  idx=10; idx < 20; idx++ ))\n"
				+ "do\n"
				+ "	echo \"Data file line $idx\" >&3\n"
				+ "done"
				;		
		executeCommand(cmd,stdIn,exitCode,"",expectErr);
			
		// close append output
		cmd = "exec >&3-";
		executeCommand(cmd,stdIn,exitCode,"",expectErr);

		// open for input
		cmd = "exec 3<"+dataFile;
		executeCommand(cmd,stdIn,exitCode,"",expectErr);
		
		// read 10 lines
		cmd = ""
				+ "while read -r line <&3; do\n"
				+ "    echo \"GOT: ${line}\"\n"
				+ "done"
				
				;
		expectOut = "GOT: Data file line 0\n"
				+ "GOT: Data file line 1\n"
				+ "GOT: Data file line 2\n"
				+ "GOT: Data file line 3\n"
				+ "GOT: Data file line 4\n"
				+ "GOT: Data file line 5\n"
				+ "GOT: Data file line 6\n"
				+ "GOT: Data file line 7\n"
				+ "GOT: Data file line 8\n"
				+ "GOT: Data file line 9\n"
				+ "GOT: Data file line 10\n"
				+ "GOT: Data file line 11\n"
				+ "GOT: Data file line 12\n"
				+ "GOT: Data file line 13\n"
				+ "GOT: Data file line 14\n"
				+ "GOT: Data file line 15\n"
				+ "GOT: Data file line 16\n"
				+ "GOT: Data file line 17\n"
				+ "GOT: Data file line 18\n"
				+ "GOT: Data file line 19\n"
				+ ""
				;

		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
	}

	@Test
	public void testRedirect04_03() throws Exception{
		setup("TestFiles");
		File dir = new File("target/logdir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs());
		}
		File file = new File(dir,"output.txt");
		if( file.exists()) {
			assertTrue(file.delete());
		}
		
		String cmd = 
				  "echo -n >|"+file+"\n"
				+ "echo step1 &>>"+file+" \n"
				+ "ls nofile &>>"+file+"\n"
				+ "echo step2 &>>"+file+" \n"
				+ "ls "+file+" &>>"+file+" \n"
				+ "echo step3 &>>"+file+" \n"
				;

		String expectOut = "";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		String expectData = "step1\n"
				+ "ls: /Volumes/Data/eclipse-git/BjlShell/TestFiles/nofile no such file or directory\n"
				+ "step2\n"
				+ "output.txt\n"
				+ "step3\n";
		
		try (InputStream in = new FileInputStream(file)) {
			String tmp = new String(in.readAllBytes());
			assertEquals(expectData, tmp);
		}
		
		assertTrue(file.delete());
	}

	@Test
	public void testRedirect04_04() throws Exception{
		File dir = new File("target/logdir").getCanonicalFile();
		if( !dir.exists()) {
			assertTrue(dir.mkdirs());
		}
		
		File logFile = new File(dir,"varlog.txt");
		if( logFile.exists()) {
			assertTrue(logFile.delete());
		}
		
		String expectData = "something to log";

		String cmd = "fid=3\n"
				+ "exec $fid<> "+logFile+"\n"
				+ "echo \""+expectData+"\" >&$fid\n"
				+ "exec $fid>&-\n";
				;
		String expectOut = "";
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);

		assertTrue(logFile.exists());
		
		try (InputStream in = new FileInputStream(logFile)) {
			String tmp = new String(in.readAllBytes());
			assertEquals(expectData+"\n", tmp);
		}
		
		assertTrue(logFile.delete());
		
	}

	
}
