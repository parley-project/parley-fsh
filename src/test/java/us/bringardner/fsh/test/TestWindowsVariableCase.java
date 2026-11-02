package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/**
 * Windows names its search path Path and ignores case in its environment. A shell variable does not: a
 * script's path is its own variable, and PATH (as scripts write it) is the environment's Path.
 */
public class TestWindowsVariableCase extends AbstractConsoleTest {

	private static String windowsPath() throws Exception {
		assumeTrue(getOs()==OperatingSystem.Windows);
		setup("ExternalTestFiles");
		// the environment as Windows has it: the name is Path
		Object orig = console.getEvironmentVariables("PATH");
		console.removeEnvironmentVariables("PATH");
		console.removeEnvironmentVariables("Path");
		console.setEnvironmentVariable("Path", orig);
		return String.valueOf(orig);
	}

	@Test
	public void aScriptsPathIsNotThePathItRunsProgramsFrom() throws Exception {
		String orig = windowsPath();
		ExecuteResult r = executeCommand("path=/tmp/mine; echo \"[$path]\"; echo \"[$PATH]\"; echo \"[$Path]\"; whoami >/dev/null; echo rc=$?\n", "");
		String[] lines = r.getBao().toString().replace("\r", "").split("\n");
		assertEquals("[/tmp/mine]", lines[0]);
		assertEquals("["+orig+"]", lines[1]);
		assertEquals("["+orig+"]", lines[2]);
		assertEquals("rc=0", lines[3], "programs are still found");
		assertEquals(orig, String.valueOf(console.getEvironmentVariables("Path")));
	}

	@Test
	public void exportingPATHChangesPath() throws Exception {
		String orig = windowsPath();
		ExecuteResult r = executeCommand("export PATH=\"/zz;$PATH\"; echo \"${Path:0:4}\"; for v in $(compgen -v); do case $v in [Pp][Aa][Tt][Hh]) echo \"var $v\";; esac; done\n", "");
		String out = r.getBao().toString().replace("\r", "");
		assertTrue(out.startsWith("/zz;"), out);
		assertTrue(out.contains("var Path"), out);
		assertTrue(!out.contains("var PATH"), "no second PATH: "+out);
		assertEquals("/zz;"+orig, String.valueOf(console.getEvironmentVariables("Path")));
	}
}
