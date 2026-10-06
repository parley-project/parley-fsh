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
}
