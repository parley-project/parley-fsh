package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class TestBraceExpantion extends AbstractConsoleTest{

	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
	}
	
	@AfterAll
	public static void afterAll() {
		
	}
	
	
	@Test
	public void testArgList01() throws IOException {
		String cmd = "echo a{d,c,b}e\n";		
		String expect = "ade ace abe\n";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	@Test
	public void testArgRange01() throws IOException {
		String cmd = "echo {1..4}\n";		
		String expect = "1 2 3 4\n";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	@Test
	public void testArgRange02() throws IOException {
		String cmd = "echo {10..20..2}\n";		
		String expect = "10 12 14 16 18 20\n";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	@Test
	public void testArgRange03() throws IOException {
		String cmd = "echo {20..10..2}\n";		
		String expect = "20 18 16 14 12 10\n";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	@Test
	public void testArgAlphaRange01() throws IOException {
		String cmd = "echo {a..e}\n";
		String expect = "a b c d e\n";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	@Test
	public void testArgAlphaRange02() throws IOException {
		String cmd = "echo {e..a}\n";
		String expect = "e d c b a\n";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	@Test
	public void testArgAlphaRange03() throws IOException {
		String cmd = "echo p{k..r}s\n";
		String expect = "pks pls pms pns pos pps pqs prs\n";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	
}
