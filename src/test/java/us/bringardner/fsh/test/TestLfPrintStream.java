package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.LfPrintStream;

/** LfPrintStream: println ends a line with LF on every platform, and nothing else changes. */
public class TestLfPrintStream {

	private static String run(java.util.function.Consumer<PrintStream> body) throws Exception {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		PrintStream target = new PrintStream(bao, true, "UTF-8");
		body.accept(LfPrintStream.of(target));
		return bao.toString("UTF-8");
	}

	@Test
	public void everyPrintlnEndsWithLf() throws Exception {
		assertEquals("\n", run(p -> p.println()));
		assertEquals("a\n", run(p -> p.println("a")));
		assertEquals("true\n", run(p -> p.println(true)));
		assertEquals("c\n", run(p -> p.println('c')));
		assertEquals("12\n", run(p -> p.println(12)));
		assertEquals("34\n", run(p -> p.println(34L)));
		assertEquals("1.5\n", run(p -> p.println(1.5f)));
		assertEquals("2.5\n", run(p -> p.println(2.5d)));
		assertEquals("xyz\n", run(p -> p.println(new char[] {'x', 'y', 'z'})));
		assertEquals("obj\n", run(p -> p.println((Object) "obj")));
		assertEquals("null\n", run(p -> p.println((Object) null)));
	}

	@Test
	public void noCarriageReturnIsEverAdded() throws Exception {
		String out = run(p -> {
			p.println("one");
			p.print("two");
			p.println();
			p.printf("%sthree", "x");
			p.println();
		});
		assertEquals("one\ntwo\nxthree\n", out);
	}

	@Test
	public void theTargetKeepsItsEncoding() throws Exception {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		PrintStream target = new PrintStream(bao, true, "ISO-8859-1");
		LfPrintStream.of(target).println("café");
		assertArrayEquals(new byte[] {'c', 'a', 'f', (byte) 0xE9, '\n'}, bao.toByteArray());
	}

	@Test
	public void bytesPassStraightThrough() throws Exception {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		PrintStream p = LfPrintStream.of(new PrintStream(bao, true, "UTF-8"));
		p.write(0xFF);
		p.write(new byte[] {1, 2, 3}, 0, 3);
		p.write(new byte[] {4, 5});
		assertArrayEquals(new byte[] {(byte) 0xFF, 1, 2, 3, 4, 5}, bao.toByteArray());
	}

	@Test
	public void wrappingTwiceOrNullIsHarmless() {
		PrintStream target = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
		PrintStream once = LfPrintStream.of(target);
		assertSame(once, LfPrintStream.of(once));
		assertEquals(null, LfPrintStream.of(null));
	}
}
