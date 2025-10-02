package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
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

	class RxTest {
		String value;
		String rx;
		String expect;

		public RxTest(String value,String rx,String expectt) {
			this.value = value;
			this.rx = rx;
			this.expect = expectt;
		}

	}

	/*
 these MUST be escaped in an rx
/,<>;'#`-={}[]|\ 

	 */
	@Test	
	public void testConditionalExpression_01() throws Exception{
		boolean tmp = showError;
		showError = true;

		RxTest [] tests = {
				new RxTest("12abXY","[0-9a-zA-Z]+","yes"),
				new RxTest("12abXY","[[:alnum:]]","yes"),				
				new RxTest("abXY","[[:alpha:]]","yes"),
				new RxTest("abXY","[[:ascii:]]","yes"),
				new RxTest("\t","[[:blank:]]","yes"),
				new RxTest(""+((char)0x1F),"[[:cntrl:]]","yes"),
				new RxTest("123","[[:digit:]]","yes"),
				new RxTest("asd","[[:graph:]]","yes"),
				new RxTest("ab","[[:lower:]]","yes"),
				new RxTest("abXY","[[:print:]]","yes"),
				new RxTest("!@#","[[:punct:]]","yes"),
				new RxTest("\n","[[:space:]]","yes"),
				new RxTest("XY","[[:upper:]]","yes"),
				new RxTest("a_word","[[:word:]]","yes"),
				new RxTest("1234567890abcdef","[[:xdigit:]]","yes"),

				
				new RxTest("12abXY","[^0-9a-zA-Z]+","no"),
				new RxTest("12abXY","[[:^alnum:]]","no"),				
				new RxTest("abXY"  ,"[[:^alpha:]]","no"),
				new RxTest("abXY"  ,"[[:^ascii:]]","no"),
				new RxTest("\t"    ,"[[:^blank:]]","no"),
				new RxTest(""+((char)0x1F),"[[:^cntrl:]]","no"),
				new RxTest("123"   ,"[[:^digit:]]","no"),
				new RxTest("asd"   ,"[[:^graph:]]","no"),
				new RxTest("ab"    ,"[[:^lower:]]","no"),
				new RxTest("abXY"  ,"[[:^print:]]","no"),
				new RxTest("!@#"   ,"[[:^punct:]]","no"),
				new RxTest("\n"    ,"[[:^space:]]","no"),
				new RxTest("XY"    ,"[[:^upper:]]","no"),
				new RxTest("a_word" ,"[[:^word:]]","no"),
				new RxTest("1234567890abcdef","[[:^xdigit:]]","no"),
		};

		try {

			String cmd1 =""
					+"val=\"%s\"\n"
					+"if [[ $val =~ %s ]]\n"
					+ "then\n"
					+ "		echo \"yes for \"$val\"\"\n"
					+ "else \n"
					+ "	echo \"no for \"$val\"\"\n"
					+ "fi\n"
					;

			for (int idx = 0; idx < tests.length; idx++) {
				RxTest test = tests[idx];
				String cmd = String.format(cmd1, test.value,test.rx);
				ExecuteResult res = executeCommand(cmd,"");
				String actual = new String(res.bao.toByteArray());
				String err = new String(res.bae.toByteArray());

				assertEquals(0, res.exitCode,"idx="+idx+" cmd="+cmd);
				assertTrue(actual.startsWith(test.expect),"idx="+idx+" cmd="+cmd);
				assertEquals("", err,"idx="+idx+" cmd="+cmd);
			}
		} finally {
			showError=tmp;
		}
	}

	/*
	 these MUST be escaped in an rx
	/,<>;'#`-={}[]|\ 

	 */
	@Test	
	public void testConditionalExpression_02() throws Exception{
		boolean tmp = showError;
		showError = true;
		
		RxTest [] tests = {
				new RxTest("string word","word$","yes"),
				new RxTest("word string","word$","no"),
				
				
				new RxTest("text","[[:^lower:]]+","no"),
				new RxTest("b","[[:space:]]*(a)?b","yes"),
				
				new RxTest(" aab","[[:space:]]*(a)?b","yes"),
				new RxTest("string","string","yes"),
				new RxTest("string word","string","yes"),
				new RxTest("word string","string","yes"),				
				new RxTest("string word","^string","yes"),
				new RxTest("string word","^\"string\"","yes"),
				new RxTest("word string word","^\"string\"","no"),				
				new RxTest(" aaaaaab","[[:space:]]*(a)?b","yes"),
				new RxTest("xaby","[[:space:]]*(a)?b","yes"),
				new RxTest(" ab","[[:space:]]*(a)?b","yes"),
				new RxTest("string word","^\"string\"","yes"),
		};

		try {

			String cmd1 =""
					+"val=\"%s\"\n"
					+"if [[ $val =~ %s ]]\n"
					+ "then\n"
					+ "		echo \"yes for \"$val\"\"\n"
					+ "else \n"
					+ "	echo \"no for \"$val\"\"\n"
					+ "fi\n"
					;

			for (int idx = 0; idx < tests.length; idx++) {
				RxTest test = tests[idx];
				String cmd = String.format(cmd1, test.value,test.rx);
				ExecuteResult res = executeCommand(cmd,"");
				String actual = new String(res.bao.toByteArray());
				String err = new String(res.bae.toByteArray());

				assertEquals(0, res.exitCode,"idx="+idx+" cmd="+cmd);
				assertTrue(actual.startsWith(test.expect),"idx="+idx+" cmd="+cmd);
				assertEquals("", err,"idx="+idx+" cmd="+cmd);
			}
		} finally {
			showError=tmp;
		}
	}

}
