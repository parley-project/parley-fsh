package us.bringardner.parley.plugin;

/**
 * Lets fsh's tests call parley-core's package-private {@link Plugins#reset()}. Plugins load once
 * per JVM, so a test that loads its own plugin folders must first forget what an earlier test
 * loaded (an interactive shell loads the default folders when it starts).
 */
public final class PluginsForTests {

	private PluginsForTests() {
	}

	/** forget the loaded plugins, their class loader and the listeners */
	public static void reset() {
		Plugins.reset();
	}
}
