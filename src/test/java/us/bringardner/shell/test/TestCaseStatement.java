package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


@TestMethodOrder(OrderAnnotation.class)
public class TestCaseStatement extends AbstractConsoleTest {

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
		String out = res.getStdOut();
		String err = res.getStdErr();
		
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
		String out = res.getStdOut();
		String err = res.getStdErr();
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
		String out = res.getStdOut();
		String err = res.getStdErr();
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
		String out = res.getStdOut();
		String err = res.getStdErr();
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
		String out = res.getStdOut();
		String err = res.getStdErr();
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
		String out = res.getStdOut();
		String err = res.getStdErr();
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
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals("", err);
		assertEquals(expect, out);
		assertEquals(0, res.exitCode);		
		
	}

	private static void expectOut(String code, String out) throws IOException {
		ExecuteResult res = executeCommand(code, "");
		assertEquals("", res.getStdErr(), code);
		assertEquals(out, res.getStdOut(), code);
		assertEquals(0, res.exitCode, code);
	}

	@Test
	public void testCaseForms() throws IOException {
		// one line, | alternatives, (pattern), quoted patterns, several commands, no ;; on the last clause
		expectOut("case abc in a*) echo match;; *) echo no;; esac", "match\n");
		expectOut("x=b; case $x in a) echo A;; b|c) echo BC;; esac", "BC\n");
		expectOut("case zz in (a) echo A;; (*) echo other;; esac", "other\n");
		expectOut("case \"x y\" in \"x y\") echo quoted;; esac", "quoted\n");
		expectOut("case abc in a*) echo m1; echo m2;; esac", "m1\nm2\n");
		expectOut("case q in a) echo A;; esac; echo after", "after\n");
		expectOut("case abc in\n  a*)\n    echo multi\n    ;;\nesac", "multi\n");
		expectOut("case b in a) echo A;; b) echo B\nesac", "B\n");
		expectOut("case c3 in [ab]*) echo ab;; [!ab]?) echo other;; esac", "other\n");
	}
}
