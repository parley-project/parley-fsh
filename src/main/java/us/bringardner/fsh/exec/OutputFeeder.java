package us.bringardner.fsh.exec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * A program's descriptor above 2 that the shell has open for writing (exec 3>file, cmd 3>&1 in
 * a pipe): the program writes a named pipe, and what comes out goes to the shell's stream. Java
 * gives a program only 0, 1 and 2; in bash it has the shell's other descriptors too.
 */
final class OutputFeeder extends Thread {

	final File fifo;
	private final OutputStream target;
	private final FileInputStream reader;
	/** keeps the pipe open (a read waits, not ends) until the program has opened its end */
	private FileOutputStream holder;
	private volatile boolean writing;

	private OutputFeeder(File fifo, OutputStream target) throws IOException {
		super("descriptor feeder");
		setDaemon(true);
		this.fifo = fifo;
		this.target = target;
		// read-write first, so neither end waits for the other to open
		try (RandomAccessFile both = new RandomAccessFile(fifo, "rw")) {
			reader = new FileInputStream(fifo);
			holder = new FileOutputStream(fifo);
		}
	}

	/** a feeder into out, or null where there are no named pipes */
	static OutputFeeder of(OutputStream out) {
		File fifo = StdinFeeder.free.poll();
		if( fifo == null ) {
			fifo = ProcessSubstitutions.makeFifo();
		}
		if( fifo == null ) {
			return null;
		}
		try {
			return new OutputFeeder(fifo, out);
		} catch (IOException e) {
			fifo.delete();
			return null;
		}
	}

	@Override
	public void run() {
		byte [] buf = new byte[8192];
		boolean ended = false;
		try {
			int n;
			while( (n = reader.read(buf)) > 0 ) {
				writing = true;
				try {
					target.write(buf, 0, n);
					target.flush();
				} finally {
					writing = false;
				}
			}
			ended = true;
		} catch (IOException e) {
			// the shell's stream is closed
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
			}
			if( ended ) {
				StdinFeeder.free.add(fifo);
			} else {
				fifo.delete();
			}
		}
	}

	/**
	 * The program has ended: what it wrote is out. (A program it left running that still has
	 * the descriptor goes on writing to the stream, as in bash.)
	 */
	void finish() {
		synchronized (this) {
			if( holder != null ) {
				try {
					holder.close();
				} catch (IOException e) {
				}
				holder = null;
			}
		}
		try {
			while( isAlive() && (writing || reader.available() > 0)) {
				join(2);
			}
			// (the end it sees now, when nothing else has it)
			join(20);
		} catch (IOException | InterruptedException e) {
		}
	}
}
