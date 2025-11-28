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
public class TestBraceExpantion extends AbstractConsoleTest{

	
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
		String cmd = "echo a{d,c,b}e\n";
		String expect = "ade ace abe";
		showError=true;
		String actual = executeCommand(cmd);
		
		assertEquals(expect, actual);
	}
	
	
}
