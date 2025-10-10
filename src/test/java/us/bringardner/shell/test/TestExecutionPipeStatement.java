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
public class TestExecutionPipeStatement extends AbstractConsoleTest{

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
				+ "sleep 20 &"
				;

		String expect = 
				"[1] 100000\n"
				+ "[2] 100001"
				;
		
		Console.setNextPid(100000);
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		
		assertEquals("", err);
		assertEquals(expect, out.trim());
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	public void testBackground03() throws Exception{
		String cmd = "sleep 100 &\n"
				+ "sleep 200 &\n"
				+ "sleep 300 &\n"				
				+ "jobs\n"
				;

		String expect = 
				"[1] 100000\n"
				+ "[2] 100001\n"
				+ "[3] 100002\n"
				+ "[1]   Running sleep   100   \n"
				+ "[2] - Running sleep   200   \n"
				+ "[3] + Running sleep   300   \n"
				;
		//System.out.println(cmd);
		Console.setNextPid(100000);
		console.jobs.clear();
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %3\n";
		expect = "[3] + Running sleep   300   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %1\n";
		expect = "[1]   Running sleep   100   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %-\n";
		expect = "[2] - Running sleep   200   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %+\n";
		expect = "[3] + Running sleep   300   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);

		cmd = "jobs %%\n";
		expect = "[3] + Running sleep   300   \n";
		res = executeCommand(cmd,"");
		out = new String(res.bao.toByteArray());
		err = new String(res.bae.toByteArray());		
		assertEquals("", err);
		assertEquals(expect.trim(), out.trim());
		assertEquals(0, res.exitCode);
		
		
		
	}

	

		
}
