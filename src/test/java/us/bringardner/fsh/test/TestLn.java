package us.bringardner.fsh.test;

import org.junit.jupiter.api.io.TempDir;
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

import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.fileproxy.FileProxyFactory;
import us.bringardner.fsh.Console;

@TestMethodOrder(OrderAnnotation.class)
public class TestLn extends AbstractConsoleTest{

	/** This class's scratch folder: everything the tests write goes here, not into target/ */
	@TempDir
	static Path scratch;

	/** @return the scratch folder's real path (on macOS /var is a link to /private/var) */
	static File scratchDir() {
		try {
			return scratch.toFile().getCanonicalFile();
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** @return the path of a file in the scratch folder */
	static String scratchPath(String name) {
		return new File(scratchDir(), name).getPath();
	}

	private static File localSymlink;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		// a copy of the test files in the scratch folder, with the links' folder (out) beside it: hard
		// links can't cross file systems, and the tests never change the files in the project
		testFilesDir = copyTree(new File("LnTestFiles").getCanonicalFile().toPath(), scratchDir().toPath().resolve("LnTestFiles")).toFile();
		assertTrue(new File(scratchDir(), "out").mkdirs());
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
		String hardLinkPath = "out/HardLinkToAbcFileA.js";
		
		File file = new File(scratchDir(), hardLinkPath);
		// (a link left from before is removed too, even one that points at nothing)
		java.nio.file.Files.deleteIfExists(file.toPath());
		
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
		String linkPath = "out/SymLinkToAbcFileA.js";
		
		File file = new File(scratchDir(), linkPath);
		// (a link left from before is removed too, even one that points at nothing)
		java.nio.file.Files.deleteIfExists(file.toPath());
		
		String source = "AbcFileA.js";
		String target = "../"+linkPath;
		String cmd = "ln -s "+source+" "+target;
		ExecuteResult res = executeCommand(cmd, "");
		
		assertEquals(0, res.exitCode,"ln exit code");
		
		// (as the system's ln makes it: the link holds the text given, relative to where the link is)
		assertTrue(java.nio.file.Files.isSymbolicLink(file.toPath()),"Link does not exist after ln");
		assertEquals(source, java.nio.file.Files.readSymbolicLink(file.toPath()).toString(),"link text");
		
		cmd = "rm "+target;
		res = executeCommand(cmd, "");
		
		
		assertEquals(0, res.exitCode,"Rm exit code");		
		assertFalse(file.exists(),"File still exist after rm");
		
	}
	
	@Test
	@Order(3)
	public void testLn_P_File2File() throws IOException {
		if( getOs()==OperatingSystem.Windows) {
			// requires admin  
			return;
		}
		String linkPath = "out/SymLinkTo_P_.js";
		
		File file = new File(scratchDir(), linkPath);
		// (a link left from before is removed too, even one that points at nothing)
		java.nio.file.Files.deleteIfExists(file.toPath());
		
		
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
		String linkPath = "out/AbcFileC.txt";
		
		File file = new File(scratchDir(), linkPath);
		// (a link left from before is removed too, even one that points at nothing)
		java.nio.file.Files.deleteIfExists(file.toPath());
		
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
		if( getOs()==OperatingSystem.Windows) {
			// no native server 
			return;
		}
		String linkPath = "out/SymLinkTo_P_.js";
		
		File file = new File(scratchDir(), linkPath);
		// (a link left from before is removed too, even one that points at nothing)
		java.nio.file.Files.deleteIfExists(file.toPath());
		
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
		String linkPath = "out";
		File dir = new File(scratchDir(), linkPath);		
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
		String linkPath = "out";
		File dir = new File(scratchDir(), linkPath);		
		for(File file : dir.listFiles()) {
			if( file.getName().startsWith("AbcFile")) {
				assertTrue(file.delete());
			}
		}
		
		String source = "AbcFile*";
		// (absolute sources: a link holds the text given, as the system's ln makes it)
		String cmd = "ln -sv "+testFilesDir.getCanonicalPath()+"/"+source+" "+dir.getAbsolutePath();

		ExecuteResult exit = executeCommand(cmd, "");
		assertEquals(0, exit.exitCode,"ln exit code");
		
		String out = exit.getStdOut();
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

	/** Copy a folder (links copied as links), keeping the files' times; @return the copy */
	static Path copyTree(Path from, Path to) throws IOException {
		try (java.util.stream.Stream<Path> all = Files.walk(from)) {
			for (Path p : (Iterable<Path>) all::iterator) {
				Path dest = to.resolve(from.relativize(p).toString());
				if (Files.isSymbolicLink(p)) {
					Files.createSymbolicLink(dest, Files.readSymbolicLink(p));
				} else if (Files.isDirectory(p)) {
					Files.createDirectories(dest);
				} else {
					Files.copy(p, dest, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}
		return to;
	}
}
