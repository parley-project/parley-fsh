package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.NativeKeyboard;
import us.bringardner.fsh.exec.NativeProcess;

/**
 * The parts of NATIVE-CONTRACT.md that can be checked without a terminal, against whichever
 * implementation is loaded: run it with -Dfsh.native=jna, and with -Dfsh.native=jni and
 * -Djava.library.path=(macos|linux|windows) to check the JNI library (see the pom's argLine).
 *
 * It does not read the keyboard (the test runner uses standard input itself).
 */
class NativeKeyboardContractTest {

	private static void available() {
		assumeTrue(NativeKeyboard.isAvailible(), "no native implementation is loaded");
	}

	// A. keyboard and terminal: every function is there, and none throws

	@Test
	void inputKindIsOneOfTheDefinedValues() {
		available();
		int kind = NativeKeyboard.inputKind();
		assertTrue(kind >= 0 && kind <= 2, "inputKind "+kind);
	}

	@Test
	void isattyIsFalseForADescriptorThatIsNotOpen() {
		available();
		assertFalse(NativeKeyboard.isatty(987));
		// (0, 1 and 2 answer, whichever they are)
		NativeKeyboard.isatty(0);
		NativeKeyboard.isatty(1);
		NativeKeyboard.isatty(2);
	}

	@Test
	void terminalNeedsAnInputTerminal() {
		available();
		if( NativeKeyboard.terminal() ) {
			assertTrue(NativeKeyboard.inputIsTerminal());
			assertTrue(NativeKeyboard.isatty(0) && NativeKeyboard.isatty(1));
		}
	}

	@Test
	void ttyNameIsNeverNull() {
		available();
		String name = NativeKeyboard.ttyName();
		assertNotNull(name);
		assertFalse(name.contains("/"), "without /dev/: "+name);
	}

	@Test
	void resourceUsageIsFourNonNegativeMicroseconds() {
		available();
		long [] usage = NativeKeyboard.resourceUsage();
		assertNotNull(usage);
		assertEquals(4, usage.length);
		for(long v : usage) {
			assertTrue(v >= 0, "negative time");
		}
	}

	@Test
	void inheritedDescriptorsAreNumberedPairs() {
		available();
		int [] fds = NativeKeyboard.inheritedDescriptors();
		assertNotNull(fds);
		assertEquals(0, fds.length % 2);
		for(int i = 0; i < fds.length; i += 2) {
			assertTrue(fds[i] >= 3 && fds[i] <= 255, "descriptor "+fds[i]);
			assertTrue(fds[i+1] >= 0 && fds[i+1] <= 2, "mode "+fds[i+1]);
		}
	}

	// B. process information: the value, or "not known" (null, -1), or not provided at all

	@Test
	void processGroupIsPositive() {
		available();
		try {
			assertTrue(NativeKeyboard.pgrp() > 0);
		} catch (UnsatisfiedLinkError e) {
			// not provided
		}
	}

	@Test
	void idsAreNullOrUidGidAndGroups() {
		available();
		try {
			long [] ids = NativeKeyboard.ids();
			if( ids != null ) {
				assertTrue(ids.length >= 2);
				for(long id : ids) {
					assertTrue(id >= 0);
				}
			}
		} catch (UnsatisfiedLinkError e) {
		}
	}

	@Test
	void startedWithAnswersOneZeroOrUnknown() {
		available();
		try {
			int path = NativeKeyboard.startedWith("PATH");
			assertTrue(path == 1 || path == -1, "PATH: "+path);
			int none = NativeKeyboard.startedWith("FSH_THERE_IS_NO_SUCH_VARIABLE");
			assertTrue(none == 0 || none == -1, "unset: "+none);
		} catch (UnsatisfiedLinkError e) {
		}
	}

	@Test
	void argv0AndReleaseAreNullOrText() {
		available();
		try {
			String a = NativeKeyboard.argv();
			assertTrue(a == null || !a.isEmpty());
			String r = NativeKeyboard.osRelease();
			assertTrue(r == null || !r.isEmpty());
		} catch (UnsatisfiedLinkError e) {
		}
	}

	@Test
	void stoppedByAnUnknownChildIsZero() {
		available();
		try {
			assertEquals(0, NativeKeyboard.stopSignal(-1));
		} catch (UnsatisfiedLinkError e) {
		}
	}

	// C. starting programs: all or none, and the capability says which

	@Test
	void startingProgramsIsAllOrNone() {
		available();
		if( !NativeProcess.usable() ) {
			assertTrue(!NativeKeyboard.isJniAvailible() || java.io.File.separatorChar != '/'
					|| "java".equals(System.getProperty("fsh.spawn")) || probeFails());
			return;
		}
		String text = NativeKeyboard.strerror(2);
		assertNotNull(text);
		assertFalse(text.isEmpty());
		int [] p = NativeKeyboard.pipe();
		assertNotNull(p);
		assertEquals(2, p.length);
		assertTrue(p[0] >= 0 && p[1] >= 0 && p[0] != p[1]);
		NativeKeyboard.close(p[0]);
		NativeKeyboard.close(p[1]);
		// (kill with signal 0 only tests: this process exists)
		assertEquals(0, NativeKeyboard.kill((int) ProcessHandle.current().pid(), 0));
		assertTrue(NativeKeyboard.kill(Integer.MAX_VALUE, 0) < 0);
	}

	/** an older library without strerror0 (the capability's own probe) */
	private static boolean probeFails() {
		try {
			NativeKeyboard.strerror(0);
			return false;
		} catch (UnsatisfiedLinkError e) {
			return true;
		}
	}

	// the JNA layer must load where there is one: every other test here skips when nothing is
	// loaded, which is how WindowsOps failing to load on 64-bit Windows (an unsupported calling
	// convention) went unnoticed

	@Test
	void theJnaLayerLoadsOnWindowsAndLinuxAndMac() {
		String os = System.getProperty("os.name", "").toLowerCase();
		assumeTrue(os.startsWith("windows") || os.startsWith("linux") || os.startsWith("mac"),
				"no JNA layer for " + os);
		assertNotNull(us.bringardner.fsh.jna.NativeOps.create(),
				"the JNA layer did not load: " + us.bringardner.fsh.jna.NativeOps.failure);
	}}
