/**
 * <PRE>
 *
 * Copyright Tony Bringardner 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *	@author Tony Bringardner
 */
package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs fsh (fsh -c SCRIPT) in its own process, with no plugins, and checks what the builtins of the
 * terminal browser (browse, view, edit, search; parley-files-viewer-tui) do.
 */
public class TestBrowserBuiltins {

	@TempDir
	Path tmp;

	@BeforeEach
	public void files() throws IOException {
		Files.createDirectories(tmp.resolve("d/sub"));
		Files.write(tmp.resolve("d/a.txt"), "hello world\n".getBytes(StandardCharsets.UTF_8));
		Files.write(tmp.resolve("d/sub/b.md"), "Hello again\n".getBytes(StandardCharsets.UTF_8));
		Files.write(tmp.resolve("d/bin.dat"), new byte[] {0, 1, 'A', 'B'});
		Files.write(tmp.resolve("d/.hidden.txt"), "hello\n".getBytes(StandardCharsets.UTF_8));
	}

	/** stdout and stderr of fsh running script in tmp */
	private String fsh(String script) throws Exception {
		return fsh(script, "echo edited >> \"$1\"\n");
	}

	/** as {@link #fsh(String)}, with an $EDITOR that runs editorScript (its $1 is the file to edit) */
	private String fsh(String script, String editorScript, String... jvmArgs) throws Exception {
		File editor = tmp.resolve("editor.sh").toFile();
		// (no #! line on Windows, which has no /bin/sh: fsh runs the file as its own script)
		String shebang = File.separatorChar == '\\' ? "" : "#!/bin/sh\n";
		Files.write(editor.toPath(), (shebang+editorScript).getBytes(StandardCharsets.UTF_8));
		editor.setExecutable(true);

		List<String> cmd = new ArrayList<>(Arrays.asList(
				new File(System.getProperty("java.home"), "bin/java").getPath(),
				"-cp", System.getProperty("java.class.path"),
				// (no plugin folders: only what is on the class path)
				"-Dparley.plugins.path="+tmp.resolve("no-plugins"),
				"us.bringardner.fsh.Console", "-c", script));
		// (as fsh's launch scripts do: Java 24+ warns on System.loadLibrary without it,
		// which would end up in the output; Java 11 doesn't know the option)
		if( Runtime.version().feature() >= 17 ) {
			cmd.add(1, "--enable-native-access=ALL-UNNAMED");
		}
		cmd.addAll(1, Arrays.asList(jvmArgs));
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(tmp.toFile()).redirectErrorStream(true);
		pb.environment().put("EDITOR", editor.getAbsolutePath());
		pb.environment().remove("VISUAL");
		pb.environment().remove("JAVA_TOOL_OPTIONS");
		Process p = pb.start();
		p.getOutputStream().close();
		byte[] out = p.getInputStream().readAllBytes();
		assertTrue(p.waitFor(60, TimeUnit.SECONDS));
		return new String(out, StandardCharsets.UTF_8).replace("\r", "");
	}

	@Test
	public void searchesByNameAndText() throws Exception {
		assertEquals("d/a.txt\nrc=0\n", fsh("search -n '*.txt' d; echo rc=$?"));
		assertEquals("d/a.txt: hello world\nd/sub/b.md: Hello again\n", fsh("search -i -t hello d | sort"));
		assertEquals("d/a.txt\nd/sub/b.md\n", fsh("search -il -t hello d | sort"));
		assertEquals("d/.hidden.txt\nd/a.txt\n", fsh("search -a -n '*.txt' d | sort"));
		assertEquals("rc=1\n", fsh("search -n nope d; echo rc=$?"));
		assertEquals("./a.txt\n", fsh("cd d; search -n a.txt"));
	}

	@Test
	public void viewPrintsWhenNotATerminal() throws Exception {
		assertEquals("hello world\n", fsh("view d/a.txt | cat"));
		assertTrue(fsh("view -c d/bin.dat").startsWith("00000000  00 01 41 42"));
		assertTrue(fsh("view -cx d/a.txt").startsWith("00000000  68 65 6c 6c 6f"));
		assertTrue(fsh("view -c d/nope; echo rc=$?").contains("No such file"));
	}

	@Test
	public void editsLocalAndMountedFiles() throws Exception {
		String edited = fsh("edit d/a.txt");
		assertEquals("hello world\nedited\n", new String(Files.readAllBytes(tmp.resolve("d/a.txt")), StandardCharsets.UTF_8).replace("\r", ""), edited);

		// a file on a connected file system is staged, edited and copied back; the local one is untouched
		String out = fsh("connect memory /mem; mkdir /mem/w; cp d/sub/b.md /mem/w/; edit /mem/w/b.md; view -c /mem/w/b.md");
		assertTrue(out.contains("edit: saved /mem/w/b.md to memory"), out);
		assertTrue(out.endsWith("Hello again\nedited\n"), out);
		assertEquals("Hello again\n", new String(Files.readAllBytes(tmp.resolve("d/sub/b.md")), StandardCharsets.UTF_8));
	}

	@Test
	public void editKeepsWhatWasTypedWhenTheEditorFails() throws Exception {
		String out = fsh("connect memory /mem >/dev/null; mkdir /mem/w; cp d/a.txt /mem/w/; edit /mem/w/a.txt; echo rc=$?; view -c /mem/w/a.txt",
				"echo typed >> \"$1\"\nexit 3\n");
		assertTrue(out.contains("edit: the editor exited with 3; /mem/w/a.txt was not copied back, your edited copy is kept in "), out);
		assertTrue(out.contains("rc=3\n"), out);
		// (the file on the connected file system is as it was)
		assertTrue(out.endsWith("hello world\n"), out);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("kept in (\\S+a\\.txt)").matcher(out);
		assertTrue(m.find(), out);
		File kept = new File(m.group(1));
		try {
			assertEquals("hello world\ntyped\n", new String(Files.readAllBytes(kept.toPath()), StandardCharsets.UTF_8).replace("\r", ""));
		} finally {
			kept.delete();
			kept.getParentFile().delete();
		}
	}

	@Test
	public void editMakesAFileThatDoesNotExist() throws Exception {
		String out = fsh("connect memory /mem >/dev/null; mkdir /mem/w; edit /mem/w/new.txt; view -c /mem/w/new.txt",
				"echo made > \"$1\"\n");
		assertTrue(out.contains("edit: saved /mem/w/new.txt to memory"), out);
		assertTrue(out.endsWith("made\n"), out);
		// (an editor that doesn't save leaves nothing behind)
		String none = fsh("connect memory /mem >/dev/null; mkdir /mem/w; edit /mem/w/none.txt; ls /mem/w", "true\n");
		assertTrue(none.contains("edit: /mem/w/none.txt: not changed"), none);
		assertFalse(none.contains("none.txt\n"), none);
	}

	private static final String EDITORS = "-Dfsh.test.editors=test-first,test-second,test-failing";

	@Test
	public void editUsesTheFirstEditorPluginThatCanRun() throws Exception {
		// (a plugin's editor works on the file where it is, on a connected file system too; $EDITOR is not run)
		assertEquals("hello world\ntest-first edited d/a.txt\n", edited("edit d/a.txt; cat d/a.txt", EDITORS));
		String out = fsh("connect memory /mem >/dev/null; mkdir /mem/w; edit /mem/w/new.txt; view -c /mem/w/new.txt", "echo no >> \"$1\"\n", EDITORS);
		assertEquals("test-first edited /mem/w/new.txt\n", out);
	}

	private String edited(String script, String... jvmArgs) throws Exception {
		return fsh(script, "echo no >> \"$1\"\n", jvmArgs);
	}

	@Test
	public void editEditsEachFileGivenInTurn() throws Exception {
		// (a pattern names every file it matches, and so does a list of names; each is edited in turn)
		assertEquals("hello world\ntest-first edited d/a.txt\nHello again\ntest-first edited d/sub/b.md\n",
				edited("edit d/a.txt d/sub/b.md; cat d/a.txt d/sub/b.md", EDITORS));
	}

	@Test
	public void editWithChoosesAnEditorPlugin() throws Exception {
		assertEquals("hello world\ntest-second edited d/a.txt\n", edited("edit --with test-second d/a.txt; cat d/a.txt", EDITORS));
		assertEquals("Hello again\ntest-second edited d/sub/b.md\n", edited("edit --with=test-second d/sub/b.md; cat d/sub/b.md", EDITORS));
		String none = edited("edit --with nope d/a.txt; echo rc=$?", EDITORS);
		assertTrue(none.contains("edit: nope: no such editor (edit --list lists them)\nrc=1\n"), none);
		// (one that can't run here is not used in place of another)
		String cannot = edited("edit --with test-first d/a.txt; echo rc=$?", "-Dfsh.test.editors=test-second");
		assertTrue(cannot.contains("edit: test-first: cannot run here\nrc=1\n"), cannot);
	}

	@Test
	public void editListsTheEditorPlugins() throws Exception {
		assertEquals("* test-first - a test editor\n  test-second - a test editor\n  test-failing - a test editor\n",
				edited("edit --list", EDITORS));
		// (the first that can run here is the one used)
		assertEquals("  test-first - a test editor (cannot run here)\n* test-second - a test editor\n  test-failing - a test editor (cannot run here)\n",
				edited("edit --list", "-Dfsh.test.editors=test-second"));
		assertEquals("  test-first - a test editor (cannot run here)\n  test-second - a test editor (cannot run here)\n  test-failing - a test editor (cannot run here)\n",
				edited("edit --list"));
	}

	@Test
	public void editFallsBackToTheEditorVariableWhenNoPluginCanRun() throws Exception {
		// (the test editors are on the class path but none can run: $EDITOR edits a copy as it always did)
		fsh("edit d/a.txt");
		assertEquals("hello world\nedited\n", new String(Files.readAllBytes(tmp.resolve("d/a.txt")), StandardCharsets.UTF_8).replace("\r", ""));
	}

	@Test
	public void anEditorPluginThatFailsIsNotReplacedByAnother() throws Exception {
		String out = edited("edit --with test-failing d/a.txt; echo rc=$?; cat d/a.txt", EDITORS);
		assertTrue(out.contains("edit: d/a.txt: the disk is on fire\nrc=1\nhello world\n"), out);
	}

	@Test
	public void pluginsListsTheEditors() throws Exception {
		String out = edited("plugins", EDITORS);
		assertTrue(out.contains("editors:\n  test-first ("), out);
	}

	@Test
	public void searchesMountedFileSystems() throws Exception {
		String out = fsh("connect memory /mem >/dev/null; mkdir /mem/x; cp d/a.txt /mem/x/; search -t world /mem");
		assertEquals("/mem/x/a.txt: hello world\n", out);
	}

	@Test
	public void browseNeedsTheTerminal() throws Exception {
		assertTrue(fsh("browse | cat; echo rc=$?").contains("browse: no browser can run here"));
		assertEquals("  terminal - the full-screen browser on Parley's own terminal code (cannot run here)\n",
				fsh("browse --list | cat"));
	}

	@Test
	public void builtinsHaveHelp() throws Exception {
		String out = fsh("for c in browse view edit search; do type $c; done; help view; plugins");
		assertTrue(out.contains("browse is a shell builtin"), out);
		assertTrue(out.contains("view is a shell builtin"), out);
		assertTrue(out.contains("search is a shell builtin"), out);
		assertTrue(out.contains("-x   hex dump even if the file looks like text"), out);
		// (built in, not a plugin's)
		assertFalse(out.contains("commands:"), out);
		assertFalse(out.contains("browsers:"), out);
	}
}
