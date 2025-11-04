package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;


@TestMethodOrder(OrderAnnotation.class)
public class TestPipeStatement extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		AbstractConsoleTest.setup("WcTestFiles");		
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testPipeStatent01() throws Exception{
		String cmd = "ls -l | wc"
				;

		String expect = 
				"4      36     230"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out.trim());
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testPipeStatent02() throws Exception{
		String cmd = "time  ls -l | wc"
				;

		String expect = 
				"4      36     230"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertTrue(err.startsWith("real"));
		assertEquals(expect, out.trim());
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testPipeStatent03() throws Exception{
		String cmd = "time -p ! ls -l | wc"
				;

		String expect = 
				"4      36     230"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		
		assertTrue(err.startsWith("posix real"));
		assertEquals(expect, out.trim());
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		
		assertEquals("", err);
		assertEquals(expect, out.trim());
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
		String cmd = "sleep 1000 &\n"
				+ "sleep 2000 &\n"
				+ "sleep 3000 &\n"				
				+ "jobs\n"
				;
		
		
		String expect = 
				"[1] 100000\n"
				+ "[2] 100001\n"
				+ "[3] 100002\n"
				+ "[1]   Running sleep   1000   \n"
				+ "[2] - Running sleep   2000   \n"
				+ "[3] + Running sleep   3000   \n"
				;
		if( !interactive) {
			 expect = 
						""
						+ "[1]   Running sleep   1000   \n"
						+ "[2] - Running sleep   2000   \n"
						+ "[3] + Running sleep   3000   \n"
						;
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
		int sz = console.jobManager.getJobs().size();
		System.out.println("job sz="+sz);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %3\n";
		expect = "[3] + Running sleep   3000   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %1\n";
		expect = "[1]   Running sleep   1000   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %-\n";
		expect = "[2] - Running sleep   2000   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %+\n";
		expect = "[3] + Running sleep   3000   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %%\n";
		expect = "[3] + Running sleep   3000   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
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
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);
		
		console.isInteractive=lastInteractive;
	}


		
}
