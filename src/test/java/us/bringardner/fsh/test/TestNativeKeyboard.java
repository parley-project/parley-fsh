package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.NativeKeyboard;

/**
 * Covers NativeKeyboard without the native library (the library needs a terminal).
 */
public class TestNativeKeyboard {

	@Test
	public void testReadLineReturnsNullAtEndOfInput() throws Exception {
		assumeFalse(NativeKeyboard.isAvailible());
		Console console = new Console();
		console.setStdIn(new ByteArrayInputStream("echo one\n".getBytes()));
		NativeKeyboard kb = new NativeKeyboard();
		kb.setPrompt("");

		assertEquals("echo one", kb.readLine(console));
		assertNull(kb.readLine(console));
	}

	@Test
	public void testReadWithoutLibraryUsesSystemIn() throws Exception {
		assumeFalse(NativeKeyboard.isAvailible());
		InputStream saved = System.in;
		try {
			System.setIn(new ByteArrayInputStream("ab".getBytes()));
			try (NativeKeyboard kb = new NativeKeyboard()) {
				assertEquals(2, kb.available());
				assertEquals('a', kb.read());
				assertEquals('b', kb.read());
				assertEquals(-1, kb.read());
			}
		} finally {
			System.setIn(saved);
		}
	}
}
