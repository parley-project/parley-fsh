package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionAlias extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("LsTestFiles");				
	}	
	
	@Test
	@Order(1)
	public void testAlias01() throws Exception{
		String cmd = "alias lll='ls -ltr' l2='ls -l'"
				;
		
		String expect = 
				""
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	@Order(3)
	public void testAlias03() throws Exception{
		String cmd = "lll\n"
				;
		
		String expect = 
				  "drwxr-xr-x 1 tony  staff   238  Nov 05 2022 Folder01\n"
				+ "-rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt\n"
				+ "-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 20 2025 AbcFileD.properties\n"
				+ ""
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	@Order(4)
	public void testAlias04() throws Exception{
		String cmd = "lll Folder01\n"
				;
		
		String expect = 
				  "-rwxr-xr-x 1 tony  staff    20  Jun 21 2025 AbcFile01.php\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 17 2025 AbcFile01.properties\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 10 2025 AbcFile01.txt\n"
				+ "drwxr-xr-x 1 tony  staff   238  Jul 23 2013 Folder01abc.1\n"
				+ "drwxr-xr-x 1 tony  staff   170  Jun 27 2025 Folder01def.2\n"
				+ ""
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	@Order(5)
	public void testAlias05() throws Exception{
		String cmd = "lll Folder01\n"
				;
		
		String expect = 
				  "-rwxr-xr-x 1 tony  staff    20  Jun 21 2025 AbcFile01.php\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 17 2025 AbcFile01.properties\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 10 2025 AbcFile01.txt\n"
				+ "drwxr-xr-x 1 tony  staff   238  Jul 23 2013 Folder01abc.1\n"
				+ "drwxr-xr-x 1 tony  staff   170  Jun 27 2025 Folder01def.2\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	@Order(6)
	public void testAlias06() throws Exception{
		String cmd = "l2\n"
				;
		
		String expect = 
				  "-rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js\n"
				+ "-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 20 2025 AbcFileD.properties\n"
				+ "drwxr-xr-x 1 tony  staff   238  Nov 05 2022 Folder01\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
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
		
		String expect = 
				  "drwxr-xr-x 1 tony  staff   238  Nov 05 2022 Folder01\n"
				+ "-rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt\n"
				+ "-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php\n"
				+ "-rwxr-xr-x 1 tony  staff    20  Jun 20 2025 AbcFileD.properties\n"
			
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
	}

}
