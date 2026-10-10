package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.NativeStatus;

/** when the shell says that native support is limited (README: Native support) */
class NativeStatusTest {

	private static String notice(boolean provider, boolean posix, boolean helper) throws Exception {
		java.lang.reflect.Method m = NativeStatus.class.getDeclaredMethod("notice", boolean.class, boolean.class, boolean.class);
		m.setAccessible(true);
		return (String) m.invoke(null, provider, posix, helper);
	}

	@Test
	void nothingIsSaidWhenNothingIsLimited() throws Exception {
		assertNull(notice(true, true, true));
	}

	@Test
	void noNativeSupportIsSaid() throws Exception {
		String n = notice(false, true, false);
		assertNotNull(n);
		assertTrue(n.contains("no native support"));
		assertTrue(n.contains("--native-status"));
		// (on any platform, with or without the helper)
		assertNotNull(notice(false, false, false));
		assertNotNull(notice(false, true, true));
	}

	@Test
	void aMissingHelperIsSaidOnlyWhereThereIsOne() throws Exception {
		String n = notice(true, true, false);
		assertNotNull(n);
		assertTrue(n.contains("fshexec"));
		// (Windows has none, by design: that is not a limit to report)
		assertNull(notice(true, false, false));
	}

	@Test
	void theNoticeIsOneLine() throws Exception {
		for(boolean provider : new boolean[] {true, false}) {
			for(boolean posix : new boolean[] {true, false}) {
				for(boolean helper : new boolean[] {true, false}) {
					String n = notice(provider, posix, helper);
					if( n != null ) {
						assertTrue(!n.contains("\n") && n.length() < 160, n);
					}
				}
			}
		}
	}
}
