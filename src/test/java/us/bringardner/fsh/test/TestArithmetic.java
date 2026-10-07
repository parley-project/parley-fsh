package us.bringardner.fsh.test;

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

	@Test
	public void testBashOperators() throws IOException {
		expect("echo $((3>2)) $((2==2)) $((1&&0)) $((1||0)) $((!0)) $((5>3 ? 10 : 20))", "1 1 0 1 1 10\n");
		expect("echo $((2**10)) $((2**3**2)) $((-2**2)) $((6&3)) $((6|3)) $((6^3)) $((~0)) $((1<<4))", "1024 512 4 2 7 5 -1 16\n");
		expect("echo $((16#ff)) $((2#1010)) $((0x1F)) $((010)) $((64#_))", "255 10 31 8 63\n");
		expect("echo $((a=1, b=2, a+b)) $a $b", "3 1 2\n");
		// the side not taken is not evaluated
		expect("x=0; echo $((1 || (x=5))) $((0 && (x=6))) $((1 ? 2 : (x=7))) $x", "1 0 2 0\n");
		// a[i++] runs i++ once
		expect("i=0; a=(0 0 0); (( a[i++] = 9 )); echo $i ${a[0]}", "1 9\n");
		// a variable holding an expression is evaluated
		expect("e='2+3'; echo $((e*2))", "10\n");
	}

	@Test
	public void testArithmeticStatusAndErrors() throws IOException {
		expect("(( 0 )); echo $?; (( 5 )); echo $?; (( x = 0 )); echo $?", "1\n0\n1\n");
		ExecuteResult res = executeCommand("(( 1/0 )); echo after $?", "");
		assertEquals("after 1\n", res.getStdOut());
		assertTrue(res.getStdErr().contains("division by 0"), res.getStdErr());
		res = executeCommand("let 'x = 1 +'; echo $?", "");
		assertEquals("1\n", res.getStdOut());
		assertTrue(res.getStdErr().startsWith("let:"), res.getStdErr());
		// in $(( )) an error ends the script, as in bash
		res = executeCommand("echo $((1/0)); echo after", "");
		assertEquals("", res.getStdOut());
		assertEquals(1, res.exitCode);
	}

	@Test
	public void testArithmeticInOtherPlaces() throws IOException {
		expect("for (( i=0, j=3; i<j; i++, j-- )); do echo $i$j; done", "03\n12\n");
		expect("for (( i=0; ; i++ )); do (( i == 2 )) && break; echo $i; done", "0\n1\n");
		expect("for (( i=0; i<4; i++ )); do (( i == 1 )) && continue; echo $i; done", "0\n2\n3\n");
		expect("s=hello; n=1; echo ${s:n:2} ${s:n+1} ${s:(-2)}", "el llo lo\n");
		expect("x=$((5)); [ $x -gt 4 ] && echo gt; a=(p q r); i=$((1)); echo ${a[i+1]}", "gt\nr\n");
		expect("echo \"sum=$((2+3)) $(( (1+2)*3 ))\"", "sum=5 9\n");
		// (( )) as a condition
		expect("i=0; while (( i < 3 )); do echo $i; (( i++ )); done", "0\n1\n2\n");
		expect("x=5; if (( x > 3 && x < 10 )); then echo in; else echo out; fi", "in\n");
	}
}
