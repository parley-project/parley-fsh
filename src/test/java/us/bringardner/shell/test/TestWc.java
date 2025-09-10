package us.bringardner.shell.test;

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

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;
import us.bringardner.shell.Console;

public class TestWc {

	public static String fileDate;
	public static Console console;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		File file = new File("WcTestFiles").getCanonicalFile();
		
		System.setProperty("user.home", file.getAbsolutePath());
		console = new Console();
	}
	
	@AfterAll
	public static void afterAll() {
		
	}
	
	public String executeCommand(String command) throws IOException {
		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		console.setStdOut(new PrintStream(bao));
		ByteArrayOutputStream bae = new ByteArrayOutputStream();
		console.setStdErr(new PrintStream(bae));
		console.setStdIn(new ByteArrayInputStream(new byte[0]));
		
		console.executeWithoutAntlr(command);
		String actual = new String(bao.toByteArray());
		String err   = new String(bae.toByteArray()).trim();
		if( !err.isEmpty()) {
			actual+= err;
		}
		
		return actual;
	}
	
	/*
	 default
 	  45     168    1547 AbcFile.js
     156     537    3866 AbcFile.php
             553?
      65     553   24216 
      65     517   24216 AbcFile.png
      45     314    2048 AbcFile.properties
     122     679    4958 AbcFile.txt
     433    2215   36635 total
     
     -L 
      79 AbcFile.js
      87 AbcFile.php
      76 AbcFile.properties
     126 AbcFile.txt
     126 total
     
     -c  Files size
    1547 AbcFile.js
    3866 AbcFile.php
    2048 AbcFile.properties
    4958 AbcFile.txt
   12419 total
   
   -l
     45 AbcFile.js
     156 AbcFile.php
      45 AbcFile.properties
     122 AbcFile.txt
     368 total
     
     -m
     1547 AbcFile.js
    3866 AbcFile.php
    2048 AbcFile.properties
    4958 AbcFile.txt
   12419 total
   
   -w
    168 AbcFile.js
     537 AbcFile.php
     314 AbcFile.properties
     679 AbcFile.txt
    1698 total
   
    wc -Lclw * 
      45     168    1547      79 AbcFile.js
     156     537    3866      87 AbcFile.php
      65     517   24216    1182 AbcFile.png
      45     314    2048      76 AbcFile.properties
     122     679    4958     126 AbcFile.txt
     433    2215   36635    1182 total
     
     wc -Lmlw *
      45     168    1547      79 AbcFile.js
     156     537    3866      87 AbcFile.php
      45     314    2048      76 AbcFile.properties
     122     679    4958     126 AbcFile.txt
     368    1698   12419     126 total
     
	 */
	@Test
	public void testWc_All() throws IOException {
		String expect1 = (""
				+ "      45     168    1547      79 AbcFile.js\n"
				+ "     156     537    3710      86 AbcFile.php\n"
				+ "      45     314    2048      76 AbcFile.properties\n"
				+ "     122     679    4958     126 AbcFile.txt\n"
				+ "     368    1698   12263     126 total\n");
		
		String expect = expect1.replace(" ", "");
				
		String actual1 = executeCommand("wc -Lclw *");
		//System.out.println(actual1);
		String actual = actual1.replace(" ", "");
		assertEquals(expect, actual);
		if( !expect.equals(actual)) {
			throw new IOException("wc -Lclw *;  does not match]n"+expect1+"\n"+actual1);
		}
		actual1 = executeCommand("wc -Lmlw *").replace(" ", "");
		if( !expect.equals(actual)) {
			throw new IOException("wc -Lclw *;  does not match]n"+expect1+"\n"+actual1);
		}
		
	}

	@Test
	public void testWc_Stdin() throws IOException {
		String expect = ("156     537    3710");
		String actual = executeCommand("wc < AbcFile.php").trim();
		if( !expect.equals(actual)) {
			throw new IOException("wc < AbcFile.php  does not match]n"+expect+"\n"+actual);
		}
		
	}

	@Test
	public void testWc_L() throws IOException {
		String expect = ("86");
		String cmd = "wc -L < AbcFile.php";
		String actual = executeCommand(cmd).trim();
		if( !expect.equals(actual)) {
			throw new IOException(cmd+" does not match]n"+expect+"\n"+actual);
		}
		
	}

	@Test
	public void testWc_c() throws IOException {
		String expect = ("3710");
		String cmd = "wc -c < AbcFile.php";
		String actual = executeCommand(cmd).trim();
		if( !expect.equals(actual)) {
			throw new IOException(cmd+" does not match]n"+expect+"\n"+actual);
		}
		
	}

	@Test
	public void testWc_m() throws IOException {
		String expect = ("3710");
		String cmd = "wc -m < AbcFile.php";
		String actual = executeCommand(cmd).trim();
		if( !expect.equals(actual)) {
			throw new IOException(cmd+" does not match]n"+expect+"\n"+actual);
		}
		
	}

	@Test
	public void testWc_l() throws IOException {
		String expect = ("156");
		String cmd = "wc -l < AbcFile.php";
		String actual = executeCommand(cmd).trim();
		if( !expect.equals(actual)) {
			throw new IOException(cmd+" does not match]n"+expect+"\n"+actual);
		}
		
	}
	
	@Test
	public void testWc_w() throws IOException {
		String expect = ("537");
		String cmd = "wc -w < AbcFile.php";
		String actual = executeCommand(cmd).trim();
		if( !expect.equals(actual)) {
			throw new IOException(cmd+" does not match]n"+expect+"\n"+actual);
		}
		
	}
}
