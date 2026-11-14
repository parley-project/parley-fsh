package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import us.bringardner.fsh.Console;
import us.bringardner.parley.files.FileSourceFactory;

/** the commands after the first batch: text tools, file tools, system tools, checksums, dumps, archives, curl, wget and xargs */
public class TestMoreCommands {

	static Console console;
	static HttpServer server;
	static String base;
	String lastErr;
	int lastStatus;

	@BeforeAll
	public static void beforeAll() throws IOException {
		// these commands are built in on Windows only; elsewhere the system has its own
		org.junit.jupiter.api.Assumptions.assumeTrue(FileSourceFactory.isWindows(), "the built in commands are Windows only");
		FileSourceFactory.setDefaultFactory(FileSourceFactory.fileProxyFactory);
		console = new Console();

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/hello", ex -> reply(ex, 200, "hi there\n"));
		server.createContext("/redirect", ex -> {
			ex.getResponseHeaders().add("Location", "/hello");
			reply(ex, 302, "moved");
		});
		server.createContext("/missing", ex -> reply(ex, 404, "nope"));
		server.createContext("/echo", ex -> {
			String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String h = ex.getRequestHeaders().getFirst("X-Test");
			reply(ex, 200, ex.getRequestMethod()+"|"+body+"|"+h+"\n");
		});
		server.createContext("/dir/", ex -> reply(ex, 200, "listing\n"));
		server.start();
		base = "http://127.0.0.1:"+server.getAddress().getPort();
	}

	@AfterAll
	public static void afterAll() {
		if( server != null ) {
			server.stop(0);
		}
	}

	private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
		byte [] b = body.getBytes(StandardCharsets.UTF_8);
		if( ex.getRequestMethod().equals("HEAD")) {
			ex.sendResponseHeaders(code, -1);
		} else {
			ex.sendResponseHeaders(code, b.length);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(b);
			}
		}
		ex.close();
	}

	byte [] runBytes(String command, byte [] stdin) throws IOException {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();
		console.setStdOut(new PrintStream(bao, true));
		console.setStdErr(new PrintStream(bae, true));
		console.setStdIn(new ByteArrayInputStream(stdin));
		lastStatus = console.executeScript(command);
		lastErr = new String(bae.toByteArray(), StandardCharsets.UTF_8).trim();
		return bao.toByteArray();
	}

	String run(String command, String stdin) throws IOException {
		return new String(runBytes(command, stdin.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	String run(String command) throws IOException {
		return run(command, "");
	}

	static String path(File f) {
		return f.getAbsolutePath().replace(File.separatorChar, '/');
	}

	static File write(File dir, String name, String text) throws IOException {
		File f = new File(dir, name);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
		return f;
	}

	static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	// ---------------------------------------------------------------- text tools

	@Test
	public void testTacRev() throws IOException {
		assertEquals("c\nb\na\n", run("tac", "a\nb\nc\n"));
		assertEquals("cba\nyx\n", run("rev", "abc\nxy\n"));
	}

	@Test
	public void testNl() throws IOException {
		assertEquals("     1\ta\n\n     2\tb\n", run("nl", "a\n\nb\n"));
		assertEquals("     1\ta\n     2\t\n     3\tb\n", run("nl -b a", "a\n\nb\n"));
		assertEquals("1  : a\n2  : b\n", run("nl -n ln -w 3 -s ': '", "a\nb\n"));
		assertEquals("000010\ta\n000015\tb\n", run("nl -n rz -v 10 -i 5", "a\nb\n"));
		assertEquals("     1\ta\n       b\n", run("nl -b p^a", "a\nb\n"));
	}

	@Test
	public void testPaste(@TempDir File dir) throws IOException {
		File a = write(dir, "a", "1\n2\n3\n");
		File b = write(dir, "b", "x\n");
		assertEquals("1\tx\n2\t\n3\t\n", run("paste "+path(a)+" "+path(b)));
		assertEquals("1,x\n2,\n3,\n", run("paste -d, "+path(a)+" "+path(b)));
		assertEquals("1\t2\t3\nx\n", run("paste -s "+path(a)+" "+path(b)));
		assertEquals("1,2,3\n", run("paste -sd, "+path(a)));
		assertEquals("1\t2\n3\t4\n", run("paste - -", "1\n2\n3\n4\n"));
	}

	@Test
	public void testFold() throws IOException {
		assertEquals("abcde\nfghij\n", run("fold -w 5", "abcdefghij\n"));
		assertEquals("hello \nworld foo\n", run("fold -s -w 10", "hello world foo\n"));
		assertEquals("short\n", run("fold", "short\n"));
		assertEquals("ab\ncd\n", run("fold -b -w 2", "abcd\n"));
	}

	@Test
	public void testComm(@TempDir File dir) throws IOException {
		File a = write(dir, "a", "a\nb\nc\n");
		File b = write(dir, "b", "b\nc\nd\n");
		String ab = path(a)+" "+path(b);
		assertEquals("a\n\t\tb\n\t\tc\n\td\n", run("comm "+ab));
		assertEquals("b\nc\n", run("comm -12 "+ab));
		assertEquals("a\n\td\n", run("comm -3 "+ab));
		assertEquals("a\n", run("comm -23 "+ab));
	}

	@Test
	public void testJoin(@TempDir File dir) throws IOException {
		File a = write(dir, "a", "1 a\n2 b\n3 c\n");
		File b = write(dir, "b", "1 x\n3 z\n");
		String ab = path(a)+" "+path(b);
		assertEquals("1 a x\n3 c z\n", run("join "+ab));
		assertEquals("1 a x\n2 b\n3 c z\n", run("join -a 1 "+ab));
		assertEquals("2 b\n", run("join -v 1 "+ab));
		assertEquals("a x\nc z\n", run("join -o 1.2,2.2 "+ab));
		File c = write(dir, "c", "x,1\ny,2\n");
		File d = write(dir, "d", "1,p\n2,q\n");
		assertEquals("1,x,p\n2,y,q\n", run("join -t, -1 2 -2 1 "+path(c)+" "+path(d)));
	}

	@Test
	public void testCmp(@TempDir File dir) throws IOException {
		File a = write(dir, "a", "abc\n");
		File b = write(dir, "b", "abd\n");
		File c = write(dir, "c", "abc\n");
		File d = write(dir, "d", "abc");
		assertEquals("", run("cmp "+path(a)+" "+path(c)));
		assertEquals(0, lastStatus);
		assertEquals(path(a)+" "+path(b)+" differ: byte 3, line 1\n", run("cmp "+path(a)+" "+path(b)));
		assertEquals(1, lastStatus);
		assertEquals("", run("cmp -s "+path(a)+" "+path(b)));
		assertEquals(1, lastStatus);
		assertEquals("3 143 144\n", run("cmp -l "+path(a)+" "+path(b)));
		run("cmp "+path(a)+" "+path(d));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("EOF on"), lastErr);
		run("cmp "+path(a)+" "+path(new File(dir, "nope")));
		assertEquals(2, lastStatus);
	}

	@Test
	public void testDiffNormal(@TempDir File dir) throws IOException {
		File a = write(dir, "a", "1\n2\n3\n");
		File same = write(dir, "same", "1\n2\n3\n");
		File changed = write(dir, "changed", "1\nX\n3\n");
		File added = write(dir, "added", "1\n2\n3\n4\n");
		File removed = write(dir, "removed", "1\n3\n");
		String pa = path(a);
		assertEquals("", run("diff "+pa+" "+path(same)));
		assertEquals(0, lastStatus);
		assertEquals("2c2\n< 2\n---\n> X\n", run("diff "+pa+" "+path(changed)));
		assertEquals(1, lastStatus);
		assertEquals("3a4\n> 4\n", run("diff "+pa+" "+path(added)));
		assertEquals("2d1\n< 2\n", run("diff "+pa+" "+path(removed)));
		assertEquals("Files "+pa+" and "+path(changed)+" differ\n", run("diff -q "+pa+" "+path(changed)));
		assertEquals("Files "+pa+" and "+path(same)+" are identical\n", run("diff -s "+pa+" "+path(same)));
		File upper = write(dir, "upper", "1\nTWO\n3\n");
		File lower = write(dir, "lower", "1\ntwo\n3\n");
		assertEquals("", run("diff -i "+path(upper)+" "+path(lower)));
		File spaced = write(dir, "spaced", "a  b\n");
		File tight = write(dir, "tight", "a b\n");
		assertEquals("", run("diff -b "+path(spaced)+" "+path(tight)));
		assertEquals("", run("diff -w "+path(spaced)+" "+path(write(dir, "nospace", "ab\n"))));
		run("diff "+pa+" "+path(new File(dir, "nope")));
		assertEquals(2, lastStatus);
	}

	@Test
	public void testDiffMultipleChanges(@TempDir File dir) throws IOException {
		File a = write(dir, "a", "a\nb\nc\nd\ne\nf\ng\n");
		File b = write(dir, "b", "a\nB\nc\nd\ne\nF\nF2\ng\n");
		assertEquals("2c2\n< b\n---\n> B\n6c6,7\n< f\n---\n> F\n> F2\n", run("diff "+path(a)+" "+path(b)));
	}

	@Test
	public void testDiffUnified(@TempDir File dir) throws IOException {
		File a = write(dir, "a", "1\n2\n3\n");
		File b = write(dir, "b", "1\nX\n3\n");
		String out = run("diff -u "+path(a)+" "+path(b));
		assertTrue(out.startsWith("--- "+path(a)+"\t"), out);
		assertTrue(out.contains("\n+++ "+path(b)+"\t"), out);
		assertEquals("@@ -1,3 +1,3 @@\n 1\n-2\n+X\n 3\n", out.substring(out.indexOf("@@")));
		// two changes far apart are two hunks
		StringBuilder big = new StringBuilder(), big2 = new StringBuilder();
		for (int i = 1; i <= 20; i++) {
			big.append(i).append('\n');
			big2.append(i == 2 || i == 19 ? "x"+i : String.valueOf(i)).append('\n');
		}
		String two = run("diff -u -U 1 "+path(write(dir, "big", big.toString()))+" "+path(write(dir, "big2", big2.toString())));
		assertEquals(2, two.split("@@ -").length-1, two);
	}

	@Test
	public void testDiffDirectories(@TempDir File dir) throws IOException {
		File d1 = new File(dir, "d1"), d2 = new File(dir, "d2");
		write(d1, "same.txt", "x\n");
		write(d2, "same.txt", "x\n");
		write(d1, "only1.txt", "x\n");
		write(d2, "only2.txt", "x\n");
		write(d1, "diff.txt", "a\n");
		write(d2, "diff.txt", "b\n");
		String out = run("diff "+path(d1)+" "+path(d2));
		assertEquals(1, lastStatus);
		assertTrue(out.contains("Only in "+path(d1)+": only1.txt\n"), out);
		assertTrue(out.contains("Only in "+path(d2)+": only2.txt\n"), out);
		assertTrue(out.contains("diff "+path(d1)+"/diff.txt "+path(d2)+"/diff.txt\n1c1\n< a\n---\n> b\n"), out);
		assertFalse(out.contains("same.txt"), out);
	}

	// ---------------------------------------------------------------- paths and files

	@Test
	public void testBasenameDirname() throws IOException {
		assertEquals("sort\n", run("basename /usr/bin/sort"));
		assertEquals("b\n", run("basename a/b.txt .txt"));
		assertEquals("a\nb\n", run("basename -a x/a y/b"));
		assertEquals("a\nb\n", run("basename -s .c x/a.c y/b.c"));
		assertEquals("dir\n", run("basename /a/dir/"));
		assertEquals("/\n", run("basename /"));
		assertEquals("/usr/bin\n", run("dirname /usr/bin/sort"));
		assertEquals(".\n", run("dirname sort"));
		assertEquals("/a\n", run("dirname /a/b/"));
		assertEquals("/\n", run("dirname /"));
		assertEquals("/\n", run("dirname /file"));
		assertEquals("a\n.\n", run("dirname a/b c"));
		run("basename");
		assertEquals(1, lastStatus);
	}

	@Test
	public void testRealpathReadlink(@TempDir File dir) throws IOException {
		File f = write(dir, "sub/f.txt", "x");
		String canon = f.getCanonicalPath().replace(File.separatorChar, '/');
		assertEquals(canon+"\n", run("realpath "+path(dir)+"/sub/../sub/f.txt"));
		assertEquals(canon+"\n", run("readlink -f "+path(dir)+"/sub/./f.txt"));
		run("realpath -e "+path(dir)+"/nope");
		assertEquals(1, lastStatus);
		assertTrue(run("realpath -m "+path(dir)+"/nope/x").endsWith("/nope/x\n"));
		assertEquals("", run("readlink "+path(f)));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testStat(@TempDir File dir) throws IOException {
		File f = write(dir, "five.txt", "12345");
		assertEquals("5 "+path(f)+"\n", run("stat -c '%s %n' "+path(f)));
		assertEquals("regular file\n", run("stat -c %F "+path(f)));
		assertEquals("directory\n", run("stat -c %F "+path(dir)));
		assertEquals(String.valueOf(f.lastModified()/1000)+"\n", run("stat -c %Y "+path(f)));
		String full = run("stat "+path(f));
		assertTrue(full.contains("Size: 5"), full);
		run("stat "+path(new File(dir, "nope")));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testDu(@TempDir File dir) throws IOException {
		write(dir, "top.bin", "x".repeat(2048));
		write(dir, "sub/b.bin", "y");
		String d = path(dir);
		assertEquals("1\t"+d+"/sub\n3\t"+d+"\n", run("du "+d));
		assertEquals("1\t"+d+"/sub\n2049\t"+d+"\n", run("du -b "+d));
		assertEquals("2049\t"+d+"\n", run("du -sb "+d));
		assertEquals("1\t"+d+"/sub/b.bin\n1\t"+d+"/sub\n2\t"+d+"/top.bin\n3\t"+d+"\n", run("du -a "+d));
		assertEquals("1\t"+d+"/sub\n3\t"+d+"\n3\ttotal\n", run("du -c "+d));
		assertEquals("2.1K\t"+d+"\n", run("du -sh "+d));
		assertEquals("3\t"+d+"\n", run("du -d 0 "+d));
		run("du "+d+"/nope");
		assertEquals(1, lastStatus);
	}

	@Test
	public void testDf(@TempDir File dir) throws IOException {
		String out = run("df "+path(dir));
		assertEquals(0, lastStatus);
		assertTrue(out.startsWith("Filesystem"), out);
		assertEquals(2, out.trim().split("\n").length, out);
		assertTrue(run("df -h "+path(dir)).contains("Size"));
	}

	@Test
	public void testFile(@TempDir File dir) throws IOException {
		File text = write(dir, "t.txt", "hello\n");
		File script = write(dir, "s.sh", "#!/bin/bash\necho hi\n");
		File empty = write(dir, "e", "");
		File png = new File(dir, "p.png");
		Files.write(png.toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0});
		File json = write(dir, "j.json", "{\"a\": 1}\n");
		assertEquals(path(text)+": ASCII text\n", run("file "+path(text)));
		assertEquals("ASCII text\n", run("file -b "+path(text)));
		assertTrue(run("file -b "+path(script)).startsWith("bash script"));
		assertEquals("empty\n", run("file -b "+path(empty)));
		assertEquals("PNG image data\n", run("file -b "+path(png)));
		assertEquals("directory\n", run("file -b "+path(dir)));
		assertEquals("image/png\n", run("file -bi "+path(png)));
		assertTrue(run("file -b "+path(json)).startsWith("JSON"));
		assertEquals("text/plain; charset=us-ascii\n", run("file -bi "+path(text)));
		assertTrue(run("file -b "+path(write(dir, "crlf", "a\r\nb\r\n"))).contains("CRLF"));
		run("file "+path(new File(dir, "nope")));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testSplit(@TempDir File dir) throws IOException {
		File in = write(dir, "in.txt", "1\n2\n3\n4\n5\n");
		String prefix = path(dir)+"/part-";
		run("split -l 2 "+path(in)+" "+prefix);
		assertEquals("1\n2\n", read(new File(dir, "part-aa")));
		assertEquals("3\n4\n", read(new File(dir, "part-ab")));
		assertEquals("5\n", read(new File(dir, "part-ac")));
		assertFalse(new File(dir, "part-ad").exists());
		run("split -b 3 -d "+path(in)+" "+path(dir)+"/b");
		assertEquals("1\n2", read(new File(dir, "b00")));
		assertEquals("\n3\n", read(new File(dir, "b01")));
		run("split -l 1 -a 3 --additional-suffix=.txt "+path(in)+" "+path(dir)+"/c");
		assertEquals("1\n", read(new File(dir, "caaa.txt")));
		run("split -l 0 "+path(in));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testTruncate(@TempDir File dir) throws IOException {
		File f = write(dir, "f", "abcdef");
		run("truncate -s 3 "+path(f));
		assertEquals("abc", read(f));
		run("truncate -s +2 "+path(f));
		assertArrayEquals(new byte[] {'a', 'b', 'c', 0, 0}, Files.readAllBytes(f.toPath()));
		run("truncate -s -4 "+path(f));
		assertEquals(1, f.length());
		File created = new File(dir, "created");
		run("truncate -s 10 "+path(created));
		assertEquals(10, created.length());
		run("truncate -c -s 10 "+path(new File(dir, "never")));
		assertFalse(new File(dir, "never").exists());
		run("truncate "+path(f));
		assertEquals(1, lastStatus);
		run("truncate -s 1K "+path(f));
		assertEquals(1024, f.length());
	}

	@Test
	public void testWhich() throws IOException {
		run("which no-such-program-xyz");
		assertEquals(1, lastStatus);
		if( File.separatorChar == '\\' ) {
			String out = run("which cmd");
			assertEquals(0, lastStatus);
			assertTrue(out.toLowerCase().trim().endsWith("cmd.exe"), out);
		} else {
			assertTrue(run("which sh").trim().endsWith("/sh"));
		}
	}

	@Test
	public void testChmod(@TempDir File dir) throws IOException {
		File f = write(dir, "f", "x");
		run("chmod 444 "+path(f));
		assertEquals(0, lastStatus, lastErr);
		assertFalse(f.canWrite());
		run("chmod u+w "+path(f));
		assertTrue(f.canWrite());
		run("chmod -w "+path(f));
		assertFalse(f.canWrite());
		run("chmod 644 "+path(f));
		assertTrue(f.canWrite());
		run("chmod 644 "+path(new File(dir, "nope")));
		assertEquals(1, lastStatus);
		run("chmod 999 "+path(f));
		assertEquals(1, lastStatus);
		assertTrue(run("chmod -v 644 "+path(f)).contains("0644"));
	}

	// ---------------------------------------------------------------- system

	@Test
	public void testEnv() throws IOException {
		assertTrue(run("env").contains("="));
		assertTrue(run("env FOO_TEST=bar").contains("FOO_TEST=bar\n"));
		assertEquals("hi\n", run("env FOO_TEST=bar echo hi"));
		assertEquals("", run("printenv NO_SUCH_VARIABLE_XYZ"));
		assertEquals(1, lastStatus);
		assertTrue(run("printenv").contains("="));
		assertEquals("a b\n", run("env X_Y=1 echo 'a b'"));
	}

	@Test
	public void testDate() throws IOException {
		assertEquals("1970-01-01\n", run("date -u -d @0 +%Y-%m-%d"));
		assertEquals("2020-02-29 13:04:05\n", run("date -u -d '2020-02-29 13:04:05' '+%F %T'"));
		assertEquals("Saturday February 060 6\n", run("date -u -d 2020-02-29 '+%A %B %j %u'"));
		assertEquals("86400\n", run("date -u -d @86400 +%s"));
		assertEquals("2020-02-29\n", run("date -u -d 2020-02-29 -I"));
		assertEquals("29/02/20 PM\n", run("date -u -d '2020-02-29 15:00' '+%d/%m/%y %p'"));
		assertEquals(" 5|5|05\n", run("date -u -d 2020-03-05 '+%e|%-d|%d'"));
		assertEquals("2020-02-29\n", run("date -u -d '2020-02-29 12:00' +%F"));
		run("date -d garbage");
		assertEquals(1, lastStatus);
		String now = run("date +%Y").trim();
		assertEquals(4, now.length());
		// Windows's own form
		assertEquals("Sat 02/29/2020\n", run("date -u -d 2020-02-29 /T"));
		assertTrue(run("date /T").matches("[A-Z][a-z]{2} \\d\\d/\\d\\d/\\d{4}\\n"), run("date /T"));
		// (what TestEval runs where there is no Unix date on the PATH)
		assertTrue(run("command=\"echo $(date /T)\"\neval \"$command\"").matches("[A-Z][a-z]{2} \\d\\d/\\d\\d/\\d{4}\\n"));
		assertEquals("", lastErr);
		assertEquals("UTC\n", run("date -u +%Z"));
		// the default format ends with a zone name of 3 or 4 letters (EST, not ET) and the year
		assertTrue(run("date").matches("[A-Z][a-z]{2} [A-Z][a-z]{2} [ \\d]\\d \\d\\d:\\d\\d:\\d\\d [A-Z]{3,5} \\d{4}\\n"), run("date"));
		assertEquals("+0000\n", run("date -u +%z"));
		assertEquals("+00:00\n", run("date -u +%:z"));
		assertEquals("2020-02-29T13:04:05+00:00\n", run("date -u -d '2020-02-29 13:04:05' -Iseconds"));
	}

	@Test
	public void testDateRelative() throws IOException {
		String today = run("date +%F").trim();
		String yesterday = run("date -d yesterday +%F").trim();
		assertEquals(java.time.LocalDate.parse(today).minusDays(1).toString(), yesterday);
		String ago = run("date -d '3 days ago' +%F").trim();
		assertEquals(java.time.LocalDate.parse(today).minusDays(3).toString(), ago);
	}

	@Test
	public void testSeq() throws IOException {
		assertEquals("1\n2\n3\n", run("seq 3"));
		assertEquals("2\n3\n4\n", run("seq 2 4"));
		assertEquals("1\n3\n5\n7\n", run("seq 1 2 7"));
		assertEquals("5\n3\n1\n", run("seq 5 -2 1"));
		assertEquals("1,2,3\n", run("seq -s, 3"));
		assertEquals("08\n09\n10\n", run("seq -w 8 10"));
		assertEquals("0.5\n1.0\n1.5\n2.0\n", run("seq 0.5 0.5 2"));
		assertEquals("", run("seq 0"));
		assertEquals("-2\n-1\n0\n", run("seq -2 0"));
		assertEquals("001\n002\n", run("seq -f %03.0f 1 2"));
		run("seq 1 0 5");
		assertEquals(1, lastStatus);
		run("seq a");
		assertEquals(1, lastStatus);
	}

	@Test
	@Timeout(20)
	public void testYes() throws IOException {
		assertEquals("y\ny\ny\n", run("yes | head -n 3"));
		assertEquals("no way\nno way\n", run("yes no way | head -n 2"));
	}

	@Test
	public void testWhoamiHostnameUname() throws IOException {
		assertEquals(System.getProperty("user.name")+"\n", run("whoami"));
		assertFalse(run("hostname").trim().isEmpty());
		assertEquals(0, lastStatus);
		assertEquals(System.getProperty("os.name")+"\n", run("uname"));
		assertTrue(run("uname -m").trim().length() > 0);
		assertTrue(run("uname -a").contains(System.getProperty("os.name")));
	}

	// ---------------------------------------------------------------- checksums and encodings

	@Test
	public void testChecksums(@TempDir File dir) throws IOException {
		assertEquals("b1946ac92492d2347c6235b4d2611184  -\n", run("md5sum", "hello\n"));
		assertEquals("f572d396fae9206628714fb2ce00f72e94f2258f  -\n", run("sha1sum", "hello\n"));
		assertEquals("5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03  -\n", run("sha256sum", "hello\n"));
		assertEquals(128+3, run("sha512sum", "hello\n").length()-1);
		File f = write(dir, "h.txt", "hello\n");
		assertEquals("b1946ac92492d2347c6235b4d2611184  "+path(f)+"\n", run("md5sum "+path(f)));
		File sums = write(dir, "sums", "b1946ac92492d2347c6235b4d2611184  "+path(f)+"\n");
		assertEquals(path(f)+": OK\n", run("md5sum -c "+path(sums)));
		assertEquals(0, lastStatus);
		assertEquals("", run("md5sum -c --quiet "+path(sums)));
		write(dir, "h.txt", "changed\n");
		assertEquals(path(f)+": FAILED\n", run("md5sum -c "+path(sums)));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("did NOT match"), lastErr);
		run("md5sum "+path(new File(dir, "nope")));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testBase64() throws IOException {
		assertEquals("aGVsbG8=\n", run("base64", "hello"));
		assertEquals("hello", run("base64 -d", "aGVsbG8=\n"));
		assertEquals("aGVs\nbG8g\nd29y\nbGQ=\n", run("base64 -w 4", "hello world"));
		assertEquals("aGVsbG8gd29ybGQ=\n", run("base64 -w 0", "hello world"));
		assertEquals("hello world", run("base64 | base64 -d", "hello world"));
		run("base64 -d", "!!!notbase64");
		assertEquals(1, lastStatus);
		byte [] data = new byte[256];
		for (int i = 0; i < 256; i++) {
			data[i] = (byte) i;
		}
		byte [] enc = runBytes("base64", data);
		assertArrayEquals(data, runBytes("base64 -d", enc));
	}

	@Test
	public void testXxd() throws IOException {
		String line = "00000000: 4865 6c6c 6f0a"+" ".repeat(25)+"  Hello.\n";
		assertEquals(line, run("xxd", "Hello\n"));
		assertEquals("48656c6c6f0a\n", run("xxd -p", "Hello\n"));
		assertEquals("Hello\n", run("xxd | xxd -r", "Hello\n"));
		assertEquals("Hello\n", run("xxd -p | xxd -r -p", "Hello\n"));
		assertEquals("00000000: 48 65 6c 6c  Hell\n", run("xxd -g 1 -l 4 -c 4", "Hello\n"));
		assertEquals("00000004: 6f0a  o.\n", run("xxd -s 4 -c 2", "Hello\n"));
		assertEquals("00000000: 4865 6C6C 6F0A"+" ".repeat(25)+"  Hello.\n", run("xxd -u", "Hello\n"));
		byte [] data = new byte[300];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) (i*7);
		}
		assertArrayEquals(data, runBytes("xxd -r", runBytes("xxd", data)));
	}

	@Test
	public void testHexdump() throws IOException {
		String hex = "48 65 6c 6c 6f 0a ";
		assertEquals("00000000  "+hex+" ".repeat(10*3+1)+" |Hello.|\n00000006\n", run("hexdump -C", "Hello\n"));
		assertEquals("0000000 4241\n0000002\n", run("hexdump", "AB"));
		String same = "0123456789abcdef".repeat(4);
		String out = run("hexdump -C", same);
		assertTrue(out.contains("\n*\n"), out);
		assertFalse(run("hexdump -C -v", same).contains("*"));
		assertEquals("00000002  6c 6c "+" ".repeat(14*3+1)+" |ll|\n00000004\n", run("hexdump -C -s 2 -n 2", "Hello\n"));
	}

	@Test
	public void testOd() throws IOException {
		assertEquals("0000000   H   e   l   l   o  \\n\n0000006\n", run("od -c", "Hello\n"));
		assertEquals(" 41 42\n", run("od -An -tx1", "AB"));
		assertEquals("0000000 41 42\n0000002\n", run("od -tx1", "AB"));
		assertEquals("  65  66\n", run("od -An -tu1", "AB"));
		assertEquals(" 101 102\n", run("od -An -to1", "AB"));
		assertEquals("0000000 041101\n0000002\n", run("od", "AB"));
		assertEquals("0000000 4241\n0000002\n", run("od -x", "AB"));
		assertEquals("000000 41 42\n000002\n", run("od -Ax -tx1", "AB"));
		assertEquals("0000001 42\n0000002\n", run("od -j 1 -tx1", "AB"));
		assertEquals("0000000 41\n0000001\n", run("od -N 1 -tx1", "AB"));
		assertEquals("   A   B\n", run("od -An -c", "AB"));
		// (a signed 2 byte value has room for -32768: the field is 6 wide)
		assertEquals("0000000  16961\n0000002\n", run("od -td2", "AB"));
		String many = "A".repeat(48);
		assertTrue(run("od -tx1", many).contains("\n*\n"));
		run("od -Az", "A");
		assertEquals(1, lastStatus);
	}

	// ---------------------------------------------------------------- archives

	@Test
	public void testGzip(@TempDir File dir) throws IOException {
		File f = write(dir, "a.txt", "hello hello hello\n");
		String p = path(f);
		run("gzip "+p);
		assertEquals(0, lastStatus, lastErr);
		assertFalse(f.exists());
		assertTrue(new File(dir, "a.txt.gz").exists());
		assertEquals("hello hello hello\n", run("zcat "+p+".gz"));
		run("gunzip "+p+".gz");
		assertEquals("hello hello hello\n", read(f));
		assertFalse(new File(dir, "a.txt.gz").exists());
		run("gzip -k "+p);
		assertTrue(f.exists() && new File(dir, "a.txt.gz").exists());
		run("gzip "+p);
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("already exists"), lastErr);
		run("gzip -kf "+p);
		assertEquals(0, lastStatus);
		assertEquals("hello hello hello\n", run("gzip -c "+p+" | gunzip"));
		assertEquals("piped\n", run("gzip | gzip -d", "piped\n"));
		run("gunzip "+path(write(dir, "plain", "x")));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("unknown suffix"), lastErr);
		assertEquals("hello hello hello\n", run("gzip -dc "+p+".gz"));
		run("gzip "+path(new File(dir, "nope")));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testZipUnzip(@TempDir File dir) throws IOException {
		write(dir, "src/a.txt", "alpha\n");
		write(dir, "src/sub/b.txt", "beta beta beta beta\n");
		String zip = path(dir)+"/out.zip";
		String listing = run("zip -r "+zip+" "+path(dir)+"/src");
		assertEquals(0, lastStatus, lastErr);
		assertTrue(listing.contains("adding:"), listing);
		assertTrue(new File(zip).exists());
		String l = run("unzip -l "+zip);
		assertTrue(l.contains("a.txt") && l.contains("b.txt") && l.contains("4 files"), l);
		File out = new File(dir, "out");
		run("unzip -q -d "+path(out)+" "+zip);
		assertEquals(0, lastStatus, lastErr);
		String rel = path(dir).replaceFirst("^[A-Za-z]:", "").replaceFirst("^/", "");
		assertEquals("alpha\n", read(new File(out, rel+"/src/a.txt")));
		assertEquals("beta beta beta beta\n", read(new File(out, rel+"/src/sub/b.txt")));
		assertEquals("alpha\n", run("unzip -p "+zip+" '*a.txt'"));
		// not overwritten without -o
		run("unzip -q -d "+path(out)+" "+zip);
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("exists"), lastErr);
		run("unzip -q -o -d "+path(out)+" "+zip);
		assertEquals(0, lastStatus);
		run("unzip "+path(dir)+"/nope.zip");
		assertEquals(9, lastStatus);
	}

	@Test
	public void testZipOptions(@TempDir File dir) throws IOException {
		write(dir, "x/y.txt", "yy\n");
		String zip = path(dir)+"/j.zip";
		run("zip -j -q "+zip+" "+path(dir)+"/x/y.txt");
		assertEquals(0, lastStatus, lastErr);
		assertTrue(run("unzip -l "+zip).contains("y.txt"));
		assertFalse(run("unzip -l "+zip).contains("/y.txt"));
		// adding to the archive keeps what is in it
		write(dir, "z.txt", "zz\n");
		run("zip -q "+zip+" "+path(dir)+"/z.txt");
		String l = run("unzip -l "+zip);
		assertTrue(l.contains("y.txt") && l.contains("z.txt"), l);
		run("zip");
		assertEquals(15, lastStatus);
	}

	@Test
	public void testTar(@TempDir File dir) throws IOException {
		write(dir, "pkg/a.txt", "alpha\n");
		write(dir, "pkg/sub/b.txt", "beta\n");
		String tar = path(dir)+"/pkg.tar";
		run("tar -cf "+tar+" -C "+path(dir)+" pkg");
		assertEquals(0, lastStatus, lastErr);
		assertEquals("pkg/\npkg/a.txt\npkg/sub/\npkg/sub/b.txt\n", run("tar -tf "+tar));
		assertEquals(0, new File(tar).length() % 512);
		File out = new File(dir, "out");
		out.mkdirs();
		run("tar -xf "+tar+" -C "+path(out));
		assertEquals(0, lastStatus, lastErr);
		assertEquals("alpha\n", read(new File(out, "pkg/a.txt")));
		assertEquals("beta\n", read(new File(out, "pkg/sub/b.txt")));
		assertEquals("pkg/a.txt\n", run("tar -tf "+tar+" pkg/a.txt"));
		assertEquals("alpha\n", run("tar -xOf "+tar+" pkg/a.txt"));
		File stripped = new File(dir, "stripped");
		stripped.mkdirs();
		run("tar -xf "+tar+" -C "+path(stripped)+" --strip-components=1");
		assertEquals("alpha\n", read(new File(stripped, "a.txt")));
		assertEquals("pkg/\npkg/a.txt\npkg/sub/\npkg/sub/b.txt\n", run("tar -cvf "+path(dir)+"/v.tar -C "+path(dir)+" pkg"));
	}

	@Test
	public void testTarGzipAndOldStyle(@TempDir File dir) throws IOException {
		write(dir, "pkg/a.txt", "alpha\n");
		String tgz = path(dir)+"/pkg.tgz";
		run("tar czf "+tgz+" -C "+path(dir)+" pkg");
		assertEquals(0, lastStatus, lastErr);
		byte [] head = new byte[2];
		try (java.io.InputStream in = Files.newInputStream(new File(tgz).toPath())) {
			in.read(head);
		}
		assertEquals(0x1f, head[0] & 0xff);
		assertEquals(0x8b, head[1] & 0xff);
		assertEquals("pkg/\npkg/a.txt\n", run("tar tzf "+tgz));
		assertEquals("pkg/\npkg/a.txt\n", run("tar -tf "+tgz));
		File out = new File(dir, "out");
		out.mkdirs();
		run("tar xzf "+tgz+" -C "+path(out));
		assertEquals("alpha\n", read(new File(out, "pkg/a.txt")));
		run("tar");
		assertEquals(2, lastStatus);
		run("tar -tf "+path(dir)+"/nope.tar");
		assertEquals(2, lastStatus);
	}

	@Test
	public void testTarLongNames(@TempDir File dir) throws IOException {
		String name = "a".repeat(60)+"/"+"b".repeat(60)+"/file.txt";
		write(dir, "root/"+name, "deep\n");
		String tar = path(dir)+"/long.tar";
		run("tar -cf "+tar+" -C "+path(dir)+" root");
		assertEquals(0, lastStatus, lastErr);
		assertTrue(run("tar -tf "+tar).contains("root/"+name+"\n"));
		File out = new File(dir, "out");
		out.mkdirs();
		run("tar -xf "+tar+" -C "+path(out));
		assertEquals("deep\n", read(new File(out, "root/"+name)));
		// a name that cannot be split is a GNU long name entry
		String single = "n".repeat(150);
		write(dir, "r2/"+single, "one\n");
		run("tar -cf "+path(dir)+"/l2.tar -C "+path(dir)+" r2");
		assertTrue(run("tar -tf "+path(dir)+"/l2.tar").contains("r2/"+single+"\n"));
	}

	// ---------------------------------------------------------------- curl and wget

	@Test
	public void testCurl(@TempDir File dir) throws IOException {
		assertEquals("hi there\n", run("curl -s "+base+"/hello"));
		assertEquals(0, lastStatus);
		// no -L: the redirect itself is the answer
		assertEquals("moved", run("curl -s "+base+"/redirect"));
		assertEquals("hi there\n", run("curl -sL "+base+"/redirect"));
		assertEquals("nope", run("curl -s "+base+"/missing"));
		assertEquals(0, lastStatus);
		assertEquals("", run("curl -sf "+base+"/missing"));
		assertEquals(22, lastStatus);
		assertEquals("POST|a=1&b=2|null\n", run("curl -s -d a=1 -d b=2 "+base+"/echo"));
		assertEquals("PUT|x|yes\n", run("curl -s -X PUT -d x -H 'X-Test: yes' "+base+"/echo"));
		assertEquals("DELETE||null\n", run("curl -s -X DELETE "+base+"/echo"));
		File body = write(dir, "body", "from file\n");
		assertEquals("POST|from file|null\n", run("curl -s -d @"+path(body)+" "+base+"/echo"));
		assertEquals("PUT|from file\n|null\n", run("curl -s -T "+path(body)+" "+base+"/echo"));
		File out = new File(dir, "out.txt");
		assertEquals("", run("curl -s -o "+path(out)+" "+base+"/hello"));
		assertEquals("hi there\n", read(out));
		// -O saves in the current directory: go to the temp directory for it, and back
		String here = run("pwd").trim();
		run("cd "+path(dir)+" && curl -s -O "+base+"/dir/list.txt");
		run("cd "+here);
		assertEquals("listing\n", read(new File(dir, "list.txt")));
		run("curl -s -I "+base+"/hello");
		assertEquals(0, lastStatus);
		String withHeaders = run("curl -si "+base+"/hello");
		assertTrue(withHeaders.startsWith("HTTP/1.1 200"), withHeaders);
		assertTrue(withHeaders.endsWith("\n\nhi there\n") || withHeaders.endsWith("hi there\n"), withHeaders);
		String head = run("curl -sI "+base+"/hello");
		assertTrue(head.startsWith("HTTP/1.1 200"), head);
		assertFalse(head.contains("hi there"));
		assertEquals("hi there\n", run("curl -s "+base.replace("http://", "")+"/hello"));
	}

	@Test
	public void testCurlErrors() throws IOException {
		run("curl");
		assertEquals(2, lastStatus);
		run("curl -s http://127.0.0.1:1/");
		assertEquals(7, lastStatus);
		run("curl http://127.0.0.1:1/");
		assertTrue(lastErr.startsWith("curl: (7)"), lastErr);
		run("curl -s -k http://127.0.0.1/");
		assertEquals(2, lastStatus);
		run("curl -s nosuchscheme://x/y");
		assertEquals(1, lastStatus);
		run("curl -sS http://127.0.0.1:1/");
		assertTrue(lastErr.startsWith("curl: (7)"), lastErr);
		run("curl -s http://no-such-host.invalid/");
		assertEquals(6, lastStatus);
	}

	@Test
	public void testCurlFileUrl(@TempDir File dir) throws IOException {
		File f = write(dir, "f.txt", "local\n");
		assertEquals("local\n", run("curl -s "+f.toURI().toString()));
	}

	@Test
	public void testWget(@TempDir File dir) throws IOException {
		assertEquals("hi there\n", run("wget -q -O - "+base+"/hello"));
		File out = new File(dir, "got.txt");
		run("wget -q -O "+path(out)+" "+base+"/redirect");
		assertEquals(0, lastStatus, lastErr);
		assertEquals("hi there\n", read(out));
		run("wget -O "+path(out)+" "+base+"/hello");
		assertTrue(lastErr.contains("saved"), lastErr);
		run("wget -q -O - "+base+"/missing");
		assertEquals(8, lastStatus);
		assertTrue(lastErr.contains("404"), lastErr);
		run("wget");
		assertEquals(1, lastStatus);
		run("wget -q http://127.0.0.1:1/");
		assertEquals(4, lastStatus);
		assertEquals("POST|k=v|null\n", run("wget -q -O - --post-data=k=v "+base+"/echo"));
		assertEquals("GET||null\n", run("wget -q -O - "+base+"/echo"));
	}

	// ---------------------------------------------------------------- xargs

	@Test
	public void testXargs() throws IOException {
		assertEquals("a b c\n", run("xargs", "a b\nc\n"));
		assertEquals("a b\nc\n", run("xargs -n 2", "a b c\n"));
		assertEquals("a\nb\nc\n", run("xargs -n 1 echo", "a b c\n"));
		assertEquals("xay\nxby\n", run("xargs -I {} echo x{}y", "a\nb\n"));
		assertEquals("a b c\n", run("xargs -0 echo", "a b\0c\0"));
		assertEquals("a b\nc\n", run("xargs -n 1 echo", "\"a b\" c\n"));
		assertEquals("", run("xargs -r echo", ""));
		assertEquals("\n", run("xargs echo", ""));
		assertEquals("a b\n", run("xargs -d , echo", "a,b"));
		assertEquals("a b\nc\n", run("xargs -L 1 echo", "a b\nc\n"));
		assertEquals("one -n\n", run("xargs echo one", "-n\n"));
		run("xargs -t echo hi", "a\n");
		assertTrue(lastErr.contains("echo hi a"), lastErr);
		run("xargs false", "x\n");
		assertEquals(123, lastStatus);
		run("xargs echo", "'unterminated\n");
		assertEquals(1, lastStatus);
	}

	// ---------------------------------------------------------------- together

	/** the registry makes a command with its constructor without arguments: each name has to come out as its own command */
	@Test
	public void testEveryCommandIsABuiltin() throws IOException {
		String [] names = {"cat", "grep", "sed", "head", "tail", "sort", "uniq", "cut", "tr", "tee",
				"tac", "rev", "nl", "paste", "fold", "comm", "join", "cmp", "diff", "basename", "dirname", "realpath", "readlink",
				"stat", "du", "df", "file", "split", "truncate", "which", "chmod", "env", "printenv", "date", "seq", "yes", "whoami",
				"hostname", "uname", "md5sum", "sha1sum", "sha256sum", "sha512sum", "base64", "xxd", "hexdump", "od", "gzip", "gunzip",
				"zcat", "zip", "unzip", "tar", "curl", "wget", "xargs"};
		for (String n : names) {
			assertEquals(n+" is a shell builtin\n", run("type "+n), n);
			// and it is that command (its help starts with its own name)
			String help = run("help "+n);
			assertTrue(help.contains(n), n+": "+help);
		}
	}

	@Test
	public void testPipelines() throws IOException {
		assertEquals("3\n2\n1\n", run("seq 3 | tac"));
		assertEquals("olleh\n", run("echo hello | rev"));
		assertEquals("aGk=\n", run("echo -n hi | base64"));
		assertEquals("1:1\n2:2\n3:3\n", run("seq 3 | nl -w 1 -s :"));
		assertEquals("      1 x\n      2 y\n", run("printf 'x\\ny\\n' | nl -w 7 -s ' '"));
		assertEquals("3\n", run("seq 3 | tail -n 1"));
		assertEquals("5d41402abc4b2a76b9719d911017c592  -\n", run("echo -n hello | md5sum"));
	}
}
