package us.bringardner.shell.test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.shell.VirtualFileSystem;

@TestMethodOrder(OrderAnnotation.class)
public class TestVirtualFileSystem extends AbstractConsoleTest{

	
	@BeforeAll
	public static void beforeAll() throws IOException {
		//System.out.println("In setup");
	}
	

	@AfterAll
	public static void afterAll() {
		//System.out.println("After all");
	}
	
	private static final String FILE_NAME_PATTERN = "Test-%d-%d.txt";
	
	
	private static void fillDir(FileSource dir,int i) throws IOException {
		for(int idx=0; idx < 10; idx++ ) {
			FileSource file = dir.getChild(String.format(FILE_NAME_PATTERN, i,idx));
			try(OutputStream out = file.getOutputStream()) {
				out.write("0123456789".getBytes());
			}
		}		
	}
	
	@Test
	@Order(1)
	public void testRoot01() throws IOException {
		FileSource localRoot = FileSourceFactory.getDefaultFactory().createFileSource("/");
		FileSource [] localkids = localRoot.listFiles();
		
		VirtualFileSystem fs = new VirtualFileSystem(localRoot);
		
		assertEquals("/", fs.toString());
		assertEquals("/", fs.getAbsolutePath());
		assertEquals("/", fs.getCanonicalPath());
		
		FileSource [] kids = fs.listFiles();		
		assertEquals(localkids.length, kids.length);
		
		MemoryFileSourceFactory mf = new MemoryFileSourceFactory();
		FileSource mfroot = mf.listRoots()[0];
		fillDir(mfroot, 0);		
		
		kids = mfroot.listFiles();
		assertEquals(10, kids.length);
		

		boolean ok = fs.mount(mfroot, "mem");
		assertTrue(ok);
		
		kids = fs.listFiles();
		assertEquals(localkids.length+1, kids.length);
		
		
		FileSource mdir1 = fs.getChild("mem");
		assertTrue(mdir1.isDirectory());

		kids = mdir1.listFiles();
		assertEquals(10, kids.length);
		for(FileSource kid : kids) {
			assertTrue(kid.getAbsolutePath().startsWith("/mem"));
		}

		FileSource mdir2 = mdir1.getChild("ChildDir");
		mdir2.mkdirs();
		fillDir(mdir2, 1);
		assertTrue(mdir2.isDirectory());
		

		kids = mdir2.listFiles();
		assertEquals(10, kids.length);
		
		for(FileSource kid : kids) {
			assertTrue(kid.getAbsolutePath().startsWith("/mem/ChildDir"));
		}

		// absolute path
		FileSource mdir3 = fs.getChild("/mem/ChildDir");
		assertTrue(mdir3.isDirectory());
		kids = mdir3.listFiles();
		assertEquals(10, kids.length);
		
		for(FileSource kid : kids) {
			assertTrue(kid.getAbsolutePath().startsWith("/mem/ChildDir"));
		}

		// relative path
		FileSource mdir4 = fs.getChild("mem/ChildDir");
		assertTrue(mdir4.isDirectory());
		kids = mdir4.listFiles();
		assertEquals(10, kids.length);
		
		for(FileSource kid : kids) {
			assertTrue(kid.getAbsolutePath().startsWith("/mem/ChildDir"));
			String kidName = kid.getName();
			FileSource kid2 = mdir4.getChild(kidName);
			assertEquals(kid.getAbsolutePath(),kid2.getAbsolutePath());
			
		}

		// /mem/ChildDir -> /mem
		FileSource f1 = mdir3.getParentFile();
		assertEquals("/mem", f1.getAbsolutePath());
		// /mem -> /
		FileSource f2 = f1.getParentFile();
		assertEquals("/", f2.getAbsolutePath());
		// / -> null
		FileSource f3 = f2.getParentFile();
		assertNull(f3);
		
	}
	
	

	
		
}
