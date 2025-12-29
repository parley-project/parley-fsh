package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.shell.VirtualFileSourceFactory;

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


	@Test
	@Order(2)
	public void testVirtualFileSourceFactory() throws IOException {
		FileSource localRoot = FileSourceFactory.getDefaultFactory().createFileSource("TestFiles");
		VirtualFileSourceFactory factory = new VirtualFileSourceFactory(localRoot);
		factory.setCurrentDirectory(localRoot);

		MemoryFileSourceFactory memFactory = new MemoryFileSourceFactory();
		FileSource tmp = memFactory.createFileSource("/level1/level2");
		tmp.mkdirs();
		factory.mount("mem",tmp);

		//  returns the first root unless something is mounted to /.
		tmp = factory.createFileSource("/");
		String expect = localRoot.getAbsolutePath();
		String path = tmp.getAbsolutePath();
		assertEquals(expect, path);

		if( getOs()==OperatingSystem.Windows) {
			expect = "G:\\";		
			tmp = factory.createFileSource(expect);
			path = tmp.getAbsolutePath();
			assertEquals(expect, path);
		}		

		expect = "/mem/test1/test2";		
		tmp = factory.createFileSource(expect);
		path = tmp.getAbsolutePath();
		assertEquals(expect, path);

		expect = "/mem/testx";
		tmp = factory.createFileSource(expect);
		path = tmp.getAbsolutePath();
		assertEquals(expect, path);

		expect = "/mem";
		tmp = factory.createFileSource(expect);
		path = tmp.getAbsolutePath();
		assertEquals(expect, path);
		assertTrue(tmp.isDirectory());

		factory.mount("/", localRoot);
		expect = "/";
		tmp = factory.createFileSource(expect);
		path = tmp.getAbsolutePath();
		assertEquals(expect, path);

		FileSource[] kids = tmp.listFiles();
		String expectFiles [] = (
				"AbcFile.js\n"
						+ "AbcFile.php\n"
						+ "AbcFile.properties\n"
						+ "Folder01\n"
						+ "Hotel California.txt\n"
						+ "SymLink2Folder01"
						).split("\n");
		if( getOs()==OperatingSystem.Linux) {
			expectFiles = (
				"Hotel California.txt\n"
						+ "Folder01\n"
						+ "AbcFile.php\n"
						+"AbcFile.js\n"
						+ "SymLink2Folder01\n"
						+ "AbcFile.properties"
						).split("\n");
		}
		assertEquals(expectFiles.length, kids.length);

		for (int idx = 0; idx < expectFiles.length; idx++) {
			FileSource kid = kids[idx];
			path = kid.getAbsolutePath();
			expect= expectFiles[idx];
			assertTrue(path.endsWith(expect));			
		}

		if( getOs()==OperatingSystem.Windows) {
			FileSource[] roots = factory.listRoots();
			expectFiles = "C:\\Git\\BjlShell\\TestFiles\n/mem\n/".split("\n");
			assertEquals(expectFiles.length, roots.length);
			for (int idx = 0; idx < expectFiles.length; idx++) {
				FileSource kid = roots[idx];
				path = kid.getAbsolutePath();
				expect= expectFiles[idx];
				assertEquals(expect, path);			
			}
		}


	}



}
