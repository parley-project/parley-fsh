package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import us.bringardner.parley.term.line.Completions;
import us.bringardner.parley.term.line.LineEditor;

/**
 * Tab completion in the line editor (parley-term's LineEditor, whose own keys are tested there):
 * fsh's Completion of file names, commands, variables and complete -W / -F.
 */
public class TestLineEditor {

	/** the keys: text as typed, and ints as keys */
	private static int[] keys(Object ... parts) {
		List<Integer> ret = new ArrayList<>();
		for(Object p : parts) {
			if( p instanceof String ) { String s = (String) p;
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

	@org.junit.jupiter.api.condition.DisabledOnOs(value = org.junit.jupiter.api.condition.OS.WINDOWS, disabledReason = "completion of $HOME depends on HOME being a directory")
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
		// complete -F: the words broken at $COMP_WORDBREAKS (--opt=va: --opt, =, va), and the part after = replaced
		console.executeScript("_wb() { COMPREPLY=(\"$2-${#COMP_WORDS[@]}-$COMP_CWORD-$3\"); }; complete -F _wb wcmd");
		assertEquals("wcmd --opt=va-4-3-= ", complete(c, "wcmd --opt=va"));
		assertEquals("wcmd user@-4-3-@ ", complete(c, "wcmd user@"));
		Completions.Result r = c.complete("echo file", 9);
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
