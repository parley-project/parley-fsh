package us.bringardner.shell.test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;

@TestMethodOrder(OrderAnnotation.class)
public class TestMv extends AbstractConsoleTest{

	static FileSource srcDir;
	static FileSource dstDir;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		
		dstDir = console.createFileSource("../target/mvdst");
		deleteIfExists(dstDir);
		if( !dstDir.exists()) {
			assertTrue(dstDir.mkdirs(),"Can't create "+dstDir);
		} 
		
		srcDir = console.createFileSource("../target/mvsrc");
		
		if( !srcDir.exists()) {
			assertTrue(srcDir.mkdirs(),"Can't create "+srcDir);
		}
		// mv will delete the original file so copy to a directory we can change without impacting other runs
		copy(console.getCurrentDirectory(),srcDir);
		
	}
	
	public static void deleteIfExists(FileSource file) throws IOException {
		if( file.exists()) {
			if(file.isDirectory()) {
				FileSource[] kids = file.listFiles();
				if( kids !=null) {
					for(FileSource kid : kids) {
						deleteIfExists(kid);
					}
				}
			}
			assertTrue(file.delete());
		}

	}
	
		
	public static void copy(FileSource from, FileSource to) throws IOException {
		FileSource parent = to.getParentFile();
		if( parent != null && !parent.exists()) {
			parent.mkdirs();
		} 
		if( from.isDirectory()) {
			FileSource [] kids = from.listFiles();
			if( kids != null ) {
				for(FileSource f : kids) {
					copy(f,to.getChild(f.getName()));
				}
			}
		} else {
			try(InputStream in = from.getInputStream()) {
				try(OutputStream out = to.getOutputStream()) {
					copy(in,out);		
				}
			}			
		}
	}

	public static void copy(InputStream in, OutputStream out) throws IOException {
		// use a small buffer to get multiple reads 
		byte [] data = new byte[1024];
		int got = 0;

		try {
			while( (got=in.read(data)) >= 0) {
				if( got > 0 ) {
					out.write(data,0,got);
				}
			}

		} finally {
			try {
				out.close();
			} catch (Exception e) {
			}
			try {
				in.close();
			} catch (Exception e) {
			}

		}
	}
	
	@Test
	@Order(1)
	public void testMn01() throws IOException {
		
			FileSource src = srcDir.getChild("AbcFile.php");
			
			
			String code = "mv "+src+"  "+dstDir ;
			
			ExecuteResult res = executeCommand(code,"");
			
			String out = res.getStdOut();
			String err = res.getStdErr();
			assertEquals("", err);
			assertEquals("", out);
			assertEquals(0, res.exitCode);
			assertFalse(src.exists());
			FileSource dest = dstDir.getChild(src.getName());
			assertTrue(dest.exists());
			
	}

	
	@Test
	@Order(2)
	public void testMn02() throws IOException {
		
			FileSource src = srcDir.getChild("Folder01");
			
			
			String code = "mv "+src+"  "+dstDir ;
			
			ExecuteResult res = executeCommand(code,"");
			
			String out = res.getStdOut();
			String err = res.getStdErr();
			assertEquals("", err);
			assertEquals("", out);
			assertEquals(0, res.exitCode);
			assertFalse(src.exists());
			FileSource dest = dstDir.getChild(src.getName());
			assertTrue(dest.exists());
			
	}
	
	@Test
	@Order(3)
	public void testMn03() throws IOException {
		
			
			FileSource src = srcDir.getChild("AbcFile.js");
			
			
			String code = "connect memory /mem\n"
					+ "mv "+src+"  /mem/"+src.getName() ;
			
			ExecuteResult res = executeCommand(code,"");
			
			String out = res.getStdOut();
			String err = res.getStdErr();
			assertEquals("", err);
			assertEquals("memory connected as /mem\n", out);
			assertEquals(0, res.exitCode);
			assertFalse(src.exists());
			FileSource dst = console.createFileSource("/mem/"+src.getName());
			assertTrue(dst.exists());
			
			
	}
	
	@Test
	@Order(4)
	public void testMn04() throws IOException {
			beforeAll();
			
			FileSource src = srcDir.getChild("Folder01");
			
			
			String code = "connect memory /mem4\n"
					+ "mv "+src+"  /mem4/"+src.getName() ;
			
			ExecuteResult res = executeCommand(code,"");
			
			String out = res.getStdOut();
			String err = res.getStdErr();
			assertEquals("", err);
			assertEquals("memory connected as /mem4\n", out);
			assertEquals(0, res.exitCode);
			assertFalse(src.exists());
			FileSource dst = console.createFileSource("/mem4/"+src.getName());
			assertTrue(dst.exists());
			
	}

	@Test
	@Order(5)
	public void testMn05() throws IOException {
		
			
			FileSource src = srcDir.getChild("AbcFile.js");
			FileSource dst = srcDir.getChild("AbcFile.php");
			//  both should exist
			assertTrue(src.exists());
			assertTrue(dst.exists());
			
			String code = ""
					+ "mv -n "+src+"  "+dst ;
			
			ExecuteResult res = executeCommand(code,"");
			
			String out = res.getStdOut();
			String err = res.getStdErr();
			assertEquals("", err);
			assertEquals(dst.getAbsolutePath()+" not overwritten\n", out);
			assertEquals(1, res.exitCode);
			assertTrue(src.exists());
			assertTrue(dst.exists());
			
			code = ""
					+ "mv -i "+src+"  "+dst ;
			
			res = executeCommand(code,"n");
			
			out = res.getStdOut();
			err = res.getStdErr();
			assertEquals("", err);
			String expect = "overwrite "+dst+"? (y/n [n])"+dst+" not overwritten.\n";
					
			assertEquals(expect, out);
			assertEquals(1, res.exitCode);
			assertTrue(src.exists());
			assertTrue(dst.exists());
			
			code = ""
					+ "mv -i "+src+"  "+dst ;
			
			res = executeCommand(code,"Y");
			
			out = res.getStdOut();
			err = res.getStdErr();
			assertEquals("", err);
			expect = "overwrite "+dst+"? (y/n [n])";
					
			assertEquals(expect, out);
			assertEquals(0, res.exitCode);
			assertFalse(src.exists());
			assertTrue(dst.exists());
	}
	

	@Test
	@Order(6)
	public void testMn06() throws IOException {
			beforeAll();
			
			FileSource src = srcDir.getChild("Folder01");
			
			
			String code = "connect memory /mem4\n"
					+ "mv -v "+src+"  /mem4/"+src.getName() ;
			
			ExecuteResult res = executeCommand(code,"");
			
			String expect = "memory connected as /mem4\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/AbcFile.php -> /mem4/Folder01/AbcFile.php\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/AbcFile.properties -> /mem4/Folder01/AbcFile.properties\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/AbcFile.php -> /mem4/Folder01/Folder01abc.1/AbcFile.php\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/AbcFile.properties -> /mem4/Folder01/Folder01abc.1/AbcFile.properties\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01ghi/AbcFile.php -> /mem4/Folder01/Folder01abc.1/Folder01ghi/AbcFile.php\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties -> /mem4/Folder01/Folder01abc.1/Folder01ghi/AbcFile.properties\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01ghi/Hotel California.txt -> /mem4/Folder01/Folder01abc.1/Folder01ghi/Hotel California.txt\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01ghi -> /mem4/Folder01/Folder01abc.1/Folder01ghi\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01jkl/AbcFile.php -> /mem4/Folder01/Folder01abc.1/Folder01jkl/AbcFile.php\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties -> /mem4/Folder01/Folder01abc.1/Folder01jkl/AbcFile.properties\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01jkl/Hotel California.txt -> /mem4/Folder01/Folder01abc.1/Folder01jkl/Hotel California.txt\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Folder01jkl -> /mem4/Folder01/Folder01abc.1/Folder01jkl\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1/Hotel California.txt -> /mem4/Folder01/Folder01abc.1/Hotel California.txt\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01abc.1 -> /mem4/Folder01/Folder01abc.1\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01def.2/AbcFile.php -> /mem4/Folder01/Folder01def.2/AbcFile.php\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01def.2/AbcFile.properties -> /mem4/Folder01/Folder01def.2/AbcFile.properties\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01def.2/Hotel California.txt -> /mem4/Folder01/Folder01def.2/Hotel California.txt\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Folder01def.2 -> /mem4/Folder01/Folder01def.2\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01/Hotel California.txt -> /mem4/Folder01/Hotel California.txt\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01 -> /mem4/Folder01\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/target/mvsrc/Folder01 -> /mem4/Folder01\n"
					;
			String out = res.getStdOut();
			String err = res.getStdErr();
			assertEquals("", err);
			assertEquals(expect.split("\n").length,out.split("\n").length);
			assertEquals(0, res.exitCode);
			assertFalse(src.exists());
			FileSource dst = console.createFileSource("/mem4/"+src.getName());
			assertTrue(dst.exists());
			
	}

}
