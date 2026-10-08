package us.bringardner.fsh.exec;

import java.io.IOException;
import java.io.InputStream;

import us.bringardner.fsh.SharedInput;

/**
 * Input from a file or a here-document (a read never waits): what a program was sent and did
 * not read can be put back, so the shell and its programs share it as bash's do.
 */
final class ReadBack extends InputStream implements SharedInput {

	private final InputStream source;
	private byte [] pushed = new byte[0];

	ReadBack(InputStream source) {
		this.source = source;
	}

	@Override
	public synchronized void unread(byte[] data, int off, int len) {
		byte [] all = new byte[len+pushed.length];
		System.arraycopy(data, off, all, 0, len);
		System.arraycopy(pushed, 0, all, len, pushed.length);
		pushed = all;
	}

	@Override
	public boolean readyOrEnded() {
		return true;
	}

	@Override
	public synchronized int read() throws IOException {
		if( pushed.length > 0 ) {
			int ret = pushed[0] & 0xff;
			pushed = java.util.Arrays.copyOfRange(pushed, 1, pushed.length);
			return ret;
		}
		return source.read();
	}

	@Override
	public synchronized int read(byte[] b, int off, int len) throws IOException {
		if( len == 0 ) {
			return 0;
		}
		if( pushed.length > 0 ) {
			int n = Math.min(len, pushed.length);
			System.arraycopy(pushed, 0, b, off, n);
			pushed = java.util.Arrays.copyOfRange(pushed, n, pushed.length);
			return n;
		}
		return source.read(b, off, len);
	}

	@Override
	public synchronized int available() throws IOException {
		return pushed.length+source.available();
	}

	@Override
	public void close() throws IOException {
		source.close();
	}
}
