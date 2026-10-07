package us.bringardner.fsh.ssh;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.KeyboardReader;

/**
 * fsh's keyboard over an SSH session channel.
 * <p>
 * With a pseudo terminal (ssh -t, an interactive login) the client sends raw keys and shows
 * only what comes back, so this does what a terminal's line discipline would: echo,
 * backspace, Ctrl-U (erase the line), Ctrl-C (drop the line), Ctrl-D (end of input on an
 * empty line), Enter, escape sequences ignored, and "\n" written as "\r\n". Without one
 * (ssh host &lt; script) the input is read as lines, with no echo.
 *
 * @author Tony Bringardner
 */
public class SshTerminal implements KeyboardReader {

	private static final int CTRL_C = 3;
	private static final int CTRL_D = 4;
	private static final int BS = 8;
	private static final int CTRL_U = 0x15;
	private static final int ESC = 0x1b;
	private static final int DEL = 0x7f;

	private final InputStream in;
	private final PrintStream out;
	private final PrintStream err;
	private final boolean pty;
	private volatile String prompt = "";
	private volatile String editText;
	// A CR ended the last line: a LF right after it belongs to it
	private boolean afterCr;

	/**
	 * @param in the channel's input (the client's keys)
	 * @param out the channel's output
	 * @param err the channel's stderr
	 * @param pty true if the client asked for a pseudo terminal
	 */
	public SshTerminal(InputStream in, OutputStream out, OutputStream err, boolean pty) {
		this.in = in;
		this.pty = pty;
		this.out = new PrintStream(pty ? new CrLf(out) : out, true, StandardCharsets.UTF_8);
		this.err = new PrintStream(pty ? new CrLf(err) : err, true, StandardCharsets.UTF_8);
	}

	@Override
	public String readLine(Console console) throws IOException {
		out.print(prompt);
		out.flush();
		return pty ? readEdited() : readPlain();
	}

	private String readPlain() throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int b;
		while( (b = in.read()) >= 0 ) {
			if( b == '\n' ) {
				return text(line);
			}
			if( b != '\r' ) {
				line.write(b);
			}
		}
		return line.size() == 0 ? null : text(line);
	}

	private String readEdited() throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		String pre = editText;
		editText = null;
		if( pre != null ) {
			byte[] p = pre.getBytes(StandardCharsets.UTF_8);
			line.write(p, 0, p.length);
			out.print(pre);
			out.flush();
		}
		while( true ) {
			int b = in.read();
			if( b < 0 ) {
				return line.size() == 0 ? null : text(line);
			}
			if( afterCr && b == '\n' ) {
				afterCr = false;
				continue;
			}
			afterCr = false;
			switch (b) {
			case '\r':
				afterCr = true;
				// fall through
			case '\n':
				out.print("\n");
				out.flush();
				return text(line);
			case DEL:
			case BS:
				if( erase(line) ) {
					out.print("\b \b");
					out.flush();
				}
				break;
			case CTRL_U:
				while( erase(line) ) {
					out.print("\b \b");
				}
				out.flush();
				break;
			case CTRL_C:
				out.print("^C\n");
				out.flush();
				return "";
			case CTRL_D:
				if( line.size() == 0 ) {
					out.print("\n");
					out.flush();
					return null;
				}
				break;
			case ESC:
				skipEscape();
				break;
			default:
				if( b >= 0x20 || b >= 0x80 ) {
					line.write(b);
					out.write(b);
					out.flush();
				}
			}
		}
	}

	/**
	 * Remove the last character (all the bytes of a UTF-8 character).
	 */
	private static boolean erase(ByteArrayOutputStream line) {
		byte[] b = line.toByteArray();
		if( b.length == 0 ) {
			return false;
		}
		int n = b.length-1;
		while( n > 0 && (b[n] & 0xc0) == 0x80 ) {
			n--;
		}
		line.reset();
		line.write(b, 0, n);
		return true;
	}

	/**
	 * Arrow keys and the like (ESC [ ... final byte) are dropped.
	 */
	private void skipEscape() throws IOException {
		int b = in.read();
		if( b == '[' || b == 'O' ) {
			do {
				b = in.read();
			} while( b >= 0 && (b < 0x40 || b > 0x7e) );
		}
	}

	private static String text(ByteArrayOutputStream b) {
		return new String(b.toByteArray(), StandardCharsets.UTF_8);
	}

	@Override
	public void setPrompt(String prompt) {
		this.prompt = prompt == null ? "" : prompt;
	}

	@Override
	public void setEditLineText(String text) {
		this.editText = text;
	}

	@Override
	public PrintStream getStdErr() {
		return err;
	}

	@Override
	public PrintStream getStdOut() {
		return out;
	}

	@Override
	public InputStream getStdIn() {
		return in;
	}

	/**
	 * "\n" to "\r\n", as a terminal's output processing (ONLCR) does.
	 */
	private static final class CrLf extends FilterOutputStream {
		private int last;

		CrLf(OutputStream out) {
			super(out);
		}

		@Override
		public void write(int b) throws IOException {
			if( b == '\n' && last != '\r' ) {
				out.write('\r');
			}
			out.write(b);
			last = b;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			ByteArrayOutputStream tmp = new ByteArrayOutputStream(len+16);
			for (int i = off; i < off+len; i++) {
				if( b[i] == '\n' && last != '\r' ) {
					tmp.write('\r');
				}
				tmp.write(b[i]);
				last = b[i];
			}
			out.write(tmp.toByteArray());
		}
	}
}
