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

	private static String complete(Completion c, String line) throws Exception {
		LineEditor e = new LineEditor(LineEditor.keys(keys(line, (int) '\t')), new PrintStream(new ByteArrayOutputStream()), List.of());
		e.setCompleter(c);
		return e.readLine("$ ");
	}

}
