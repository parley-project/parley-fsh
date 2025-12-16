package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class TestCd extends AbstractConsoleTest{

	
	
	@Test
	@Order(1)
	public void testPwd_01() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		String actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("TestFiles"));
	}
	
	@Test
	@Order(2)
	public void testCd_01() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
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
		AbstractConsoleTest.setup("TestFiles");
		String actual = executeCommand("cd Folder01/Folder01abc.1").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01abc.1"));
		
		actual = executeCommand("cd ..").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01"));
		
		
	}
	
	@Test()
	@Order(4)
	public void testCd_03() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		String actual = executeCommand("cd Folder01/Folder01abc.1/Folder01ghi").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01ghi"));
		actual = executeCommand("cd ../../Folder01def.2").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01def.2"));
	}

	@Test()
	@Order(5)
	public void testCd_04() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		String actual = executeCommand("cd Folder01/../SymLink2Folder01/Folder01abc.1/..").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("SymLink2Folder01"));
		
	}
	
	@Test()
	@Order(6)
	public void testCd_05() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		String actual = executeCommand("cd SymLink2Folder01/Folder01abc.1").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("Folder01abc.1"));
		
		actual = executeCommand("cd ../../..").trim();		
		assertEquals("",actual);
		actual = executeCommand("pwd").trim();
		assertTrue(actual.endsWith("BjlShell"));
		
	}
}
