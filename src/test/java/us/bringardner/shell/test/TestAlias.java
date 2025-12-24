package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestAlias extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("LsTestFiles");	
		setupAlias01();
	}	
	
	
	public static void setupAlias01() throws IOException{
		String cmd = "alias lll='ls -ltr' l2='ls -l'"
				;
		
		String expect = 
				""
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = res.getStdOut();
		String err = res.getStdErr();;
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	@Order(2)
	public void testAlias02() throws Exception{
		String cmd = "alias -p"
				;
		
		String expect = 
				"alias l2='ls -l'\n"
				+"alias lll='ls -ltr'\n"
			
				;
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = res.getStdOut().replaceAll("\r", "");
		String err = res.getStdErr().replaceAll("\r", "");
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	@Order(3)
	public void testAlias03() throws Exception{
		String cmd = "lll\n"
				;
		
		String[] expect = 
				  ("Folder01\n"
				+ "AbcFileA.js\n"
				+ "AbcFileC.txt\n"
				+ "AbcFileB.php\n"
				+ "AbcFileD.properties"
				).split("\n");
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		assertEquals(0, res.exitCode);
		String err = res.getStdErr();;
		assertEquals("", err);
		String [] out = res.getStdOut().replaceAll("\r", "").split("\n");
		
		assertEquals(expect.length, out.length);
		for (int idx = 0; idx < out.length; idx++) {
			assertTrue(out[idx].endsWith(expect[idx]));
		}
		
		
	}

	@Test
	@Order(4)
	public void testAlias04() throws Exception{
		String cmd = "lll Folder01\n"
				;
		
		String expect[] = 
				  (
				  "Folder01abc.1\n"
				  + "AbcFile01.txt\n"
				  + "AbcFile01.properties\n"
				  + "AbcFile01.php\n"				
				  + "Folder01def.2"
				).split("\n");
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String [] out = res.getStdOut().replaceAll("\r", "").split("\n");
		String err = res.getStdErr();;
		assertEquals("", err);
		
		assertEquals(0, res.exitCode);
		assertEquals(expect.length, out.length);
		for (int idx = 0; idx < out.length; idx++) {
			assertTrue(out[idx].endsWith(expect[idx]));
		}
	}

	@Test
	@Order(5)
	public void testAlias05() throws Exception{
		String cmd = "lll Folder01\n"
				;
		
		String expect[] = 
				  (
				  "Folder01abc.1\n"
				  + "AbcFile01.txt\n"
				  + "AbcFile01.properties\n"
				  + "AbcFile01.php\n"				
				  + "Folder01def.2"
				).split("\n");
				;
		ExecuteResult res = executeCommand(cmd,"", 0);
		String err = res.getStdErr();;
		assertEquals("", err);
		
		assertEquals(0, res.exitCode);
		String [] out = res.getStdOut().replaceAll("\r", "").split("\n");		
		assertEquals(expect.length, out.length);
		for (int idx = 0; idx < out.length; idx++) {
			assertTrue(out[idx].endsWith(expect[idx]));
		}
		
	}
	
	@Test
	@Order(6)
	public void testAlias06() throws Exception{
		String cmd = "l2\n"
				;
		
		String []expect = 
				  ("AbcFileA.js\n"
				+ "AbcFileB.php\n"
				+ "AbcFileC.txt\n"
				+ "AbcFileD.properties\n"
				+ "Folder01").split("\n");
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String err = res.getStdErr();;
		assertEquals("", err);
		
		assertEquals(0, res.exitCode);
		String [] out = res.getStdOut().replaceAll("\r", "").split("\n");		
		assertEquals(expect.length, out.length);
		for (int idx = 0; idx < out.length; idx++) {
			assertTrue(out[idx].endsWith(expect[idx]));
		}
		
	}
	
	@Test
	@Order(7)
	public void testAlias07() throws Exception{
		String cmd = "unalias l2\n"
				;
		
		String expect = 
				""
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = res.getStdOut();
		String err = res.getStdErr();;
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}

	@Test
	@Order(8)
	public void testAlias08() throws Exception{
		String cmd = "alias -p"
				;
		
		String expect = 
				"alias lll='ls -ltr'\n"
			
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = res.getStdOut();
		String err = res.getStdErr();;
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
	@Test
	@Order(9)
	public void testAlias09() throws Exception{
		String cmd = "unalias -a"
				;
		
		String expect = 
				""
			
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = res.getStdOut();
		String err = res.getStdErr();;
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}
	
	@Test
	@Order(10)
	public void testAlias10() throws Exception{
		String cmd = "alias ls='ls -ltr'\n"
				+ "ls\n"
				;
		
		String expect[] = 
				  ("Folder01\n"
				+ "AbcFileA.js\n"
				+ "AbcFileC.txt\n"
				+ "AbcFileB.php\n"
				+ "AbcFileD.properties").split("\n");
			
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String err = res.getStdErr();;
		assertEquals("", err);
		assertEquals(0, res.exitCode);
		String [] out = res.getStdOut().replaceAll("\r", "").split("\n");		
		assertEquals(expect.length, out.length);
		for (int idx = 0; idx < out.length; idx++) {
			assertTrue(out[idx].endsWith(expect[idx]));
		}
		
	}

}
