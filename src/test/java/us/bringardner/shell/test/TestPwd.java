package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;

public class TestPwd extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		// can't use AbstractConsoleTest.beforeAll because in uses a canonical file
		String home = "TestFiles/SymLink2Folder01";		
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		testFilesDir = new File(home);;

		System.setProperty("user.home", testFilesDir.getAbsolutePath());
		console = new Console();
	}
	
	
	//TODO:  test -P.  need to be able to create a link
	@Test
	public void testPwd() throws IOException {
		String actual = executeCommand("pwd\n").trim();
		if( getOs()==OperatingSystem.Windows) {
			assertTrue(actual.endsWith("TestFiles\\SymLink2Folder01"));
		} else {
			assertTrue(actual.endsWith("TestFiles/SymLink2Folder01"));
		}
	}
	
	@Test
	public void testPwd_P() throws IOException {
		String actual = executeCommand("pwd -P\n").trim();		
		if( getOs()==OperatingSystem.Windows) {
			assertTrue(actual.endsWith("TestFiles\\Folder01"));
		} else {
			assertTrue(actual.endsWith("TestFiles/Folder01"));
		}
	}
}
