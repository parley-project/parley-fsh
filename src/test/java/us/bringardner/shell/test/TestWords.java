package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Arguments are words: parts with no whitespace between them make one argument.
 */
public class TestWords extends AbstractConsoleTest {

	@TempDir
	static Path dir;

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		Files.createDirectories(dir.resolve("sub-dir"));
		Files.createDirectories(dir.resolve("-dash"));
		Files.writeString(dir.resolve("-dash").resolve("y.txt"), "");
		// only for testDoubleDashEndsOptions (another test writes into -dash)
		Files.createDirectories(dir.resolve("-opt"));
		Files.writeString(dir.resolve("-opt").resolve("z.txt"), "");
	}

	private static String path(String name) throws IOException {
		return new File(dir.toFile(), name).getCanonicalPath();
	}

	private static void expect(String code, String out) throws IOException {
		ExecuteResult res = executeCommand(code, "");
		assertEquals("", res.getStdErr(), code);
		assertEquals(out, res.getStdOut(), code);
		assertEquals(0, res.exitCode, code);
	}

	@Test
	public void testPartsJoin() throws IOException {
		expect("x=1; echo a$x", "a1\n");
		expect("x=1; echo $x/y", "1/y\n");
		expect("echo a\"b c\"d", "ab cd\n");
		expect("echo 'a b'c", "a bc\n");
		expect("echo a,b", "a,b\n");
		expect("echo x$(echo y)z", "xyz\n");
		expect("echo true false", "true false\n");
		// a keyword only stands alone
		expect("echo done-now if.txt for-each", "done-now if.txt for-each\n");
	}

	@Test
	public void testVariableAndPathIsOneArgument() throws IOException {
		expect("d="+path("")+"; cd $d/sub-dir; pwd", path("sub-dir")+"\n");
	}

	@Test
	public void testPathsWithDashes() throws IOException {
		expect("cd "+path("-dash")+"; pwd", path("-dash")+"\n");
		expect("ls "+path("-dash"), "y.txt\n");
		expect("echo hi > "+path("-dash")+"/out.txt; cat "+path("-dash")+"/out.txt", "hi\n");
	}

	@Test
	public void testOptionsStillWork() throws IOException {
		expect("echo -n hi", "hi");
		expect("echo $((5-2))", "3\n");
		expect("a=5; b=2; echo $((a-b))", "3\n");
	}

	@Test
	public void testAssignmentValueIsAWord() throws IOException {
		expect("x=sub-dir; echo $x", "sub-dir\n");
		expect("x=a; y=$x$x; echo $y", "aa\n");
		// one part keeps its type
		expect("i=4; echo $((i+1))", "5\n");
	}

	@Test
	public void testBraceExpansionInWord() throws IOException {
		expect("echo pre{1..3}post", "pre1post pre2post pre3post\n");
		expect("echo x{a,b}y", "xay xby\n");
		expect("echo pre{1..2}post next", "pre1post pre2post next\n");
		// the expansion is redone on each run of the statement
		expect("for i in 1 2; do echo {a,b}$i; done", "a1 b1\na2 b2\n");
	}

	@Test
	public void testAlias() throws IOException {
		expect("alias ll='ls -1'; alias ll", "alias ll='ls -1'\n");
		expect("alias foo=bar; unalias foo; alias foo", "alias: foo: not found\n");
	}

	@Test
	public void testGroupInPipeAndTime() throws IOException {
		expect("{ echo a; echo b; } | wc -l", "       2\n");
		ExecuteResult res = executeCommand("time echo t", "");
		assertEquals("t\n", res.getStdOut());
		assertEquals(0, res.exitCode);
	}

	@Test
	public void testUnsetVariableIsEmpty() throws IOException {
		expect("echo :$nope:", "::\n");
		expect("echo a$nope", "a\n");
		expect("echo \":$nope:${nope}:\"", ":::\n");
		expect("y=$nope; echo :$y:", "::\n");
		expect("f() { echo :$1:$2:; }; f one", ":one::\n");
		expect("if [ \"$nope\" == \"\" ]; then echo empty; fi", "empty\n");
	}

	@Test
	public void testUnsetVariableWithSetU() throws IOException {
		ExecuteResult res = executeCommand("set -u; echo :$nope:", "");
		assertEquals("", res.getStdOut());
		assertEquals("nope: unbound variable", res.getStdErr().trim());
		assertEquals(1, res.exitCode);
	}

	@Test
	public void testDoubleDashEndsOptions() throws IOException {
		expect("cd "+path("")+"; ls -- -opt", "z.txt\n");
	}

	@Test
	public void testKeywordsAndBracketsAreText() throws IOException {
		expect("echo done", "done\n");
		expect("echo if then else fi for in do", "if then else fi for in do\n");
		expect("echo git commit -m done", "git commit -m done\n");
		expect("w=x; echo [$w] a[1]b ]", "[x] a[1]b ]\n");
		// [ at the start of a statement is still a test
		expect("x=3; if [ $x == 3 ]; then echo yes; fi; [ 3 == 3 ] && echo eq", "yes\neq\n");
	}

	@Test
	public void testCharacterClassGlob() throws IOException {
		Files.writeString(dir.resolve("a1.txt"), "");
		Files.writeString(dir.resolve("b1.txt"), "");
		Files.writeString(dir.resolve("c1.txt"), "");
		expect("cd "+path("")+"; ls [ab]1.txt", "a1.txt\nb1.txt\n");
		expect("cd "+path("")+"; ls [!ab]1.txt", "c1.txt\n");
	}

	@Test
	public void testWordSplitting() throws IOException {
		// unquoted expansions are split on IFS; quoted ones and literal text are not
		expect("for w in $(echo one two three); do echo \"[$w]\"; done", "[one]\n[two]\n[three]\n");
		expect("x=\"1 2 3\"; for i in $x; do echo \"<$i>\"; done", "<1>\n<2>\n<3>\n");
		expect("f() { echo $#; }; x=\"a b c\"; f $x; f \"$x\"", "3\n1\n");
		// an empty unquoted expansion is no word at all; quoted, it is an empty word
		expect("f() { echo $#; }; f a $nope b; f a \"$nope\" b", "2\n3\n");
		// text next to the expansion joins the first and last fields
		expect("f() { echo $# $1 $4; }; x=\" b c \"; f a${x}d", "4 a d\n");
		expect("IFS=:; x=\"a:b::c\"; for p in $x; do echo \"<$p>\"; done", "<a>\n<b>\n<>\n<c>\n");
		// a field with an unquoted wildcard is a glob; a quoted one is not
		expect("cd "+path("")+"; touch g1.log g2.log; x=\"*.log\"; for f in $x; do echo $f; done", "g1.log\ng2.log\n");
		expect("x=\"*.log\"; for f in \"$x\"; do echo $f; done", "*.log\n");
		// export (and local, declare ...) name=value words are not split
		expect("y=\"a b\"; export Z=$y; echo \"[$Z]\"", "[a b]\n");
		// echo joins its words with one space; -n only at the start
		expect("echo   spaced    out; echo a -n b", "spaced out\na -n b\n");
	}

	@Test
	public void testQuotedAt() throws IOException {
		// "$@" is one word per positional parameter, each kept whole
		expect("f() { for a in \"$@\"; do echo \"[$a]\"; done; }; f \"a b\" c", "[a b]\n[c]\n");
		expect("g() { echo $#; }; f() { g \"$@\"; }; f \"a b\" \"\" c; f", "3\n0\n");
		expect("g() { echo $#; }; f() { g \"${@}\" \"$*\"; }; f a b c", "4\n");
		// text before and after joins the first and last parameter
		expect("g() { echo \"$#:$1|$2|$3\"; }; f() { g \"x$@y\"; }; f 1 2 3; f", "3:x1|2|3y\n1:xy||\n");
		expect("f() { echo \"\\$@ $1\"; }; f a b", "$@ a\n");
	}

	@Test
	public void testSetDoubleDash() throws IOException {
		expect("set -- x y z; echo $# $1", "3 x\n");
		expect("set -- -a b; echo $# $1", "2 -a\n");
		expect("set -- a; set --; echo $#", "0\n");
		expect("set -- \"p q\" r; for a in \"$@\"; do echo \"<$a>\"; done", "<p q>\n<r>\n");
	}

	@Test
	public void testNestedCommandSubstitution() throws IOException {
		expect("echo $(echo $(echo deep)); x=$(echo $(echo $(echo $(echo four)))); echo $x", "deep\nfour\n");
		expect("echo \"<$(echo \"$(echo \"$(echo in)\")\")>\" \"<$(echo $(echo in))>\"", "<in> <in>\n");
		// a ) in quotes does not end it
		expect("echo \"[$(echo \"a)b\")]\" \"[$(echo 'a)b')]\"", "[a)b] [a)b]\n");
		expect("echo $(echo $((2+3))) \"x$(echo $((1+1)))y\" \"$(( (1+2)*3 ))\"", "5 x2y 9\n");
		expect("echo \"a) b ( c 'q'\"", "a) b ( c 'q'\n");
	}

	@Test
	public void testFunctionStateInSubshell() throws IOException {
		// $( ) inside a function sees its parameters and local variables, and its changes stay inside
		expect("f() { echo \"$(echo \"$@\")\" $(echo $1); }; f a b", "a b a\n");
		expect("f() { local v=lv; echo $(echo $v); }; f", "lv\n");
		expect("for i in a b; do echo $(echo $i); done", "a\nb\n");
		expect("f() { echo $(set -- z; echo $1) $1; }; f a", "z a\n");
		expect("f() { local v=1; echo $(v=2; echo $v) $v; }; f", "2 1\n");
	}

	@Test
	public void testFunctionParametersAndLocals() throws IOException {
		expect("f() { set -- z; echo $1; }; f a", "z\n");
		expect("f() { shift; echo $1; }; f a b", "b\n");
		// shifting more than $# changes nothing and fails
		expect("f() { shift 3; echo $? $1; }; f a b", "1 a\n");
		expect("f() { local v=1; v=2; echo $v; }; f; echo :$v:", "2\n::\n");
	}
}
