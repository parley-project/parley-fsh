package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

public class TestCat {

	static Console console;
	String lastErr;
	int lastStatus;

	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(FileSourceFactory.fileProxyFactory);
		console = new Console();
	}

	/** run a command with the given standard input, return the raw output bytes */
	byte [] run(String command, byte [] stdin) throws IOException {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();
		console.setStdOut(new PrintStream(bao, true));
		console.setStdErr(new PrintStream(bae, true));
		console.setStdIn(new ByteArrayInputStream(stdin));
		lastStatus = console.executeScript(command);
		lastErr = new String(bae.toByteArray()).trim();
		return bao.toByteArray();
	}

	String text(String command, String stdin) throws IOException {
		return new String(run(command, stdin.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
	}

	static String path(File f) {
		return f.getAbsolutePath().replace(File.separatorChar, '/');
	}

	@Test
	public void testStdin() throws IOException {
		assertEquals("a\nb", text("cat", "a\nb"));
		assertEquals("a\nb\n", text("cat -", "a\nb\n"));
	}

	@Test
	public void testBinaryPassesThrough() throws IOException {
		byte [] data = new byte[256];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) i;
		}
		assertArrayEquals(data, run("cat", data));
	}

	@Test
	public void testNumber() throws IOException {
		assertEquals("     1\ta\n     2\t\n     3\tb\n", text("cat -n", "a\n\nb\n"));
		assertEquals("     1\ta\n     2\t\n     3\tb\n", text("cat --number", "a\n\nb\n"));
	}

	@Test
	public void testNumberNonBlankOverridesNumber() throws IOException {
		assertEquals("     1\ta\n\n     2\tb\n", text("cat -nb", "a\n\nb\n"));
		assertEquals("     1\ta\n\n     2\tb\n", text("cat -b -n", "a\n\nb\n"));
	}

	@Test
	public void testSqueeze() throws IOException {
		assertEquals("a\n\nb\n", text("cat -s", "a\n\n\n\nb\n"));
		assertEquals("\na\n", text("cat -s", "\n\n\na\n"));
	}

	@Test
	public void testShowEndsAndTabs() throws IOException {
		assertEquals("a\tb$\n$\n", text("cat -E", "a\tb\n\n"));
		assertEquals("a^Ib\n", text("cat -T", "a\tb\n"));
		assertEquals("a^Ib$\n", text("cat -A", "a\tb\n"));
	}

	@Test
	public void testNoTrailingNewline() throws IOException {
		assertEquals("     1\ta\n     2\tb", text("cat -n", "a\nb"));
		assertEquals("a$\nb", text("cat -E", "a\nb"));
	}

	@Test
	public void testNonPrinting() throws IOException {
		byte [] in = {1, 'a', 127, (byte) 200, (byte) 0x81, '\t', '\n'};
		String out = new String(run("cat -v", in), StandardCharsets.ISO_8859_1);
		assertEquals("^Aa^?M-HM-^A\t\n", out);
		out = new String(run("cat -e", in), StandardCharsets.ISO_8859_1);
		assertEquals("^Aa^?M-HM-^A\t$\n", out);
		out = new String(run("cat -t", in), StandardCharsets.ISO_8859_1);
		assertEquals("^Aa^?M-HM-^A^I\n", out);
	}

	@Test
	public void testFilesAndNumbersContinue(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		File b = new File(dir, "b.txt");
		Files.write(a.toPath(), "one\ntwo\n".getBytes());
		Files.write(b.toPath(), "three\n".getBytes());
		assertEquals("one\ntwo\nthree\n", text("cat "+path(a)+" "+path(b), ""));
		assertEquals("     1\tone\n     2\ttwo\n     3\tthree\n", text("cat -n "+path(a)+" "+path(b), ""));
		// - is standard input, in its place
		assertEquals("one\ntwo\nIN\nthree\n", text("cat "+path(a)+" - "+path(b), "IN\n"));
	}

	@Test
	public void testMissingFileAndDirectory(@TempDir File dir) throws IOException {
		File a = new File(dir, "a.txt");
		Files.write(a.toPath(), "one\n".getBytes());
		String missing = path(new File(dir, "nope.txt"));
		assertEquals("one\n", text("cat "+missing+" "+path(a), ""));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("nope.txt: No such file or directory"), lastErr);

		text("cat "+path(dir), "");
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("Is a directory"), lastErr);
	}

	@Test
	public void testBadOption() throws IOException {
		assertEquals("", text("cat -z", "a\n"));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("invalid option -- 'z'"), lastErr);
	}
}
