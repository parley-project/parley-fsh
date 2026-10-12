package us.bringardner.fsh;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;

/**
 * A PrintStream whose println ends the line with LF whatever the platform: the shell's output is
 * the same on Windows as on Linux, and not a mix of println's separator (CR LF on Windows) and
 * the LF that echo, printf and the explicit "\n" in a message write.
 * <p>
 * It writes to another PrintStream, so the one it wraps keeps its encoding, its buffering and its
 * identity. Each line is handed over in one call, so another thread's output does not fall between
 * a line and its end.
 */
public final class LfPrintStream extends PrintStream {

	private final PrintStream target;

	private LfPrintStream(PrintStream target) {
		super(OutputStream.nullOutputStream(), false);
		this.target = target;
	}

	/**
	 * @return ps, or an LfPrintStream writing to it (ps itself if it is one already, or null)
	 */
	public static PrintStream of(PrintStream ps) {
		if( ps == null || ps instanceof LfPrintStream ) {
			return ps;
		}
		return new LfPrintStream(ps);
	}

	/** @return the stream this one writes to */
	public PrintStream getTarget() {
		return target;
	}

	// ------------------------------------------------------------ the lines: LF, in one call

	@Override
	public void println() {
		target.print('\n');
	}

	@Override
	public void println(boolean x) {
		target.print(x + "\n");
	}

	@Override
	public void println(char x) {
		target.print(x + "\n");
	}

	@Override
	public void println(int x) {
		target.print(x + "\n");
	}

	@Override
	public void println(long x) {
		target.print(x + "\n");
	}

	@Override
	public void println(float x) {
		target.print(x + "\n");
	}

	@Override
	public void println(double x) {
		target.print(x + "\n");
	}

	@Override
	public void println(char[] x) {
		target.print(new String(x) + "\n");
	}

	@Override
	public void println(String x) {
		target.print(x + "\n");
	}

	@Override
	public void println(Object x) {
		target.print(String.valueOf(x) + "\n");
	}

	// ------------------------------------------------------------ everything else goes straight through

	@Override
	public void write(int b) {
		target.write(b);
	}

	@Override
	public void write(byte[] buf, int off, int len) {
		target.write(buf, off, len);
	}

	@Override
	public void write(byte[] buf) throws IOException {
		target.write(buf);
	}

	@Override
	public void print(boolean b) {
		target.print(b);
	}

	@Override
	public void print(char c) {
		target.print(c);
	}

	@Override
	public void print(int i) {
		target.print(i);
	}

	@Override
	public void print(long l) {
		target.print(l);
	}

	@Override
	public void print(float f) {
		target.print(f);
	}

	@Override
	public void print(double d) {
		target.print(d);
	}

	@Override
	public void print(char[] s) {
		target.print(s);
	}

	@Override
	public void print(String s) {
		target.print(s);
	}

	@Override
	public void print(Object obj) {
		target.print(obj);
	}

	@Override
	public PrintStream printf(String format, Object... args) {
		target.printf(format, args);
		return this;
	}

	@Override
	public PrintStream printf(Locale l, String format, Object... args) {
		target.printf(l, format, args);
		return this;
	}

	@Override
	public PrintStream format(String format, Object... args) {
		target.format(format, args);
		return this;
	}

	@Override
	public PrintStream format(Locale l, String format, Object... args) {
		target.format(l, format, args);
		return this;
	}

	@Override
	public PrintStream append(CharSequence csq) {
		target.append(csq);
		return this;
	}

	@Override
	public PrintStream append(CharSequence csq, int start, int end) {
		target.append(csq, start, end);
		return this;
	}

	@Override
	public PrintStream append(char c) {
		target.append(c);
		return this;
	}

	@Override
	public void flush() {
		target.flush();
	}

	@Override
	public void close() {
		target.close();
	}

	@Override
	public boolean checkError() {
		return target.checkError();
	}
}
