package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestSource extends AbstractConsoleTest {


	
	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("SourceTestFiles");
		
	}

	@AfterAll
	public static void afterAll() {

	}


	@Test
	public void testSource01() throws IOException {
		testSource01("source");
		testSource01(".");
	}
	
	public void testSource01(String name) throws IOException {
		String expect = "init pos=one two\n"
				+ "enter test pos=\"one two\"\n"
				+ "enter s1 pos=\"ones1\"\n"
				+ "enter s2 pos=\"s1one s1two s1three\"\n"
				+ "exit s2 pos=\"s1one s1two s1three\"\n"
				+ "exit s1 pos=\"ones1\"\n"
				+ "exit test pos=\"one two\"\n"
				;
		
		String code = "echo init pos=$*\n"
				+ name+" ~/test01.sh";
		ExecuteResult res = executeCommand(code, "","one","two");
		validate("testSource01 "+name,res,0,expect,"");
	}


	@Test
	public void testSource02() throws IOException {
		testSource01("source");
		testSource01(".");
	}
	
	public void testSource02(String name) throws IOException {
		String expect = ""
				;
		
		String code = ""
				+ name+" ~/config.sh"
				;
		ExecuteResult res = executeCommand(code, "");
		validate("testSource02 "+name,res,0,expect,"");
		Object v = console.getVariable("var1");
		assertEquals(1,v);
		v = console.getVariable("var2");
		assertEquals(2,v);
		v = console.getVariable("var3");
		assertEquals(3,v);
		
		
	}

	private void validate(String msg,ExecuteResult res ,int exitCode, String stdOut, String stdErr) {
		assertEquals(0, res.exitCode,msg);
		assertEquals(stdOut, new String(res.bao.toByteArray()),msg);
		assertEquals(stdErr, new String(res.bae.toByteArray()),msg);

		
	}
}
