package us.bringardner.fsh;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/** a closed descriptor (>&-): writing to it is an error */
public class ClosedStream extends PrintStream {
	public ClosedStream() {
		super(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new IOException("Bad file descriptor");
			}
		});
	}
}
