package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;

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
				"list of  extra   cool",
				 "list of extra cool words   with space\t and tab"
					,"help"
					, "list of words"
					, "list of extra cool words"
			}
		;

		for (int idx = 0; idx < code.length; idx++) {
			// echo prints its words separated by one space, as in bash (it used to copy the spacing)
			String expect = String.join(" ", code[idx].trim().split("\\s+"));
			String cmd = "echo "+code[idx];

			ExecuteResult res = executeCommand(cmd, "");
			assertEquals(0,res.exitCode,"Exit code for cmd="+cmd);
			assertEquals(expect,res.getStdOut().trim(),"Stdout for cmd="+cmd);

			cmd = "echo -n "+code[idx];

			res = executeCommand(cmd, "");
			assertEquals(0,res.exitCode,"Exit code for cmd="+cmd);
			String out = res.getStdOut();
			assertEquals(expect,out,"Stdout for cmd="+cmd);
		}


	}
}
