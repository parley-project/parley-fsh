package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.memory.MemoryFileSourceFactory;
import us.bringardner.fsh.Console;
import us.bringardner.fsh.RootFile;
import us.bringardner.fsh.VirtualFileSourceFactory;

/** BJL-25: mount lookup, ".." and "~" work on whole path elements. */
public class TestVirtualPaths {

	@Test
	public void logicalPaths() {
		assertEquals("/", VirtualFileSourceFactory.logicalPath("/"));
		assertEquals("/", VirtualFileSourceFactory.logicalPath("/.."));
		assertEquals("/a/c", VirtualFileSourceFactory.logicalPath("//a/./b/../c/"));
		// a name containing ".." is just a name
		assertEquals("/data/a..b", VirtualFileSourceFactory.logicalPath("/data/a..b"));
		assertEquals("/x", VirtualFileSourceFactory.logicalPath("/data/../x"));
		// relative paths keep a leading ".." (used to throw StringIndexOutOfBoundsException)
		assertEquals("../x", VirtualFileSourceFactory.logicalPath("../x"));
		assertEquals("C:\\b", VirtualFileSourceFactory.logicalPath("C:\\a\\..\\b"));
		assertEquals("C:\\", VirtualFileSourceFactory.logicalPath("C:\\.."));
	}

	private static FileSource dir(MemoryFileSourceFactory mem, String path, String file) throws IOException {
		FileSource d = mem.createFileSource(path);
		assertTrue(d.mkdirs());
		try(OutputStream out = d.getChild(file).getOutputStream()) {
			out.write(1);
		}
		return d;
	}

	@Test
	public void aMountIsMatchedByWholePathElements() throws IOException {
		MemoryFileSourceFactory mem = new MemoryFileSourceFactory();
		FileSource primary = dir(mem, "/primary", "p.txt");
		VirtualFileSourceFactory vfs = new VirtualFileSourceFactory(primary);
		vfs.setCurrentDirectory(primary);
		assertTrue(vfs.mount("data", dir(mem, "/stores/data", "d.txt")));
		assertTrue(vfs.mount("database", dir(mem, "/stores/database", "db.txt")));

		// /database is its own mount, not a child of /data
		assertTrue(vfs.createFileSource("/database/db.txt").exists());
		assertTrue(vfs.createFileSource("/data/d.txt").exists());
		assertFalse(vfs.createFileSource("/data/db.txt").exists());
		assertEquals(mem.createFileSource("/stores/database/db.txt").getCanonicalPath(),
				vfs.createFileSource("/database/db.txt").getCanonicalPath());
		// ".." is resolved before the mount is chosen
		assertTrue(vfs.createFileSource("/data/../database/db.txt").exists());
		assertTrue(vfs.createFileSource("/data/sub/../d.txt").exists());
	}

	@Test
	public void mountedRootsUseTheSharedIsChildOfMine() throws IOException {
		assertThrows(NoSuchMethodException.class,
				() -> RootFile.class.getDeclaredMethod("isChildOfMine", FileSource.class));

		MemoryFileSourceFactory mem = new MemoryFileSourceFactory();
		FileSource primary = dir(mem, "/primary", "p.txt");
		VirtualFileSourceFactory vfs = new VirtualFileSourceFactory(primary);
		assertTrue(vfs.mount("data", dir(mem, "/stores/data", "d.txt")));
		dir(mem, "/stores/dataX", "x.txt");
		FileSource data = vfs.createFileSource("/data");
		assertTrue(data.isChildOfMine(vfs.createFileSource("/data/d.txt")));
		assertFalse(data.isChildOfMine(mem.createFileSource("/stores/dataX/x.txt")));
		assertFalse(data.isChildOfMine(mem.createFileSource("/stores/data/../dataX/x.txt")));
	}

	@Test
	public void homeIsAbbreviatedByWholePathElements() {
		assertEquals("~", Console.abbreviateHome("/home/tony", "/home/tony"));
		assertEquals("~/src", Console.abbreviateHome("/home/tony/src", "/home/tony"));
		assertEquals("/home/tony2", Console.abbreviateHome("/home/tony2", "/home/tony"));
		assertEquals("/etc", Console.abbreviateHome("/etc", "/home/tony"));
		assertEquals("/etc", Console.abbreviateHome("/etc", null));
	}
}
