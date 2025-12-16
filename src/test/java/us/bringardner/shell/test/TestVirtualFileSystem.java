package us.bringardner.shell.test;

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
				"/AbcFile.js\n"
						+ "/AbcFile.php\n"
						+ "/AbcFile.properties\n"
						+ "/Folder01\n"
						+ "/Hotel California.txt\n"
						+ "/SymLink2Folder01").split("\n");
		assertEquals(expectFiles.length, kids.length);

		for (int idx = 0; idx < expectFiles.length; idx++) {
			FileSource kid = kids[idx];
			path = kid.getAbsolutePath();
			expect= expectFiles[idx];
			assertEquals(expect, path);			
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
