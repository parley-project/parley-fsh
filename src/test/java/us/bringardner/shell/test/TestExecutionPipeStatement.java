package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionPipeStatement {

	@BeforeAll
	public static void beforeAll() throws IOException {
		
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		File file = new File("WcTestFiles").getCanonicalFile();
		
		System.setProperty("user.home", file.getAbsolutePath());
	}
	
	public static class ExecuteResult {
		int exitCode=0;		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();			
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn) {
		Console console = new Console();
		ExecuteResult ret = new ExecuteResult();
		console.setStdOut(new PrintStream(ret.bao));
		console.setStdErr(new PrintStream(ret.bae));
		console.setStdIn(new ByteArrayInputStream(stdIn.getBytes()));
		
		Console.jobs.clear();
		Console.setNextPid(0);
		ret.exitCode=console.executeUsingAntlr(command);
		
		return ret;
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
				+"true || echo two"
				+"false || echo two"
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
				"[1] 0\n"
				+ "[2] 1"
				;
		
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
				+ "jobs"
				;

		String expect = 
				"[1] 0\n"
				+ "[2] 1\n"
				+ "[1] Running sleep   100    &\n"
				+ "[2] Running sleep   200    &"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		
		assertEquals("", err);
		assertEquals(expect, out.trim());
		assertEquals(0, res.exitCode);
		
		
	}

	

		
}
