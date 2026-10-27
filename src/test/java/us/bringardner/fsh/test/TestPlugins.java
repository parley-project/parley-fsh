package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.Console;
import us.bringardner.parley.plugin.PluginFolders;
import us.bringardner.parley.plugin.Plugins;
import us.bringardner.parley.plugin.PluginsForTests;

/**
 * A plugin jar in fsh's plugin folder: its commands (one shadowed by a builtin), its browser, and
 * the plugins and browse builtins.
 */
public class TestPlugins extends AbstractConsoleTest {

	@TempDir
	static Path tmp;
	private static ClassLoader before;

	private static final Map<String, String> SOURCES = Map.of(
			"tp.Hello", "package tp; public class Hello extends us.bringardner.fsh.ShellCommand {"
					+ " public Hello() { super(\"hello-plugin\", \"hello-plugin: says hello\"); }"
					+ " public int process(us.bringardner.fsh.ShellContext ctx) { ctx.stdout.println(\"hello from a plugin\"); return 0; } }",
			"tp.MyEcho", "package tp; public class MyEcho extends us.bringardner.fsh.ShellCommand {"
					+ " public MyEcho() { super(\"echo\", \"not the builtin\"); }"
					+ " public int process(us.bringardner.fsh.ShellContext ctx) { ctx.stdout.println(\"wrong echo\"); return 0; } }",
			"tp.Commands", "package tp; public class Commands implements us.bringardner.fsh.spi.CommandProvider {"
					+ " public java.util.List<us.bringardner.fsh.ShellCommand> commands() { return java.util.List.of(new Hello(), new MyEcho()); } }",
			"tp.Browser", "package tp; import us.bringardner.fsh.spi.*; public class Browser implements BrowserProvider {"
					+ " public String name() { return \"testbrowser\"; }"
					+ " public String description() { return \"a browser for the tests\"; }"
					+ " public int priority() { return 10; }"
					+ " public boolean available(us.bringardner.fsh.ShellContext ctx) { return true; }"
					+ " public us.bringardner.parley.files.FileSource browse(us.bringardner.fsh.ShellContext ctx, BrowseRequest r) {"
					+ "  ctx.stdout.println(\"browsing \"+r.left.getName()+\" \"+r.right.getName()+\" \"+r.onePane);"
					+ "  if( r.left.getName().equals(\"conn\") ) { try {"
					+ "   us.bringardner.parley.files.memory.MemoryFileSourceFactory m = new us.bringardner.parley.files.memory.MemoryFileSourceFactory();"
					+ "   m.connect(new java.util.Properties());"
					+ "   ctx.stdout.println(\"connected \"+r.connections.connect(\"/plugmem\", m.listRoots()[0])+\" \"+r.connections.list().keySet()"
					+ "    +\" \"+r.connections.file(\"/plugmem\").isDirectory());"
					+ "  } catch (java.io.IOException e) { throw new RuntimeException(e); } }"
					+ "  return r.right; } }");

	@BeforeAll
	public static void plugin() throws Exception {
		File classes = tmp.resolve("classes").toFile();
		List<String> args = new ArrayList<>(List.of("-d", classes.getPath(), "-cp", System.getProperty("java.class.path")));
		for(Map.Entry<String, String> e : SOURCES.entrySet()) {
			File f = new File(classes, "src/"+e.getKey().replace('.', '/')+".java");
			f.getParentFile().mkdirs();
			Files.write(f.toPath(), e.getValue().getBytes(StandardCharsets.UTF_8));
			args.add(f.getPath());
		}
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(new String[0])));
		File folder = tmp.resolve("plugins").toFile();
		folder.mkdirs();
		Manifest m = new Manifest();
		m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		m.getMainAttributes().putValue(Plugins.MANIFEST_ATTRIBUTE, "1");
		try (JarOutputStream out = new JarOutputStream(new FileOutputStream(new File(folder, "testplugin.jar")), m)) {
			for(String cls : SOURCES.keySet()) {
				String path = cls.replace('.', '/')+".class";
				out.putNextEntry(new JarEntry(path));
				out.write(Files.readAllBytes(new File(classes, path).toPath()));
				out.closeEntry();
			}
			out.putNextEntry(new JarEntry("META-INF/services/us.bringardner.fsh.spi.CommandProvider"));
			out.write("tp.Commands\n".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
			out.putNextEntry(new JarEntry("META-INF/services/us.bringardner.fsh.spi.BrowserProvider"));
			out.write("tp.Browser\n".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		Properties sys = new Properties();
		sys.setProperty(PluginFolders.PROP_PATH, folder.getPath());
		before = Thread.currentThread().getContextClassLoader();
		// plugins load once per JVM, and an earlier test's interactive shell (TestJobControl's) has
		// loaded the default folders: forget them, or this load returns that report
		PluginsForTests.reset();
		Plugins.load(new PluginFolders("fsh", false, null, sys, null, false, tmp.toString(), null), before);
		Console.addPluginCommands();
		console = new Console();
	}

	@AfterAll
	public static void restore() {
		Thread.currentThread().setContextClassLoader(before);
		// (the other tests see fsh's own commands only)
		for(String name : Console.pluginCommands.keySet()) {
			Console.commands.remove(name);
		}
		Console.pluginCommands.clear();
		Console.pluginNotes.clear();
		// and the test plugin's jar and browser are gone (a later shell loads the default folders)
		PluginsForTests.reset();
	}

	@Test
	public void commandFromAPlugin() throws Exception {
		ExecuteResult r = executeCommand("hello-plugin; echo builtin", "");
		assertEquals("hello from a plugin\nbuiltin\n", r.getStdOut());
		assertEquals(0, r.exitCode);
	}

	@Test
	public void pluginsReport() throws Exception {
		String out = executeCommand("plugins", "").getStdOut();
		assertTrue(out.contains("testplugin.jar: plugin"), out);
		assertTrue(out.contains("  hello-plugin (testplugin.jar)"), out);
		assertTrue(out.contains("  testbrowser (testplugin.jar)"), out);
		assertTrue(out.contains("note: echo from testplugin.jar: not added, a builtin has the name"), out);
	}

	@Test
	public void browse() throws Exception {
		File left = tmp.resolve("left").toFile();
		File right = tmp.resolve("right").toFile();
		left.mkdirs();
		right.mkdirs();
		String l = left.getPath().replace('\\', '/');
		String r = right.getPath().replace('\\', '/');
		ExecuteResult res = executeCommand("browse --list", "");
		// (fsh's own terminal browser can't run here: the output isn't the terminal)
		assertEquals("  terminal - the full-screen browser on Parley's own terminal code (cannot run here)\n"
				+ "* testbrowser - a browser for the tests\n", res.getStdOut());
		res = executeCommand("browse -1 '"+l+"' '"+r+"'", "");
		assertEquals("browsing left right true\n", res.getStdOut());
		res = executeCommand("cd '"+l+"'; browse -c --with testbrowser . '"+r+"'; pwd", "");
		assertEquals("browsing left right false\n"+r+"\n", res.getStdOut().replace('\\', '/'));
		res = executeCommand("browse --with nosuch", "");
		assertEquals(1, res.exitCode);
		assertEquals("fsh: line 1: browse: nosuch: no such browser (browse --list lists them)\n", res.getStdErr());
		res = executeCommand("browse '"+l+"/none'", "");
		assertEquals(1, res.exitCode);
		// the browser connects a file system in the shell (ShellConnections): it stays after browse
		File conn = tmp.resolve("conn").toFile();
		conn.mkdirs();
		res = executeCommand("browse '"+conn.getPath().replace('\\', '/')+"' '"+r+"'; ls -d /plugmem; disconnect /plugmem; ls -d /plugmem", "");
		assertEquals("browsing conn right false\nconnected true [/plugmem] true\n/plugmem\n", res.getStdOut());
		assertTrue(res.getStdErr().contains("/plugmem"), res.getStdErr());
	}
}
