package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;

@TestMethodOrder(OrderAnnotation.class)
public class TestCd {

	public static String fileDate;
	public static Console console;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		File file = new File("TestFiles").getCanonicalFile();
		
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
		
		console.executeUsingAntlr(command);
		String actual = new String(bao.toByteArray());
		System.setIn(stdin);
		System.setOut(stdout);
		System.setErr(stderr);
		return actual;
	}

	@Test
	@Order(1)
	public void testPwd_01() throws IOException {
		String actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("TestFiles"));
	}
	
	@Test
	@Order(2)
	public void testCd_01() throws IOException {
		String actual = executeCommand("cd Folder01").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01"));
		
		actual = executeCommand("cd Folder01abc.1").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01abc.1"));
		actual = executeCommand("cd ..").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01"));
		
		actual = executeCommand("cd ..").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("TestFiles"));
		
		
	}
	
	@Test
	@Order(3)
	public void testCd_02() throws IOException {
		String actual = executeCommand("cd Folder01/Folder01abc.1").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01/Folder01abc.1"));
		actual = executeCommand("cd ..").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("TestFiles/Folder01"));
		
		
	}
	
	
}
