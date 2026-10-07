package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		// (IFS is local: the tests share one shell)
		expect("f() { local IFS=:; x=\"a:b::c\"; for p in $x; do echo \"<$p>\"; done; }; f", "<a>\n<b>\n<>\n<c>\n");
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
		expect("f() { local lv21=1; lv21=2; echo $lv21; }; f; echo :$lv21:", "2\n::\n");
	}

	@Test
	public void testCompoundCommandInPipe() throws IOException {
		expect("echo a b | while read x; do echo \"<$x>\"; done", "<a b>\n");
		expect("printf 'b\\na\\n' | sort | while read l; do echo \"<$l>\"; done | cat", "<a>\n<b>\n");
		expect("printf 'x\\ny\\n' | for i in 1 2; do read v; echo $i$v; done", "1x\n2y\n");
		expect("echo z | if read v; then echo got $v; fi", "got z\n");
		expect("echo k | case k in k) cat;; esac", "k\n");
	}

	@Test
	public void testCommandNameFromExpansion() throws IOException {
		expect("c=echo; $c hi; c=\"echo hi\"; $c there", "hi\nhi there\n");
		expect("$(echo echo stmt); $(echo echo) a b; `echo echo` bt", "stmt\na b\nbt\n");
		expect("\"echo\" q; \"$(echo echo)\" quoted; \\echo esc", "q\nquoted\nesc\n");
		expect("f() { echo \"f:$*\"; }; g=f; $g 1 2", "f:1 2\n");
		// an empty name: the next word is the command, or nothing runs
		expect("$nope echo shifted; $nope", "shifted\n");
		expect("for c in echo printf; do $c x; done", "x\nx");
	}

	@Test
	public void testCommandSubstitutionStatus() throws IOException {
		// every command runs; the value is the output and the status is the last command's
		expect("x=$(ls nope 2>/dev/null; echo done); echo \"[$x]\"", "[done]\n");
		expect("x=$(false); echo \"[$x] $?\"; x=\"$(false)\"; echo $?", "[] 1\n1\n");
		expect("x=\"$(echo a; exit 4; echo b)\"; echo \"[$x] $?\"", "[a] 4\n");
		expect("alias t='false; echo after'; t", "after\n");
		// standard error is not captured
		ExecuteResult res = executeCommand("x=$(echo out; ls /no-such-dir; exit 3); echo \"[$x] $?\"", "");
		assertEquals("[out] 3\n", res.getStdOut());
		assertTrue(res.getStdErr().contains("no-such-dir"), res.getStdErr());
	}

	@Test
	public void testEchoEscapes() throws IOException {
		expect("echo -e \"a\\tb\\nc\"", "a\tb\nc\n");
		expect("echo -E \"a\\tb\"; echo \"a\\tb\"", "a\\tb\na\\tb\n");
		expect("echo -ne \"x\\ty\"", "x\ty");
		expect("echo -en \"1\\c2\"; echo", "1\n");
		expect("echo -e \"\\x41\\x4a\\0101\\u00e9|\\q|\\\\|end\"", "AJA\u00e9|\\q|\\|end\n");
		// options only before the first word, and -E after -e turns it off
		expect("echo -e a -n b; echo -x a; echo -e -E \"a\\nb\"", "a -n b\n-x a\na\\nb\n");
		expect("echo \"h\u00e9llo\"", "h\u00e9llo\n");
	}

	@Test
	public void testTestOperatorsAreOnlyInTests() throws IOException {
		// -eq -ne -lt ... were rewritten everywhere (echo -ne printed !=, ls -lt read a file)
		expect("echo -eq -ne -lt -le -gt -ge", "-eq -ne -lt -le -gt -ge\n");
		expect("x=2; if [ $x -lt 3 ] && [ $x -le 2 ] && [ $x -gt 1 ] && [ $x -ge 2 ] && [ $x -eq 2 ] && [ $x -ne 5 ]; then echo all; fi", "all\n");
		expect("i=0; while [ $i -lt 3 ]; do i=$((i+1)); done; echo $i", "3\n");
	}

	@Test
	public void testUnset() throws IOException {
		expect("x=1; unset x; echo \":$x:\"; y=2; unset -v y; echo \":$y:\"", "::\n::\n");
		expect("a=1; b=2; unset a b; echo \":$a$b:\"; export E1=v; unset E1; echo \":$E1:\"", "::\n::\n");
		expect("unset nothing; echo $?", "0\n");
		// a local variable stays unset until the function returns; the global is not seen
		expect("h() { local z=in; unset z; echo \":$z:\"; }; z=out; h; echo \":$z:\"", "::\n:out:\n");
		ExecuteResult res = executeCommand("f() { echo f; }; unset -f f; f", "");
		assertEquals("", res.getStdOut());
		assertEquals("f: command not found", res.getStdErr().trim());
		assertEquals(127, res.exitCode);
		res = executeCommand("g() { echo g; }; unset g; g", "");
		assertEquals(127, res.exitCode);
		res = executeCommand("unset -q a", "");
		assertEquals(2, res.exitCode);
		assertTrue(res.getStdErr().contains("-q: invalid option"), res.getStdErr());
	}

	@Test
	public void testRedirectFileDescriptors() throws IOException {
		String f = path("fd.txt");
		// stderr inside $( ) is not captured, and >&2 goes to the current stderr
		ExecuteResult res = executeCommand("x=$(echo out; echo err >&2); echo \"[$x]\"", "");
		assertEquals("[out]\n", res.getStdOut());
		assertEquals("err", res.getStdErr().trim());
		res = executeCommand("echo e2 1>&2", "");
		assertEquals("", res.getStdOut());
		assertEquals("e2", res.getStdErr().trim());
		// 2>&1 and the order of redirects
		expect("ls /no-such-26 2>&1 | cut -c1-3", "ls:\n");
		expect("ls /no-such-26 > /dev/null 2>&1; echo $?", "1\n");
		expect("ls /no-such-26 2>&1 > /dev/null | cut -c1-3", "ls:\n");
		expect("f() { echo out; echo err >&2; }; f > "+f+" 2>&1; cat "+f, "out\nerr\n");
		expect("f() { echo out; echo err >&2; }; f &> "+f+"; cat "+f, "out\nerr\n");
		// digits with a space are a word
		expect("echo 2 > "+f+"; cat "+f+"; echo x 2>/dev/null", "2\nx\n");
		// before and after the command
		expect("echo in > "+f+"; < "+f+" cat > "+f+"2; cat "+f+"2", "in\n");
		// exec and file descriptors above 2
		expect("exec 3>"+f+"; echo three >&3; exec 3>&-; cat "+f, "three\n");
		expect("exec 4>&1; echo four >&4; exec 4>&-; echo still", "four\nstill\n");
		expect("echo line > "+f+"; exec 5<"+f+"; read v <&5; echo \"$v\"; exec 5<&-", "line\n");
		res = executeCommand("echo bad >&7", "");
		assertEquals("7: Bad file descriptor", res.getStdErr().trim());
	}

	@Test
	public void testGroupsAndSubshells() throws IOException {
		expect("{ echo a; echo b >&2; } 2>/dev/null", "a\n");
		expect("x=$( { echo in; echo err >&2; } 2>&1 ); echo \"[$x]\"", "[in\nerr]\n");
		expect("{ echo g; } > "+path("g.txt")+"; cat "+path("g.txt"), "g\n");
		expect("( echo sub; echo suberr >&2 ) 2>&1 | cat", "sub\nsuberr\n");
		expect("x=$( (echo q) ); echo $x; ( exit 3 ); echo $?", "q\n3\n");
		expect("f() { ( set -- z; echo $1 ); echo $1; }; f a", "z\na\n");
		// a failed command does not stop the rest
		expect("{ false; echo hi; }; ( false; echo there )", "hi\nthere\n");
	}
}
