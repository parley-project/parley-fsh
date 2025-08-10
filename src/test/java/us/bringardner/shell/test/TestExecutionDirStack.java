package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.shell.Console;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionDirStack {

	private static Console console;
	private static FileSource dirStack;
	@BeforeAll
	public static void setup() throws IOException {
		System.setProperty("user.home", "/home");
		String names [] = {"one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve"};
		FileSourceFactory.setDefaultFactory(new MemoryFileSourceFactory());
		FileSource home = FileSourceFactory.getDefaultFactory().createFileSource("/home");
		home.mkdirs();
		
		
		
		dirStack = home.getChild("dirstack");
		dirStack.mkdir();
		FileSourceFactory.getDefaultFactory().setCurrentDirectory(dirStack);
		FileSource dir = dirStack;
		for(String name : names) {
			FileSource d = dir.getChild(name);
			d.mkdir();
			dir = d;
		}
		
		
		
		console = new Console();
		console.setCurrentDirectory(dirStack);
	}

	public static class ExecuteResult {
		int exitCode=0;		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();			
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn,int expectedExitCode) throws IOException {
		console = new Console();
		console.setCurrentDirectory(dirStack);
		PrintStream stdout= System.out;
		PrintStream stderr= System.err;
		InputStream stdin = System.in;
		
		ExecuteResult ret = new ExecuteResult();
		System.setOut(new PrintStream(ret.bao));
		System.setErr(new PrintStream(ret.bae));
		System.setIn(new ByteArrayInputStream(stdIn.getBytes()));
		
		ret.exitCode=console.executeUsingAntlr(command);
		System.setIn(stdin);
		System.setOut(stdout);
		System.setErr(stderr);
		
		String err = new String(ret.bae.toByteArray());
		if( !err.isEmpty()) {
			System.out.println(command);
			System.out.println(err);
		}
		assertEquals(expectedExitCode, ret.exitCode);
		
		return ret;
	}
	
	@Test
	@Order(1)
	public void testDirStack01() throws Exception{
		
		String cmd = ""
				+ "pushd one\n"
				
				;
		
		String expect = 
				"~/dirstack/one ~/dirstack\n"
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
	public void testDirStack02() throws Exception{
		
		String cmd = ""
				+ "pushd one\n"
				+ "pushd two\n"
				
				;
		
		String expect = 
				"~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
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
	public void testDirStack03() throws Exception{
		
		String cmd = ""
				+ "pushd one\n"
				+ "pushd two\n"
				+ "pushd three\n"
				
				;
		
		String expect = 
				"~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two/three ~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
						
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
	public void testDirStack04() throws Exception{
		
		String cmd = ""
				+ "pushd one\n"
				+ "pushd two\n"
				+ "pushd three\n"
				+ "popd\n"
				
				;
		
		String expect = 
				"~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two/three ~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
						
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
	public void testDirStack05() throws Exception{
		
		String cmd = ""
				+ "pushd one\n"
				+ "pushd two\n"
				+ "pushd three\n"
				+ "popd -1\n"
				
				;
		
		String expect = 
				"~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two/three ~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two/three ~/dirstack/one/two ~/dirstack\n"
						
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
	public void testDirStack06() throws Exception{
		
		String cmd = ""
				+ "pushd one\n"
				+ "pushd two\n"
				+ "pushd three\n"
				+ "popd +2\n"
				
				;
		
		String expect = 
				"~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two/three ~/dirstack/one/two ~/dirstack/one ~/dirstack\n"
				+"~/dirstack/one/two/three ~/dirstack/one/two ~/dirstack\n"
						
				;
		
		ExecuteResult res = executeCommand(cmd,"", 0);
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}
	
	
	public void testDirStackxxx() throws Exception{
		
		String cmd = "dir=`pwd`\n"
				+ "echo \"start wd = $dir\"\n"
				+ "for name in *;\n"
				+ "do\n"
				+ "        if [ -d \"$name\" ]; then\n"
				+ "                echo \"echo dir=$dir name=$name\"\n"
				+ "                pushd -n $dir/$name\n"
				+ "                dir2=`pwd`\n"
				+ "                echo \"pwd2 = $dir2\"\n"
				+ "                cd $dir\n"
				+ "        fi\n"
				+ "done\n"
				+ ""
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
	
	
}
