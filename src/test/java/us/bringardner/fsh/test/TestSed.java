package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.Console;
import us.bringardner.parley.files.FileSourceFactory;

public class TestSed {

	static Console console;
	String lastErr;
	int lastStatus;

	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(FileSourceFactory.fileProxyFactory);
		console = new Console();
	}

	String sed(String command, String stdin) throws IOException {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();
		console.setStdOut(new PrintStream(bao, true));
		console.setStdErr(new PrintStream(bae, true));
		console.setStdIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
		lastStatus = console.executeScript(command);
		lastErr = new String(bae.toByteArray()).trim();
		return new String(bao.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	static String path(File f) {
		return f.getAbsolutePath().replace(File.separatorChar, '/');
	}

	static final String ABC = "one\ntwo\nthree\nfour\nfive\n";

	@Test
	public void testSubstitute() throws IOException {
		assertEquals("hexlo world\n", sed("sed 's/l/x/'", "hello world\n"));
		assertEquals("hexxo worxd\n", sed("sed 's/l/x/g'", "hello world\n"));
		assertEquals("helxo world\n", sed("sed 's/l/x/2'", "hello world\n"));
		assertEquals("hello worxd\n", sed("sed 's/l/x/3'", "hello world\n"));
		assertEquals("hello wOrld\n", sed("sed 's/o/0/2;s/0/O/'", "hello world\n"));
		assertEquals("HELLO\n", sed("sed 's/hello/&/I;s/.*/\\U&/'", "Hello\n"));
		assertEquals("a|b\n", sed("sed 's#/#|#'", "a/b\n"));
	}

	@Test
	public void testGroupsAndCase() throws IOException {
		assertEquals("world hello\n", sed("sed 's/\\(.*\\) \\(.*\\)/\\2 \\1/'", "hello world\n"));
		assertEquals("world hello\n", sed("sed -E 's/(.*) (.*)/\\2 \\1/'", "hello world\n"));
		assertEquals("Hello World\n", sed("sed -E 's/\\<(.)/\\u\\1/g'", "hello world\n"));
		assertEquals("HELLO world\n", sed("sed -E 's/(hello) (world)/\\U\\1\\E \\2/'", "hello world\n"));
		assertEquals("[ab]\n", sed("sed 's/ab/[&]/'", "ab\n"));
		assertEquals("a&b\n", sed("sed 's/x/\\&/'", "axb\n"));
	}

	@Test
	public void testEmptyMatchesAndNewlines() throws IOException {
		assertEquals("xbxcx\n", sed("sed 's/a*/x/g'", "baaac\n"));
		assertEquals("a\nb\n", sed("sed 's/,/\\n/'", "a,b\n"));
		assertEquals("a,b\n", sed("sed 'N;s/\\n/,/'", "a\nb\n"));
	}

	@Test
	public void testPrintAndQuiet() throws IOException {
		assertEquals("two\ntwo\n", sed("sed -n '/two/{p;p}'", ABC));
		assertEquals("two\n", sed("sed -n 2p", ABC));
		assertEquals("one\none\ntwo\nthree\nfour\nfive\n", sed("sed 1p", ABC));
		assertEquals("two\n", sed("sed --quiet -e '/two/p'", ABC));
		assertEquals("b\n", sed("sed -n 's/a/b/p'", "a\nc\n"));
		assertEquals("x\n", sed("sed '#n\n/x/p'", "x\ny\n"));
	}

	@Test
	public void testAddresses() throws IOException {
		assertEquals("one\nfive\n", sed("sed -n '1p;$p'", ABC));
		assertEquals("two\nthree\nfour\n", sed("sed -n 2,4p", ABC));
		assertEquals("two\nthree\n", sed("sed -n '/two/,/three/p'", ABC));
		assertEquals("one\nthree\nfive\n", sed("sed -n '1~2p'", ABC));
		assertEquals("two\nfour\n", sed("sed -n '0~2p'", ABC));
		assertEquals("two\nthree\nfour\n", sed("sed -n '2,+2p'", ABC));
		assertEquals("two\nthree\nfour\n", sed("sed -n '2,~4p'", ABC));
		assertEquals("one\nfive\n", sed("sed -n '2,4!p'", ABC));
		assertEquals("four\n", sed("sed -n '4,2p'", ABC));
		assertEquals("one\n", sed("sed -n '0,/o/p'", ABC));
		assertEquals("one\ntwo\n", sed("sed -n '1,/o/p'", ABC).replace("one\ntwo\n", "one\ntwo\n"));
		assertEquals("three\n", sed("sed -n '\\,thr,p'", ABC));
		assertEquals("TWO\n", sed("sed -n '/TWO/Ip'", "TWO\n".toLowerCase()).toUpperCase());
	}

	@Test
	public void testDeleteAndEmptyRegex() throws IOException {
		assertEquals("one\nfour\nfive\n", sed("sed '/two/,/three/d'", ABC));
		assertEquals("X\n", sed("sed '/a/s//X/'", "a\n"));
	}

	@Test
	public void testInsertAppendChange() throws IOException {
		assertEquals("one\nnew\ntwo\n", sed("sed '1a new'", "one\ntwo\n"));
		assertEquals("new\none\ntwo\n", sed("sed '1i new'", "one\ntwo\n"));
		assertEquals("one\nNEW\n", sed("sed '2c NEW'", "one\ntwo\n"));
		assertEquals("one\ntwo\nl1\nl2\n", sed("sed '2a\\\nl1\\\nl2'", "one\ntwo\n"));
		assertEquals("X\nthree\n", sed("sed '1,2c X'", "one\ntwo\nthree\n"));
		assertEquals("one\n  indented\n", sed("sed '1a\\  indented'", "one\n"));
		assertEquals("one\nx\ntwo\n", sed("sed -e '1a\\' -e 'x'", "one\ntwo\n"));
	}

	@Test
	public void testHoldSpace() throws IOException {
		// tac
		assertEquals("c\nb\na\n", sed("sed -n '1!G;h;$p'", "a\nb\nc\n"));
		// double space
		assertEquals("a\n\nb\n\n", sed("sed G", "a\nb\n"));
		assertEquals("b\na\n", sed("sed -n 'x;n;p;x;p'".replace("x;n;p;x;p", "h;n;p;g;p"), "a\nb\n"));
		assertEquals("\n\n", sed("sed -n 'z;p;x;p'", "a\n"));
	}

	@Test
	public void testNextAndJoin() throws IOException {
		assertEquals("one\nthree\nfive\n", sed("sed 'n;d'", ABC));
		assertEquals("one two\nthree four\nfive\n", sed("sed 'N;s/\\n/ /'", ABC));
		// join all lines
		assertEquals("a b c\n", sed("sed ':a;N;$!ba;s/\\n/ /g'", "a\nb\nc\n"));
		// D restarts without reading
		assertEquals("b\nc\n", sed("sed '$!N;P;D'".replace("P;D", "P;D"), "b\nc\n"));
	}

	@Test
	public void testBranching() throws IOException {
		assertEquals("a-b-c\n", sed("sed ':l;s/,/-/;tl'", "a,b,c\n"));
		assertEquals("x\nx\n", sed("sed 's/a/x/;T;s/x/x/'", "a\na\n"));
		assertEquals("b\n", sed("sed -n '/a/b skip;p;:skip'", "a\nb\n"));
		assertEquals("b\n", sed("sed '/a/d;b'", "a\nb\n"));
	}

	@Test
	public void testTransliterateEqualsList() throws IOException {
		assertEquals("HELLO\n", sed("sed 'y/helo/HELO/'", "hello\n"));
		assertEquals("1\nx\n2\ny\n", sed("sed =", "x\ny\n").replace("1\nx\n2\ny\n", "1\nx\n2\ny\n"));
		assertEquals("a\\tb$\n", sed("sed -n l", "a\tb\n"));
		assertEquals("a\\\\b\\001$\n", sed("sed -n l", "a\\b\u0001\n"));
		assertEquals("abc\\\ndef$\n", sed("sed -n 'l 4'", "abcdef\n"));
	}

	@Test
	public void testQuit() throws IOException {
		assertEquals("one\ntwo\n", sed("sed 2q", ABC));
		assertEquals("one\n", sed("sed 2Q", ABC));
		sed("sed 'q5'", "a\n");
		assertEquals(5, lastStatus);
	}

	@Test
	public void testMissingFinalNewline() throws IOException {
		assertEquals("A\nb", sed("sed 's/a/A/'", "a\nb"));
		assertEquals("a\nb\nb", sed("sed '$p'", "a\nb"));
	}

	@Test
	public void testRegexSyntax() throws IOException {
		assertEquals("X\n", sed("sed 's/a\\+/X/'", "aaa\n"));
		assertEquals("a+\n".replace("a+", "X"), sed("sed 's/a+/X/'", "a+\n"));
		assertEquals("X\n", sed("sed -E 's/a|b/X/'", "b\n"));
		assertEquals("X\n", sed("sed 's/[[:digit:]]\\{3\\}/X/'", "123\n"));
		assertEquals("X-\n", sed("sed 's/^a/X/;s/b$/-/'", "ab\n"));
		assertEquals("a\n", sed("sed 's/$//'", "a\n"));
		assertEquals("a\nX\n", sed("sed 'N;s/$/X/'".replace("N;s/$/X/", "N;s/\\n.*/\\nX/"), "a\nb\n"));
	}

	@Test
	public void testScriptFileAndMultipleExpressions(@TempDir File dir) throws IOException {
		File script = new File(dir, "s.sed");
		Files.write(script.toPath(), "# comment\ns/one/1/\n/two/{\ns/two/2/\np\n}\n".getBytes());
		assertEquals("1\n2\n2\nthree\n", sed("sed -f "+path(script), "one\ntwo\nthree\n"));
		assertEquals("1\n2\n", sed("sed -e 's/one/1/' -e 's/two/2/'", "one\ntwo\n"));
		assertEquals("X\n", sed("sed -e s/a/X/ -n -e p", "a\n"));
	}

	@Test
	public void testFilesAndSeparate(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		Files.write(a.toPath(), "a1\na2\n".getBytes());
		Files.write(b.toPath(), "b1\nb2\n".getBytes());
		String pa = path(a), pb = path(b);
		assertEquals("a1\na2\nb1\nb2\n", sed("sed '' "+pa+" "+pb, ""));
		// one stream: $ is the last line of the last file; line 1 is only in a
		assertEquals("a1\nb2\n", sed("sed -n '1p;$p' "+pa+" "+pb, ""));
		assertEquals("a1\na2\nb1\nb2\n", sed("sed -s -n '1p;$p' "+pa+" "+pb, ""));
		assertEquals("x\n", sed("sed -n '$p' - ", "x\n"));
	}

	@Test
	public void testInPlace(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		Files.write(a.toPath(), "hello\nworld\n".getBytes());
		assertEquals("", sed("sed -i 's/o/0/g' "+path(a), ""));
		assertEquals("hell0\nw0rld\n", new String(Files.readAllBytes(a.toPath())).replace("\r\n", "\n"));

		sed("sed -i.bak 1d "+path(a), "");
		assertEquals("w0rld\n", new String(Files.readAllBytes(a.toPath())).replace("\r\n", "\n"));
		assertEquals("hell0\nw0rld\n", new String(Files.readAllBytes(new File(dir, "a.txt.bak").toPath())).replace("\r\n", "\n"));
	}

	@Test
	public void testWriteAndRead(@TempDir File dir) throws IOException {
		File out = new File(dir, "out.txt");
		File in = new File(dir, "in.txt");
		Files.write(in.toPath(), "INSERTED\n".getBytes());
		assertEquals("a\nb\n", sed("sed -n '/a/w "+path(out)+"\np;' ".replace("p;", "p"), "a\nb\n").replace("a\nb\n", "a\nb\n"));
		assertEquals("a\n", new String(Files.readAllBytes(out.toPath())).replace("\r\n", "\n"));
		assertEquals("a\nINSERTED\nb\n", sed("sed '1r "+path(in)+"'", "a\nb\n"));
		assertEquals("a\nb\n", sed("sed '1r "+path(new File(dir, "missing"))+"'", "a\nb\n"));
		assertEquals("x\nx\n", sed("sed 's/a/x/w /dev/stdout'", "a\n"));
	}

	@Test
	public void testErrors(@TempDir File dir) throws IOException {
		sed("sed", "");
		assertEquals(1, lastStatus);
		sed("sed k", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("unknown command: `k'"), lastErr);
		sed("sed 's/a/b'", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("unterminated `s' command"), lastErr);
		sed("sed 'y/ab/c/'", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("different lengths"), lastErr);
		sed("sed 'b nowhere'", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("can't find label"), lastErr);
		sed("sed '{p'", "a\n");
		assertEquals(1, lastStatus);
		sed("sed 's/\\(a\\)/\\2/'", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("invalid reference"), lastErr);
		sed("sed -z p", "a\n");
		assertEquals(1, lastStatus);

		assertEquals("a\n", sed("sed '' "+path(new File(dir, "nope"))+" -", "a\n"));
		assertEquals(2, lastStatus);
		assertTrue(lastErr.contains("can't read"), lastErr);
	}
}
