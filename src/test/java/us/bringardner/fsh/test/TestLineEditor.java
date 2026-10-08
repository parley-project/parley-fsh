package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Completion;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.LineEditor;
import us.bringardner.fsh.NativeKeyboard;

/**
 * The line editor's keys, as bash's readline (emacs mode) does them: each case types keys and
 * checks the line it gives (the same keys checked against bash 5.3 on a terminal).
 */
public class TestLineEditor {

	private static final int ESC = 27;

	/** the keys: text as typed, and ints as keys */
	private static int[] keys(Object ... parts) {
		List<Integer> ret = new ArrayList<>();
		for(Object p : parts) {
			if( p instanceof String s ) {
				for(int k : LineEditor.typed(s)) {
					ret.add(k);
				}
			} else {
				ret.add((Integer) p);
			}
		}
		ret.add((int) '\n');
		return ret.stream().mapToInt(Integer::intValue).toArray();
	}

	private static int ctrl(char c) {
		return Character.toLowerCase(c) & 0x1f;
	}

	private static String edit(List<String> history, Object ... parts) throws Exception {
		LineEditor e = new LineEditor(LineEditor.keys(keys(parts)), new PrintStream(new ByteArrayOutputStream()), history);
		return e.readLine("$ ");
	}

	private static String edit(Object ... parts) throws Exception {
		return edit(new ArrayList<>(), parts);
	}

	@Test
	public void movingAndDeleting() throws Exception {
		assertEquals("echo 1", edit("cho 1", ctrl('a'), "e"));
		assertEquals("echo 2", edit("echo 2 extra", ctrl('w'), 127));
		assertEquals("echo 3 aaa zz", edit("echo 3 aaa bbb", ESC, (int) 'b', ctrl('k'), "zz"));
		assertEquals("echo ba", edit("echo ab", ctrl('t')));
		assertEquals("echo FOO bar", edit("echo foo bar", ESC, (int) 'b', ESC, (int) 'b', ESC, (int) 'u'));
		assertEquals("echo ac", edit("echo abc", ctrl('b'), ctrl('b'), ctrl('d')));
		assertEquals("echo xyz", edit("echo xz", ESC, (int) '[', (int) 'D', "y"));
		assertEquals("Xecho abY", edit("echo ab", ESC, (int) '[', (int) 'H', "X", ESC, (int) '[', (int) 'F', "Y"));
		assertEquals("echo é", edit("echo é"));
	}

	@Test
	public void killAndYank() throws Exception {
		assertEquals("echo 7b echo 7 kill", edit("echo 7 kill", ctrl('u'), "echo 7b ", ctrl('y')));
		assertEquals("echo bbbbbb", edit("echo aaa", ctrl('w'), "bbb", ctrl('w'), ctrl('y'), ctrl('y')));
		// two kills in a row are one: yank gives both
		assertEquals("one two", edit("one two", ctrl('w'), ctrl('w'), ctrl('y')));
		// Alt-Y: the kill before
		assertEquals("a b first", edit("first", ctrl('u'), "second", ctrl('u'), "a b ", ctrl('y'), ESC, (int) 'y'));
	}

	@Test
	public void undo() throws Exception {
		assertEquals("", edit("echo abc", 31));
		assertEquals("echo abc", edit("echo abc", ctrl('w'), 31));
	}

	@Test
	public void history() throws Exception {
		List<String> h = new ArrayList<>(List.of("echo one", "echo two", "ls -l /tmp"));
		assertEquals("ls -l /tmp", edit(h, ctrl('p')));
		assertEquals("echo two", edit(h, ESC, (int) '[', (int) 'A', ESC, (int) '[', (int) 'A'));
		assertEquals("echo one", edit(h, ESC, (int) '<'));
		assertEquals("", edit(h, ctrl('p'), ctrl('n')));
		// Alt-.: the last word of the command before, then the one before that
		assertEquals("cat /tmp", edit(h, "cat ", ESC, (int) '.'));
		assertEquals("cat two", edit(h, "cat ", ESC, (int) '.', ESC, (int) '.'));
		// Ctrl-R: the latest line with the text; Ctrl-R again: an earlier one
		assertEquals("echo two", edit(h, ctrl('r'), "echo"));
		assertEquals("echo one", edit(h, ctrl('r'), "echo", ctrl('r')));
		// an editing key keeps the line found
		assertEquals("echo two!", edit(h, ctrl('r'), "two", ctrl('e'), "!"));
		// Ctrl-G puts the line back
		assertEquals("x", edit(h, "x", ctrl('r'), "echo", ctrl('g')));
	}

	@Test
	public void endAndCancel() throws Exception {
		LineEditor e = new LineEditor(LineEditor.keys(ctrl('d')), new PrintStream(new ByteArrayOutputStream()), List.of());
		assertNull(e.readLine("$ "));
		// Ctrl-D with text deletes
		assertEquals("ab", edit("abc", ctrl('b'), ctrl('d')));
		LineEditor c = new LineEditor(LineEditor.keys(LineEditor.typed("abc\u0003")), new PrintStream(new ByteArrayOutputStream()), List.of());
		assertThrows(NativeKeyboard.LineCancelled.class, () -> c.readLine("$ "));
	}

	@Test
	public void completion() throws Exception {
		File dir = Files.createTempDirectory("complete").toFile();
		new File(dir, "dirA").mkdir();
		new File(dir, "fileone").createNewFile();
		new File(dir, "filetwo").createNewFile();
		new File(dir, "with space").createNewFile();
		Console.exitJvm = false;
		Console console = new Console();
		console.setCurrentDirectory(console.createFileSource(dir.getAbsolutePath()));
		Completion c = new Completion(console);
		assertEquals("echo fileone ", complete(c, "echo fileo"));
		assertEquals("echo dirA/", complete(c, "echo dir"));
		assertEquals("echo with\\ space ", complete(c, "echo wit"));
		assertEquals("echo \"fileone\" ", complete(c, "echo \"fileo"));
		// more than one: what they start with
		assertEquals("echo file", complete(c, "echo fil"));
		assertEquals("echo ", complete(c, "ech"));
		console.executeScript("myfn_abc() { :; }; complete -W 'alpha beta' tcmd");
		assertEquals("myfn_abc ", complete(c, "myfn_"));
		assertEquals("tcmd alpha ", complete(c, "tcmd al"));
		assertEquals("echo $HOME ", complete(c, "echo $HOM"));
		Completion.Result r = c.complete("echo file", 9);
		assertEquals(2, r.candidates.size());
		assertEquals("fileone", r.candidates.get(0).display);
		// the listing on the second Tab
		ByteArrayOutputStream shown = new ByteArrayOutputStream();
		LineEditor e = new LineEditor(LineEditor.keys(keys("echo file\t\t")), new PrintStream(shown), List.of());
		e.setCompleter(c);
		assertEquals("echo file", e.readLine("$ "));
		assertTrue(shown.toString().contains("fileone  filetwo\n"), shown.toString());
		for(String name : new String[] {"dirA", "fileone", "filetwo", "with space"}) {
			new File(dir, name).delete();
		}
		dir.delete();
	}

	private static String complete(Completion c, String line) throws Exception {
		LineEditor e = new LineEditor(LineEditor.keys(keys(line, (int) '\t')), new PrintStream(new ByteArrayOutputStream()), List.of());
		e.setCompleter(c);
		return e.readLine("$ ");
	}
}
