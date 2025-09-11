package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;

@TestMethodOrder(OrderAnnotation.class)
public class TestLn extends AbstractConsoleTest{

	private static File localSymlink;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		testFilesDir = new File("LnTestFiles").getCanonicalFile();
		//  set up linked files
		File local = new File(testFilesDir,"AbcFileA.js");
		assertTrue(local.exists());		
		Path target=local.toPath();
		
		localSymlink = new File(testFilesDir,"SymLink2AbcFileA2.js").getAbsoluteFile();
		if( localSymlink.exists()) {
			assertTrue(localSymlink.delete());
		}
		
		Path link = localSymlink.toPath();
		
		Files.createSymbolicLink(link, target);
		
		System.setProperty("user.home", testFilesDir.getAbsolutePath());
		console = new Console();
	}
	
	@AfterAll
	public static void afterAll() {
		if( localSymlink.exists()) {
			assertTrue(localSymlink.delete());
		}
	}
	
	
	
	@Test
	@Order(1)
	public void testLn_HardFile2File() throws IOException {
		String hardLinkPath = "target/HardLinkToAbcFileA.js";
		
		File file = new File(hardLinkPath).getCanonicalFile();
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "AbcFileA.js";
		String target = "../"+hardLinkPath;
		String cmd = "ln "+source+" "+target;

		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"ln exit code");
		
		assertTrue(file.exists(),"File does not exist after ln");
		
		cmd = "rm "+target;
		res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"Rm exit code");		
		assertFalse(file.exists(),"File still exist after rm");
		
	}
	
	@Test
	@Order(2)
	public void testLn_sFile2File() throws IOException {
		String linkPath = "target/SymLinkToAbcFileA.js";
		
		File file = new File(linkPath).getCanonicalFile();
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "AbcFileA.js";
		String target = "../"+linkPath;
		String cmd = "ln -s "+source+" "+target;
		ExecuteResult res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"ln exit code");
		
		assertTrue(file.exists(),"File does not exist after ln");
		
		cmd = "rm "+target;
		res = executeCommand(cmd, "");
		
		
		assertEquals(0, res.exitCode,"Rm exit code");		
		assertFalse(file.exists(),"File still exist after rm");
		
	}
	
	@Test
	@Order(3)
	public void testLn_P_File2File() throws IOException {
		String linkPath = "target/SymLinkTo_P_.js";
		
		File file = new File(linkPath).getCanonicalFile();
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "SymLink2AbcFileA2.js";
		String target = "../"+linkPath;
		String cmd = "ln -P "+source+" "+target;

		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"ln exit code");
		
		assertTrue(file.exists(),"File does not exist after ln");
		
	
		
		cmd = "rm "+target;
		res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"Rm exit code");		
		assertFalse(file.exists(),"File still exist after rm");
		
	}
	
	@Test
	@Order(4)
	public void testLn_f_File2File() throws IOException {
		String linkPath = "target/AbcFileC.txt";
		
		File file = new File(linkPath);
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "AbcFileC.txt";
		String target = "../"+linkPath;
		
		
		
		String cmd = "cp "+source+" "+target;
		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"ln exit code");
		
		assertTrue(file.exists(),"File does not exist after cp");
		
		cmd = "ln -f "+source+" "+target;

		res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"ln exit code");
		
		assertTrue(file.exists(),"File does not exist after cp");
		
		
	
		
		cmd = "rm "+target;
		res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"Rm exit code");		
		assertFalse(file.exists(),"File still exist after rm");
		
	}
	

	@Test
	@Order(5)
	public void testLn_h_File2File() throws IOException {
		String linkPath = "target/SymLinkTo_P_.js";
		
		File file = new File(linkPath).getCanonicalFile();
		if( file.exists()) {
			assertTrue(file.delete(),"Can't delete existing file");
		}
		
		String source = "SymLink2AbcFileA2.js";
		String target = "../"+linkPath;
		String cmd = "ln -h "+source+" "+target;
		ExecuteResult res = executeCommand(cmd, "");
		assertEquals(0, res.exitCode,"ln exit code");
		
		assertTrue(file.exists(),"File does not exist after ln");
		
	
		
		cmd = "rm "+target;
		res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"Rm exit code");		
		assertFalse(file.exists(),"File still exist after rm");
		
	}
	
	@Test
	@Order(6)
	public void testLn_Files2Dir() throws IOException {
		String linkPath = "target";
		File dir = new File(linkPath).getCanonicalFile();		
		for(File file : dir.listFiles()) {
			if( file.getName().startsWith("AbcFile")) {
				assertTrue(file.delete());
			}
		}
		
		String source = "AbcFile*";
		String target = "../"+linkPath;
		String cmd = "ln "+source+" "+target;

		ExecuteResult res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"ln exit code");
		
		int cnt = 0;
		for(File file : dir.listFiles()) {
			if( file.getName().startsWith("AbcFile")) {
				cnt++;
				assertTrue(file.delete());
			}
		}
		assertEquals(4, cnt,"ln to dir count");
		
	}

	@Test
	@Order(7)
	public void testLn_sv_Files2Dir() throws IOException {
		String linkPath = "target";
		File dir = new File(linkPath).getCanonicalFile();		
		for(File file : dir.listFiles()) {
			if( file.getName().startsWith("AbcFile")) {
				assertTrue(file.delete());
			}
		}
		
		String source = "AbcFile*";
		String cmd = "ln -sv "+source+" "+dir.getAbsolutePath();

		ExecuteResult exit = executeCommand(cmd, "");
		assertEquals(0, exit.exitCode,"ln exit code");
		
		String out = new String(exit.bao.toByteArray());
		String [] lines = out.split("\n");
		assertEquals(4, lines.length,"ln verbose line count");
		
		int cnt = 0;
		Path testFilesPath = testFilesDir.toPath();
		
		for(File file : dir.listFiles()) {
			if( file.getName().startsWith("AbcFile")) {
				cnt++;				
				Path link = getLinkedTo(file);
				assertTrue(link.startsWith(testFilesPath),"Bad link "+link);
				assertTrue(file.delete());
			}
		}
		assertEquals(4, cnt,"ln to dir count");
		
		
	}

	
	public Path getLinkedTo(File target) {
		Path ret = null;
		try {

			Path path = target.toPath();
			BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
			if (view != null) {
				BasicFileAttributes at = view.readAttributes();
				if( at.isSymbolicLink()) {
					Path newPath = Files.readSymbolicLink(path);
					if( newPath != null) {
						ret = newPath;
					}

				}
			}

		} catch (IOException e) {
			//ignore here
		}

		return ret;
	}
}
