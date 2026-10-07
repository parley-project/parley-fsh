package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		// the output is run as a command: test with no arguments is false
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
		assertEquals(1, res.exitCode);
		
		
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

	@Test
	public void testExpansionsInDoubleQuotes() throws IOException {
		// spaces are kept (runs of spaces were dropped), `cmd` runs, and $( ) may contain quotes
		String[][] cases = {
				// (unquoted, echo a   b prints a b; quoted spaces must survive)
				{"echo \"$(echo 'a   b')\"", "a   b\n"},
				{"echo \"[`echo 'a   b'`]\"", "[a   b]\n"},
				{"echo \"${nope:-hello   world}\"", "hello   world\n"},
				{"echo \"$(echo \"inner\")\"", "inner\n"},
				{"echo \"$(echo 'a  b')\"", "a  b\n"},
				{"echo \"a)b (x)\"", "a)b (x)\n"},
		};
		for(String[] c : cases) {
			ExecuteResult res = executeCommand(c[0], "");
			assertEquals(c[1], res.getStdOut(), c[0]);
			assertEquals(0, res.exitCode, c[0]);
		}
	}

	@Test
	public void testDollarAndEscapesInDoubleQuotes() throws IOException {
		String[][] cases = {
				// a $ with no name is just a $
				{"echo \"price: $5 and $\"", "price:  and $\n"},
				{"echo \"a $ b\"", "a $ b\n"},
				// \" \$ \\ \` give the character; other escapes stay; backslash-newline is removed
				{"echo \"a\\\"b\"", "a\"b\n"},
				{"echo \"\\$HOME\"", "$HOME\n"},
				{"echo \"a\\\\b\"", "a\\b\n"},
				{"echo \"\\`echo no\\`\"", "`echo no`\n"},
				{"echo \"a\\nb\"", "a\\nb\n"},
				{"echo \"a\\\nb\"", "ab\n"},
				// in a here-document \" keeps its backslash
				{"x=5; cat <<EOF\na\\\"b \\$x $x\nEOF", "a\\\"b $x 5\n"},
		};
		for(String[] c : cases) {
			ExecuteResult res = executeCommand(c[0], "");
			assertEquals("", res.getStdErr(), c[0]);
			assertEquals(c[1], res.getStdOut(), c[0]);
			assertEquals(0, res.exitCode, c[0]);
		}
	}

	@Test
	public void testUnclosedExpansionInDoubleQuotes() throws IOException {
		for(String code : new String[] {"echo \"cost $( x\"", "echo \"`echo hi\"", "echo \"${x\"", "echo \"$((1+2\""}) {
			ExecuteResult res = executeCommand(code, "");
			assertEquals("", res.getStdOut(), code);
			assertEquals(1, res.exitCode, code);
			assertTrue(res.getStdErr().contains("syntax error: no matching"), code+" -> "+res.getStdErr());
		}
	}
}
