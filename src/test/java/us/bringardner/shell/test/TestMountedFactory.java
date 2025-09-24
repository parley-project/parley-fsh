package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.memory.MemoryFileSource;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.shell.MountFactory;
import us.bringardner.shell.RootFile;

public class TestMountedFactory {

	public static String fileDate;

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
	}


	@AfterAll
	public static void afterAll() {
		//System.out.println("After all");
	}

	private static void fillDir(FileSource dir,int i) throws IOException {
		for(int idx=0; idx < 10; idx++ ) {
			FileSource file = dir.getChild("Test-"+i+"-"+idx+".txt");
			try(OutputStream out = file.getOutputStream()) {
				out.write("0123456789".getBytes());
			}
		}

	}



	@Test
	public void testMount() throws IOException {
		MountFactory factory = new MountFactory();

		assertTrue(factory.mount(new MemoryFileSourceFactory(), "mem"),"Failed to mount mem");


		String expectName = "/mem";
		FileSource data = factory.createFileSource(expectName);
		assertEquals(RootFile.class, data.getClass());
		assertEquals(expectName, data.getAbsolutePath());
		assertTrue(data.exists(),expectName+" does not exists");


		expectName = "/Volumes/Data";
		data = factory.createFileSource(expectName);
		assertEquals(expectName, data.getAbsolutePath());
		assertTrue(data.exists(),expectName+" does not exists");



		expectName= "/mem/Volumes/Data";
		data = factory.createFileSource(expectName);
		assertEquals(expectName, data.getAbsolutePath());


		assertFalse(data.exists(),expectName+" Should not exists");
		assertTrue(data.mkdirs(),"Can't mkdirs for "+data);
		assertTrue(data.exists(),expectName+" does not exists");

		fillDir(data, 0);
		FileSource [] kids = data.listFiles();
		assertEquals(10, kids.length);


		for (int idx = 0; idx < kids.length; idx++) {
			String expect = "/mem/Volumes/Data/Test-0-"+idx+".txt";
			String actual = kids[idx].getAbsolutePath();
			assertEquals(expect, actual);
		}
	}


	@Test
	public void testMount2() throws Exception {
		MountFactory factory = new MountFactory();
		MemoryFileSourceFactory memoryFactory = new MemoryFileSourceFactory();
		assertTrue(factory.mount(memoryFactory, "mem"),"Failed to mount mem");


		String rootName1 = "/mem";
		FileSource root1 = factory.createFileSource(rootName1);
		FileSource dir1 = root1;

		assertEquals(RootFile.class, dir1.getClass());
		assertEquals(rootName1, dir1.getAbsolutePath());
		assertTrue(dir1.exists(),rootName1+" does not exists");
		int levels = 20;

		for(int level = 0; level < levels; level++) {
			String name = "Level"+level;
			FileSource child = dir1.getChild(name);
			assertTrue(child.mkdir());
			String path = child.getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(name));
			dir1 = child;
		}

		fillDir(dir1,1);
		FileSource kids[] = dir1.listFiles(); 
		for (int idx = 0; idx < kids.length; idx++) {
			String path = kids[idx].getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(".txt"));
		}

		FileSource dir = factory.createFileSource(rootName1);
		assertEquals(RootFile.class, dir.getClass());
		assertEquals(rootName1, dir.getAbsolutePath());
		assertTrue(dir.exists(),rootName1+" does not exists");

		for(int level = 0; level < levels; level++) {
			String name = "Level"+level;
			FileSource child = dir.getChild(name);
			assertTrue(child.exists());
			assertTrue(child.isDirectory());
			assertTrue(dir.isChildOfMine(child));
			String path = child.getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(name));
			dir = child;
		}
		kids = dir.listFiles(); 
		for (int idx = 0; idx < kids.length; idx++) {
			String path = kids[idx].getAbsolutePath();
			assertTrue(path.startsWith("/mem"));
			assertTrue(path.endsWith(".txt"));
		}

		FileSource root = memoryFactory.listRoots()[0];
		String rootName = "/";
		assertEquals(MemoryFileSource.class, root.getClass());
		assertEquals(rootName, root.getAbsolutePath());
		assertTrue(root.exists(),rootName+" does not exists");
		FileSource mdir = root;
		for(int level = 0; level < levels; level++) {			
			String name = "Level"+level;
			kids = mdir.listFiles();
			assertEquals(1, kids.length);
			FileSource child = kids[0];
			assertTrue(child.exists());
			assertTrue(child.isDirectory());
			assertTrue(mdir.isChildOfMine(child));
			String path1 = child.getAbsolutePath();
			assertTrue(path1.startsWith("/Level0"));
			assertTrue(path1.endsWith(name));

			FileSource child2 = factory.createFileSource("/mem"+path1);
			assertTrue(child2.exists());
			assertTrue(child2.isDirectory());
			assertTrue(root1.isChildOfMine(child2));
			String path2 = child2.getAbsolutePath();
			assertTrue(path2.startsWith(rootName1));
			assertTrue(path2.endsWith(name));
			compare(child,child2);
			mdir = child;
			root1 = child2;
		}

	}


	private void compare(FileSource child1, FileSource child2) throws Exception {
		Method[] methods = FileSource.class.getDeclaredMethods();
		for(Method method : methods) {
			String name = method.getName();

			if( method.getParameterCount()==0) {
				if( !name.startsWith("getParent")) {
					if( !name.startsWith("list")) {
						if( !name.endsWith("Stream")) {
							if( !name.endsWith("Path")) {
								method.setAccessible(true);
								Object r1 = method.invoke(child1);
								Object r2 = method.invoke(child2);
								assertEquals(r1, r2,name);
							}
						}
					}
				}
			}

		}		
	}


}
