package us.bringardner.fsh.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.spi.EditRequest;
import us.bringardner.fsh.spi.EditorProvider;
import us.bringardner.parley.files.FileSource;

/**
 * Editors for TestBrowserBuiltins, which runs fsh with them on its class path. Each is available only
 * when the system property fsh.test.editors (a list of names) names it.
 */
public class TestEditors {

	static boolean listed(String name) {
		String list = System.getProperty("fsh.test.editors");
		return list != null && Arrays.asList(list.split(",")).contains(name);
	}

	/** Adds a line "NAME" to the file, through its FileSource. */
	abstract static class Appender implements EditorProvider {
		private final String name;

		Appender(String name) {
			this.name = name;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String description() {
			return "a test editor";
		}

		@Override
		public boolean available(ShellContext ctx) {
			return listed(name);
		}

		@Override
		public int edit(ShellContext ctx, EditRequest request) throws IOException {
			FileSource file = request.file;
			ByteArrayOutputStream text = new ByteArrayOutputStream();
			if( file.exists()) {
				try (InputStream in = file.getInputStream()) {
					in.transferTo(text);
				}
			}
			text.write((name+" edited "+request.name+"\n").getBytes(StandardCharsets.UTF_8));
			try (OutputStream out = file.getOutputStream()) {
				out.write(text.toByteArray());
			}
			return 0;
		}
	}

	public static class First extends Appender {
		public First() {
			super("test-first");
		}
	}

	public static class Second extends Appender {
		public Second() {
			super("test-second");
		}
	}

	public static class Failing extends Appender {
		public Failing() {
			super("test-failing");
		}

		@Override
		public int edit(ShellContext ctx, EditRequest request) throws IOException {
			throw new IOException("the disk is on fire");
		}
	}
}
