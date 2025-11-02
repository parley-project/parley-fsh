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
		console.isInteractive=false;
		String code = "# Define a function\n"
				+ "my_function() {\n"
				+ "    echo \"Function started sleep=$1 ret=$2\"\n"
				+ "    sleep $1  \n"
				+ "    echo \"Function completed sleep=$1 ret=$2\"\n"
				+ "   return $2\n"
				+ "}\n"
				+ ""
				;
		// install the function
		ExecuteResult res = executeCommand(code, "");
		
		assertEquals(res.exitCode, 0);
	}

	@AfterAll
	public static void afterAll() {

	}




	@Test
	public void testWait01() throws IOException, InterruptedException {
		String expectOut = "";
		String expectErr = "";
		
		String code = ""
				+ "sleep 1 &\n"
				+ "wait"
				;

		Console.setNextPid(100000);
		console.jobManager.clear();
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
		String expectOut = "Function started sleep=1 ret=2\n"
				+ "Function started sleep=2 ret=3\n"
				+ "started 200000 200001\n"
				+ "Function completed sleep=1 ret=2\n"
				+ "Function completed sleep=2 ret=3\n"
				;
		String expectErr = "";
		
		String code = ""
				+ "my_function 1 2 &\n"
				+ "pid1=$!\n"
				+ "pid=$!\n"
				+ "my_function 2 3 &\n"
				+ "pid2=$!\n"
				+ "pid=\"$pid1 $pid2\"\n"
				+ "echo \"started $pid\"\n"
				+ "wait -p var $pid1 $pid2"
				;

		Console.setNextPid(200000);
		console.jobManager.clear();
		ExecuteResult res = executeCommand(code, "");
		
		
		
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(expectErr, err);
		assertEquals(expectOut, out);
		assertEquals(3, res.exitCode);
		String val = ""+console.getVariable("var");
		assertEquals("200001", val);
	}
	

	@Test
	public void testWait03() throws IOException, InterruptedException {
		String expectOut = "Function started sleep=1 ret=2\n"
				+ "Function started sleep=2 ret=3\n"
				+ "started 300000 300001\n"
				+ "Function completed sleep=1 ret=2\n"
				+ "Function completed sleep=2 ret=3\n"
				;
		String expectErr = "";
		
		String code = ""
				+ "my_function 1 2 &\n"
				+ "pid1=$!\n"
				+ "pid=$!\n"
				+ "my_function 2 3 &\n"
				+ "pid2=$!\n"
				+ "pid=\"$pid1 $pid2\"\n"
				+ "echo \"started $pid\"\n"
				+ "wait -f -p var $pid1 $pid2"
				;

		Console.setNextPid(300000);
		console.jobManager.clear();
		ExecuteResult res = executeCommand(code, "");
		
		
		
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(expectErr, err);
		assertEquals(expectOut, out);
		assertEquals(2, res.exitCode);
		String val = ""+console.getVariable("var");
		assertEquals("300000", val);
	}

}
