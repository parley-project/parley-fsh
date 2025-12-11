package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;

public class TestKill extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		waitForJobs = true;
		console = new Console();
	}

	@AfterAll
	public static void afterAll() {

	}





	@Test
	public void testKill01() throws IOException {

		String expect = "HUP     INT     QUIT    ILL     TRAP    ABRT    FPE     KILL    BUS     SEGV    SYS     PIPE    ALRM    TERM    URG     STOP    TSTP    CONT    \n"
				+ "CHLD    TTIN    TTOU    IO      XCPU    XFSZ    VTALRM  PROF    WINCH   USR1    USR2\n"
				;
		
		String code = "kill -l\n"
				+ ""
				;

		ExecuteResult res = executeCommand(code, "");
		assertEquals(0, res.exitCode);
		assertEquals(expect, res.getStdOut());
		assertEquals("", res.getStdErr());

		code = "kill -L\n"
				+ ""
				;

		res = executeCommand(code, "");
		assertEquals(0, res.exitCode);
		assertEquals(expect, res.getStdOut());
		assertEquals("", res.getStdErr());

		code = "kill -l 9\n"
				+ ""
				;

		res = executeCommand(code, "");
		assertEquals(0, res.exitCode);
		assertEquals("KILL\n", res.getStdOut());
		assertEquals("", res.getStdErr());

		code = "kill -l HUP\n"
				+ ""
				;

		res = executeCommand(code, "");
		assertEquals(0, res.exitCode);
		assertEquals("HUP\n", res.getStdOut());
		assertEquals("", res.getStdErr());
	}
	
	@Test
	public void testKill02() throws IOException, InterruptedException {
		String expect = "Running... Press Ctrl+C to stop.\n"
				+ "[1] 100000\n"
				+ "Caught SIGINT! Exiting...\n";
		
		String code = "function handle_ctrlc02() {\n"
				+ "    echo \"Caught SIGINT! Exiting...\"\n"
				+ "    exit\n"
				+ "}\n"
				+ "\n"
				+ "trap handle_ctrlc02 SIGINT\n"
				+ "\n"
				+ "function work() {"
				+ "while true; do\n"
				+ "    echo \"Running... Press Ctrl+C to stop.\"\n"
				+ "    sleep 5\n"
				+ "done\n"
				+ "}\n"
				+ "work &\n"				
				+ "kill -s INT $!\n"
				//+ "sleep 1\n"
				;

		Console.setNextPid(100000);
		//Console.jobs.clear();
		console.isInteractive=true;
		ExecuteResult res = executeCommand(code, "");
		//  give time for signal handling
		Thread.sleep(50);
		String val = res.getStdErr();
		assertEquals("", val);
		assertEquals(expect, res.getStdOut());
		assertEquals(0, res.exitCode);
		
	}

	@Test
	public void testKill03() throws IOException, InterruptedException {
		String expect = "Running... Press Ctrl+C to stop.\n"
				+ "[1] 100000\n"
				+ "Caught SIGINT! Exiting...\n";
		
		String code = "function handle_ctrlc03() {\n"
				+ "    echo \"Caught SIGINT! Exiting...\"\n"
				+ "    exit\n"
				+ "}\n"
				+ "\n"
				+ "trap handle_ctrlc03 SIGINT\n"
				+ "\n"
				+ "function work() {"
				+ "while true; do\n"
				+ "    echo \"Running... Press Ctrl+C to stop.\"\n"
				+ "    sleep 5\n"
				+ "done\n"
				+ "}\n"
				+ "work &\n"				
				+ "kill -s INT %1\n"
				//+ "sleep 1\n"
				;

		console = new Console();
		Console.setNextPid(100000);
		console.jobManager.clear();
		console.isInteractive=true;
		
		ExecuteResult res = executeCommand(code, "");
		//  give time for signal handling
		Thread.sleep(50);
		assertEquals("", res.getStdErr());
		assertEquals(expect, res.getStdOut());
		assertEquals(0, res.exitCode);
		
	}
	
	@Test
	public void testKill04() throws IOException, InterruptedException {
		String expect = "[1] 100000\n";
		
		String code = "sleep 60 &\n"				
				+ "kill %?ee\n"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		//console.isInteractive=true;
		ExecuteResult res = executeCommand(code, "");
		String err = res.getStdErr();
		String out = res.getStdOut();
		
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
	}

}
