package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionCaseStatement extends AbstractConsoleTest {

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");		
		
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
