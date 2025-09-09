package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import us.bringardner.shell.Console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionCaseStatement {

	public static class ExecuteResult {
		int exitCode=0;		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();			
	}
	
	public static ExecuteResult executeCommand(String command,String stdIn) {
		PrintStream stdout= System.out;
		PrintStream stderr= System.err;
		InputStream stdin = System.in;
		
		ExecuteResult ret = new ExecuteResult();
		System.setOut(new PrintStream(ret.bao));
		System.setErr(new PrintStream(ret.bae));
		System.setIn(new ByteArrayInputStream(stdIn.getBytes()));
		Console console = new Console();
		ret.exitCode=console.executeUsingAntlr(command);
		System.setIn(stdin);
		System.setOut(stdout);
		System.setErr(stderr);
		
		return ret;
	}

	@Test
	public void testCaseStatent01() throws Exception{
		String cmd = "ANIMAL=dog\n"
				+ "echo -n \"The $ANIMAL has \"\n"
				+ "case $ANIMAL in\n"
				+ "  horse | dog | cat) echo -n \"four\";;\n"
				+ "  man | kangaroo ) echo -n \"two\";;\n"
				+ "  *) echo -n \"an unknown number of\";;\n"
				+ "esac\n"
				+ "echo \" legs.\""
				;

		String expect = 
				"The dog has four legs.\n"
				;
		
		//System.out.println(cmd);
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);
		
		
	}

	@Test
	public void testCaseStatent02() throws Exception{
		String cmd = "ANIMAL=man\n"
				+ "echo -n \"The $ANIMAL has \"\n"
				+ "case $ANIMAL in\n"
				+ "  horse | dog | cat) echo -n \"four\";;\n"
				+ "  man | kangaroo ) echo -n \"two\";;\n"
				+ "  *) echo -n \"an unknown number of\";;\n"
				+ "esac\n"
				+ "echo \" legs.\""
				;

		String expect = 
				"The man has two legs.\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);		
		
	}
	
	@Test
	public void testCaseStatent03() throws Exception{
		String cmd = "ANIMAL=snake\n"
				+ "echo -n \"The $ANIMAL has \"\n"
				+ "case $ANIMAL in\n"
				+ "  horse | dog | cat) echo -n \"four\";;\n"
				+ "  man | kangaroo ) echo -n \"two\";;\n"
				+ "  *) echo -n \"an unknown number of\";;\n"
				+ "esac\n"
				+ "echo \" legs.\""
				;

		String expect = 
				"The snake has an unknown number of legs.\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);		
		
	}

	@Test
	public void testCaseStatent04() throws Exception{
		String cmd = "ANIMAL=dog\n"
				+ "echo -n \"The $ANIMAL has \"\n"
				+ "case $ANIMAL in\n"
				+ "  horse | dog | cat) echo -n \"four\";&\n"
				+ "  man | kangaroo ) echo -n \"two\";;\n"
				+ "  *) echo -n \"an unknown number of\";;\n"
				+ "esac\n"
				+ "echo \" legs.\""
				;

		String expect = 
				"The dog has fourtwo legs.\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);		
		
	}

	@Test
	public void testCaseStatent05() throws Exception{
		String cmd = "ANIMAL=dog\n"
				+ "echo -n \"The $ANIMAL has \"\n"
				+ "case $ANIMAL in\n"
				+ "  horse | dog | cat) echo -n \"four\";;&\n"
				+ "  man | kangaroo ) echo -n \"two\";;\n"
				+ "  *) echo -n \"an unknown number of\";;\n"
				+ "esac\n"
				+ "echo \" legs.\""
				;

		String expect = 
				"The dog has fouran unknown number of legs.\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);		
		
	}
	
	@Test
	public void testCaseStatent06() throws Exception{
		String cmd = "ANIMAL=dog\n"
				+ "echo -n \"The $ANIMAL has \"\n"
				+ "case $ANIMAL in\n"
				+ "  horse | dog | cat) echo -n \"four\";;&\n"
				+ "  dog | kangaroo ) echo -n \"two\";;\n"
				+ "  *) echo -n \"an unknown number of\";;\n"
				+ "esac\n"
				+ "echo \" legs.\""
				;

		String expect = 
				"The dog has fourtwo legs.\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);		
		
	}
	
	@Test
	public void testCaseStatent07() throws Exception{
		String cmd = "ANIMAL=dog\n"
				+ "echo -n \"The $ANIMAL has \"\n"
				+ "case $ANIMAL in\n"
				+ "  horse | d?g | cat) echo -n \"four\";;\n"
				+ "  man | kangaroo ) echo -n \"two\";;\n"
				+ "  *) echo -n \"an unknown number of\";;\n"
				+ "esac\n"
				+ "echo \" legs.\""
				;

		String expect = 
				"The dog has four legs.\n"
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);		
		
	}
}
