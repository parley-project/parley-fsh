package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.Console.HistoryEntry;
import us.bringardner.fsh.HistoryExpansion;
import us.bringardner.fsh.KeyboardReader;
import us.bringardner.fsh.syntax.Parser;

/**
 * What an interactive shell does with typed lines, as bash does: PS2 lines until a command is
 * complete, history expansion (checked against bash's history -p), and what goes into the
 * history.
 */
public class TestInteractiveInput {

	/** typed lines, and the prompt each was read with */
	private static class Keyboard implements KeyboardReader {
		final Deque<String> lines = new ArrayDeque<>();
		final List<String> prompts = new ArrayList<>();
		private String prompt;

		Keyboard(String ... typed) {
			lines.addAll(List.of(typed));
		}

		@Override
		public String readLine(Console console) {
			prompts.add(prompt);
			return lines.poll();
		}

		@Override
		public void setPrompt(String prompt) {
			this.prompt = prompt;
		}

		@Override
		public void setEditLineText(String text) {
		}

		@Override
		public PrintStream getStdErr() {
			return null;
		}

		@Override
		public PrintStream getStdOut() {
			return null;
		}

		@Override
		public InputStream getStdIn() {
			return new ByteArrayInputStream(new byte[0]);
		}
	}

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	private Console console(String ... history) {
		Console c = new Console();
		c.setStdOut(new PrintStream(out, true));
		c.setStdErr(new PrintStream(err, true));
		c.setOption(Console.Option.HistExpand, true);
		c.history.clear();
		for(String h : history) {
			c.addHistory(h);
		}
		return c;
	}

	@Test
	public void moreLinesUntilTheCommandIsComplete() throws Exception {
		String [][] typed = {
				{"if true; then", "echo yes", "fi"},
				{"echo \"a", "b\""},
				{"cat <<EOF", "text", "EOF"},
				{"echo a |", "wc -l"},
				{"true &&", "echo b"},
				{"echo a \\", "b"},
				{"for i in 1 2", "do", "echo $i; done"},
				{"f() {", "echo f", "}"},
				{"x=$(echo", "y)"},
		};
		for(String [] lines : typed) {
			Console c = console();
			Keyboard kb = new Keyboard(lines);
			String code = c.readCommand(kb);
			assertEquals(String.join("\n", lines), code);
			assertEquals(lines.length, kb.prompts.size());
			for (int i = 1; i < lines.length; i++) {
				assertEquals("> ", kb.prompts.get(i), "PS2 for "+String.join("|", lines));
			}
			assertTrue(Parser.isComplete(code));
		}
	}

	@Test
	public void aSyntaxErrorIsNotWaitedOn() throws Exception {
		Keyboard kb = new Keyboard("fi", "echo never");
		assertEquals("fi", console().readCommand(kb));
		assertEquals(1, kb.prompts.size());
		assertFalse(Parser.isComplete("if true; then"));
		assertTrue(Parser.isComplete("echo 'a' \\\\"));
	}

	@Test
	public void theEndOfInputInTheMiddle() throws Exception {
		assertEquals("", console().readCommand(new Keyboard("if true; then")));
		assertTrue(err.toString().contains("syntax error: unexpected end of file"), err.toString());
		assertNull(console().readCommand(new Keyboard()));
	}

	@Test
	public void historyExpansionOfTypedLines() throws Exception {
		Console c = console("echo one two", "ls -l /tmp");
		assertEquals("ls -l /tmp", c.readCommand(new Keyboard("!!")));
		assertEquals("ls -l /tmp\n", out.toString());
		assertEquals("echo two", c.readCommand(new Keyboard("echo !-2:$")));
		assertEquals("", c.readCommand(new Keyboard("!nosuch")));
		assertEquals("fsh: !nosuch: event not found\n", err.toString());
		// :p shows it and keeps it, but does not run it
		out.reset();
		assertEquals("", c.readCommand(new Keyboard("!echo:p")));
		assertEquals("echo one two\n", out.toString());
		assertEquals("echo one two", c.history.get(c.history.size()-1).command);
		// off with set +H
		c.setOption(Console.Option.HistExpand, false);
		assertEquals("echo !!", c.readCommand(new Keyboard("echo !!")));
	}

	@Test
	public void whatGoesIntoTheHistory() {
		Console c = console();
		c.rememberCommand("if true; then\necho yes\nfi");
		c.rememberCommand("for i in 1 2\ndo\necho $i\ndone");
		c.rememberCommand("cat <<EOF\nx\nEOF");
		c.rememberCommand("echo \"a\nb\"");
		c.setVariable("HISTCONTROL", "ignoreboth");
		c.rememberCommand(" secret");
		c.rememberCommand("echo dup");
		c.rememberCommand("echo dup");
		c.setVariable("HISTIGNORE", "ls*:pwd");
		c.rememberCommand("ls -l");
		c.rememberCommand("pwd");
		List<String> got = new ArrayList<>();
		for(HistoryEntry e : c.history) {
			got.add(e.command);
		}
		assertEquals(List.of("if true; then echo yes; fi", "for i in 1 2; do echo $i; done", "cat <<EOF\nx\nEOF",
				"echo \"a\nb\"", "echo dup"), got);
	}

	/** bash 5.3's history -p for each word, after history -s of these three commands */
	private static final String [] HISTORY = {"echo one two three", "ls -l /usr/local/lib/file.tar.gz", "grep -n \"a b\" file.txt | wc -l"};
	private static final String [][] EXPANSIONS = {
			{"!!", "grep -n \"a b\" file.txt | wc -l"},
			{"!-2", "ls -l /usr/local/lib/file.tar.gz"},
			{"!1", "echo one two three"},
			{"!ls", "ls -l /usr/local/lib/file.tar.gz"},
			{"!?grep?", "grep -n \"a b\" file.txt | wc -l"},
			{"!$", "-l"},
			{"!^", "-n"},
			{"!*", "-n \"a b\" file.txt | wc -l"},
			{"!!:2", "\"a b\""},
			{"!!:1-2", "-n \"a b\""},
			{"!!:2-", "\"a b\" file.txt | wc"},
			{"!-3:1*", "one two three"},
			{"!ls:2:h", "/usr/local/lib"},
			{"!ls:2:t", "file.tar.gz"},
			{"!ls:2:r", "/usr/local/lib/file.tar"},
			{"!ls:2:e", ".gz"},
			{"!-3:s/one/1/", "echo 1 two three"},
			{"!-3:gs/o/0/", "ech0 0ne tw0 three"},
			{"x \\!! y", "x \\!! y"},
			{"'!!'", "'!!'"},
	};

	@Test
	public void expansionsAsBashMakesThem() {
		List<String> history = List.of(HISTORY);
		for(String [] e : EXPANSIONS) {
			HistoryExpansion.Result r = HistoryExpansion.expand(e[0], history);
			assertNull(r.error, e[0]+": "+r.error);
			assertEquals(e[1], r.line, e[0]);
		}
		// ^old^new^: the last command with the first old replaced
		assertEquals("echo 2 two", HistoryExpansion.expand("^two^2^", List.of("echo two two")).line);
		assertEquals("echo 2 two more", HistoryExpansion.expand("^two^2^ more", List.of("echo two two")).line);
		assertTrue(HistoryExpansion.expand("^nope^x", List.of("echo two")).error != null);
		for(String bad : new String [] {"a!b", "!nosuch", "!!:9"}) {
			assertTrue(HistoryExpansion.expand(bad, history).error != null, bad);
		}
	}
}
