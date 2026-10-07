package us.bringardner.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/**
 * A pipe between two stages of a pipeline: what is written to {@link #out} is read from {@link #in}.
 * The reader sees the end only when the writer closes out. (java.io.PipedInputStream also ends a
 * read with "Write end dead" when the last thread that wrote has finished, which happens when a
 * program's output is copied by a thread that ends before the stage closes the pipe.)
 * Writing after the reader closed in fails, as a broken pipe.
 */
public class Pipe {

	private final byte [] buf = new byte[64*1024];
	private int head;
	private int count;
	private boolean writerClosed;
	private boolean readerClosed;

	public final InputStream in = new InputStream() {
		@Override
		public int read() throws IOException {
			byte [] one = new byte[1];
			int n = read(one, 0, 1);
			return n < 0 ? -1 : one[0] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if( len == 0 ) {
				return 0;
			}
			synchronized (Pipe.this) {
				while( count == 0 && !writerClosed && !readerClosed ) {
					waitHere();
				}
				if( count == 0 ) {
					return -1;
				}
				int n = Math.min(len, count);
				for (int i = 0; i < n; i++) {
					b[off+i] = buf[(head+i) % buf.length];
				}
				head = (head+n) % buf.length;
				count -= n;
				Pipe.this.notifyAll();
				return n;
			}
		}

		@Override
		public int available() {
			synchronized (Pipe.this) {
				return count;
			}
		}

		@Override
		public void close() {
			synchronized (Pipe.this) {
				readerClosed = true;
				Pipe.this.notifyAll();
			}
		}
	};

	public final OutputStream out = new OutputStream() {
		@Override
		public void write(int b) throws IOException {
			write(new byte[] {(byte) b}, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			synchronized (Pipe.this) {
				while( len > 0 ) {
					if( readerClosed ) {
						throw new IOException("Broken pipe");
					}
					if( writerClosed ) {
						throw new IOException("Pipe closed");
					}
					if( count == buf.length ) {
						waitHere();
						continue;
					}
					int tail = (head+count) % buf.length;
					int n = Math.min(len, Math.min(buf.length-count, buf.length-tail));
					System.arraycopy(b, off, buf, tail, n);
					count += n;
					off += n;
					len -= n;
					Pipe.this.notifyAll();
				}
			}
		}

		@Override
		public void close() {
			synchronized (Pipe.this) {
				writerClosed = true;
				Pipe.this.notifyAll();
			}
		}
	};

	private void waitHere() throws InterruptedIOException {
		try {
			wait();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("pipe interrupted");
		}
	}
}
