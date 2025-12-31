package us.bringardner.shell.test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.IRandomAccessStream;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;

@TestMethodOrder(OrderAnnotation.class)
public class TestFind extends AbstractConsoleTest {

	
	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		AbstractConsoleTest.setup("TestFiles");		
		
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
	
	//@Test
	public void testFind00() throws IOException {
		AbstractConsoleTest.setup("target");
		FileSource targetDir = console.getCurrentDirectory();
		FileSource findDir = targetDir.getChild("FindTestFiles");
		
		if(!findDir.exists()) {
			assertTrue(findDir.mkdirs());
		}
		
		//  create some files with a variety of sizes
		FileSource k1 = findDir.getChild("k1");
		k1.delete();
		
		try(IRandomAccessStream ras = k1.getRandomAccessStream("rw")){
			ras.seek(1023);
			ras.write('\n');
		}
		
		//  test size equal
		String cmd = "find . -size 1024c";
		ExecuteResult res = executeCommand(cmd, "", 0);
		String expect = "";
		String out = res.getStdOut();
		System.out.println("out"+out);
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
		String expect = "TestFiles\n"
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
		String cmd = "find . -name Abc*";
		String expect = "/Volumes/Data/eclipse-git/BjlShell/TestFiles/AbcFile.js\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01ghi/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01jkl/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01def.2/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01def.2/AbcFile.properties";
		
		ExecuteResult res = executeCommand(cmd, "", 0);
		assertEquals("", res.getStdErr());
		compare(expect,res.getStdOut());
		
	}

	@Test
	public void testFind04() throws IOException {
		String cmd = "find Folder01 -name Abc*";
		String expect = 
				 "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01ghi/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01jkl/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01def.2/AbcFile.php\n"
				+ "/Volumes/Data/eclipse-git/BjlShell/TestFiles/Folder01/Folder01def.2/AbcFile.properties";
		
		ExecuteResult res = executeCommand(cmd, "", 0);
		assertEquals("", res.getStdErr());
		compare(expect,res.getStdOut());
		
	}

}
