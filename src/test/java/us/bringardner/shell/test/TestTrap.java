package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Scanner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import sun.misc.Signal;
import us.bringardner.shell.Console;

public class TestTrap extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		waitForJobs = true;
		console = new Console();
	}

	@AfterAll
	public static void afterAll() {

	}




	@Test
	public void testTrap00() throws IOException {
		String code = "trap \"echo hello from trap debug\" DEBUG\n"
				+ "echo simple echo line 1\n"
				+ "echo simple echo line 2\n"
				;
		String expect = "hello from trap debug\n"
				+ "simple echo line 1\n"
				+ "hello from trap debug\n"
				+ "simple echo line 2\n"
				;
		
		ExecuteResult res = executeCommand(code, "");
		assertEquals(0,res.exitCode);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(expect, out);
		assertEquals("", err);
		


	}

	/**
	 * This is not a unit test.  Run it manually and enter signal names in the keyboard.
	 *  
	 * @throws IOException
	 */
	
	public void testTrap01() throws IOException {
		String code = "trap -lpP action SIGKILL KILL 9";
		code = "trap 'rm -f \"$tmpfile\"' EXIT\n"
				+ "trap 'exit 2' KILL HUP INT QUIT TERM"
				;
		ExecuteResult res = executeCommand(code, "");
		System.out.println("testTrap01 needs work res="+res);
		try (Scanner in = new Scanner(System.in)) {
			System.out.print("Enter Signal: ");
			String line = in.next();
			while(line !=null) {
				
				line = line.toUpperCase();
				if( line.startsWith("SIG")) {
					line = line.substring(3);
				}
				Signal signal = new Signal(line);
				try {
					Signal.raise(signal);	
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.out.print("Enter Signal: ");
				line = in.next();
			}
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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
		assertEquals(expect, new String(res.bao.toByteArray()));
		assertEquals("", new String(res.bae.toByteArray()));

		code = "kill -L\n"
				+ ""
				;

		res = executeCommand(code, "");
		assertEquals(0, res.exitCode);
		assertEquals(expect, new String(res.bao.toByteArray()));
		assertEquals("", new String(res.bae.toByteArray()));

		code = "kill -l 9\n"
				+ ""
				;

		res = executeCommand(code, "");
		assertEquals(0, res.exitCode);
		assertEquals("KILL\n", new String(res.bao.toByteArray()));
		assertEquals("", new String(res.bae.toByteArray()));

		code = "kill -l HUP\n"
				+ ""
				;

		res = executeCommand(code, "");
		assertEquals(0, res.exitCode);
		assertEquals("HUP\n", new String(res.bao.toByteArray()));
		assertEquals("", new String(res.bae.toByteArray()));
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
		String val = new String(res.bae.toByteArray());
		assertEquals("", val);
		assertEquals(expect, new String(res.bao.toByteArray()));
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
		assertEquals("", new String(res.bae.toByteArray()));
		assertEquals(expect, new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
		
	}

}
