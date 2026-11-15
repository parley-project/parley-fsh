package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A program runs on the local disk, so fsh does not start one (status 126, with an error saying
 * why) when the current directory, or a file an argument names, is on a connected file system.
 * fsh's own commands still work there.
 */
public class TestRemotePrograms {

	@TempDir
	Path tmp;

	/** a program every system has: this JVM's java, as fsh quotes it */
	private static final String JAVA = "\""+new File(System.getProperty("java.home"), "bin/java").getPath().replace('\\', '/')+"\"";

	/** stdout and stderr of fsh -c script, run in its own process with no plugins */
	private String fsh(String script) throws Exception {
		List<String> cmd = new ArrayList<>(Arrays.asList(
				new File(System.getProperty("java.home"), "bin/java").getPath(),
				"-cp", System.getProperty("java.class.path"),
				"-Dparley.plugins.path="+tmp.resolve("no-plugins"),
				"us.bringardner.fsh.Console", "-c", script));
		// (as fsh's launch scripts do: Java 24+ warns on System.loadLibrary without it,
		// which would end up in the output; Java 11 doesn't know the option)
		if( Runtime.version().feature() >= 17 ) {
			cmd.add(1, "--enable-native-access=ALL-UNNAMED");
		}
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(tmp.toFile()).redirectErrorStream(true);
		pb.environment().remove("JAVA_TOOL_OPTIONS");
		Process p = pb.start();
		try {
			p.getOutputStream().close();
			byte[] out;
			try(InputStream in = p.getInputStream()) {
				out = in.readAllBytes();
			}
			assertTrue(p.waitFor(60, TimeUnit.SECONDS));
			return new String(out, StandardCharsets.UTF_8).replace("\r\n", "\n");
		} finally {
			p.destroyForcibly();
			p.getErrorStream().close();
		}
	}

	@Test
	public void notInARemoteDirectory() throws Exception {
		String out = fsh("connect memory /mem >/dev/null; mkdir /mem/w; cd /mem/w; "+JAVA+" -version; echo status $?");
		assertTrue(out.contains("cannot run: the current directory /mem/w is on a remote file system;"
				+ " programs run only on the local disk"), out);
		assertTrue(out.endsWith("status 126\n"), out);
	}

	@Test
	public void notOnARemoteFile() throws Exception {
		String out = fsh("connect memory /mem >/dev/null; echo hi > /mem/f.txt; "+JAVA+" /mem/f.txt; echo status $?");
		assertTrue(out.contains("cannot run: /mem/f.txt is on a remote file system;"), out);
		assertTrue(out.endsWith("status 126\n"), out);
	}

	@Test
	public void fshsOwnCommandsWorkThere() throws Exception {
		assertEquals("hi\n1\n", fsh("connect memory /mem >/dev/null; mkdir /mem/w; cd /mem/w; echo hi > a; cat a; grep -c hi a"));
	}

	@Test
	public void localProgramsStillRun() throws Exception {
		assertEquals("status 0\n", fsh("connect memory /mem >/dev/null; "+JAVA+" -version >/dev/null 2>&1; echo status $?"));
	}
}
