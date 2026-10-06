package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * $(( )), let, expr and array indexes.
 */
public class TestArithmetic extends AbstractConsoleTest {

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
	}

	private static void expect(String code, String out) throws IOException {
		expect(code, out, 0);
	}

	private static void expect(String code, String out, int exitCode) throws IOException {
		ExecuteResult res = executeCommand(code, "");
		assertEquals("", res.getStdErr(), code);
		assertEquals(out, res.getStdOut(), code);
		assertEquals(exitCode, res.exitCode, code);
	}

	@Test
	public void testArithmetic() throws IOException {
		expect("echo $(( 2 * 3 )) $((2*3)) $(( (1+2) * 3 ))", "6 6 9\n");
		expect("echo $((10/3)) $(( 10 / 3 )) $(( 7 % 3 )) $(( -5 + 2 ))", "3 3 1 -3\n");
		expect("x=7; echo $(( x - 2 ))", "5\n");
		expect("x=5; (( x++ )); echo $x", "6\n");
	}

	@Test
	public void testUnsetVariableIsZero() throws IOException {
		expect("echo $((nope+1)) $((nope*2+1))", "1 1\n");
		expect("x=; echo $((x+1))", "1\n");
	}

	@Test
	public void testLet() throws IOException {
		expect("let x=4; echo $x", "4\n");
		expect("let x=2+3; echo $x", "5\n");
		expect("let \"x = 2 * 3\"; echo $x", "6\n");
		expect("x=5; let x=x+1; echo $x", "6\n");
		expect("x=5; let x++; echo $x", "6\n");
		expect("let a=2 b=a*3; echo $a $b", "2 6\n");
		expect("let x=10/4; echo $x", "2\n");
		// the status is 1 when the last value is 0
		expect("let 0; echo $?", "1\n");
		expect("let 1; echo $?", "0\n");
	}

	@Test
	public void testExpr() throws IOException {
		expect("expr 1 + 2", "3\n");
		expect("x=4; expr $x \\* 3", "12\n");
		expect("expr 10 / 3; expr 10 % 3; expr 7 - 2", "3\n1\n5\n");
		expect("expr 3 \\> 2; expr 2 = 2; expr abc '<' abd", "1\n1\n1\n");
		expect("expr length hello; expr substr hello 2 3; expr index hello l", "5\nell\n3\n");
		expect("expr hello : 'h\\(..\\)'; expr hello : 'hel'", "el\n3\n");
		expect("expr \\( 1 + 2 \\) \\* 3", "9\n");
		expect("x=$(expr 2 + 2); echo $x", "4\n");
		// the word expr is ordinary text
		expect("echo expr", "expr\n");
		// a 0 result has status 1
		expect("expr 2 - 2", "0\n", 1);
	}

	@Test
	public void testExprErrors() throws IOException {
		ExecuteResult res = executeCommand("expr 1 / 0", "");
		assertEquals(2, res.exitCode);
		assertTrue(res.getStdErr().contains("division by zero"), res.getStdErr());
		res = executeCommand("expr 1 +", "");
		assertEquals(2, res.exitCode);
	}

	@Test
	public void testArrayIndex() throws IOException {
		expect("arr=(a b c); echo :${arr[1]}:${arr[5]}:${arr[-1]}:", ":b::c:\n");
		expect("arr=(a b c); i=1; echo :${arr[i]}:", ":b:\n");
		expect("arr=(a b c); echo ${#arr[5]}", "0\n");
	}
}
