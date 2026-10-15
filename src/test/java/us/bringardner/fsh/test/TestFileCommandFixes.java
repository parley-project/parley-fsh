package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** cp, mv and rm do what their help says; an unknown option is an error. */
public class TestFileCommandFixes extends AbstractConsoleTest {

	@TempDir
	Path dir;

	@BeforeAll
	public static void beforeAll() throws IOException {
		setup("CpTestFiles");
	}

	private ExecuteResult run(String cmd, String stdin) throws IOException {
		return executeCommand("cd "+dir+"; "+cmd, stdin);
	}

	private void write(String name, String text) throws IOException {
		Path p = dir.resolve(name);
		Files.createDirectories(p.getParent());
		Files.writeString(p, text, StandardCharsets.UTF_8);
	}

	private String read(String name) throws IOException {
		return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
	}

	@Test
	public void anUnknownOptionIsAnError() throws IOException {
		write("a", "x");
		ExecuteResult res = run("cp -X a b", "");
		assertEquals(2, res.exitCode);
		assertTrue(res.getStdErr().startsWith("cp: unknown option -X\nusage: cp "), res.getStdErr());
		assertFalse(Files.exists(dir.resolve("b")), "the system's cp didn't run");
	}

	@Test
	public void cpRecursiveMakesTheTarget() throws IOException {
		write("src/a", "1");
		write("src/sub/b", "2");
		ExecuteResult res = run("cp -r src new", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertEquals("2", read("new/sub/b"));
		res = run("cp -R src/ flat", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertEquals("1", read("flat/a"), "SOURCE/ copies what is in it");
		res = run("cp src other", "");
		assertEquals(1, res.exitCode);
		res = run("cp -R src src/sub/in", "");
		assertEquals(1, res.exitCode, "not into itself");
	}

	@Test
	public void cpOverwriteChoices() throws IOException {
		write("new", "new");
		write("old", "old");
		assertEquals(0, run("cp -n new old", "").exitCode);
		assertEquals("old", read("old"));
		assertEquals(0, run("cp -n -i new old", "n\n").exitCode, "-i is the last, so it asks");
		assertEquals("old", read("old"));
		assertEquals(0, run("cp -i new old", "y\n").exitCode);
		assertEquals("new", read("old"));
		ExecuteResult res = run("cp -v new copy", "");
		assertTrue(res.getStdOut().contains("new -> "), res.getStdOut());
		res = run("cp onlyone", "");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().startsWith("cp: missing destination file operand"), res.getStdErr());
	}

	@Test
	public void mvTheLastOfFinWins() throws IOException {
		write("a", "A");
		write("b", "B");
		ExecuteResult res = run("mv -i -f a b", "");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertEquals("A", read("b"), "-f came last: no question");
		write("c", "C");
		res = run("mv -n c b", "");
		assertEquals(1, res.exitCode, "-n: not overwritten");
		assertEquals("A", read("b"));
		Files.createDirectory(dir.resolve("d"));
		res = run("mv missing c d", "");
		assertEquals(1, res.exitCode);
		assertTrue(Files.exists(dir.resolve("d/c")), "a missing source doesn't stop the others");
	}

	@Test
	public void rmKeepsGoingAndIAsksOnce() throws IOException {
		write("one", "1");
		ExecuteResult res = run("rm missing one", "");
		assertEquals(1, res.exitCode);
		assertEquals("rm: missing: does not exist", res.getStdErr().trim());
		assertFalse(Files.exists(dir.resolve("one")), "the next file is still removed");

		write("tree/x/f", "f");
		res = run("rm -rI tree", "y\n");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertFalse(Files.exists(dir.resolve("tree")), "-I doesn't mean -d");

		write("keep/f", "f");
		res = run("rm -rI keep", "n\n");
		assertEquals(0, res.exitCode);
		assertTrue(Files.exists(dir.resolve("keep/f")));

		res = run("rm .", "");
		assertEquals(1, res.exitCode);
		assertEquals("rm: refusing to remove '.'", res.getStdErr().trim());
	}
}
