package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;


public class TestConnect extends AbstractConsoleTest {

	
	@BeforeAll
	public static void beforeAll() throws IOException {
		setup("ConnectTestFiles");
	}
	
	/**
	 * TODO: MUST redo the connect cmd use of native keyboard
	 * @param command
	 * @param str
	 * @return
	 * @throws IOException
	 */
	public static ExecuteResult executeCommandFudge(String command,String str) throws IOException {
		ByteArrayInputStream in = (new ByteArrayInputStream(str.getBytes()));
		System.setIn(in);
		return executeCommand(command, str);
	}
	
	
	@Test
	@Order(1)
	public void testConnectMemoryNoArgs() throws IOException {
		String expect = "memory connected as /mem";
		
		String cmd = "connect memory /mem";
		
		ExecuteResult res = executeCommandFudge(cmd, "");
		String out = new String(res.bao.toByteArray()).trim();
		String err = new String(res.bae.toByteArray()).trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");		
		
		cmd = "ls -l /mem";
		res = executeCommandFudge(cmd, "");
		out = new String(res.bao.toByteArray()).trim();
		err = new String(res.bae.toByteArray()).trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals("", out,"stdout");
		assertEquals("", err,"stderr");		
		
		/*
		cmd = "unmount /mem";
		res = executeCommandFudge(cmd, "");
		out = new String(res.bao.toByteArray()).trim();
		err = new String(res.bae.toByteArray()).trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals("", out,"stdout");
		assertEquals("", err,"stderr");		
		*/
	}
	
	@Test
	@Order(2)	
	public void testConnectMemory_f() throws IOException {
		String expect = ""
				+ "memory connected as /mem02";
		
		String cmd = "connect memory -f~/Memory02.properties /mem02";
		
		ExecuteResult res = executeCommandFudge(cmd, "");
		String out = new String(res.bao.toByteArray()).trim();
		String err = new String(res.bae.toByteArray()).trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");				
	}
	
	@Test
	@Order(3)	
	public void testConnectSftp() throws IOException {
		String expect = ""
				+ "sftp connected as /sftp1";
		
		String cmd = "connect sftp user=unittest1 password=0000 host=localhost /sftp1";
		
		ExecuteResult res = executeCommandFudge(cmd, "");
		String out = new String(res.bao.toByteArray()).trim();
		String err = new String(res.bae.toByteArray()).trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");				
	}
	
	public void testConnectSftpStdin() throws IOException {
		String expect = ""
				+ "sftp connected as /sftp2";
		
		String cmd = "connect sftp /sftp2";
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ExecuteResult res = executeCommandFudge(cmd, "user=unittest2 password=0000 host=localhost");
		String out = new String(res.bao.toByteArray()).trim();
		String err = new String(res.bae.toByteArray()).trim();
		if( res.exitCode !=0) {
			System.out.println(err);
		}
		// for some reason this fails when executes right after testConnectSftp
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");				
	}
	
}
