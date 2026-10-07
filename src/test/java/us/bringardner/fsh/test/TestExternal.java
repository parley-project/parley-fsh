package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.InteractiveInput;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.FileSourceShVisitorImpl;
import us.bringardner.fsh.antlr.Statement;
import us.bringardner.fsh.antlr.signal.ExitException;


public class TestExternal extends AbstractConsoleTest {


	public static void setup(String home) throws IOException {
		AbstractConsoleTest.setup(home);

	}


	@Test
	public void testExternal01() throws Exception{
		setup("ExternalTestFiles");
		String cmd = "/usr/bin/wc -w\n";

		if( getOs()==OperatingSystem.Windows) {
			cmd = "wc -w\n";	
		}
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
		String cmd = "/usr/bin/wc\n";
		if( getOs()==OperatingSystem.Windows) {
			cmd = "wc\n";	
		}

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
		String cmd = "/usr/bin/wc -Lclw *\n";
		if( getOs()==OperatingSystem.Windows) {
			cmd = "wc -Lclw *\n";
		}
		String expectOut = "[ ]+45[ ]+168[ ]+1547[ ]+79.*AbcFile.js\n"
				+ "[ ]+156[ ]+537[ ]+3710[ ]+86.*AbcFile.php\n"
				+ "[ ]+45[ ]+314[ ]+2048[ ]+76.*AbcFile.properties\n"
				+ "[ ]+122[ ]+679[ ]+4958[ ]+126.*AbcFile.txt\n"
				+ "[ ]+368[ ]+1698[ ]+12263[ ]+126[ ]+total\n"
				+ "";
		if( getOs()==OperatingSystem.Linux) {
			// linux wc counts tabs as 8 
			 expectOut = "[ ]+45[ ]+168[ ]+1547[ ]+79.*AbcFile.js\n"
						+ "[ ]+156[ ]+537[ ]+3710[ ]+107.*AbcFile.php\n"
						+ "[ ]+45[ ]+314[ ]+2048[ ]+76.*AbcFile.properties\n"
						+ "[ ]+122[ ]+679[ ]+4958[ ]+126.*AbcFile.txt\n"
						+ "[ ]+368[ ]+1698[ ]+12263[ ]+126[ ]+total\n"
						+ "";
		}

		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;


		ExecuteResult ret = executeCommand(cmd,stdIn,exitCode);
		String out = ret.getStdOut();
	
			String expectLines[] =expectOut.split("\n");
			String outLines[] = out.split("\n");
			assertEquals(expectLines.length, outLines.length);
			for (int idx = 0; idx < outLines.length; idx++) {
				String eline = expectLines[idx];
				String oline = outLines[idx];
				Pattern p = Pattern.compile(eline);
				Matcher m = p.matcher(oline);
				boolean ok = m.matches();
				assertTrue(ok,"idx="+idx);	
		}
		String err = ret.getStdErr();
		assertEquals(expectErr, err);


	}

	@Test
	public void testExternal04() throws Exception{
		if( getOs()==OperatingSystem.Windows) {
			return;
		}
		
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
		// the position of -O (the old token started at the space before it)
		String expectErr = "fsh 1,4: -O: invalid option\n";
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

		String cmd = "test01.sh\n";
		String expectOut = "hello\n";
		if( getOs() == OperatingSystem.Windows) {
			cmd = "test01.bat\n";
			expectOut = "hello \n";
		}
		

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
		if( getOs() == OperatingSystem.Windows) {
			cmd = "test01.bat dude\n";
		}
		
		String expectOut = "hello dude\n";

		String stdIn = "";
		String expectErr = "";
		int exitCode = 0;

		executeCommand(cmd,stdIn,exitCode,expectOut,expectErr);		
	}

	@Test
	public void testExternal07() throws Exception{
		if( getOs() == OperatingSystem.Windows) {
			// on *nix machines this will fail because there is no shebag so nothing to do on windows
			return;
		}
		
		setup("ExternalTestFiles");

		String cmd = "test02.sh dude\n";
		
		
		String expectErr = "execute permission denied: test02.sh\n"
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
		if( getOs() == OperatingSystem.Windows) {
			// no shs command
			return;
		}
		
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

	/**
	 * Typed input that never ends, like the GUI console's keyboard.
	 */
	static class TypedInput extends InputStream implements InteractiveInput {
		final LinkedBlockingQueue<Integer> data = new LinkedBlockingQueue<>();

		void type(String text) {
			for(byte b : text.getBytes()) {
				data.add(b & 0xff);
			}
		}

		@Override
		public int read() throws IOException {
			try {
				return data.take();
			} catch (InterruptedException e) {
				throw new InterruptedIOException();
			}
		}

		@Override
		public int available() {
			return data.size();
		}
	}

	@Test
	public void testExternalOutputKeepsCallerStdout() throws Exception{
		assumeFalse(getOs()==OperatingSystem.Windows);
		setup("ExternalTestFiles");
		ExecuteResult res = executeCommand("x=$(seq 1 3; echo b); echo \"[$x]\"","");
		assertEquals("[1\n2\n3\nb]\n", res.getStdOut());
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testExternalLargeOutputIsComplete() throws Exception{
		assumeFalse(getOs()==OperatingSystem.Windows);
		setup("ExternalTestFiles");
		ExecuteResult res = executeCommand("seq 1 200000","");
		assertEquals(0, res.exitCode);
		assertEquals(200000, res.getStdOut().split("\n").length);
		assertTrue(res.getStdOut().endsWith("\n200000\n"));
	}

	@Test
	public void testExternalDoesNotTakeInteractiveInput() throws Exception{
		assumeFalse(getOs()==OperatingSystem.Windows);
		setup("ExternalTestFiles");
		Console c = new Console();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		c.setStdOut(new PrintStream(out, true));
		c.setStdErr(new PrintStream(out, true));
		ShellContext sc = new ShellContext(c);
		TypedInput typed = new TypedInput();
		sc.stdin = typed;

		assertEquals(0, c.executeUsingAntlr(sc, "/bin/echo hi"));
		assertEquals("hi\n", out.toString());

		// the next line belongs to the shell, not to the finished command
		typed.type("next\n");
		Thread.sleep(300);
		assertEquals(5, typed.available());
	}

	@Test
	public void testExternalProcessEndsWhenJobStops() throws Exception{
		assumeFalse(getOs()==OperatingSystem.Windows);
		setup("ExternalTestFiles");
		Console c = new Console();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		c.setStdOut(new PrintStream(out, true));
		c.setStdErr(new PrintStream(out, true));
		ShellContext sc = new ShellContext(c);
		sc.stdin = new ByteArrayInputStream(new byte[0]);

		Statement sleep = FileSourceShVisitorImpl.parse("/bin/sleep 47").get(0);
		// run the statement directly: Console.exit would call System.exit off the JUnit thread
		Thread job = new Thread(() -> {
			try {
				sleep.process(sc);
			} catch (Exception e) {
				// ExitException is expected
			}
		});
		job.setDaemon(true);
		long start = System.currentTimeMillis();
		job.start();
		Thread.sleep(1000);
		// what a job does when it is interrupted or killed
		sc.setExecption(new ExitException(sc, 1));
		job.join(10000);

		assertFalse(job.isAlive());
		assertTrue(System.currentTimeMillis()-start < 10000);
		Process pgrep = new ProcessBuilder("pgrep","-f","/bin/sleep 47").start();
		pgrep.waitFor();
		assertEquals("", new String(pgrep.getInputStream().readAllBytes()).trim());
	}
}
