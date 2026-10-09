package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.Console;

public class TestHistory {

	@TempDir
	Path home;

	private Console newConsole() {
		Console console = new Console();
		console.setEnvironmentVariable("HOME", home.toString());
		console.history.clear();
		return console;
	}

	private static List<String> commands(Console console) {
		return console.history.stream().map(e -> e.command).collect(Collectors.toList());
	}

	@Test
	public void testSaveAndReadUnderHome() throws Exception {
		Console console = newConsole();
		console.addHistory("echo one");
		console.addHistory("ls -la");
		console.saveHistory();

		assertTrue(new File(home.toFile(), ".fsh_history").exists(), "~ in HISTFILE is the home directory");

		Console next = newConsole();
		next.readHistory();
		assertEquals(List.of("echo one", "ls -la"), commands(next));
	}

	@Test
	public void testSaveReplacesFile() throws Exception {
		Console console = newConsole();
		console.addHistory("echo one");
		console.saveHistory();
		console.saveHistory();

		Console next = newConsole();
		next.readHistory();
		assertEquals(List.of("echo one"), commands(next));
	}

	@Test
	public void testReadSkipsBadLines() throws Exception {
		Files.writeString(home.resolve(".fsh_history"),
				"#12x\n"
				+ "echo a\n"
				+ "#123\n"
				+ "\n"
				+ "#456\n"
				+ "ls\n"
				+ "# a comment\n"
				+ "#789\n");

		Console console = newConsole();
		console.readHistory();
		assertEquals(List.of("echo a", "ls"), commands(console));
		// (bash's time stamps are seconds; the history keeps milliseconds)
		assertEquals(456000, console.history.get(1).time);
	}
}
