package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;

public class TestEcho extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		console = new Console();
	}

	@AfterAll
	public static void afterAll() {

	}


	@Test
	public void testEcho01() throws IOException {
		String [] code = 
			{
				 "list of extra cool words   with space\t and tab"
					,"help"
					, "list of words"
					, "list of extra cool words"
			}
		;

		for (int idx = 0; idx < code.length; idx++) {
			String expect = code[idx];
			String cmd = "echo "+expect;

			ExecuteResult res = executeCommand(cmd, "");
			assertEquals(0,res.exitCode,"Exit code for cmd="+cmd);
			assertEquals(expect+"\n",new String(res.bao.toByteArray()),"Stdout for cmd="+cmd);

			cmd = "echo -n "+expect;

			res = executeCommand(cmd, "");
			assertEquals(0,res.exitCode,"Exit code for cmd="+cmd);
			String out = new String(res.bao.toByteArray());
			assertEquals(expect,out,"Stdout for cmd="+cmd);
		}


	}
}
