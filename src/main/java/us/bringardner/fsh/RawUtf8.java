package us.bringardner.fsh;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8 that keeps every byte: a byte that is not part of a UTF-8 character is read as a
 * character of its own (a lone low surrogate, U+DC80 to U+DCFF, which UTF-8 text never has) and
 * written back as that byte. So printf '\xff' writes the byte ff, and a file with bytes that are
 * not UTF-8 goes through $( ), read and the like unchanged, as it does in bash.
 */
public final class RawUtf8 extends Charset {

	/** the charset the shell reads and writes text in */
	public static final RawUtf8 CHARSET = new RawUtf8();

	private RawUtf8() {
		super("x-fsh-raw-utf-8", new String[0]);
	}

	/** the character that stands for byte b (0x80 to 0xff) */
	public static char escape(int b) {
		return (char) (0xDC00 | (b & 0xff));
	}

	/** c stands for a byte (see escape) */
	public static boolean isEscape(char c) {
		return c >= 0xDC80 && c <= 0xDCFF;
	}

	/** c does not print: a control character, DEL, or a byte that is not UTF-8 (shown as $'\\377') */
	public static boolean unprintable(char c) {
		return c < ' ' || c == 0x7f || isEscape(c);
	}

	/** the value $'\\ooo' shows for c: a byte's own for one that stands for a byte */
	public static int valueOf(char c) {
		return isEscape(c) ? c & 0xff : c;
	}

	/** bytes as text (see the class) */
	public static String toText(byte [] bytes) {
		return toText(bytes, 0, bytes.length);
	}

	public static String toText(byte [] bytes, int off, int len) {
		StringBuilder ret = new StringBuilder(len);
		int end = off+len;
		int i = off;
		while( i < end ) {
			int n = sequence(bytes, i, end);
			if( n == 1 && (bytes[i] & 0x80) == 0 ) {
				ret.append((char) bytes[i]);
			} else if( n > 0 ) {
				ret.appendCodePoint(codePoint(bytes, i, n));
			} else {
				ret.append(escape(bytes[i]));
				n = 1;
			}
			i += n;
		}
		return ret.toString();
	}

	/** text as bytes (see the class) */
	public static byte [] toBytes(CharSequence text) {
		java.io.ByteArrayOutputStream ret = new java.io.ByteArrayOutputStream(text.length());
		int n = text.length();
		for (int i = 0; i < n; i++) {
			char c = text.charAt(i);
			if( Character.isHighSurrogate(c) && i+1 < n && Character.isLowSurrogate(text.charAt(i+1))) {
				write(ret, Character.toCodePoint(c, text.charAt(++i)));
			} else if( isEscape(c)) {
				ret.write(c & 0xff);
			} else if( Character.isSurrogate(c)) {
				ret.write('?');
			} else {
				write(ret, c);
			}
		}
		return ret.toByteArray();
	}

	private static void write(java.io.ByteArrayOutputStream out, int cp) {
		if( cp < 0x80 ) {
			out.write(cp);
		} else if( cp < 0x800 ) {
			out.write(0xC0 | cp >> 6);
			out.write(0x80 | cp & 0x3F);
		} else if( cp < 0x10000 ) {
			out.write(0xE0 | cp >> 12);
			out.write(0x80 | cp >> 6 & 0x3F);
			out.write(0x80 | cp & 0x3F);
		} else {
			out.write(0xF0 | cp >> 18);
			out.write(0x80 | cp >> 12 & 0x3F);
			out.write(0x80 | cp >> 6 & 0x3F);
			out.write(0x80 | cp & 0x3F);
		}
	}

	/**
	 * The length of the UTF-8 character at bytes[i], 0 if it is not one, or -1 if it may be one
	 * whose bytes do not all come before end.
	 */
	private static int sequence(byte [] bytes, int i, int end) {
		int b = bytes[i] & 0xff;
		if( b < 0x80 ) {
			return 1;
		}
		int n;
		int low = 0x80;
		int high = 0xBF;
		if( b >= 0xC2 && b <= 0xDF ) {
			n = 2;
		} else if( b >= 0xE0 && b <= 0xEF ) {
			n = 3;
			if( b == 0xE0 ) {
				low = 0xA0;
			} else if( b == 0xED ) {
				// (not a surrogate)
				high = 0x9F;
			}
		} else if( b >= 0xF0 && b <= 0xF4 ) {
			n = 4;
			if( b == 0xF0 ) {
				low = 0x90;
			} else if( b == 0xF4 ) {
				high = 0x8F;
			}
		} else {
			return 0;
		}
		for (int k = 1; k < n; k++) {
			if( i+k >= end ) {
				return -1;
			}
			int c = bytes[i+k] & 0xff;
			if( k == 1 ? c < low || c > high : c < 0x80 || c > 0xBF ) {
				return 0;
			}
		}
		return n;
	}

	private static int codePoint(byte [] bytes, int i, int n) {
		int b = bytes[i] & 0xff;
		int cp = n == 2 ? b & 0x1F : n == 3 ? b & 0x0F : b & 0x07;
		for (int k = 1; k < n; k++) {
			cp = cp << 6 | bytes[i+k] & 0x3F;
		}
		return cp;
	}

	@Override
	public boolean contains(Charset cs) {
		return cs instanceof RawUtf8 || StandardCharsets.UTF_8.contains(cs);
	}

	@Override
	public CharsetDecoder newDecoder() {
		return new CharsetDecoder(this, 1f, 1f) {
			@Override
			protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
				byte [] one = new byte[4];
				while( in.hasRemaining()) {
					int start = in.position();
					int avail = Math.min(4, in.remaining());
					for (int k = 0; k < avail; k++) {
						one[k] = in.get(start+k);
					}
					int n = sequence(one, 0, avail);
					if( n == -1 ) {
						// (the rest of it may come with the next bytes)
						return CoderResult.UNDERFLOW;
					}
					if( n == 0 || n == 1 && (one[0] & 0x80) != 0 ) {
						if( !out.hasRemaining()) {
							return CoderResult.OVERFLOW;
						}
						out.put(escape(one[0]));
						in.position(start+1);
						continue;
					}
					int cp = n == 1 ? one[0] : codePoint(one, 0, n);
					if( out.remaining() < Character.charCount(cp)) {
						return CoderResult.OVERFLOW;
					}
					out.put(Character.toChars(cp));
					in.position(start+n);
				}
				return CoderResult.UNDERFLOW;
			}
		};
	}

	@Override
	public CharsetEncoder newEncoder() {
		return new CharsetEncoder(this, 1.1f, 4f, new byte[] {'?'}) {
			@Override
			protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
				java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(4);
				while( in.hasRemaining()) {
					int start = in.position();
					char c = in.get(start);
					int used = 1;
					bytes.reset();
					if( Character.isHighSurrogate(c)) {
						if( in.remaining() < 2 ) {
							// (its other half may come next)
							return CoderResult.UNDERFLOW;
						}
						char d = in.get(start+1);
						if( Character.isLowSurrogate(d)) {
							write(bytes, Character.toCodePoint(c, d));
							used = 2;
						} else {
							bytes.write('?');
						}
					} else if( isEscape(c)) {
						bytes.write(c & 0xff);
					} else if( Character.isSurrogate(c)) {
						bytes.write('?');
					} else {
						write(bytes, c);
					}
					if( out.remaining() < bytes.size()) {
						return CoderResult.OVERFLOW;
					}
					out.put(bytes.toByteArray());
					in.position(start+used);
				}
				return CoderResult.UNDERFLOW;
			}

			@Override
			public boolean isLegalReplacement(byte [] repl) {
				return true;
			}
		};
	}
}
