package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ls -S, -h, -l and -R; wc's counts; find's ( ). */
public class TestListFixes extends AbstractConsoleTest {

	@TempDir
	Path dir;

	@BeforeAll
	public static void beforeAll() throws IOException {
		setup("LsTestFiles");
	}

	private ExecuteResult run(String cmd) throws IOException {
		return executeCommand("cd "+dir+"; "+cmd, "");
	}

	private void bytes(String name, byte[] data) throws IOException {
		Path p = dir.resolve(name);
		Files.createDirectories(p.getParent());
		Files.write(p, data);
	}

	@Test
	public void lsSizes() throws IOException {
		bytes("small", new byte[3]);
		bytes("big", new byte[3000]);
		bytes("mid", new byte[100]);
		assertEquals("big\nmid\nsmall\n", run("ls -S").getStdOut(), "largest first");
		String line = run("ls -lh big").getStdOut();
		assertTrue(line.contains(" 3.0K "), line);
		line = run("ls -lh small").getStdOut();
		assertTrue(line.contains("    3 "), line);
	}

	@Test
	public void lsLongShowsTheTimeOrTheYear() throws IOException {
		bytes("new", new byte[1]);
		bytes("old", new byte[1]);
		Files.setLastModifiedTime(dir.resolve("old"),
				FileTime.from(LocalDateTime.of(2020, 1, 2, 3, 4).atZone(ZoneId.systemDefault()).toInstant()));
		String old = run("ls -l old").getStdOut();
		assertTrue(old.matches("(?s).* [A-Z][a-z]{2}  2  2020 old\n"), old);
		String now = run("ls -l new").getStdOut();
		assertTrue(now.matches("(?s).* [A-Z][a-z]{2} [ 0-9]\\d \\d\\d:\\d\\d new\n"), now);
	}

	@Test
	public void lsLongShowsTheOtherPermissions() throws IOException {
		assumePosix();
		bytes("f", new byte[1]);
		Files.setPosixFilePermissions(dir.resolve("f"), PosixFilePermissions.fromString("rwxr-x---"));
		String line = run("ls -l f").getStdOut();
		assertTrue(line.startsWith("-rwxr-x--- "), line);
	}

	private static void assumePosix() {
		org.junit.jupiter.api.Assumptions.assumeTrue(getOs() != OperatingSystem.Windows);
	}

	@Test
	public void lsRecursiveWithNoPath() throws IOException {
		bytes("a", new byte[1]);
		bytes("d/b", new byte[1]);
		bytes("d/e/c", new byte[1]);
		assertEquals("a\nd\n\nd:\nb\ne\n\nd/e:\nc\n", run("ls -R").getStdOut());
	}

	@Test
	public void wcCounts() throws IOException {
		bytes("crlf", "a\r\nb\r\n".getBytes("UTF-8"));
		assertEquals("       2       2       6 crlf", run("wc crlf").getStdOut().trim().replaceAll("^ *", "       "));
		bytes("utf", "café à x\n".getBytes("UTF-8"));
		assertEquals("9", run("wc -m < utf").getStdOut().trim(), "characters, not bytes");
		assertEquals("3", run("wc -w < utf").getStdOut().trim(), "an accented letter is part of a word");
		ExecuteResult res = run("wc -z crlf");
		assertEquals(2, res.exitCode);
		res = run("wc missing crlf");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdOut().contains("crlf"), "the other files are counted");
	}

	@Test
	public void findGroups() throws IOException {
		bytes("d/big", new byte[1]);
		bytes("d/small", new byte[1]);
		bytes("d/sub/x.txt", new byte[1]);
		assertEquals("d/big\nd/sub/x.txt\n", run("find d \\( -name big -o -name '*.txt' \\) -type f").getStdOut());
		assertEquals("./d/small\n", run("find . -name small").getStdOut());
		ExecuteResult res = run("find d \\( -name x");
		assertEquals(1, res.exitCode);
		assertEquals("find: missing )", res.getStdErr().trim());
	}
}
