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

public class TestGrep {

	static Console console;
	String lastErr;
	int lastStatus;

	@BeforeAll
	public static void beforeAll() throws IOException {
		// these commands are built in on Windows only; elsewhere the system has its own
		org.junit.jupiter.api.Assumptions.assumeTrue(FileSourceFactory.isWindows(), "the built in commands are Windows only");
		FileSourceFactory.setDefaultFactory(FileSourceFactory.fileProxyFactory);
		console = new Console();
	}

	String run(String command, byte [] stdin) throws IOException {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();
		console.setStdOut(new PrintStream(bao, true));
		console.setStdErr(new PrintStream(bae, true));
		console.setStdIn(new ByteArrayInputStream(stdin));
		lastStatus = console.executeScript(command);
		lastErr = new String(bae.toByteArray()).trim();
		return new String(bao.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	String grep(String command, String stdin) throws IOException {
		return run(command, stdin.getBytes(StandardCharsets.UTF_8));
	}

	static String path(File f) {
		return f.getAbsolutePath().replace(File.separatorChar, '/');
	}

	static final String TEXT = "apple\nBanana\ncherry\napple pie\n";

	@Test
	public void testBasicAndStatus() throws IOException {
		assertEquals("apple\napple pie\n", grep("grep apple", TEXT));
		assertEquals(0, lastStatus);
		assertEquals("", grep("grep zzz", TEXT));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testIgnoreCaseInvertCount() throws IOException {
		assertEquals("Banana\n", grep("grep -i banana", TEXT));
		assertEquals("Banana\ncherry\n", grep("grep -v apple", TEXT));
		assertEquals("2\n", grep("grep -c apple", TEXT));
		assertEquals("0\n", grep("grep -c zzz", TEXT));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testLineNumberAndMaxCount() throws IOException {
		assertEquals("1:apple\n4:apple pie\n", grep("grep -n apple", TEXT));
		assertEquals("apple\n", grep("grep -m1 apple", TEXT));
	}

	@Test
	public void testWordAndLine() throws IOException {
		assertEquals("apple\napple pie\n", grep("grep -w apple", "apple\napple pie\npineapple\n"));
		assertEquals("apple\n", grep("grep -x apple", "apple\napple pie\npineapple\n"));
	}

	@Test
	public void testOnlyMatching() throws IOException {
		assertEquals("12\n345\n6\n", grep("grep -o '[0-9][0-9]*'", "a12b345\nc6\n"));
	}

	@Test
	public void testBasicRegexSyntax() throws IOException {
		// in a basic expression + ? | ( ) are plain characters, and the backslashed ones are operators
		assertEquals("a+b\n", grep("grep 'a+b'", "a+b\naab\n"));
		assertEquals("aab\n", grep("grep 'a\\+b'", "a+b\naab\n"));
		assertEquals("cat\ndog\n", grep("grep 'cat\\|dog'", "cat\ndog\nbird\n"));
		assertEquals("abab\n", grep("grep '\\(ab\\)\\1'", "abab\nabba\n"));
		assertEquals("a*b\n", grep("grep 'a*b'", "a*b\n"));
		assertEquals("*x\n", grep("grep '^*x'", "*x\nx\n"));
	}

	@Test
	public void testExtendedRegexSyntax() throws IOException {
		assertEquals("aab\n", grep("grep -E 'a+b'", "a+b\naab\n"));
		assertEquals("cat\ndog\n", grep("grep -E 'cat|dog'", "cat\ndog\nbird\n"));
		assertEquals("ab12\n", grep("grep -E '^[a-z]{2}[0-9]+$'", "ab12\nabc\n"));
	}

	@Test
	public void testFixedStrings() throws IOException {
		assertEquals("a.b\n", grep("grep -F a.b", "a.b\naxb\n"));
	}

	@Test
	public void testPosixClassesAndWordBoundaries() throws IOException {
		assertEquals("ab1\n", grep("grep '[[:digit:]]'", "ab1\nabc\n"));
		assertEquals("a cat\n", grep("grep '\\<cat\\>'", "a cat\nconcat\n"));
	}

	@Test
	public void testMultiplePatterns() throws IOException {
		assertEquals("apple\ncherry\napple pie\n", grep("grep -e apple -e cherry", TEXT));
		assertEquals("apple\ncherry\napple pie\n", grep("grep 'apple\ncherry'", TEXT));
	}

	@Test
	public void testContext() throws IOException {
		String in = "1\n2\nhit\n4\n5\n6\n7\nhit\n9\n";
		assertEquals("2\nhit\n4\n--\n7\nhit\n9\n", grep("grep -C1 hit", in));
		assertEquals("hit\n4\n--\nhit\n9\n", grep("grep -A1 hit", in));
		assertEquals("2\nhit\n--\n7\nhit\n", grep("grep -B1 hit", in));
		// groups that touch are not separated, and -n marks context lines with -
		assertEquals("2-b\n3:hit\n4:hit\n5-d\n", grep("grep -n -C1 hit", "a\nb\nhit\nhit\nd\ne\n").replace("2-b", "2-b"));
	}

	@Test
	public void testFilesNamesAndLists(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		Files.write(a.toPath(), "one\ntwo\n".getBytes());
		Files.write(b.toPath(), "three\ntwo\n".getBytes());
		String pa = path(a), pb = path(b);
		assertEquals("one\n", grep("grep one "+pa, ""));
		assertEquals(pa+":two\n"+pb+":two\n", grep("grep two "+pa+" "+pb, ""));
		assertEquals("two\ntwo\n", grep("grep -h two "+pa+" "+pb, ""));
		assertEquals(pa+":one\n", grep("grep -H one "+pa, ""));
		assertEquals(pa+"\n"+pb+"\n", grep("grep -l two "+pa+" "+pb, ""));
		assertEquals(pb+"\n", grep("grep -L one "+pa+" "+pb, ""));
		assertEquals(pa+":1\n"+pb+":0\n", grep("grep -c one "+pa+" "+pb, ""));
		assertEquals("", grep("grep -q one "+pa+" "+pb, ""));
		assertEquals(0, lastStatus);
	}

	@Test
	public void testMissingFileAndDirectory(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		Files.write(a.toPath(), "one\n".getBytes());
		String missing = path(new File(dir, "nope.txt"));
		assertEquals(path(a)+":one\n", grep("grep one "+missing+" "+path(a), ""));
		assertEquals(2, lastStatus);
		assertTrue(lastErr.contains("nope.txt: No such file or directory"), lastErr);

		// -s hides the message, not the status
		grep("grep -s one "+missing, "");
		assertEquals(2, lastStatus);
		assertEquals("", lastErr);

		grep("grep one "+path(dir), "");
		assertEquals(2, lastStatus);
		assertTrue(lastErr.contains("Is a directory"), lastErr);

		// -q: a match wins over an error
		grep("grep -q one "+path(a)+" "+missing, "");
		assertEquals(0, lastStatus);
	}

	@Test
	public void testRecursive(@TempDir File dir) throws IOException {
		File sub = new File(dir, "sub");
		sub.mkdirs();
		Files.write(new File(dir, "a.txt").toPath(), "needle\n".getBytes());
		Files.write(new File(sub, "b.java").toPath(), "a needle\n".getBytes());
		Files.write(new File(sub, "c.txt").toPath(), "hay\n".getBytes());
		String d = path(dir);
		assertEquals(d+"/a.txt:needle\n"+d+"/sub/b.java:a needle\n", grep("grep -r needle "+d, ""));
		assertEquals(d+"/sub/b.java:a needle\n", grep("grep -r --include=*.java needle "+d, ""));
		assertEquals(d+"/a.txt:needle\n", grep("grep -r --exclude=*.java needle "+d, ""));
	}

	@Test
	public void testBinaryFile(@TempDir File dir) throws IOException {
		File bin = new File(dir, "x.bin");
		Files.write(bin.toPath(), new byte[] {'a', 'b', 0, 'c', '\n'});
		String out = grep("grep ab "+path(bin), "");
		assertEquals(0, lastStatus);
		assertTrue(out.contains("binary file matches"), out);
		assertEquals("ab\u0000c\n", grep("grep -a ab "+path(bin), ""));
	}

	@Test
	public void testPatternFile(@TempDir File dir) throws IOException {
		File pats = new File(dir, "pats");
		Files.write(pats.toPath(), "apple\ncherry\n".getBytes());
		assertEquals("apple\ncherry\napple pie\n", grep("grep -f "+path(pats), TEXT));
		Files.write(pats.toPath(), new byte[0]);
		assertEquals("", grep("grep -f "+path(pats), TEXT));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testErrors() throws IOException {
		grep("grep", "");
		assertEquals(2, lastStatus);
		grep("grep -Q a", "a\n");
		assertEquals(2, lastStatus);
		assertTrue(lastErr.contains("invalid option -- 'Q'"), lastErr);
		grep("grep -E '(a'", "a\n");
		assertEquals(2, lastStatus);
		assertTrue(lastErr.contains("invalid regular expression"), lastErr);
	}
}
