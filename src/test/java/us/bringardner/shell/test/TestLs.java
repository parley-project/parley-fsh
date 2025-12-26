package us.bringardner.shell.test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSourceFactory;

public class TestLs extends AbstractConsoleTest {

	public static String fileDate;
	

	
	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		AbstractConsoleTest.setup("LsTestFiles");		
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
	public void testLs_l_2() throws IOException {


		// The result is different than linux... In linux the folder is not listed
		String expect[] = (
					"drwxr-xr-x 1 tony  staff   170  May 31 2015 Folder01ghi\n"
					+ "\n"
					+ "/Volumes/Data/eclipse-git/BjlShell/LsTestFiles/Folder01/Folder01abc.1/Folder01ghi:\n"
					+ "-rwxr-xr-x 1 tony  staff    20  Nov 30 2020 AbcFile.php\n"
					+ "-rwxr-xr-x 1 tony  staff    20  Jul 03 2018 AbcFile.properties\n"
					+ "-rwxr-xr-x 1 tony  staff    20  Dec 15 2023 AbcFile.txt"
				    
				    ).split("\n");				  
				  
		
		String cmd = "ls -lR ~/Folder01/*1/*i";

		String actual1 = executeLsCommand(true,cmd).trim();
		String[] actual = actual1.split("\n");
		
		checkResult(expect, actual);
	}

	@Test
	public void testLs_ld() throws IOException {


		String expect[] = {"drwxr-xr-x 1 tony  staff   306  May 08 2025 LsTestFiles"}	;
		
		String cmd = "ls -ld";

		String[] actual = executeLsCommand(true,cmd).trim().split("\n");
		
		checkResult(expect, actual);
	}

	@Test
	public void testLs_lR() throws IOException {

		String expect[] = (
				     "-rw-r--r-x@ 1 tony  staff  1547 Jun  4  2025 AbcFileA.js\n"
				    + "-rwxr-xr-x@ 1 tony  staff  3710 Jun 16  2025 AbcFileB.php\n"
				    + "-rwxr-xr-x@ 1 tony  staff    20 Jun  9  2025 AbcFileC.txt\n"
				    + "-rwxr-xr-x@ 1 tony  staff    20 Jun 20  2025 AbcFileD.properties\n"
				    + "drwxr-xr-x@ 4 tony  staff   238 Nov  5  2022 Folder01\n"
				    + "\n"
				    + "Folder01:\n"
				    + "-rwxr-xr-x@ 1 tony  staff   20 Jun 21  2025 AbcFile01.php\n"
				    + "-rwxr-xr-x@ 1 tony  staff   20 Jun 17  2025 AbcFile01.properties\n"
				    + "-rwxr-xr-x@ 1 tony  staff   20 Jun 10  2025 AbcFile01.txt\n"
				    + "drwxr-xr-x@ 4 tony  staff  238 Jul 23  2013 Folder01abc.1\n"
				    + "drwxr-xr-x@ 2 tony  staff  170 Jun 27 00:53 Folder01def.2\n"
				    + "\n"
				    + "Folder01/Folder01abc.1:\n"
				    + "-rwxr-xr-x@ 1 tony  staff   20 Jun 10  2025 AbcFile.php\n"
				    + "-rwxr-xr-x@ 1 tony  staff   20 Dec 13  2014 AbcFile.properties\n"
				    + "-rwxr-xr-x@ 1 tony  staff   20 Jun 13  2025 AbcFile.txt\n"
				    + "drwxr-xr-x@ 2 tony  staff  170 May 31  2015 Folder01ghi\n"
				    + "drwxr-xr-x@ 2 tony  staff  170 Aug  4  2020 Folder01jkl\n"
				    + "\n"
				    + "Folder01/Folder01abc.1/Folder01ghi:\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Nov 30  2020 AbcFile.php\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Jul  3  2018 AbcFile.properties\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Dec 15  2023 AbcFile.txt\n"
				    + "\n"
				    + "Folder01/Folder01abc.1/Folder01jkl:\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Jun 27 04:53 AbcFile.php\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Jun 21  2025 AbcFile.properties\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Jun  8  2025 AbcFile.txt\n"
				    + "\n"
				    + "Folder01/Folder01def.2:\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 May 31  2025 AbcFile.php\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Jun 17  2025 AbcFile.properties\n"
				    + "-rwxr-xr-x@ 1 tony  staff  20 Jun  9  2025 AbcFile01def2.txt"
				    + "").split("\n");				  
				  ;
		
		String cmd = "ls -lR";

		String actual1 = executeLsCommand(true,cmd).trim();
		String[] actual =actual1.split("\n");
		
		checkResult(expect, actual);
	}
	
	@Test
	public void testLs_lt() throws IOException {


		String expect[] = 
				    (  "drwxr-xr-x@ 4 tony  staff   238 Dec 26 01:10 Folder01\n"
				    		+ "-rwxr-xr-x@ 1 tony  staff    20 Jun 20  2025 AbcFileD.properties\n"
				    		+ "-rwxr-xr-x@ 1 tony  staff  3710 Jun 16  2025 AbcFileB.php\n"
				    		+ "-rwxr-xr-x@ 1 tony  staff    20 Jun  9  2025 AbcFileC.txt\n"
				    		+ "-rw-r--r-x@ 1 tony  staff  1547 Jun  4  2025 AbcFileA.js").replaceAll("\r", "").split("\n")			  
				  ;
		
		
		String cmd = "ls -lt";

		String [] actual = executeLsCommand(true,cmd).trim().split("\n");
		checkResult(expect, actual);	
	}
	
	@Test
	public void testLs_ltr() throws IOException {


		String expect[] = (
				      "-rw-r--r-x@ 1 tony  staff  1547 Jun  4  2025 AbcFileA.js\n"
				      + "-rwxr-xr-x@ 1 tony  staff    20 Jun  9  2025 AbcFileC.txt\n"
				      + "-rwxr-xr-x@ 1 tony  staff  3710 Jun 16  2025 AbcFileB.php\n"
				      + "-rwxr-xr-x@ 1 tony  staff    20 Jun 20  2025 AbcFileD.properties\n"
				      + "drwxr-xr-x@ 4 tony  staff   238 Dec 26 01:10 Folder01").split("\n");
				  
				  ;
		
		String cmd = "ls -ltr";

		String actual[] = executeLsCommand(true,cmd).trim().split("\n");
		checkResult(expect,actual);
	}
	
	private void checkResult(String[] expect, String[] actual) {
		//assertEquals(expect.length,actual.length);
		for (int idx = 0; idx < actual.length; idx++) {
			String line = expect[idx];
			int pos=line.lastIndexOf(' ');
			if( pos < 0 ) {
				String test = actual[idx];
				assertTrue(test.trim().endsWith(line.trim()),"idx="+idx);
			} else {
				String name = line.substring(pos);
				assertTrue(actual[idx].trim().endsWith(name),"idx="+idx);
			}
		}
	
		
	}

	@Test
	public void testLsTilde() throws IOException {


	
		String expect = "AbcFile01.php         AbcFile01.properties  AbcFile01.txt         Folder01abc.1         Folder01def.2";
		
		String cmd = "ls ~/Folder01";
		if(FileSourceFactory.isWindows()) {
			cmd = "ls ~\\Folder01";
		}
		String actual = executeLsCommand(true,cmd).trim();		
		assertEquals(expect, actual);
		
		expect = "AbcFile01.php\n"
				+ "AbcFile01.properties\n"
				+ "AbcFile01.txt\n"
				+ "Folder01abc.1\n"
				+ "Folder01def.2"
				;
		if( AbstractConsoleTest.getOs()==OperatingSystem.Windows) {
			expect = expect.replaceAll("\n", "\r\n");
		}
		
		String actual2 = executeLsCommand(false,cmd).trim();		
		assertEquals(expect, actual2);
	}

	@Test
	public void testLs_R() throws IOException {
	
		String expect[]= (
				     "AbcFileA.js             AbcFileB.php            AbcFileC.txt            AbcFileD.properties     Folder01\n"
				     + "\n"
				     + "Folder01:\n"
				     + "AbcFile01.php           AbcFile01.properties    AbcFile01.txt           Folder01abc.1           Folder01def.2\n"
				     + "\n"
				     + "Folder01/Folder01abc.1:\n"
				     + "AbcFile.php             AbcFile.properties      AbcFile.txt             Folder01ghi             Folder01jkl\n"
				     + "\n"
				     + "Folder01/Folder01abc.1/Folder01ghi:\n"
				     + "AbcFile.php             AbcFile.properties      AbcFile.txt\n"
				     + "\n"
				     + "Folder01/Folder01abc.1/Folder01jkl:\n"
				     + "AbcFile.php             AbcFile.properties      AbcFile.txt\n"
				     + "\n"
				     + "Folder01/Folder01def.2:\n"
				     + "AbcFile.php             AbcFile.properties      AbcFile01def2.txt"
				    + "").replaceAll(" ", "").split("\n");				  
				  ;
		
		String cmd = "ls -R";
	
		String actual1 = executeLsCommand(true,cmd).trim().replaceAll(" ", "");
		String actual[] = actual1.split("\n"); 
		for (int idx = 0; idx < actual.length; idx++) {
			if( actual[idx].endsWith(":")) {
				assertTrue(actual[idx].endsWith(actual[idx]));
			} else {
				assertEquals(expect[idx], actual[idx],"idx="+idx);
			}
		}
		
	}

}
