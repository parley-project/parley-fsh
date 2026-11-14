/**
 * <PRE>
 *
 * Copyright Tony Bringardner 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *	@author Tony Bringardner
 */
package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.Console;

/**
 * fsh's own versions of the system's programs (Console.UTILITIES): builtins where the system has
 * no such program, and otherwise only after enable name or shopt -s fshutils.
 */
public class TestFshUtils {

	@TempDir
	Path tmp;

	/** stdout and stderr of fsh -c script, run in its own process with no plugins */
	private String fsh(String script) throws Exception {
		List<String> cmd = List.of(
				new File(System.getProperty("java.home"), "bin/java").getPath(),
				"-cp", System.getProperty("java.class.path"),
				"-Dparley.plugins.path="+tmp.resolve("no-plugins"),
				"us.bringardner.fsh.Console", "-c", script);
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
	public void offWhereTheSystemHasThem() throws Exception {
		assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
		assertEquals("file\nbuiltin\nfile\n",
				fsh("type -t cat; enable cat; type -t cat; enable -n cat; type -t cat"));
	}

	@Test
	public void shoptFshutils() throws Exception {
		assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
		assertEquals("1\nbuiltin\nbuiltin\n0\nfile\n",
				fsh("shopt -q fshutils; echo $?; shopt -s fshutils; type -t cat; type -t sha256sum; "
						+ "shopt -q fshutils; echo $?; shopt -u fshutils; type -t cat"));
		// (enable -n wins: that one stays off)
		assertEquals("file\n", fsh("enable -n cat; shopt -s fshutils; type -t cat"));
	}

	@Test
	public void alwaysOnWhereTheSystemHasNone() throws Exception {
		assumeTrue(Console.isMacos() || System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
		assertEquals("builtin\nbuiltin\n", fsh("type -t tac; type -t wget"));
	}

	@Test
	public void enableLists() throws Exception {
		assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
		String off = fsh("enable -n");
		assertTrue(off.contains("enable -n cat\n"), off);
		assertTrue(fsh("shopt -s fshutils; enable -n").isEmpty());
	}
}
