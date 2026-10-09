package us.bringardner.fsh.exec;

import java.io.IOException;
import java.io.InputStream;

import us.bringardner.fsh.SharedInput;

/**
 * Input from a named pipe or a device, read a byte at a time (what the shell does not read stays
 * for the programs that read it too). What is waiting in a pipe is not known (Java's available
 * may be 0), so available reads one byte in the background: read -t sees it when it comes, and
 * the byte is the next one read. It is shared with programs as a file is (see SharedInput).
 */
final class PipeInput extends InputStream implements SharedInput {
	private final InputStream source;
	/** the byte read ahead, -2 if none (-1 is the end) */
	private int ahead = -2;
	private Thread reading;
	/** put back (see unread) */
	private byte [] pushed = new byte[0];

	PipeInput(InputStream source) {
		this.source = source;
	}

	/** from a named pipe opened for reading and writing */
	PipeInput(java.io.RandomAccessFile raf) {
		this(new InputStream() {
			@Override
			public int read() throws IOException {
				return raf.read();
			}

			@Override
			public void close() throws IOException {
				raf.close();
			}
		});
	}

	@Override
	public synchronized int available() throws IOException {
		if( pushed.length > 0 ) {
			return pushed.length;
		}
		if( ahead != -2 ) {
			return ahead < 0 ? 0 : 1;
		}
		if( reading == null ) {
			reading = new Thread(() -> {
				int b;
				try {
					b = source.read();
				} catch (IOException e) {
					b = -1;
				}
				synchronized (PipeInput.this) {
					ahead = b;
					reading = null;
					PipeInput.this.notifyAll();
				}
			}, "named pipe read-ahead");
			reading.setDaemon(true);
			reading.start();
		}
		return 0;
	}

	@Override
	public synchronized boolean readyOrEnded() {
		try {
			available();
		} catch (IOException e) {
			return true;
		}
		return pushed.length > 0 || ahead != -2;
	}

	@Override
	public synchronized void unread(byte [] data, int off, int len) {
		byte [] all = new byte[len+pushed.length];
		System.arraycopy(data, off, all, 0, len);
		System.arraycopy(pushed, 0, all, len, pushed.length);
		pushed = all;
	}

	@Override
	public synchronized int read() throws IOException {
		if( pushed.length > 0 ) {
			int ret = pushed[0] & 0xff;
			pushed = java.util.Arrays.copyOfRange(pushed, 1, pushed.length);
			return ret;
		}
		while( reading != null ) {
			try {
				wait();
			} catch (InterruptedException e) {
				throw new java.io.InterruptedIOException();
			}
		}
		if( ahead != -2 ) {
			int b = ahead;
			ahead = -2;
			return b;
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
		int first = read();
		if( first < 0 ) {
			return -1;
		}
		b[off] = (byte) first;
		// (and what is there already: a program fed from it reads as much at once as from the pipe)
		int more = Math.min(len-1, source.available());
		int got = more > 0 ? source.read(b, off+1, more) : 0;
		return 1+Math.max(0, got);
	}

	@Override
	public void close() throws IOException {
		source.close();
	}
}
