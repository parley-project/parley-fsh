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
		String expect1 = ""
				+ "[1] - Running sleep   2   \n"
				+ "[2] + Running sleep   1   \n"
				+ "[2] + Running sleep   1   \n"
				+ "";
		
		String expect2 = ""
				+ "[1] - Running sleep   2   \n"
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
		boolean tmp = waitForJobs;
		waitForJobs=true;
		ExecuteResult res = executeCommand(code, "");
		waitForJobs=tmp;
		Thread.sleep(20);
		String val = res.getStdErr();
		assertEquals(expectErr, val);
		
		assertEquals(0, res.exitCode);
		String out = res.getStdOut().replaceAll("\r", "");
		if(!out.startsWith(expect1)) {
			if( !out.startsWith(expect2)) {
				throw new RuntimeException("Bad response="+out);
			}
		}
		
	}

	@Test
	public void testDisown02() throws IOException, InterruptedException {
		// disown all running jobs
		String expectErr = "";
		String expect1 = "[1] - Running sleep   40   \n"
				+ "[2] + Running sleep   2   \n"
				+ "[1] - Suspended sleep   40   \n"
				+ "[2] + Running sleep   2   \n"
				+ "[1] - Suspended sleep   40"
				;
		
		String expect2 = "[1] - Running sleep   40   \n"
				+ "[2] + Running sleep   2   \n"
				+ "[1] - Suspended sleep   40   \n"
				+ "[2] + Running sleep   2"
				;
		
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
		boolean tmp = waitForJobs;
		waitForJobs=false;
		ExecuteResult res = executeCommand(code, "");
		waitForJobs=tmp;
		
		Thread.sleep(10);
		String val = res.getStdErr();
		assertEquals(expectErr, val);
		assertEquals(0, res.exitCode);
		
		String out = res.getStdOut().trim().replaceAll("\r", "");
		if(!out.startsWith(expect1)) {
			if( !out.startsWith(expect2)) {
				throw new RuntimeException("Bad response="+out);
			}
		}
	}
	

	@Test
	public void testDisown03() throws IOException, InterruptedException {
		// disown all running jobs
		String expectErr = "";
		String expect1 = "[1] - Running sleep   40   \n"
				+ "[2] + Running sleep   2"
				;
		String expect2 = "[1] - Running sleep   40"
				;
		
		String code = ""
				+ "sleep 40 &\n"
				+ "sleep 2 &\n"
				+ "jobs\n"
				+ "disown -a\n"
				+ "jobs\n"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
		boolean tmp = waitForJobs;
		waitForJobs=false;
		ExecuteResult res = executeCommand(code, "");
		waitForJobs=tmp;
		
		Thread.sleep(10);
		String val = res.getStdErr();
		assertEquals(expectErr, val);
		assertEquals(0, res.exitCode);
		String out = res.getStdOut().trim().replaceAll("\r", "");
		if(!out.equals(expect1)) {
			if( !out.equals(expect2)) {
				throw new RuntimeException("Bad response="+out);
			}
		}
	}
	
	
}
