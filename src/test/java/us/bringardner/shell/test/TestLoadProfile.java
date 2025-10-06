package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;

public class TestLoadProfile extends AbstractConsoleTest {


	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("LoadProfileTestFiles");
		
	}

	@Test
	public void testSource01() throws IOException {
		Console c = new Console();
		Object var1 = c.getVariable("var1");
		assertEquals(1, var1);
		Object var2 = c.getVariable("var2");
		assertEquals(2, var2);
		ExecuteResult res = executeCommand("ll", "");
		assertEquals(0, res.exitCode);
		assertEquals("", new String(res.bae.toByteArray()));
		/*
		 * -rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js
-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php
-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt
		 */
		String out = new String(res.bao.toByteArray());
		String [] lines = out.split("\n");
		
		assertEquals(3, lines.length);
		assertTrue(lines[0].endsWith("AbcFileA.js"));
		assertTrue(lines[1].endsWith("AbcFileB.php"));
		assertTrue(lines[2].endsWith("AbcFileC.txt"));
	}
	
}
