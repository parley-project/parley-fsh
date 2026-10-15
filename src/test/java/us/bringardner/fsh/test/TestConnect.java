package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;


public class TestConnect extends AbstractConsoleTest {

	
	@BeforeAll
	public static void beforeAll() throws IOException {
		setup("ConnectTestFiles");
	}
	
	
	
	@Test
	@Order(1)
	public void testConnectMemoryNoArgs() throws IOException {
		String expect = "memory connected as /mem";
		
		String cmd = "connect memory /mem";
		
		ExecuteResult res = executeCommand(cmd, "");
		String out = res.getStdOut().trim();
		String err = res.getStdErr().trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");		
		
		cmd = "ls -l /mem";
		res = executeCommand(cmd, "");
		out = res.getStdOut().trim();
		err = res.getStdErr().trim();
		assertEquals(0, res.exitCode,"Exit code");
		// not recursive and no kids so no output
		assertEquals("", out,"stdout");
		assertEquals("", err,"stderr");		
		
		
	}
	
	@Test
	@Order(2)	
	public void testConnectMemory_f() throws IOException {
		String expect = ""
				+ "memory connected as /mem02";
		
		String cmd = "connect memory -f~/Memory02.properties /mem02";
		
		ExecuteResult res = executeCommand(cmd, "");
		String out = res.getStdOut().trim();
		String err = res.getStdErr().trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");				
	}
	
	@Test
	@Order(3)	
	public void testConnectSftp() throws IOException {
		if( getOs()!=OperatingSystem.Mac) {
			// no native server 
			return;
		}
		String expect = ""
				+ "sftp connected as /sftp1";
		
		String cmd = "connect sftp user=unittest1 password=0000 host=localhost /sftp1";
		
		ExecuteResult res = executeCommand(cmd, "");
		String out = res.getStdOut().trim();
		String err = res.getStdErr().trim();
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");				
	}
	
	@Test
	public void testConnectMemoryStdinWhiteSpace() throws IOException {
		// settings from standard input: separated by any white space (two spaces, a tab), and
		// blanks around the line are not settings
		ExecuteResult res = executeCommand("connect memory - /mem03", "  name=memset03  \tother=x  ");
		assertEquals("", res.getStdErr().trim(),"stderr");
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals("memory connected as /mem03", res.getStdOut().trim(),"stdout");
	}

	@Test
	public void testConnectValueContainsEquals() throws IOException {
		// generated passwords (base64) often end in '='; the value is everything after the first '='
		ExecuteResult res = executeCommand("connect memory - /mem04", "name=memset04 password=abc==");
		assertEquals("", res.getStdErr().trim(),"stderr");
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals("memory connected as /mem04", res.getStdOut().trim(),"stdout");
	}

	@Test
	public void testConnectBadArgDoesNotEchoValue() throws IOException {
		// a malformed setting is reported by name only: the rest may be a password
		ExecuteResult res = executeCommand("connect memory - /mem05", "=s3cret");
		assertNotEquals(0, res.exitCode,"Exit code");
		assertFalse((res.getStdOut()+res.getStdErr()).contains("s3cret"), "secret echoed");
	}

	@Test()
	public void testConnectSftpStdin() throws IOException {
		if( getOs()!=OperatingSystem.Mac) {
			// no native server 
			return;
		}
		String expect = ""
				+ "sftp connected as /sftp2";
		
		String cmd = "connect sftp - /sftp2";
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ExecuteResult res = executeCommand(cmd, "user=unittest2 password=0000 host=localhost");
		String out = res.getStdOut().trim();
		String err = res.getStdErr().trim();
		if( res.exitCode !=0) {
			System.out.println(err);
		}
		// for some reason this fails when executes right after testConnectSftp
		assertEquals(0, res.exitCode,"Exit code");
		assertEquals(expect, out,"stdout");
		assertEquals("", err,"stderr");				
	}
	

	@Test
	public void disconnectRemovesTheMount() throws IOException {
		ExecuteResult res = executeCommand("connect memory /memdis", "");
		assertEquals(0, res.exitCode, res.getStdErr());

		res = executeCommand("disconnect /memdis/", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertEquals("", res.getStdErr().trim());

		res = executeCommand("ls -d /memdis", "");
		assertNotEquals(0, res.exitCode, "gone after disconnect: "+res.getStdOut());

		res = executeCommand("disconnect /memdis", "");
		assertEquals(1, res.exitCode);
		assertEquals("disconnect: nothing is connected at /memdis", res.getStdErr().trim());

		res = executeCommand("disconnect /", "");
		assertEquals(1, res.exitCode);

		res = executeCommand("disconnect", "");
		assertEquals(2, res.exitCode);
	}

	@Test
	public void mistakesHaveAStatus() throws IOException {
		ExecuteResult res = executeCommand("connect", "");
		assertEquals(2, res.exitCode);
		assertEquals("usage: connect FACTORY [-f FILE] [name=value ...] [-] MOUNT_POINT", res.getStdErr().trim());

		res = executeCommand("connect nosuch /x", "");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().startsWith("connect: unknown file system 'nosuch'; the known ones are "), res.getStdErr());

		res = executeCommand("connect memory relative", "");
		assertEquals(2, res.exitCode);

		res = executeCommand("connect memory -f", "");
		assertEquals(2, res.exitCode);
		assertEquals("connect: -f needs a properties file", res.getStdErr().trim());

		res = executeCommand("connect memory /memtwice", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		res = executeCommand("connect memory /memtwice", "");
		assertEquals(1, res.exitCode, "a mount point already in use");
		assertTrue(res.getStdErr().contains("can't mount memory at /memtwice"), res.getStdErr());
		executeCommand("disconnect /memtwice", "");
	}
}
