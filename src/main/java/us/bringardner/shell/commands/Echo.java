package us.bringardner.shell.commands;

import java.io.IOException;


import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Echo extends ShellCommand{
	static String name = "echo";
	static String help = "echo – write arguments to the standard output\n"
			+ "\n"
			+ "USAGE:  echo [-neE] [string ...]\n"
			+ "	-n  no newline at the end\n"
			+ "	-e  interpret backslash escapes: \\\\ \\a \\b \\c (stop here) \\e \\f \\n \\r \\t \\v\n"
			+ "	    \\0nnn (octal) \\xHH (hex) \\uHHHH \\UHHHHHHHH\n"
			+ "	-E  do not interpret them (the default)\n"
			;


	public Echo() {
		super(name, help);
	}



	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean nl = true;
		boolean escapes = false;
		int idx = 0;
		// options only before the first word, and only a word made of n, e and E, as in bash
		for(; idx < args.length; idx++) {
			String text = ""+args[idx].getValue(ctx);
			if( !text.matches("-[neE]+")) {
				break;
			}
			for(char c : text.substring(1).toCharArray()) {
				if( c == 'n' ) {
					nl = false;
				} else {
					escapes = c == 'e';
				}
			}
		}

		// the words, separated by one space (the arguments are already split into words)
		StringBuilder buf = new StringBuilder();
		for(int first = idx; idx < args.length; idx++ ) {
			if( idx > first ) {
				buf.append(' ');
			}
			String text = ""+args[idx].getValue(ctx);
			if( escapes && unescape(text, buf)) {
				// \c: no more output, not even the newline
				ctx.stdout.print(buf);
				ctx.stdout.flush();
				return 0;
			}
			if( !escapes ) {
				buf.append(text);
			}
		}

		if( nl ) {
			buf.append('\n');
		}
		ctx.stdout.print(buf);
		ctx.stdout.flush();
		return 0;
	}

	/**
	 * Append text to buf with its backslash escapes interpreted.
	 * @return true if it has \c, which ends the output
	 */
	static boolean unescape(String text, StringBuilder buf) {
		int n = text.length();
		for(int idx = 0; idx < n; idx++) {
			char c = text.charAt(idx);
			if( c != '\\' || idx+1 >= n ) {
				buf.append(c);
				continue;
			}
			char e = text.charAt(++idx);
			switch (e) {
			case '\\': buf.append('\\'); break;
			case 'a': buf.append('\u0007'); break;
			case 'b': buf.append('\b'); break;
			case 'c': return true;
			case 'e':
			case 'E': buf.append('\u001b'); break;
			case 'f': buf.append('\f'); break;
			case 'n': buf.append('\n'); break;
			case 'r': buf.append('\r'); break;
			case 't': buf.append('\t'); break;
			case 'v': buf.append('\u000b'); break;
			case '0':
				idx = number(text, idx+1, 3, 8, buf, "\\0");
				break;
			case 'x':
				idx = number(text, idx+1, 2, 16, buf, "\\x");
				break;
			case 'u':
				idx = number(text, idx+1, 4, 16, buf, "\\u");
				break;
			case 'U':
				idx = number(text, idx+1, 8, 16, buf, "\\U");
				break;
			default:
				// not an escape: kept as written
				buf.append('\\').append(e);
			}
		}
		return false;
	}

	/**
	 * Read up to max digits in radix starting at start and append the character they code.
	 * With no digits, the x, u and U escapes are kept as written (a 0 alone is a NUL).
	 * @return the index of the last character used
	 */
	private static int number(String text, int start, int max, int radix, StringBuilder buf, String written) {
		int end = start;
		while( end < text.length() && end-start < max && Character.digit(text.charAt(end), radix) >= 0 ) {
			end++;
		}
		if( end == start ) {
			if( radix == 8 ) {
				buf.append('\0');
			} else {
				buf.append(written);
			}
		} else {
			long code = Long.parseLong(text.substring(start, end), radix);
			if( Character.isValidCodePoint((int)code) && code <= Character.MAX_CODE_POINT ) {
				buf.appendCodePoint((int)code);
			} else {
				buf.append(written).append(text, start, end);
			}
		}
		return end-1;
	}

}
