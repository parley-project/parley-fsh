package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * <(cmd) read more than once, and $? after a command with a <(cmd): both depended on timing, so
 * each is done many times.
 */
public class TestProcessSubstitutionRaces extends AbstractConsoleTest {

	@BeforeAll
	public static void beforeAll() throws IOException {
		setup("TestFiles");
	}

	@Test
	public void readingAgainFindsTheEnd() {
		assumeTrue(getOs() != OperatingSystem.Windows, "named pipes");
		// (the writer reopened the pipe for each later reader, so fast that a reader waiting for
		// the end could wait for ever: about one run in ten hung)
		String script = "f() { wc -l < $1; wc -l < $1; true | wc -l < $1; }\n"
				+ "for i in {1..40}; do f <(echo one); done | tr -d ' ' | sort | uniq -c | tr -s ' '";
		ExecuteResult res = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> executeCommand(script, ""));
		assertEquals(" 80 0\n 40 1", res.getStdOut().stripTrailing());
	}

	@Test
	public void theSubstitutionDoesNotSetTheShellsStatus() {
		assumeTrue(getOs() != OperatingSystem.Windows, "named pipes");
		// (yes is cut off, status 141; that was sometimes $? instead of head's 0)
		String script = "for i in {1..40}; do head -c 1 <(yes) > /dev/null; printf %s $?; done";
		ExecuteResult res = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> executeCommand(script, ""));
		assertEquals("0".repeat(40), res.getStdOut().trim());
	}
}
