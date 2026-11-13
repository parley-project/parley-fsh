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

/** head, tail, sort, uniq, cut, tr and tee */
public class TestTextCommands {

	static Console console;
	String lastErr;
	int lastStatus;

	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(FileSourceFactory.fileProxyFactory);
		console = new Console();
	}

	String run(String command, String stdin) throws IOException {
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

	static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	static final String TWELVE = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n";

	// ---------------------------------------------------------------- head

	@Test
	public void testHead() throws IOException {
		assertEquals("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n", run("head", TWELVE));
		assertEquals("1\n2\n", run("head -n 2", TWELVE));
		assertEquals("1\n2\n", run("head -n2", TWELVE));
		assertEquals("1\n2\n", run("head -2", TWELVE));
		assertEquals("1\n2\n", run("head --lines=2", TWELVE));
		assertEquals("", run("head -n 0", TWELVE));
		assertEquals("a\nb", run("head", "a\nb"));
	}

	@Test
	public void testHeadAllButLast() throws IOException {
		assertEquals("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n", run("head -n -2", TWELVE));
		assertEquals("", run("head -n -20", TWELVE));
		assertEquals("abc", run("head -c -3", "abcdef"));
	}

	@Test
	public void testHeadBytes() throws IOException {
		assertEquals("abc", run("head -c 3", "abcdef"));
		assertEquals("abc", run("head -c3", "abcdef"));
		assertEquals("abcdef", run("head -c 1K", "abcdef"));
	}

	@Test
	public void testHeadFiles(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		Files.write(a.toPath(), "a1\na2\n".getBytes());
		Files.write(b.toPath(), "b1\nb2\n".getBytes());
		String pa = path(a), pb = path(b);
		assertEquals("a1\n", run("head -n1 "+pa, ""));
		assertEquals("==> "+pa+" <==\na1\n\n==> "+pb+" <==\nb1\n", run("head -n1 "+pa+" "+pb, ""));
		assertEquals("a1\nb1\n", run("head -q -n1 "+pa+" "+pb, ""));
		assertEquals("==> "+pa+" <==\na1\n", run("head -v -n1 "+pa, ""));
		assertEquals("==> "+pa+" <==\na1\n", run("head -n1 "+path(new File(dir, "nope"))+" "+pa, ""));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("No such file or directory"), lastErr);
	}

	@Test
	public void testHeadErrors() throws IOException {
		run("head -n x", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("invalid number of lines"), lastErr);
		run("head -z", "a\n");
		assertEquals(1, lastStatus);
	}

	// ---------------------------------------------------------------- tail

	@Test
	public void testTail() throws IOException {
		assertEquals("3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n", run("tail", TWELVE));
		assertEquals("11\n12\n", run("tail -n 2", TWELVE));
		assertEquals("11\n12\n", run("tail -2", TWELVE));
		assertEquals("11\n12\n", run("tail --lines=2", TWELVE));
		assertEquals("", run("tail -n 0", TWELVE));
		assertEquals("a\nb", run("tail -n 2", "a\nb"));
		assertEquals("b", run("tail -n 1", "a\nb"));
	}

	@Test
	public void testTailFromStart() throws IOException {
		assertEquals("10\n11\n12\n", run("tail -n +10", TWELVE));
		assertEquals(TWELVE, run("tail -n +1", TWELVE));
		assertEquals("", run("tail -n +20", TWELVE));
		assertEquals("def", run("tail -c +4", "abcdef"));
	}

	@Test
	public void testTailBytes() throws IOException {
		assertEquals("def", run("tail -c 3", "abcdef"));
		assertEquals("abcdef", run("tail -c 100", "abcdef"));
	}

	@Test
	public void testTailFiles(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		Files.write(a.toPath(), "a1\na2\n".getBytes());
		Files.write(b.toPath(), "b1\nb2\n".getBytes());
		String pa = path(a), pb = path(b);
		assertEquals("a2\n", run("tail -n1 "+pa, ""));
		assertEquals("==> "+pa+" <==\na2\n\n==> "+pb+" <==\nb2\n", run("tail -n1 "+pa+" "+pb, ""));
		assertEquals("a2\nb2\n", run("tail -q -n1 "+pa+" "+pb, ""));
	}

	// ---------------------------------------------------------------- sort

	@Test
	public void testSortBasic() throws IOException {
		assertEquals("a\nb\nc\n", run("sort", "b\na\nc\n"));
		assertEquals("c\nb\na\n", run("sort -r", "b\na\nc\n"));
		assertEquals("a\nb\n", run("sort", "b\na"));
		assertEquals("a\nb\n", run("sort -u", "b\na\nb\n"));
		assertEquals("a\na\nb\n", run("sort", "b\na\na\n"));
	}

	@Test
	public void testSortNumeric() throws IOException {
		assertEquals("10\n100\n9\n", run("sort", "10\n9\n100\n"));
		assertEquals("9\n10\n100\n", run("sort -n", "10\n9\n100\n"));
		assertEquals("3\n2\n1\n", run("sort -nr", "1\n3\n2\n"));
		assertEquals("-5\n-1.5\n0\n2.25\n", run("sort -n", "2.25\n-1.5\n0\n-5\n"));
		assertEquals("abc\n1\n2\n", run("sort -n", "2\nabc\n1\n"));
		assertEquals("-1e3\n5\n2e1\n", run("sort -g", "5\n2e1\n-1e3\n"));
		assertEquals("2K\n3M\n1G\n", run("sort -h", "1G\n2K\n3M\n"));
	}

	@Test
	public void testSortFoldVersionMonth() throws IOException {
		assertEquals("B\na\nb\n", run("sort", "b\nB\na\n"));
		assertEquals("a\nB\nb\n", run("sort -f", "b\nB\na\n"));
		assertEquals("v1.2\nv1.9\nv1.10\n", run("sort -V", "v1.10\nv1.2\nv1.9\n"));
		assertEquals("foo\nJAN\nFEB\nMAR\n", run("sort -M", "MAR\nJAN\nfoo\nFEB\n"));
	}

	@Test
	public void testSortKeys() throws IOException {
		assertEquals("y 1\nz 2\nx 3\n", run("sort -k2", "x 3\ny 1\nz 2\n"));
		assertEquals("b,9\na,10\nc,100\n", run("sort -t, -k2n", "a,10\nb,9\nc,100\n"));
		assertEquals("b,9\na,10\nc,100\n", run("sort -t , -k 2,2n", "a,10\nb,9\nc,100\n"));
		assertEquals("b 1\na 2\nb 2\n", run("sort -k2n -k1", "b 2\na 2\nb 1\n"));
		assertEquals("a 1\nb 3\n", run("sort -u -k1,1", "a 1\na 2\nb 3\n"));
		// a key's own option replaces the global one
		assertEquals("b 9\na 10\n", run("sort -r -k2n", "a 10\nb 9\n"));
		assertEquals("a  1\nb 2\n", run("sort -b -k2", "b 2\na  1\n"));
	}

	@Test
	public void testSortStable() throws IOException {
		assertEquals("a 1\nb 1\n", run("sort -k2n", "b 1\na 1\n"));
		assertEquals("b 1\na 1\n", run("sort -s -k2n", "b 1\na 1\n"));
	}

	@Test
	public void testSortCheck() throws IOException {
		assertEquals("", run("sort -c", "a\nb\nc\n"));
		assertEquals(0, lastStatus);
		assertEquals("", run("sort -c", "b\na\n"));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("disorder: a"), lastErr);
	}

	@Test
	public void testSortFilesAndOutput(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		File out = new File(dir, "out.txt");
		Files.write(a.toPath(), "c\na\n".getBytes());
		Files.write(b.toPath(), "d\nb\n".getBytes());
		assertEquals("a\nb\nc\nd\n", run("sort "+path(a)+" "+path(b), ""));
		assertEquals("", run("sort -o "+path(out)+" "+path(a)+" "+path(b), ""));
		assertEquals("a\nb\nc\nd\n", read(out));
		// the output may be an input
		run("sort -o "+path(a)+" "+path(a), "");
		assertEquals("a\nc\n", read(a));
		run("sort "+path(new File(dir, "nope")), "");
		assertEquals(2, lastStatus);
	}

	@Test
	public void testSortErrors() throws IOException {
		run("sort -k0", "a\n");
		assertEquals(2, lastStatus);
		assertTrue(lastErr.contains("invalid key"), lastErr);
		run("sort -Q", "a\n");
		assertEquals(2, lastStatus);
	}

	// ---------------------------------------------------------------- uniq

	static final String RUNS = "a\na\nb\nc\nc\nc\n";

	@Test
	public void testUniq() throws IOException {
		assertEquals("a\nb\nc\n", run("uniq", RUNS));
		assertEquals("      2 a\n      1 b\n      3 c\n", run("uniq -c", RUNS));
		assertEquals("a\nc\n", run("uniq -d", RUNS));
		assertEquals("b\n", run("uniq -u", RUNS));
		assertEquals("a\na\nc\nc\nc\n", run("uniq -D", RUNS));
		assertEquals("a\nb\na\n", run("uniq", "a\nb\na\n"));
	}

	@Test
	public void testUniqComparison() throws IOException {
		assertEquals("A\n", run("uniq -i", "A\na\n"));
		assertEquals("x a\nz b\n", run("uniq -f1", "x a\ny a\nz b\n"));
		assertEquals("xa\nzb\n", run("uniq -s1", "xa\nya\nzb\n"));
		assertEquals("ab\nb\n", run("uniq -w1", "ab\nac\nb\n"));
	}

	@Test
	public void testUniqFiles(@TempDir File dir) throws IOException {
		File in = new File(dir, "in.txt");
		File out = new File(dir, "out.txt");
		Files.write(in.toPath(), RUNS.getBytes());
		assertEquals("a\nb\nc\n", run("uniq "+path(in), ""));
		assertEquals("", run("uniq "+path(in)+" "+path(out), ""));
		assertEquals("a\nb\nc\n", read(out));
		run("uniq a b c", "");
		assertEquals(1, lastStatus);
	}

	// ---------------------------------------------------------------- cut

	@Test
	public void testCutCharactersAndBytes() throws IOException {
		assertEquals("bcd\n", run("cut -c2-4", "abcdef\n"));
		assertEquals("ace\n", run("cut -c1,3,5", "abcdef\n"));
		assertEquals("def\n", run("cut -c4-", "abcdef\n"));
		assertEquals("ab\n", run("cut -c-2", "abcdef\n"));
		assertEquals("aef\n", run("cut --complement -c2-4", "abcdef\n"));
		assertEquals("ab\n", run("cut -b1-2", "abcdef\n"));
		assertEquals("a\nb\n", run("cut -c1", "ax\nby\n"));
		assertEquals("abc\n", run("cut -c1-3", "abcdef"));
	}

	@Test
	public void testCutFields() throws IOException {
		assertEquals("b\nd e\n", run("cut -d: -f2", "a:b:c\nd e\n"));
		assertEquals("b\n", run("cut -d: -f2 -s", "a:b:c\nd e\n"));
		assertEquals("a:c\n", run("cut -d: -f1,3", "a:b:c\n"));
		assertEquals("b:c\n", run("cut -d: -f2-", "a:b:c\n"));
		assertEquals("a:c\n", run("cut -d : --complement -f 2", "a:b:c\n"));
		assertEquals("a-c\n", run("cut -d: -f1,3 --output-delimiter=-", "a:b:c\n"));
		assertEquals("b\n", run("cut -f2", "a\tb\tc\n"));
		assertEquals("c\n", run("cut -d, -f3", "a,,c\n"));
		assertEquals("\n", run("cut -d, -f2", "a,,c\n"));
	}

	@Test
	public void testCutFiles(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		Files.write(a.toPath(), "1,2\n3,4\n".getBytes());
		assertEquals("2\n4\n", run("cut -d, -f2 "+path(a), ""));
	}

	@Test
	public void testCutErrors() throws IOException {
		run("cut", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("you must specify a list"), lastErr);
		run("cut -f0", "a\n");
		assertEquals(1, lastStatus);
		run("cut -d ab -f1", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("single character"), lastErr);
		run("cut -c1 -f1", "a\n");
		assertEquals(1, lastStatus);
		run("cut -f3-1", "a\n");
		assertEquals(1, lastStatus);
	}

	// ---------------------------------------------------------------- tr

	@Test
	public void testTrTranslate() throws IOException {
		assertEquals("HELLO\n", run("tr a-z A-Z", "hello\n"));
		assertEquals("HELLO\n", run("tr '[:lower:]' '[:upper:]'", "hello\n"));
		assertEquals("ifmmp\n", run("tr a-y b-z", "hello\n"));
		assertEquals("a\nb\n", run("tr ',' '\\n'", "a,b\n"));
		assertEquals("xxx\n", run("tr abc x", "abc\n"));
		assertEquals("xbc\n", run("tr -t abc x", "abc\n"));
		assertEquals("xxx\n", run("tr abc '[x*3]'", "abc\n"));
		assertEquals("xxx\n", run("tr abc '[x*]'", "abc\n"));
		assertEquals("a\tb\n", run("tr ' ' '\\t'", "a b\n"));
		assertEquals("12\n", run("tr 'ab' '\\061\\062'", "ab\n"));
	}

	@Test
	public void testTrDeleteAndSqueeze() throws IOException {
		assertEquals("heo\n", run("tr -d l", "hello\n"));
		assertEquals("helo\n", run("tr -s l", "hello\n"));
		assertEquals("a b\n", run("tr -s ' '", "a   b\n"));
		assertEquals("abc\n", run("tr -s a-z", "aabbcc\n"));
		assertEquals("ab\n", run("tr -cd 'a-z\\n'", "a1b2\n"));
		assertEquals("a1b2\n", run("tr -d '[:blank:]'", "a 1 b\t2\n"));
		assertEquals("ab_\n", run("tr -c 'a-z\\n' '_'", "ab1\n"));
		assertEquals("a_b\n", run("tr -cs 'a-z\\n' '_'", "a12b\n"));
		assertEquals("hlo\n", run("tr -ds 'e' 'l'", "hello\n"));
	}

	@Test
	public void testTrErrors() throws IOException {
		run("tr", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("missing operand"), lastErr);
		run("tr a", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("missing operand after 'a'"), lastErr);
		run("tr -d a b", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("extra operand 'b'"), lastErr);
		run("tr z-a x", "a\n");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("reverse collating"), lastErr);
		run("tr '[:bogus:]' x", "a\n");
		assertEquals(1, lastStatus);
	}

	// ---------------------------------------------------------------- tee

	@Test
	public void testTee(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		assertEquals("x\ny\n", run("tee "+path(a), "x\ny\n"));
		assertEquals("x\ny\n", read(a));
		assertEquals("x\ny\n", run("tee "+path(a)+" "+path(b), "x\ny\n"));
		assertEquals("x\ny\n", read(a));
		assertEquals("x\ny\n", read(b));
		assertEquals("z\n", run("tee", "z\n"));
	}

	@Test
	public void testTeeAppend(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		Files.write(a.toPath(), "old\n".getBytes());
		assertEquals("new\n", run("tee -a "+path(a), "new\n"));
		assertEquals("old\nnew\n", read(a));
		run("tee "+path(a), "over\n");
		assertEquals("over\n", read(a));
	}

	// ---------------------------------------------------------------- together

	@Test
	public void testPipeline() throws IOException {
		assertEquals("      2 a\n      1 b\n", run("sort | uniq -c", "b\na\na\n"));
		assertEquals("c\nb\n", run("sort -r | head -n 2", "a\nb\nc\n"));
		assertEquals("B\n", run("tail -n 1 | tr a-z A-Z", "a\nb\n"));
		assertEquals("2\n", run("cut -d, -f2 | head -1", "1,2\n3,4\n"));
	}
}
