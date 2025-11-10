package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;

public class TestSleep extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		waitForJobs = true;
		console = new Console();
	}

	@AfterAll
	public static void afterAll() {

	}



	
	
	@Test
	public void testSleep00() throws IOException, InterruptedException {
		String vals [] = {
				"ival= 1 multiplyer=86400000 timeToSleep=86400000",
				"ival= 60 multiplyer=1000 timeToSleep=60000",
				"ival= 1 multiplyer=1000 timeToSleep=1000",
				"ival= 1 multiplyer=60000 timeToSleep=60000",
				"ival= 1 multiplyer=3600000 timeToSleep=3600000",
				"ival= 1 multiplyer=86400000 timeToSleep=86400000",
				"ival= 2 multiplyer=1 timeToSleep=2"
		};
		String cmds [] = {
				"sleep 1d -db",
				"sleep 60 -db",
				"sleep 1s -db",
				"sleep 1m -db",
				"sleep 1h -db",
				"sleep 1d -db",
				"sleep 2M -db",
		};
		
		for (int idx = 0; idx < cmds.length; idx++) {
			String expect = vals[idx]+"\n";
			String code = cmds[idx]+" &\nkill %1\n";	
			console.jobManager.clear();
			ExecuteResult res = executeCommand(code,"");
			assertEquals(0,res.exitCode);
			String out = new String(res.bao.toByteArray());
			String err = new String(res.bae.toByteArray());
			assertEquals("", err);
			assertEquals(expect, out);
			assertEquals(0, res.exitCode);

		}
		
		
	}

	@Test
	public void testSleep01() throws IOException, InterruptedException {
		//  should return immediately
		String code = "sleep 1 \n";
		
		String expect = "";
		long start = System.currentTimeMillis();
		ExecuteResult res = executeCommand(code,"");
		long end = System.currentTimeMillis();
		long time = (end-start);
		
		assertEquals(0,res.exitCode);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		assertTrue(time>= 1000 && time < 1200);
	}
	
	@Test
	public void testSleep02() throws IOException, InterruptedException {

		String code = "sleep 2 \n";
		
		String expect = "";
		long start = System.currentTimeMillis();
		ExecuteResult res = executeCommand(code,"");
		long end = System.currentTimeMillis();
		long time = (end-start);
				
		assertEquals(0,res.exitCode);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		assertTrue(time>= 2000 && time < 2200);
	}

	@Test
	public void testSleep03() throws IOException, InterruptedException {
		
		String code = "sleep 2s\n";
		
		String expect = "";
		long start = System.currentTimeMillis();
		ExecuteResult res = executeCommand(code,"");
		long end = System.currentTimeMillis();
		long time = (end-start);
				
		assertEquals(0,res.exitCode);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		assertTrue(time>= 2000 && time < 2200);
		
	}

}
