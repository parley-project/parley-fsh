package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;

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
		boolean showErrTmp = showError;
		showError = false;
		String expect = "HUP     INT     QUIT    ILL     TRAP    ABRT    FPE     KILL    BUS     SEGV    SYS     PIPE    ALRM    TERM    URG     STOP    TSTP    CONT    \n"
				+ "CHLD    TTIN    TTOU    IO      XCPU    XFSZ    VTALRM  PROF    WINCH   USR1    USR2\n"
				;
		if(getOs()==OperatingSystem.Windows) {
			expect = "INT   ILL   FPE   SEGV  TERM  ABRT\n";
		} else if( getOs()==OperatingSystem.Linux) {
			expect = "HUP     INT     QUIT    ILL     TRAP    ABRT    BUS     FPE     KILL    USR1    SEGV    USR2    PIPE    ALRM    TERM    CHLD    CONT    STOP    \n"
					+ "TSTP    TTIN    TTOU    URG     XCPU    XFSZ    VTALRM  PROF    WINCH   IO      SYS\n"
					;
		}
		
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
		if(getOs()==OperatingSystem.Windows) {
			assertEquals("", res.getStdOut());
			assertEquals("kill: (9) - No such signal", res.getStdErr().trim());
		} else {
			assertEquals("KILL\n", res.getStdOut());
			assertEquals("", res.getStdErr());
		}

		code = "kill -l HUP\n"
				+ ""
				;

		res = executeCommand(code, "");
		// as in bash, a name gives its number
		if(getOs()==OperatingSystem.Windows) {
			assertEquals(1, res.exitCode);
			assertEquals("", res.getStdOut());
			assertEquals("kill: HUP: invalid signal specification", res.getStdErr().trim());
		} else {
			assertEquals(0, res.exitCode);
			assertEquals("1\n", res.getStdOut());
			assertEquals("", res.getStdErr());
		}
		showError = showErrTmp;
	}
	
	/** the shell's trap for a signal is not the job's: kill -INT ends the job (an interactive shell) */
	@Test
	public void testKill02() throws IOException, InterruptedException {
		String code = "function handle_ctrlc02() {\n"
				+ "    echo \"Caught SIGINT! Exiting...\"\n"
				+ "    exit\n"
				+ "}\n"
				+ "\n"
				+ "trap handle_ctrlc02 SIGINT\n"
				+ "\n"
				+ "function work() { "
				+ "while true; do\n"
				+ "    echo \"Running... Press Ctrl+C to stop.\"\n"
				+ "    sleep 5\n"
				+ "done\n"
				+ "}\n"
				+ "work &\n"				
				+ "kill -s INT $!\n"
				+ "wait $!\n"
				+ "echo \"st=$?\"\n"
				;

		console = new Console();
		Console.setNextPid(100000);
		console.jobManager.clear();
		console.isInteractive=true;
		ExecuteResult res = executeCommand(code, "");
		String out = res.getStdOut();
		assertEquals("", res.getStdErr());
		assertTrue(out.contains("[1] 100000\n"), out);
		assertTrue(out.contains("st=130\n"), out);
		assertFalse(out.contains("Caught SIGINT"), out);
		assertEquals(0, res.exitCode);
	}

	/** as in bash, without job control a job ignores SIGINT; TERM ends it (143) */
	@Test
	public void testKill03() throws IOException, InterruptedException {
		String code = "work() { while true; do sleep 1; done; }\n"
				+ "work &\n"
				+ "sleep 0.2\n"
				+ "kill -s INT %1\n"
				+ "sleep 0.2\n"
				+ "jobs\n"
				+ "kill %1\n"
				+ "wait %1\n"
				+ "echo \"st=$?\"\n"
				+ "jobs\n"
				;

		console = new Console();
		Console.setNextPid(100000);
		console.jobManager.clear();
		console.isInteractive=false;
		
		ExecuteResult res = executeCommand(code, "");
		assertEquals("", res.getStdErr());
		assertEquals("[1]+  Running                    work &\nst=143\n", res.getStdOut());
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
		console.isInteractive=true;
		ExecuteResult res = executeCommand(code, "");
		String err = res.getStdErr();
		String out = res.getStdOut();
		
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
	}

}
