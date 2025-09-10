package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxyFactory;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionIfStatement extends AbstractConsoleTest{

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("LnTestFiles");		
		
	}
	
	@Test
	public void testIfStatent01_1() throws Exception{
		String cmd = "if [ true ] ; then\n"
				+ "		echo one\n"
				+ "fi\n"
				;

		String expect = "one\n";

		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent01_2() throws Exception{
		String cmd = "if [ false ] ; then\n"
				+ "		echo one\n"
				+ "fi\n"
				;

		String expect = "";

		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent01_3() throws Exception{
		String cmd = "if [ false ] ; then\n"
				+ "		echo one\n"
				+ "else \n"
				+ "		echo two\n"
				+ "fi\n"
				;

		String expect = "two\n";

		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent01_4() throws Exception{

		String cmd = ""
				+ "var=1\n"
				+ "if [ $var -eq 1 ] ; then\n"
				+ "		echo one\n"
				+ "else \n"
				+ "		echo two\n"
				+ "fi\n"
				;

		String expect = "one\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent01_5() throws Exception{

		String cmd = ""
				+ "var=1\n"
				+ "if [ $var -ne 1 ] ; then\n"
				+ "		echo one\n"
				+ "else \n"
				+ "		echo two\n"
				+ "fi\n"
				;

		String expect = "two\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent01_6() throws Exception{

		String cmd = ""
				+ "var=1\n"
				+ "if [ $var -ne 1 ] ; then\n"
				+ "		echo one\n"
				+ "elif [ $var -eq 1 ]; then\n"
				+ "		echo \"elif\"\n"
				+ "else \n"
				+ "		echo three\n"
				+ "fi\n"
				;

		String expect = "elif\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent02_1() throws Exception{

		String cmd = ""
				+ "var1=1\n"
				+ "var2=2\n"
				+ "if [ $var1 -eq 1 ] && [ $var2 -eq 2 ] ; then\n"
				+ "		echo one\n"
				+ "elif [ $var -eq 1 ]; then\n"
				+ "		echo \"elif\"\n"
				+ "else \n"
				+ "		echo three\n"
				+ "fi\n"
				;

		String expect = "one\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent02_2() throws Exception{

		String cmd = ""
				+ "var1=1\n"
				+ "var2=2\n"
				+ "if [ $var1 -eq 0 ] || [ $var2 -eq 2 ] ; then\n"
				+ "		echo one\n"
				+ "elif [ $var1 -eq 1 ]; then\n"
				+ "		echo \"elif\"\n"
				+ "else \n"
				+ "		echo three\n"
				+ "fi\n"
				;
		String expect = "one\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent02_3() throws Exception{

		String cmd = ""
				+ "var1=1\n"
				+ "var2=2\n"
				+ "if ![ $var1 -eq 0 ] && [ $var2 -eq 2 ] ; then\n"
				+ "		echo one\n"
				+ "elif [ $var1 -eq 1 ]; then\n"
				+ "		echo \"elif\"\n"
				+ "else \n"
				+ "		echo three\n"
				+ "fi\n"
				;
		String expect = "one\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent02_4() throws Exception{

		String cmd = ""
				+ "var1=1\n"
				+ "var2=2\n"
				+ "if ![[ $var1 -eq 0 ] || [ $var2 -eq 2 ]] ; then\n"
				+ "		echo one\n"
				+ "elif [ $var1 -eq 1 ]; then\n"
				+ "		echo \"elif\"\n"
				+ "else \n"
				+ "		echo three\n"
				+ "fi\n"
				;
		String expect = "elif\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent02_5() throws Exception{

		String cmd = ""
				+ "var1=1\n"
				+ "var2=2\n"
				+ "if [[ $var1 -eq 0 ]||[ $var1 -eq 1 ]] || [ $var2 -eq 2 ] ; then\n"
				+ "		echo one\n"
				+ "elif [ $var1 -eq 1 ]; then\n"
				+ "		echo \"elif\"\n"
				+ "else \n"
				+ "		echo three\n"
				+ "fi\n"
				;
		String expect = "one\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		if( !err.isEmpty()) {
			System.out.println(err);
		}
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent03_1() throws Exception{

		String cmd = ""
				+ "if[ -a /file/path] ; then\n"
				+ "	echo yes\n"
				+ "else\n"
				+ "	echo no\n"
				+ "fi"
				;
		//System.out.println(cmd);
		String expect = "no\n";
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		if( !err.isEmpty()) {
			System.out.println(err);
		}
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent03_2() throws Exception{
		File testFilesDir = new File("LnTestFiles").getCanonicalFile();

		System.setProperty("user.home", testFilesDir.getAbsolutePath());
		FileSourceFactory.setDefaultFactory(new FileProxyFactory());
		
		String [] options = {
				"a","yes",
				"b","no",
				"c","no",
				"d","no",
				"e","yes",
				"f","yes",
				"g","no",
				"h","no",
				"k","no",
				"p","no",
				"r","yes",
				"s","yes",
				"t","no",
				"u","no",
				"w","yes",
				"x","no",
				"G","no",
				"L","no",
				"N","no",
				"O","no",
				"S","no",
				
		};
		
		String cmd1 = "if [ -%s AbcFileA.js ] ; then\n"
				+ "		echo yes\n"
				+ "else\n"
				+ "		echo no\n"
				+ "fi\n"
				;

		for (int idx = 0; idx < options.length; idx++) {
			String cmd = String.format(cmd1, options[idx++]);
			String expect = options[idx];
			ExecuteResult res = executeCommand(cmd,"");
			String out = new String(res.bao.toByteArray());
			String err = new String(res.bae.toByteArray());
			if( !err.isEmpty()) {
				System.out.println(err);
			}
			assertEquals(0, res.exitCode);
			assertEquals(expect, out.trim(),"idx="+idx+" cmd="+(cmd)+" home="+System.getProperty("user.home"));
			assertEquals("", err);
		}
	
	}
	
	@Test
	public void testIfStatent04_1() throws Exception{
		String cmd = "[ 1 -gt 0] {echo yes}"
				;

		String expect = "yes\n";

		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		if( !err.isEmpty()) {
			System.out.println(err);
		}
	
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}
	
	@Test
	public void testIfStatent04_2() throws Exception{
		String cmd = "[ 1 -gt 2] {echo yes}"
				;

		String expect = "";

		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		if( !err.isEmpty()) {
			System.out.println(err);
		}
	
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testIfStatent01_7() throws Exception{

		String cmd = ""
				+ "var=1\n"
				+ "if [ $var -eq 1 ] ; then\n"
				+ "		exit 2\n"
				+ "fi\n"
				+ "echo 'after if'"
				;

		String expect = "";
		boolean tmp = showError;
		showError = false;
		ExecuteResult res = executeCommand(cmd,"");
		showError = tmp;
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(2, res.exitCode);
		assertEquals("Exit 2\n", err);
		assertEquals(expect, out);
		
	}

}
