package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.RawUtf8;

public class TestRawUtf8 {

	private static byte [] bytes(int ... values) {
		byte [] ret = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			ret[i] = (byte) values[i];
		}
		return ret;
	}

	@Test
	public void testRoundTrip() {
		// text, a byte that is not UTF-8, an incomplete character, a surrogate, and too large
		byte [] in = bytes('a', 0xce, 0xb1, 0xff, 'b', 0xe2, 0x82, 'c', 0xed, 0xa0, 0x80, 0xf4, 0x90, 0x80, 0x80, 0xf0, 0x9f, 0x98, 0x80, 0xe2, 0x82);
		String text = RawUtf8.toText(in);
		assertEquals("aα", text.substring(0, 2));
		assertEquals(RawUtf8.escape(0xff), text.charAt(2));
		assertEquals("😀", text.substring(text.length()-4, text.length()-2));
		assertArrayEquals(in, RawUtf8.toBytes(text));
	}

	@Test
	public void testStreams() throws Exception {
		byte [] in = bytes('x', 0x80, 0xce, 0xb1, 0xfe, 0xce, 'y');
		String text = new String(in, RawUtf8.CHARSET);
		assertEquals(RawUtf8.toText(in), text);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try(PrintStream ps = new PrintStream(out, true, RawUtf8.CHARSET)) {
			ps.print(text);
		}
		assertArrayEquals(in, out.toByteArray());
	}
}
