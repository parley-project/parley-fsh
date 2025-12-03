package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;

public class TestExec extends AbstractConsoleTest {


	public static FileSource logDir;
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("LsTestFiles");
		File file = new File("target/logdir").getCanonicalFile();
		if( !file.exists()) {
			assertTrue(file.mkdirs());
		}
		
		logDir = console.createFileSource(file.getAbsolutePath());
		
	}



	@Test
	public void testExec01() throws IOException {
		String expect = 
				"-rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js\n"
						+ "-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php\n"
						+ "-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt\n"
						+ "-rwxr-xr-x 1 tony  staff    20  Jun 20 2025 AbcFileD.properties\n"
						+ "drwxr-xr-x 1 tony  staff   238  Nov 05 2022 Folder01\n";

		String cmd = "exec ls -l";

		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0,res.exitCode,"Exit code for cmd="+cmd);
		String out = new String(res.bao.toByteArray());
		assertEquals(expect,out,"Stdout for cmd="+cmd);

	}

	@Test
	public void testExec02() throws IOException {
		String expect = "This will be logged.\n";

		FileSource logFile = logDir.getChild("exec02.log");
		if( logFile.exists()) {
			assertTrue(logFile.delete());
		}
		
		
		String cmd = "exec > "+logFile.getAbsolutePath()+"\n"
				+ "echo \""+expect.trim()+"\"";

		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0,res.exitCode,"Exit code for cmd="+cmd);
		String out = new String(res.bao.toByteArray());
		assertEquals("",out,"Stdout for cmd="+cmd);

		try (InputStream in = logFile.getInputStream()){
			String data = new String(in.readAllBytes());
			assertEquals(expect,data,"Logfile data");
		}
		assertTrue(logFile.delete());
	}
}
