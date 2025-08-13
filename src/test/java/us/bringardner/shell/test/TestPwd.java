package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;

public class TestPwd {

	public static String fileDate;
	public static Console console;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		File file = new File("PwdTestFiles/SymLink2Folder01");
		System.setProperty("user.home", file.getAbsolutePath());
		console = new Console();
	}
	
	@AfterAll
	public static void afterAll() {
		
	}
	
	public String executeCommand(String command) throws IOException {
		PrintStream stdout= System.out;
		PrintStream stderr= System.err;
		InputStream stdin = System.in;
		
		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		System.setOut(new PrintStream(bao));
		ByteArrayOutputStream bae = new ByteArrayOutputStream();
		System.setErr(new PrintStream(bae));
		System.setIn(new ByteArrayInputStream(new byte[0]));
		
		console.executeWithoutAntlr(command);
		String actual = new String(bao.toByteArray());
		System.setIn(stdin);
		System.setOut(stdout);
		System.setErr(stderr);
		return actual;
	}
	//TODO:  test -P.  need to be able to create a link
	@Test
	public void testPwd() throws IOException {
		String actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("PwdTestFiles/SymLink2Folder01"));
	}
	
	@Test
	public void testPwd_P() throws IOException {
		String actual = executeCommand("pwd -P").trim();		
		assertTrue(actual.endsWith("PwdTestFiles/Folder01"));
	}
}
