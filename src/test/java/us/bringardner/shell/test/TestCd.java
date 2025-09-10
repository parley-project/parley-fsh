package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class TestCd extends AbstractConsoleTest{

	public static String fileDate;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	@AfterAll
	public static void afterAll() {
		
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
