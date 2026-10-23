package us.bringardner.fsh.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.fileproxy.FileProxyFactory;
import us.bringardner.fsh.Console;


@TestMethodOrder(OrderAnnotation.class)
public class TestPipeStatement extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		AbstractConsoleTest.setup("WcTestFiles");		
	}
	

	/**
	 * The output of "ls -l | wc": lines, words and bytes. The lines and words are expect's (the
	 * date in each line is 12 characters, "Oct 15 06:29" or "Jan  2  2020", as ls shows it); the
	 * bytes depend on the width of the owner and group names (root, a domain\\user on Windows), so
	 * they are compared with what ls -l itself prints, as wc counts it.
	 */
	private static void assertWc(String expect, String actual) throws Exception {
		String [] e = expect.trim().split("\\s+");
		String [] a = actual.trim().split("\\s+");
		assertEquals(3, a.length, "wc's three counts: "+actual);
		assertEquals(e[0] + " " + e[1], a[0] + " " + a[1], "lines and words of " + actual);
		ExecuteResult ls = executeCommand("ls -l", "");
		int bytes = ls.getStdOut().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
		assertEquals(String.valueOf(bytes), a[2], "bytes of " + actual + " (ls -l printed:\n" + ls.getStdOut() + ")");
	}
	@Test
	public void testLogicStatent01() throws Exception{
		String cmd = "echo one && echo two"
				;

		String expect = 
				"one\n"
				+"two\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testLogicStatent02() throws Exception{
		String cmd = "echo one || echo two"
				;

		String expect = 
				"one\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testLogicStatent03() throws Exception{
		String cmd = 
				"false || echo two\n"
				+"true || echo two\n"
				+"false || echo two"
				;

		cmd = 
				"false || echo two\n"
				;
		String expect = 
				"two\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testPipeStatent01() throws Exception{
		String cmd = "ls -l | wc"
				;

		String expect = "4      36     234";
		
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertWc(expect, out.trim());
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testPipeStatent02() throws Exception{
		String cmd = "time  ls -l | wc"
				;

		String expect = 
				"4      36     234"
				;
		
		boolean tmp = showError;
		showError = false;
		ExecuteResult res = executeCommand(cmd,"");
		showError=tmp;
		String out = res.getStdOut();
		String err = res.getStdErr();
		// bash's format: a blank line, then real, user and sys
		assertTrue(err.startsWith("\nreal\t"));
		assertWc(expect, out.trim());
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testPipeStatent03() throws Exception{
		String cmd = "time -p ! ls -l | wc"
				;

		String expect = 
				"4      36     234"
				;
		
		boolean tmp = showError;
		showError = false;
		ExecuteResult res = executeCommand(cmd,"");
		showError=tmp;
		String out = res.getStdOut();
		String err = res.getStdErr();
		
		assertTrue(err.startsWith("real "));
		assertWc(expect, out.trim());
		assertEquals(1, res.exitCode);
		
		
	}

	@Test
	public void testBackground01() throws Exception{
		String cmd = "sleep 10 &\n"
				+ "sleep 20 &\n"
				+ "kill 100001\n"
				+ "kill 100000\n"
				;

		String expect = 
				"[1] 100000\n"
				+ "[2] 100001"
				;
		
		Console.setNextPid(100000);
		console.jobManager.clear();
		console.isInteractive=true;
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		
		assertEquals("", err);
		if( getOs() == OperatingSystem.Windows ) {
			// (as before: the first job only)
			assertTrue(out, out.trim().startsWith("[1] 100000"));
		} else {
			assertEquals(expect, out.trim());
		}
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	public void testBackground03_interactive() throws Exception{
		executeBackground03(true);
	}

	@Test
	public void testBackground03_not_interactive() throws Exception{
		executeBackground03(false);
	}


	public void executeBackground03(boolean interactive) throws Exception{
		String cmd = 
				  "sleep 1000 &\n"
				+ "sleep 2000 &\n"
				+ "sleep 3000 &\n"				
				+ "jobs\n"
				;
		
		String expect1 = 
				"[1] 100000\n"
				+ "[2] 100001\n"
				+ "[3] 100002\n"
				+ "[1]   Running                    sleep 1000 &\n"
				+ "[2]-  Running                    sleep 2000 &\n"
				+ "[3]+  Running                    sleep 3000 &"
				;
		
		String expect2 = 
				"[1] 100000\n"
				+ "[2] 100001\n"
				+ "[3] 100002\n"
				+ "[1]   Running                    sleep 1000 &\n"
				+ "[2]-  Running                    sleep 2000 &"
				;
		
		if( !interactive) {
			 expect1 = 
						""
						+ "[1]   Running                    sleep 1000 &\n"
						+ "[2]-  Running                    sleep 2000 &\n"
						+ "[3]+  Running                    sleep 3000 &"
						;
			 expect2 = expect1;
		}
		//System.out.println(cmd);
		waitForJobs=false;
		
		boolean lastInteractive = console.isInteractive;
		console.isInteractive=interactive;
		console.jobManager.clear();
		if( interactive) {
			Console.setNextPid(100000);
		} else {
			Console.setNextPid(400000);
		}
		ExecuteResult res = executeCommand(cmd,"");
		// give time for stdout to get written
		Thread.sleep(10);
		
		String out = res.getStdOut().trim();
		String err = res.getStdErr().trim();
		
		assertEquals("", err);
		if( !expect1.equals(out)) {
			assertEquals(out, expect2);			
		}
		assertEquals(0, res.exitCode);

		cmd = "jobs %3\n";
		String expect = "[3]+  Running                    sleep 3000 &\n";
		res = executeCommand(cmd,"");
		out = res.getStdOut();
		err = res.getStdErr();		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %1\n";
		expect = "[1]   Running                    sleep 1000 &\n";
		res = executeCommand(cmd,"");
		out = res.getStdOut();
		err = res.getStdErr();		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %-\n";
		expect = "[2]-  Running                    sleep 2000 &\n";
		res = executeCommand(cmd,"");
		out = res.getStdOut();
		err = res.getStdErr();		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %+\n";
		expect = "[3]+  Running                    sleep 3000 &\n";
		res = executeCommand(cmd,"");
		out = res.getStdOut();
		err = res.getStdErr();		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %%\n";
		expect = "[3]+  Running                    sleep 3000 &\n";
		res = executeCommand(cmd,"");
		out = res.getStdOut();
		err = res.getStdErr();		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);
		if(interactive) {
			cmd = "kill 100000 100001 100002\n";
		} else {
			cmd = "kill 400000 400001 400002\n";
		}
		expect = "";
		waitForJobs=true;
		res = executeCommand(cmd,"");
		out = res.getStdOut();
		err = res.getStdErr();		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);
		
		console.isInteractive=lastInteractive;
	}

	@Test
	public void testPipeKeepsCallerStdout() throws Exception{
		ExecuteResult res = executeCommand("echo a | wc -l; echo AFTER","");
		assertEquals("", res.getStdErr());
		assertEquals("       1\nAFTER\n", res.getStdOut());
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testPipeInsideCommandSubstitution() throws Exception{
		ExecuteResult res = executeCommand("x=$(echo a | wc -l; echo b); echo \"[$x]\"","");
		assertEquals("", res.getStdErr());
		assertEquals("[       1\nb]\n", res.getStdOut());
		assertEquals(0, res.exitCode);
	}
}
