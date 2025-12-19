package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.shell.Console;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class TestGlob extends ShellCommand {

	/*
	interesting keyboard
	https://stackoverflow.com/questions/1066318/how-to-read-a-single-char-from-the-console-in-java-as-the-user-types-it
	 */
	public TestGlob() {
		super("", "");
	}

	public static String fileDate;
	public static FileSourceFactory factory = FileSourceFactory.getDefaultFactory();
	public static FileSource testDir;
	public static String testDirPath="./TestFiles";
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		File file = new File(testDirPath).getCanonicalFile();
		testDir = factory.createFileSource(file.getAbsolutePath());
		factory.setCurrentDirectory(testDir);
		System.setProperty("user.home", testDir.getAbsolutePath());
	}
	
	@AfterAll
	public static void afterAll() {
		
	}
	
	@Test
	public void testTildeExpantion() throws IOException {
		
		String home = System.getProperty("user.home");
		ShellContext ctx = new ShellContext(new Console());
		String tmp = "";
	
		//~ The value of $HOME
		tmp = expandTilde(ctx,"~"); 
		assertEquals(home, tmp);

		//~/foo = $HOME/foo
		tmp = expandTilde(ctx,"~/foo"); 
		assertEquals(home+"/foo", tmp);
		
		//~fred/foo   The sub directory foo of the home directory of the user fred
		tmp = expandTilde(ctx,"~fred/foo"); 
		assertTrue(tmp.endsWith("fred/foo"));
		
		String pwd = ctx.console.getMountFactory().getCurrentDirectory().getAbsolutePath();
		//~+/foo  $PWD/foo
		tmp = expandTilde(ctx,"~+/foo"); 
		assertEquals(pwd+"/foo", tmp);
		
		//~-/foo  ${OLDPWD-'~-'}/foo  (Not supported)
		//tmp = expandTilde(ctx,"~-/foo");
	}
		
	@Test
	public void testGlob01Expantion() throws IOException {	 
		String pattern = "Folder[!a-x]1/F[:lower:]*/*.txt";
		List<FileSource> list =getFiles(new ShellContext(new Console()), pattern);
		
		assertEquals(2, list.size());
		if( FileSourceFactory.isWindows()) {
			assertTrue(list.get(0).getAbsolutePath().endsWith("TestFiles\\Folder01\\Folder01abc.1\\Hotel California.txt"));
			assertTrue(list.get(1).getAbsolutePath().endsWith("TestFiles\\Folder01\\Folder01def.2\\Hotel California.txt"));			
		} else {
			assertTrue(list.get(0).getAbsolutePath().endsWith("TestFiles/Folder01/Folder01abc.1/Hotel California.txt"));
			assertTrue(list.get(1).getAbsolutePath().endsWith("TestFiles/Folder01/Folder01def.2/Hotel California.txt"));
		}
		
	}

	@Test
	public void testGlob02Expantion() throws IOException {
		String pattern = "~/*.txt";
		ShellContext ctx = new ShellContext(new Console());
		List<FileSource> list =getFiles(ctx, pattern);
		assertEquals(1, list.size());
		assertTrue(list.get(0).getAbsolutePath().endsWith("Hotel California.txt"));
		
	}

	
	@Test
	public void testGlob02ExpantionProprtyFile() throws IOException {
		String pattern = "~/*.properties";
		ShellContext ctx = new ShellContext(new Console());
		List<FileSource> list =getFiles(ctx, pattern);
		assertEquals(1, list.size());
		assertTrue(list.get(0).getAbsolutePath().endsWith("AbcFile.properties"));		
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// This does nothing
		return 0;
	}
}
