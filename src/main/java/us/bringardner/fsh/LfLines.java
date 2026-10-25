package us.bringardner.fsh;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * A PrintStream whose println ends the line with LF whatever the platform (as LfPrintStream does
 * for a stream it wraps): for the streams of a pipeline, which are PrintStreams of their own, so
 * that "ls -l | wc" counts the bytes "ls -l" writes and not CR LF on Windows.
 */
public abstract class LfLines extends PrintStream {

	protected LfLines(OutputStream out, boolean autoFlush, Charset charset) {
		super(out, autoFlush, charset);
	}

	@Override
	public void println() {
		print('\n');
	}

	@Override
	public void println(boolean x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(char x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(int x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(long x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(float x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(double x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(char[] x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(String x) {
		synchronized (this) {
			print(x);
			print('\n');
		}
	}

	@Override
	public void println(Object x) {
		String s = String.valueOf(x);
		synchronized (this) {
			print(s);
			print('\n');
		}
	}
}
