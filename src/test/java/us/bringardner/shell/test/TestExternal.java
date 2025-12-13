package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;


//TestMethodOrder(OrderAnnotation.class)
public class TestExternal extends AbstractConsoleTest {

	
	public static void setup(String home) throws IOException {
		AbstractConsoleTest.setup(home);
		
	}


	@Test
	public void testExternal01() throws Exception{
		setup("ExternalTestFiles");
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
		setup("ExternalTestFiles");
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
		String out = ret.getStdOut();
		Pattern p = Pattern.compile(expectOut);
		Matcher m = p.matcher(out);
		boolean ok = m.matches();
		assertTrue(ok);
		String err = ret.getStdErr();
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
		setup("ExternalTestFiles");
		
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
		setup("ExternalTestFiles");
		
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
		setup("ExternalTestFiles");
		
		String cmd = "set -One -two\n"
				+ "echo $*\n"
				;

		String expectOut = "\n";
		String stdIn = "";
		String expectErr = "fssh 1,3: -O: invalid option\n";
		int exitCode = 0;
		boolean tmp = showError;
		showError = false;
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		showError = tmp;
	}

	@Test
	public void testExternal04_4() throws Exception{
		setup("ExternalTestFiles");
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

		String expectErr = "java.io.IOException: execute permission denied: test02.sh\n"
				+ "";
		
		String stdIn = "";
		String expectOut = "";
		int exitCode = 1;
		boolean tmp = showError;
		showError = false;
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		showError = tmp;
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
	public void testExternal11() throws Exception{
		setup("ExternalTestFiles");
		String path = new File(testFilesDir,"test03.fssh").getCanonicalPath();
		
		String cmd[] = {path,"dude"};
		String expectOut = "hello dude\n";
		
		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;
		
		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);
		
	}
	
}
