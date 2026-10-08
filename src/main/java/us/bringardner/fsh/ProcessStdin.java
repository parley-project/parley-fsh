package us.bringardner.fsh;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The shell's own standard input when it is not a terminal (a pipe or a file): read with no
 * buffer of the shell's, as bash reads it, so a program the shell runs reads it itself
 * (inherits it) from where the shell is, and what the program leaves stays for the shell:
 * printf 'a\nb\n' | fsh -c 'while read l; do /bin/echo "$l"; done' prints both lines.
 */
public final class ProcessStdin extends InputStream {

	private final FileInputStream in = new FileInputStream(FileDescriptor.in);

	@Override
	public int read() throws IOException {
		return in.read();
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return in.read(b, off, len);
	}

	@Override
	public int available() throws IOException {
		return in.available();
	}

	@Override
	public void close() {
		// the process's standard input stays open
	}
}
