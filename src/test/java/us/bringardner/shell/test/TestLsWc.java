package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.shell.Console;
import us.bringardner.shell.commands.Ls;

public class TestLsWc extends AbstractConsoleTest{

	public static String fileDate;
	
	@BeforeAll
	public static void beforeAll() throws IOException {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		FileSource dir = factory.listRoots()[0];
		factory.setCurrentDirectory(dir);
		fillDir(dir,0);
		FileSource dir2 = dir.getChild("Folder01");
		assertTrue(dir2.mkdirs());
		fillDir(dir2,1);
		long time = System.currentTimeMillis();
		
		fileDate = Ls.DATE_FORMAT.format(new Date(time));
	
		FileSourceFactory.setDefaultFactory(factory);
		console = new Console();
	}
	
	private static void fillDir(FileSource dir,int i) throws IOException {
		for(int idx=0; idx < 10; idx++ ) {
			FileSource file = dir.getChild("Test"+(i==0?"":""+i+"-")+idx+".txt");
			try(OutputStream out = file.getOutputStream()) {
				out.write("0123456789".getBytes());
			}
		}
		
		
	}

	@AfterAll
	public static void afterAll() {
		
	}
	
	@Test
	public void testLs() throws IOException {
		String expect = 
				"Folder01\n"
				+ "Test0.txt\n"
				+ "Test1.txt\n"
				+ "Test2.txt\n"
				+ "Test3.txt\n"
				+ "Test4.txt\n"
				+ "Test5.txt\n"
				+ "Test6.txt\n"
				+ "Test7.txt\n"
				+ "Test8.txt\n"
				+ "Test9.txt\n";
		
		String cmd = "ls";
		ExecuteResult res = executeCommand(cmd,"");
		String actual = new String(res.bao.toByteArray());
		
		assertEquals(expect, actual);
		
		
	}
	
	@Test
	public void testLs_l() throws IOException {
		
		String expect = (
				  "drwxrwxrwx 1 tony  staff     0  DATE Folder01\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test0.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test2.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test3.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test4.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test5.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test6.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test7.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test8.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test9.txt\n").replaceAll("DATE", fileDate);
		
		String cmd = "ls -l";
		ExecuteResult res = executeCommand(cmd,"");
		String actual = new String(res.bao.toByteArray());		
		assertEquals(expect, actual);
		
	}
	
	@Test
	public void testWc() throws IOException {
		String expect = "0       9      44";
		String stdin = "the quick brown fox jumped over the lasy dog";
		
		String cmd = "wc";
		ExecuteResult res = executeCommand(cmd,stdin);
		String actual = new String(res.bao.toByteArray());				
		assertEquals(expect, actual.trim());
		
		
	}

	@Test
	public void testWc_l() throws IOException {
		String expect = "3";
		
		
		String stdin = "the quick \nbrown fox \njumped over \nthe lasy dog";
		
		//c.executeWithoutAntlr("wc -l");
		String cmd = "wc -l";
		ExecuteResult res = executeCommand(cmd,stdin);
		String actual = new String(res.bao.toByteArray());				
		assertEquals(expect, actual.trim());
		
		
	}

	@Test
	public void testLs_l_WithPath() throws IOException {
		
		String expect = (
				  "-rwxrwxrwx 1 tony  staff    10  DATE Test0.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test1.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test2.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test3.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test4.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test5.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test6.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test7.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test8.txt\n"
				+ "-rwxrwxrwx 1 tony  staff    10  DATE Test9.txt\n").replaceAll("DATE", fileDate);
		
		String cmd = ("ls -l *.txt");
		ExecuteResult res = executeCommand(cmd,"");
		String actual = new String(res.bao.toByteArray());
		
		assertEquals(expect, actual);
		
	}
	
	@Test
	public void testLs_l_WithAbsolutePath() throws IOException {
		
		String expect = (
				    "-rwxrwxrwx 1 tony  staff    10  DATE Test1-0.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-1.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-2.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-3.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-4.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-5.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-6.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-7.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-8.txt\n"
				  + "-rwxrwxrwx 1 tony  staff    10  DATE Test1-9.txt\n").replaceAll("DATE", fileDate);
		
		String cmd = "ls -l /Folder01/*.txt";
		ExecuteResult res = executeCommand(cmd,"");
		String actual = new String(res.bao.toByteArray());		
		assertEquals(expect, actual);
		
	}
}
