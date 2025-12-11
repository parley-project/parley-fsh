package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.shell.Console;
import us.bringardner.shell.test.AbstractConsoleTest.OperatingSystem;

@TestMethodOrder(OrderAnnotation.class)
public class TestLs {

	public static String fileDate;
	public static Console console;

	
	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {

		File file = new File("LsTestFiles");
		System.setProperty("user.home", file.getAbsolutePath());
		console = new Console();
	}

	public static void setAccessDate() throws IOException, InterruptedException {
		File top = new File("LsTestFiles");
		Random r = new Random();
		List<File> complete = new ArrayList<>();
		List<File> files = new ArrayList<>();
		getFiles(files,top);

		while(complete.size() < files.size()) {
			int idx = r.nextInt(files.size());
			if( idx < files.size()) {
				File file = files.get(idx);
				if(!complete.contains(file)) {					
					long cdate = r.nextLong(System.currentTimeMillis());
					FileTime time = FileTime.fromMillis(cdate);
					Files.setAttribute(file.toPath(), "lastAccessTime", time);
					complete.add(file);
					System.out.println(file);
					int sleep = r.nextInt(600000);
					while(sleep < 60000) {
						sleep = r.nextInt(600000);
					}
					
					Thread.sleep(sleep);
				}
			}
		}
	}

	private static void getFiles(List<File> files, File file) {
		files.add(file);
		if( file.isDirectory()) {
			for(File f : file.listFiles()) {
				getFiles(files, f);
			}
		}		
	}

	public static void setDates(Random r, File file) throws IOException, InterruptedException {
		System.out.println(file);
		Path p = file.toPath();
		/*
"lastModifiedTime"    FileTime
"lastAccessTime"      FileTime
"creationTime"        FileTime

		 */


		long cdate = r.nextLong(System.currentTimeMillis());
		FileTime ctime = FileTime.fromMillis(cdate);
		long mdate = cdate+r.nextLong(99999999);
		FileTime mtime = FileTime.fromMillis(mdate);
		long adate = mdate+r.nextLong(99999);
		FileTime atime = FileTime.fromMillis(adate);
		
		if( file.isDirectory()) {
			for(File kid : file.listFiles()) {
				setDates(r,kid);
			}			
		}
		Files.setAttribute(p, "creationTime", ctime);
		Files.setAttribute(p, "lastModifiedTime", mtime);
		Files.setAttribute(p, "lastAccessTime", atime);


		Thread.sleep(60100);

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
		
		console.executeUsingAntlr(command);
		String err = new String(bae.toByteArray());
		
		String actual = new String(bao.toByteArray());
		
		
		if( !err.isEmpty()) {
			System.err.println(err);
			System.err.println("stdout="+actual);
			actual = err;
		}
		return actual;
	}

	@Test
	@Order(1)
	public void testLs() throws IOException {
		String expect = "AbcFileA.js          AbcFileB.php         AbcFileC.txt         AbcFileD.properties  Folder01";
		
		String cmd = "ls";
		String actual = executeLsCommand(true,cmd).trim();
		assertEquals(expect, actual);
	}


	private String executeLsCommand(boolean b, String cmd) throws IOException {
		boolean tmp = console.isInteractive;
		console.isInteractive = b;
		String actual = executeCommand(cmd).trim();
		console.isInteractive = tmp;
		return actual;
	}

	@Test
	@Order(2)
	public void testLs_X() throws IOException {

		String expect = "Folder01             AbcFileA.js          AbcFileB.php         AbcFileD.properties  AbcFileC.txt";
		String cmd = "ls -X";

		String actual = executeLsCommand(true,cmd).trim();		
		assertEquals(expect, actual);
		
		expect = "Folder01\n"
				+ "AbcFileA.js\n"
				+ "AbcFileB.php\n"
				+ "AbcFileD.properties\n"
				+ "AbcFileC.txt"
				;
		if( AbstractConsoleTest.getOs()==OperatingSystem.Windows) {
			expect = expect.replaceAll("\n", "\r\n");
		}
		String actual2 = executeLsCommand(false,cmd).trim();		
		assertEquals(expect, actual2);
	}

	
	@Test
	@Order(3)
	public void testLs_a() throws IOException {
		String expect = ".Hidden01.txt        .Hidden02.txt        AbcFileA.js          AbcFileB.php         AbcFileC.txt         AbcFileD.properties  Folder01";
		String cmd = "ls -a";

		String actual = executeLsCommand(true,cmd).trim();		
		assertEquals(expect, actual);
		
		expect = ".Hidden01.txt\n"
				+ ".Hidden02.txt\n"
				+ "AbcFileA.js\n"
				+ "AbcFileB.php\n"
				+ "AbcFileC.txt\n"
				+ "AbcFileD.properties\n"
				+ "Folder01";
		if( AbstractConsoleTest.getOs()==OperatingSystem.Windows) {
			expect = expect.replaceAll("\n", "\r\n");
		}
		
		String actual2 = executeLsCommand(false,cmd).trim();		
		assertEquals(expect, actual2);

	}
	
	@Test
	@Order(4)
	public void testLs_l() throws IOException {


		String expect[] = (
				    "-rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js\n"
				    + "-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php\n"
				    + "-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt\n"
				    + "-rwxr-xr-x 1 tony  staff    20  Jun 20 2025 AbcFileD.properties\n"
				    + "drwxr-xr-x 1 tony  staff   238  Nov 05 2022 Folder01").split("\n");				  
				  ;
		
		String cmd = "ls -l";

		String[] actual = executeLsCommand(true,cmd).trim().split("\n");
		
		checkResult(expect, actual);
	}

	@Test
	@Order(4)
	public void testLs_lt() throws IOException {


		String expect[] = 
				    (  "-rwxr-xr-x 1 tony  staff    20  Jun 20 2025 AbcFileD.properties\n"
				    + "-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php\n"
				    + "-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt\n"
				    + "-rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js\n"
				    + "drwxr-xr-x 1 tony  staff   238  Nov 05 2022 Folder01")	.split("\n")			  
				  ;
		
		
		String cmd = "ls -lt";

		String [] actual = executeLsCommand(true,cmd).trim().split("\n");
		checkResult(expect, actual);	
	}
	
	@Test
	@Order(4)
	public void testLs_ltr() throws IOException {


		String expect[] = (
				      "drwxr-xr-x 1 tony  staff   238  Nov 05 2022 Folder01\n"
				    + "-rw-r--r-- 1 tony  staff  1547  Jun 04 2025 AbcFileA.js\n"
				    + "-rwxr-xr-x 1 tony  staff    20  Jun 09 2025 AbcFileC.txt\n"
				    + "-rwxr-xr-x 1 tony  staff  3710  Jun 16 2025 AbcFileB.php\n"
				    + "-rwxr-xr-x 1 tony  staff    20  Jun 20 2025 AbcFileD.properties\n").split("\n");
				  
				  ;
		
		String cmd = "ls -ltr";

		String actual[] = executeLsCommand(true,cmd).trim().split("\n");
		checkResult(expect,actual);
	}
	
	private void checkResult(String[] expect, String[] actual) {
		assertEquals(expect.length,actual.length);
		for (int idx = 0; idx < actual.length; idx++) {
			String line = expect[idx];
			int pos=line.lastIndexOf(' ');
			String name = line.substring(pos);
			assertTrue(actual[idx].trim().endsWith(name));
		}
	
		
	}

	@Test
	@Order(5)
	public void testLsTilde() throws IOException {


	
		String expect = "AbcFile01.php         AbcFile01.properties  AbcFile01.txt         AbcFile.php           AbcFile.properties    AbcFile.txt           \n"
				  + "Folder01ghi           Folder01jkl           AbcFile.php           AbcFile.properties    AbcFile01def2.txt";
		
		String cmd = "ls ~/Folder01";

		String actual = executeLsCommand(true,cmd).trim();		
		assertEquals(expect, actual);
		
		expect = "AbcFile01.php\n"
				+ "AbcFile01.properties\n"
				+ "AbcFile01.txt\n"
				+ "AbcFile.php\n"
				+ "AbcFile.properties\n"
				+ "AbcFile.txt\n"
				+ "Folder01ghi\n"
				+ "Folder01jkl\n"
				+ "AbcFile.php\n"
				+ "AbcFile.properties\n"
				+ "AbcFile01def2.txt"
				;
		if( AbstractConsoleTest.getOs()==OperatingSystem.Windows) {
			expect = expect.replaceAll("\n", "\r\n");
		}
		
		String actual2 = executeLsCommand(false,cmd).trim();		
		assertEquals(expect, actual2);
	}

}
