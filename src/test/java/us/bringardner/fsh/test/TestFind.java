package us.bringardner.fsh.test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.fsh.Console;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.IRandomAccessStream;
import us.bringardner.parley.files.memory.MemoryFileSourceFactory;

/** find, on a memory file system: TestFiles' tree is made there, so nothing is written to the disk */
@TestMethodOrder(OrderAnnotation.class)
public class TestFind extends AbstractConsoleTest {

	/** TestFiles' files (the folders are made for them), and SymLink2Folder01, a link to Folder01 */
	private static final String [] FILES = {
			"AbcFile.js", "AbcFile.php", "AbcFile.properties", "Hotel California.txt",
			"Folder01/AbcFile.php", "Folder01/AbcFile.properties", "Folder01/Hotel California.txt",
			"Folder01/Folder01abc.1/AbcFile.php", "Folder01/Folder01abc.1/AbcFile.properties",
			"Folder01/Folder01abc.1/Hotel California.txt",
			"Folder01/Folder01abc.1/Folder01ghi/AbcFile.php", "Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties",
			"Folder01/Folder01abc.1/Folder01ghi/Hotel California.txt",
			"Folder01/Folder01abc.1/Folder01jkl/AbcFile.php", "Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties",
			"Folder01/Folder01abc.1/Folder01jkl/Hotel California.txt",
			"Folder01/Folder01def.2/AbcFile.php", "Folder01/Folder01def.2/AbcFile.properties",
			"Folder01/Folder01def.2/Hotel California.txt"
	};

	@BeforeAll
	public static void beforeAll() throws IOException {
		MemoryFileSourceFactory memory = new MemoryFileSourceFactory();
		FileSourceFactory.setDefaultFactory(memory);
		// a path the memory file system understands on every platform: the real TestFiles is C:\...
		// on Windows, which it takes for a folder with that name under /
		String home = "/TestFiles";
		System.setProperty("user.home", home);
		FileSource dir = memory.createFileSource(home);
		assertTrue(dir.mkdirs());
		for(String name : FILES) {
			FileSource file = memory.createFileSource(home+"/"+name);
			file.getParentFile().mkdirs();
			try(OutputStream out = file.getOutputStream()) {
				out.write(name.getBytes());
			}
		}
		memory.createSymbolicLink(dir.getChild("SymLink2Folder01"), dir.getChild("Folder01"));
		memory.setCurrentDirectory(dir);
		// (nothing here is on disk, so there are no test file dates to restore)
		console = new Console();
	}

	private static void compare(String expect1, String stdOut1) {
		String expect [] = expect1.replaceAll("\r", "").replaceAll("\\r", "/").split("\n");
		String stdOut [] = stdOut1.replaceAll("\r", "").replaceAll("\\r", "/").split("\n");
		assertEquals(expect.length, stdOut.length);
		for (int idx = 0; idx < stdOut.length; idx++) {
			String eline = expect[idx];
			eline = eline.substring(eline.lastIndexOf('/')+1);
			String aline = stdOut[idx];
			aline = aline.substring(aline.lastIndexOf('/')+1);
			assertEquals(eline, aline,"idx="+idx);
		}
	}
	
	/**
	 * -size, on a file of 1024 bytes in a folder of its own, found by its path: setup() is not
	 * called again, so the other tests still run in TestFiles.
	 */
	@Test
	public void testFind00() throws IOException {
		// (beside TestFiles, not in it; not the real path of FindTestFiles, which is C:\... on Windows)
		FileSource findDir = console.createFileSource("/FindTestFiles");
		if(!findDir.exists()) {
			assertTrue(findDir.mkdirs());
		}
		FileSource k1 = findDir.getChild("k1");
		k1.delete();
		try(IRandomAccessStream ras = k1.getRandomAccessStream("rw")){
			ras.seek(1023);
			ras.write('\n');
		}
		assertEquals(1024, k1.length());

		String dir = "'"+findDir.getAbsolutePath()+"' -type f";
		// Nc: exactly N bytes
		assertEquals("k1", found("find "+dir+" -size 1024c"));
		assertEquals("", found("find "+dir+" -size 1023c"));
		assertEquals("", found("find "+dir+" -size 1025c"));
		// Nk: in 1024-byte blocks, rounded up (1024 bytes is 1k); - is less than, + more than
		assertEquals("k1", found("find "+dir+" -size 1k"));
		assertEquals("k1", found("find "+dir+" -size -2k"));
		assertEquals("", found("find "+dir+" -size +1k"));
		assertEquals("k1", found("find "+dir+" -size +1023c"));
	}

	/** the names find prints for cmd (without their folders), after checking it ran without errors */
	private static String found(String cmd) throws IOException {
		ExecuteResult res = executeCommand(cmd, "", 0);
		assertEquals("", res.getStdErr().trim(), cmd);
		StringBuilder ret = new StringBuilder();
		for(String line : res.getStdOut().replace("\r", "").split("\n")) {
			if( !line.isEmpty()) {
				ret.append(ret.length() > 0 ? "\n" : "").append(line.substring(line.lastIndexOf('/')+1));
			}
		}
		return ret.toString();
	}

	@Test
	public void testFind01() throws IOException {
		String cmd = "find";
		String expectErr = "usage: find [-P] path ... [expression]";
		
		showError=false;
		ExecuteResult res = executeCommand(cmd, "", 1);
		showError = true;
		assertEquals(expectErr, res.getStdErr().trim());
		compare("",res.getStdOut());
		
	}
	
	@Test
	public void testFind02() throws IOException {
		String cmd = "find .";
		// as in bash: the start point as given, then the paths under it
		String expect = ".\n"
				+ "./AbcFile.js\n"
				+ "./AbcFile.php\n"
				+ "./AbcFile.properties\n"
				+ "./Folder01\n"
				+ "./Folder01/AbcFile.php\n"
				+ "./Folder01/AbcFile.properties\n"
				+ "./Folder01/Folder01abc.1\n"
				+ "./Folder01/Folder01abc.1/AbcFile.php\n"
				+ "./Folder01/Folder01abc.1/AbcFile.properties\n"
				+ "./Folder01/Folder01abc.1/Folder01ghi\n"
				+ "./Folder01/Folder01abc.1/Folder01ghi/AbcFile.php\n"
				+ "./Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties\n"
				+ "./Folder01/Folder01abc.1/Folder01ghi/Hotel California.txt\n"
				+ "./Folder01/Folder01abc.1/Folder01jkl\n"
				+ "./Folder01/Folder01abc.1/Folder01jkl/AbcFile.php\n"
				+ "./Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties\n"
				+ "./Folder01/Folder01abc.1/Folder01jkl/Hotel California.txt\n"
				+ "./Folder01/Folder01abc.1/Hotel California.txt\n"
				+ "./Folder01/Folder01def.2\n"
				+ "./Folder01/Folder01def.2/AbcFile.php\n"
				+ "./Folder01/Folder01def.2/AbcFile.properties\n"
				+ "./Folder01/Folder01def.2/Hotel California.txt\n"
				+ "./Folder01/Hotel California.txt\n"
				+ "./Hotel California.txt\n"
				+ "./SymLink2Folder01";
		
		ExecuteResult res = executeCommand(cmd, "", 0);
		assertEquals("", res.getStdErr().trim());
		compare(expect,res.getStdOut());
		
	}
	
	@Test
	public void testFind03() throws IOException {
		// quoted, as in bash: unquoted, Abc* would be expanded before find runs
		String cmd = "find . -name 'Abc*'";
		String expect = "./AbcFile.js\n"
				+ "./AbcFile.php\n"
				+ "./AbcFile.properties\n"
				+ "Folder01/AbcFile.php\n"
				+ "Folder01/AbcFile.properties\n"
				+ "Folder01/Folder01abc.1/AbcFile.php\n"
				+ "Folder01/Folder01abc.1/AbcFile.properties\n"
				+ "Folder01/Folder01abc.1/Folder01ghi/AbcFile.php\n"
				+ "Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties\n"
				+ "Folder01/Folder01abc.1/Folder01jkl/AbcFile.php\n"
				+ "Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties\n"
				+ "Folder01/Folder01def.2/AbcFile.php\n"
				+ "Folder01/Folder01def.2/AbcFile.properties";
		
		ExecuteResult res = executeCommand(cmd, "", 0);
		assertEquals("", res.getStdErr());
		compare(expect,res.getStdOut());
		
	}

	@Test
	public void testFind04() throws IOException {
		String cmd = "find Folder01 -name 'Abc*'";
		String expect = 
				 "Folder01/AbcFile.php\n"
				+ "Folder01/AbcFile.properties\n"
				+ "Folder01/Folder01abc.1/AbcFile.php\n"
				+ "Folder01/Folder01abc.1/AbcFile.properties\n"
				+ "Folder01/Folder01abc.1/Folder01ghi/AbcFile.php\n"
				+ "Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties\n"
				+ "Folder01/Folder01abc.1/Folder01jkl/AbcFile.php\n"
				+ "Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties\n"
				+ "Folder01/Folder01def.2/AbcFile.php\n"
				+ "Folder01/Folder01def.2/AbcFile.properties";
		
		ExecuteResult res = executeCommand(cmd, "", 0);
		assertEquals("", res.getStdErr());
		compare(expect,res.getStdOut());
		
	}

}
