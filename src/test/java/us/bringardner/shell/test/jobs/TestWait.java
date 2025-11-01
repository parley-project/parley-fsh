package us.bringardner.shell.test.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;
import us.bringardner.shell.test.AbstractConsoleTest;

public class TestWait extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		waitForJobs = true;
		AbstractConsoleTest.setup("TestFiles");		
	}

	@AfterAll
	public static void afterAll() {

	}




	@Test
	public void testWait01() throws IOException, InterruptedException {
		String expectOut = "[1] 100000\n";
		String expectErr = "";
		
		String code = ""
				+ "sleep 1 &\n"
				+ "wait"
				;

		Console.setNextPid(100000);
		console.isInteractive=true;
		long start = System.currentTimeMillis();
		ExecuteResult res = executeCommand(code, "");
		long stop = System.currentTimeMillis();
		long time = stop - start;
		System.out.println("time="+time);
		String val = new String(res.bae.toByteArray());
		assertEquals(expectErr, val);
		assertEquals(expectOut, new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
		
	}

	@Test
	public void testWait02() throws IOException, InterruptedException {
		String expectOut = "[1] 100000\n";
		String expectErr = "";
		
		String code = ""
				+ "sleep 1 &\n"
				+ "wait"
				;

		Console.setNextPid(100000);
		console.isInteractive=true;
		long start = System.currentTimeMillis();
		ExecuteResult res = executeCommand(code, "");
		long stop = System.currentTimeMillis();
		long time = stop - start;
		System.out.println("time="+time);
		String val = new String(res.bae.toByteArray());
		assertEquals(expectErr, val);
		assertEquals(expectOut, new String(res.bao.toByteArray()));
		assertEquals(0, res.exitCode);
		
	}

}
