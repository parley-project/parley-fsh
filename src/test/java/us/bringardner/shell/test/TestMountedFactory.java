package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
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
	
		
}
