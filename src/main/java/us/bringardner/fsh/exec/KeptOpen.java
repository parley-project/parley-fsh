package us.bringardner.fsh.exec;

import java.io.PrintStream;

/**
 * Output a command writes to that is someone else's (the caller's standard output): closing it
 * only flushes. A program writing to it writes to the stream it wraps itself (the terminal).
 */
final class KeptOpen extends PrintStream {

	final PrintStream target;

	KeptOpen(PrintStream target) {
		super(target, true);
		this.target = target;
	}

	@Override
	public void close() {
		flush();
	}

	/** the stream out writes to, under any KeptOpen */
	static java.io.OutputStream unwrap(java.io.OutputStream out) {
		while( out instanceof KeptOpen k ) {
			out = k.target;
		}
		return out;
	}
}
