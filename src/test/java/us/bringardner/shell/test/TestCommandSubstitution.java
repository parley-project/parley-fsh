package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestCommandSubstitution extends AbstractConsoleTest{


	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	@Test
	public void testCommandSubstitue01() throws Exception{
		String cmd = "$(echo -n test)"
				;

		String expect = 
				""
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	public void testCommandSubstitue02() throws Exception{
		String cmd = "echo $(echo -n test)"
				;

		String expect = 
				"test\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}
		
	@Test
	public void testCommandSubstitue03() throws Exception{
		String cmd = "var=$(echo -n test)\n"
				+ "echo \"var=$var\"";
				;

		String expect = 
				"var=test\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
	@Test
	public void testCommandSubstitue04() throws Exception{
		String cmd = "echo $(echo -n test)\n"
				;

		String expect = 
				"test\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testCommandTextIsKept() throws IOException {
		// the command inside $( ) is run as written (its tokens were joined with spaces)
		ExecuteResult res = executeCommand("x=$(echo a-b.txt); echo \"[$x]\"", "");
		assertEquals("[a-b.txt]\n", res.getStdOut());
		res = executeCommand("x=$(echo \"a  b\"); echo \"[$x]\"", "");
		assertEquals("[a  b]\n", res.getStdOut());
	}
}
