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
		
	}

	@AfterAll
	public static void afterAll() {

	}

	@Test
	public void testDisown01() throws IOException, InterruptedException {
		// disown a specific job
		String expectErr = "";
		String expectOut = ""
				+ "[1] - Running sleep   2   \n"
				+ "[2] + Running sleep   1   \n"
				+ "[2] + Running sleep   1   \n"
				+ "";
		
		String code = ""
				+ "sleep 2 &\n"
				+ "sleep 1 &\n"
				+ "jobs\n"
				+ "disown %1\n"
				+ "jobs"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		
		ExecuteResult res = executeCommand(code, "");
		Thread.sleep(20);
		String val = new String(res.bae.toByteArray());
		assertEquals(expectErr, val);
		assertEquals(expectOut, new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
		
	}

	@Test
	public void testDisown02() throws IOException, InterruptedException {
		// disown all running jobs
		String expectErr = "";
		String expectOut = "[1] - Running sleep   40   \n"
				+ "[2] + Running sleep   2   \n"
				+ "[1] - Suspended sleep   40   \n"
				+ "[2] + Running sleep   2   \n"
				+ "[1] - Suspended sleep   40   \n"
				+ "";
		
		String code = ""
				+ "sleep 40 &\n"
				+ "sleep 2 &\n"
				+ "jobs\n"
				+ "kill -TSTP %1\n"
				+ "jobs\n"
				+ "disown -r\n"
				+ "jobs\n"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		waitForJobs = false;
		ExecuteResult res = executeCommand(code, "");
		Thread.sleep(10);
		String val = new String(res.bae.toByteArray());
		assertEquals(expectErr, val);
		assertEquals(expectOut, new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
		
	}
	

	@Test
	public void testDisown03() throws IOException, InterruptedException {
		// disown all running jobs
		String expectErr = "";
		String expectOut = "[1] - Running sleep   40   \n"
				+ "[2] + Running sleep   2   \n"
				+ "";
		
		String code = ""
				+ "sleep 40 &\n"
				+ "sleep 2 &\n"
				+ "jobs\n"
				+ "disown -a\n"
				+ "jobs\n"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		waitForJobs = false;
		ExecuteResult res = executeCommand(code, "");
		Thread.sleep(10);
		String val = new String(res.bae.toByteArray());
		assertEquals(expectErr, val);
		assertEquals(expectOut, new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
		
	}
	
	
}
