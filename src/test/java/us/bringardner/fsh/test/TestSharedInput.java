package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Programs read the shell's input file themselves (through macos/fshexec), from where the shell
 * is, and the shell goes on from where they left it: head -n 1 reads a block and seeks back
 * after its line, as bash's programs do on a file they share with it. (Checked against bash.)
 */
public class TestSharedInput extends AbstractConsoleTest {

	private static final File HELPER = new File("macos/fshexec");

	@BeforeAll
	public static void setUp() throws Exception {
		AbstractConsoleTest.setup("TestFiles");
		System.setProperty("fsh.exec", HELPER.getAbsolutePath());
	}

	@AfterAll
	public static void tearDown() {
		System.clearProperty("fsh.exec");
	}

	@Test
	public void programsLeaveTheRestOfTheFile() throws Exception {
		assumeTrue(HELPER.canExecute(), "no fshexec");
		File dir = Files.createTempDirectory("shared").toFile();
		File f = new File(dir, "f");
		Files.writeString(f.toPath(), "a\nb\nc\nd\n");
		String path = f.getAbsolutePath();
		String code = "while read l; do echo \"x=$l\"; head -n 1; done < "+path+"\n"
				+ "{ head -n 1; read x; echo \"x=$x\"; cat; } < "+path+"\n"
				+ "{ head -n 1; sort -r; } < "+path+"\n"
				+ "while read l; do /bin/echo \"f=$l\"; done < "+path+"\n";
		ExecuteResult res = executeCommand(code, "");
		assertEquals("", res.getStdErr());
		assertEquals("x=a\nb\nx=c\nd\n"
				+ "a\nx=b\nc\nd\n"
				+ "a\nd\nc\nb\n"
				+ "f=a\nf=b\nf=c\nf=d\n", res.getStdOut());
		f.delete();
		dir.delete();
	}
}
