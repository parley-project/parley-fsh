package us.bringardner.shell.test.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;
import us.bringardner.shell.test.AbstractConsoleTest;

public class TestDisown extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		waitForJobs = true;
		AbstractConsoleTest.setup("TestFiles");
		console.isInteractive=false;
		waitForJobs=false;
	}

	@AfterAll
	public static void afterAll() {
		waitForJobs=true;
	}

	@Test
	public void testDisown01() throws IOException, InterruptedException {
		// disown a specific job
		String expectErr = "";
		
		String code = ""
				+ "sleep 1 &\n"
				+ "sleep 1 &\n"
				+ "jobs\n"
				+ "disown %1\n"
				+ "jobs"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		ExecuteResult res = executeCommand(code, "");
		String val = res.getStdErr();
		assertEquals(expectErr, val);
		
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testDisown02() throws IOException, InterruptedException {
		// disown all running jobs
		String expectErr = "";
		
		String code = ""
				+ "sleep 4 &\n"
				+ "sleep 2 &\n"
				+ "jobs\n"
				+ "kill -TSTP %1\n"
				+ "jobs\n"
				+ "disown -r\n"
				+ "jobs\n"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		ExecuteResult res = executeCommand(code, "");
		String val = res.getStdErr();
		assertEquals(expectErr, val);
		assertEquals(0, res.exitCode);
		
	}
	

	@Test
	public void testDisown03() throws IOException, InterruptedException {
		// disown all running jobs
		String expectErr = "";
		
		String code = ""
				+ "sleep 4 &\n"
				+ "sleep 2 &\n"
				+ "jobs\n"
				+ "disown -a\n"
				+ "jobs\n"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		ExecuteResult res = executeCommand(code, "");
		String val = res.getStdErr();
		assertEquals(expectErr, val);
		assertEquals(0, res.exitCode);
		
	}
	
	
}
