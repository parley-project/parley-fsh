package us.bringardner.fsh.test;

import us.bringardner.parley.files.tree.VirtualFileSourceFactory;
import us.bringardner.parley.files.tree.TreeFile;
import us.bringardner.parley.files.tree.LocalFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import us.bringardner.fsh.Completion;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.memory.MemoryFileSourceFactory;
import us.bringardner.parley.term.line.Completions;

/**
 * The shell's tree with file systems connected into it: where a connection is listed, cd .. out
 * of one, connections in connections, globs, Tab, the local files programs get, and the table
 * read while it changes.
 */
public class TestConnections extends AbstractConsoleTest {

	@BeforeAll
	public static void beforeAll() throws Exception {
		setup("ConnectTestFiles");
	}

	/** (what the tests made in the test folder) */
	@AfterAll
	public static void afterAll() throws Exception {
		for(String name : new String[] {"hidden-by", "has-connection", "mountparent"}) {
			java.nio.file.Path dir = new File(testFilesDir, name).toPath();
			if( java.nio.file.Files.exists(dir)) {
				try (java.util.stream.Stream<java.nio.file.Path> all = java.nio.file.Files.walk(dir)) {
					all.sorted(java.util.Comparator.reverseOrder()).forEach(p->p.toFile().delete());
				}
			}
		}
	}

	private static String run(String code) throws Exception {
		ExecuteResult res = executeCommand(code, "");
		assertEquals("", res.getStdErr(), "stderr of: "+code);
		return res.getStdOut();
	}

	private static FileSource memoryTop() throws Exception {
		MemoryFileSourceFactory m = new MemoryFileSourceFactory();
		m.connect(new java.util.Properties());
		return m.listRoots()[0];
	}

	/** a connection in a connection: listed in it, cd .. goes back up through both */
	@Test
	public void connectionsInConnections() throws Exception {
		String out = run("connect memory /cin1 >/dev/null; mkdir -p /cin1/a; touch /cin1/a/f;"
				+ " connect memory /cin1/a/cin2 >/dev/null; touch /cin1/a/cin2/g;"
				+ " ls /cin1/a; echo --; ls /cin1/a/cin2; echo --;"
				+ " cd /cin1/a/cin2; pwd; cd ..; pwd; ls; cd ..; cd ..; pwd;"
				+ " echo /cin1/a/*; echo /cin1/a/cin2/*;"
				+ " disconnect /cin1/a/cin2 >/dev/null; ls /cin1/a; disconnect /cin1 >/dev/null");
		assertEquals("cin2\nf\n--\ng\n--\n/cin1/a/cin2\n/cin1/a\ncin2\nf\n/\n"
				+ "/cin1/a/cin2 /cin1/a/f\n/cin1/a/cin2/g\nf\n", out);
	}

	/** a connection hides a local file of the same name, and gives it back when it goes */
	@Test
	public void aConnectionHidesWhatIsThere() throws Exception {
		File dir = new File(testFilesDir, "hidden-by");
		new File(dir, "x").mkdirs();
		new File(dir, "x/local.txt").createNewFile();
		String d = dir.getAbsolutePath().replace('\\', '/');
		String out = run("connect memory '"+d+"/x' >/dev/null; touch '"+d+"/x/remote'; ls '"+d+"'; ls '"+d+"/x';"
				+ " disconnect '"+d+"/x' >/dev/null; ls '"+d+"/x'");
		assertEquals("x\nremote\nlocal.txt\n", out);
	}

	/** ls -l: a connection's time is its folder's, and what is connected there is shown */
	@Test
	public void lsLongShowsTheConnection() throws Exception {
		String out = run("connect memory /cls >/dev/null; ls -ld /cls; disconnect /cls >/dev/null");
		assertTrue(out.trim().endsWith("/cls -> memory"), out);
		assertFalse(out.contains("1969") || out.contains("1970"), out);
	}

	/** Tab completes a connection's name in the folder it's in, and the names in it */
	@Test
	public void tabCompletesConnections() throws Exception {
		run("connect memory /ctab-one >/dev/null; mkdir /ctab-one/inside");
		try {
			Completions.Result r = new Completion(console).complete("ls /ctab-o", 10);
			assertEquals("/ctab-one", r.commonPrefix());
			r = new Completion(console).complete("ls /ctab-one/in", 15);
			assertEquals("/ctab-one/inside", r.commonPrefix());
		} finally {
			run("disconnect /ctab-one");
		}
	}

	/**
	 * A local folder with a connection in it is the tree's (it lists the connection), and still
	 * the local folder for what needs one: a program started there, pwd -P.
	 */
	@DisabledOnOs(value = OS.WINDOWS, disabledReason = "runs /bin/pwd")
	@Test
	public void aLocalFolderWithAConnectionIsStillLocal() throws Exception {
		File dir = new File(testFilesDir, "has-connection").getCanonicalFile();
		dir.mkdirs();
		String d = dir.getAbsolutePath();
		run("connect memory '"+d+"/conn' >/dev/null");
		try {
			FileSource f = console.createFileSource(d);
			assertTrue(f instanceof TreeFile, f.getClass().getName());
			assertEquals(dir, LocalFiles.of(f));
			assertNull(LocalFiles.of(console.createFileSource(d+"/conn")));
			assertEquals(d+"\n"+d+"\n"+d+"\n", run("cd '"+d+"'; /bin/pwd; pwd -P; cd conn; cd ..; /bin/pwd"));
		} finally {
			run("disconnect '"+d+"/conn'");
		}
	}

	/** where connections can be: not /, not twice at one path, and disconnect only what is there */
	@Test
	public void whereConnectionsCanBe() throws Exception {
		VirtualFileSourceFactory tree = new VirtualFileSourceFactory();
		FileSource top = memoryTop();
		assertFalse(tree.mount("/", top));
		assertTrue(tree.mount("/w1", top));
		assertFalse(tree.mount("/w1/", top));
		assertTrue(tree.mount("w2", top));
		assertNotNull(tree.connectionAt("/w2"));
		assertTrue(tree.mount("/w1/x/../in", top));
		assertEquals(List.of("/w1", "/w1/in", "/w2"), new ArrayList<>(tree.connections().keySet()));
		assertNull(tree.unmount("/"));
		assertNull(tree.unmount("/nothing"));
		assertEquals(top, tree.unmount("/w1"));
		// (the one in it stays, at its path)
		assertTrue(tree.createFileSource("/w1/in") instanceof TreeFile);
		assertEquals("/w1/in", tree.createFileSource("/w1/in").getAbsolutePath());
	}

	/**
	 * Threads that list and look up files while others connect and disconnect see one table at a
	 * time: no exception, and every listing of / has the connections that stayed.
	 */
	@Test
	public void theTableIsReadWhileItChanges() throws Exception {
		VirtualFileSourceFactory tree = new VirtualFileSourceFactory();
		FileSource top = memoryTop();
		top.getChild("there").mkdir();
		assertTrue(tree.mount("/stays", top));
		int writers = 4;
		int readers = 4;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(writers + readers);
		AtomicReference<Throwable> failed = new AtomicReference<>();
		for(int w = 0; w < writers; w++) {
			int n = w;
			new Thread(()->{
				try {
					start.await();
					for(int i = 0; i < 300; i++) {
						String p = "/churn"+n+"-"+(i % 5);
						tree.mount(p, top);
						tree.unmount(p);
					}
				} catch (Throwable e) {
					failed.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			}).start();
		}
		for(int r = 0; r < readers; r++) {
			new Thread(()->{
				try {
					start.await();
					for(int i = 0; i < 300; i++) {
						boolean stays = false;
						for(FileSource f : tree.createFileSource("/").listFiles()) {
							stays |= f.getName().equals("stays");
						}
						if( !stays ) {
							throw new AssertionError("/stays not listed");
						}
						if( !tree.createFileSource("/stays/there").isDirectory()) {
							throw new AssertionError("/stays/there not found");
						}
					}
				} catch (Throwable e) {
					failed.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			}).start();
		}
		start.countDown();
		done.await();
		if( failed.get() != null ) {
			throw new AssertionError(failed.get());
		}
		assertEquals(List.of("/stays"), new ArrayList<>(tree.connections().keySet()));
	}
}
