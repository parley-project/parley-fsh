package us.bringardner.fsh.exec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentLinkedQueue;

import us.bringardner.fsh.SharedInput;

/**
 * A program's standard input from input the shell shares with it (a file, a here-document, a
 * pipe): it goes through a named pipe the shell can read too, and when the program ends, what it
 * did not read is put back for the next reader. In bash the program reads the shell's file
 * itself, so while read l; do prog; done < file leaves the lines prog does not read.
 */
final class StdinFeeder extends Thread {

	/** named pipes that are empty and free again */
	private static final ConcurrentLinkedQueue<File> free = new ConcurrentLinkedQueue<>();

	final File fifo;
	private final InputStream source;
	private final SharedInput shared;
	private final FileInputStream reader;
	private FileOutputStream writer;
	private volatile boolean stop;

	private StdinFeeder(File fifo, InputStream source) throws IOException {
		super("stdin feeder");
		setDaemon(true);
		this.fifo = fifo;
		this.source = source;
		this.shared = (SharedInput) source;
		// read-write first, so neither end waits for the other to open
		try (RandomAccessFile both = new RandomAccessFile(fifo, "rw")) {
			reader = new FileInputStream(fifo);
			writer = new FileOutputStream(fifo);
		}
	}

	/** a feeder for in (a SharedInput), or null where there are no named pipes */
	static StdinFeeder of(InputStream in) {
		if( !(in instanceof SharedInput)) {
			return null;
		}
		File fifo = free.poll();
		if( fifo == null ) {
			fifo = ProcessSubstitutions.makeFifo();
		}
		if( fifo == null ) {
			return null;
		}
		try {
			return new StdinFeeder(fifo, in);
		} catch (IOException e) {
			fifo.delete();
			return null;
		}
	}

	@Override
	public void run() {
		byte [] buf = new byte[8192];
		try {
			while( !stop ) {
				// never wait in a read: the program may end first, and what comes then is not its
				if( !shared.readyOrEnded()) {
					Thread.sleep(5);
					continue;
				}
				int n = source.read(buf, 0, buf.length);
				if( n < 0 ) {
					break;
				}
				writer.write(buf, 0, n);
			}
		} catch (IOException | InterruptedException e) {
			// the program is gone
		} finally {
			closeWriter();
		}
	}

	private synchronized void closeWriter() {
		if( writer != null ) {
			try {
				writer.close();
			} catch (IOException e) {
			}
			writer = null;
		}
	}

	/** the program has ended: what it did not read goes back to the input */
	void finish() {
		stop = true;
		ByteArrayOutputStream left = new ByteArrayOutputStream();
		try {
			// the feeder may be waiting to write to the full pipe
			while( isAlive()) {
				drain(left);
				join(2);
			}
			closeWriter();
			byte [] buf = new byte[8192];
			int n;
			while( (n = reader.read(buf)) > 0 ) {
				left.write(buf, 0, n);
			}
			reader.close();
			free.add(fifo);
		} catch (IOException | InterruptedException e) {
			fifo.delete();
		}
		if( left.size() > 0 ) {
			shared.unread(left.toByteArray(), 0, left.size());
		}
	}

	private void drain(ByteArrayOutputStream left) throws IOException {
		int n = reader.available();
		if( n > 0 ) {
			byte [] buf = new byte[n];
			int got = reader.read(buf);
			if( got > 0 ) {
				left.write(buf, 0, got);
			}
		}
	}
}
