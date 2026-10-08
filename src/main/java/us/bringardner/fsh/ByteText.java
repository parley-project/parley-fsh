package us.bringardner.fsh;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Bytes written as escapes (\303\251 in $'...', printf, echo -e) are bytes of UTF-8 text, as
 * in bash in a UTF-8 locale: $'\303\251' is é, one character. While the escapes are read each
 * byte is a mark (a character of its own); finish turns runs of marks into the text they
 * code. A byte that is not part of UTF-8 stays the character with its value, as before.
 */
public final class ByteText {

	private ByteText() {
	}

	/** the first of the 256 marks (in a private use area) */
	private static final char FIRST = '';

	/** the mark for byte b (0-255) */
	public static char mark(int b) {
		return (char) (FIRST+(b & 0xff));
	}

	private static boolean isMark(char c) {
		return c >= FIRST && c <= FIRST+0xff;
	}

	/** text with its runs of marks decoded */
	public static String finish(CharSequence text) {
		StringBuilder ret = new StringBuilder(text.length());
		int n = text.length();
		for (int i = 0; i < n; i++) {
			char c = text.charAt(i);
			if( !isMark(c)) {
				ret.append(c);
				continue;
			}
			int end = i;
			while( end < n && isMark(text.charAt(end))) {
				end++;
			}
			byte [] bytes = new byte[end-i];
			for (int k = i; k < end; k++) {
				bytes[k-i] = (byte) (text.charAt(k)-FIRST);
			}
			ret.append(decode(bytes));
			i = end-1;
		}
		return ret.toString();
	}

	/** finish, in place */
	public static void finishInPlace(StringBuilder text) {
		for (int i = 0; i < text.length(); i++) {
			if( isMark(text.charAt(i))) {
				String done = finish(text.toString());
				text.setLength(0);
				text.append(done);
				return;
			}
		}
	}

	/** bytes as UTF-8, where they are; another byte is the character with its value */
	private static String decode(byte [] bytes) {
		CharsetDecoder d = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		try {
			return d.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			// decode what is valid, a byte at a time otherwise
		}
		StringBuilder ret = new StringBuilder();
		int i = 0;
		while( i < bytes.length ) {
			int b = bytes[i] & 0xff;
			int len = b >= 0xf0 && b < 0xf8 ? 4 : b >= 0xe0 ? 3 : b >= 0xc2 && b < 0xe0 ? 2 : 1;
			if( len > 1 && i+len <= bytes.length ) {
				try {
					CharBuffer cb = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
							.decode(ByteBuffer.wrap(bytes, i, len));
					ret.append(cb);
					i += len;
					continue;
				} catch (CharacterCodingException e) {
				}
			}
			ret.append((char) b);
			i++;
		}
		return ret.toString();
	}
}
