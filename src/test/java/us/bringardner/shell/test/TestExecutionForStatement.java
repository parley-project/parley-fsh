package us.bringardner.shell.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.shell.Console;


@TestMethodOrder(OrderAnnotation.class)
public class TestExecutionForStatement extends AbstractConsoleTest {

	
	
	@BeforeAll
	public static void setup() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		if(!testFilesDir.exists() ) {
			System.out.println(testFilesDir.getAbsolutePath()+" does not exists");
		}
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
		
		if( ret.bae.size()!=0) {
			//System.out.println(new String(ret.bae.toByteArray()));
		}
		return ret;
	}
	

	@Test
	public void testForStatent01_1() throws Exception{
		String cmd = 
				 "for i in 1 2 3; do\n"
				 + "	echo \"i=$i\"\n"
				 + "done"
				;

		String expect = 
				"i=1\n"
				+ "i=2\n"
				+ "i=3\n"
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testForStatent02_1() throws Exception{
		String cmd = 
				 "for i in {1..3}; do\n"
				 + "  echo \"Outer loop: $i\"\n"
				 + "  for j in {1..3}; do \n"
				 + "    if [ $j -eq 2 ]; then\n"
				 + "      continue 2\n"
				 + "    fi\n"
				 + "    echo \"    Inner loop: $j\"\n"
				 + "  done\n"
				 + "done"
				;
		
		String expect = 
				"Outer loop: 1\n"
				+ "    Inner loop: 1\n"
				+ "Outer loop: 2\n"
				+ "    Inner loop: 1\n"
				+ "Outer loop: 3\n"
				+ "    Inner loop: 1\n"
				+ ""
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode,err);
		assertEquals(expect, out);
		assertEquals("", err);
	}
	
	@Test
	public void testForStatent02_2() throws Exception{
		String cmd = 
				 "for i in {1..3}; do\n"
				 + "  echo \"Outer loop: $i\"\n"
				 + "  for j in {1..3}; do \n"
				 + "    if [ $j -eq 2 ]; then\n"
				 + "      continue\n"
				 + "    fi\n"
				 + "    echo \"    Inner loop: $j\"\n"
				 + "  done\n"
				 + "done"
				;
		
		String expect = 
				"Outer loop: 1\n"
				+ "    Inner loop: 1\n"
				+ "    Inner loop: 3\n"
				+ "Outer loop: 2\n"
				+ "    Inner loop: 1\n"
				+ "    Inner loop: 3\n"
				+ "Outer loop: 3\n"
				+ "    Inner loop: 1\n"
				+ "    Inner loop: 3\n"
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode,err);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testForStatent02_3() throws Exception{
		String cmd = 
				 "for i in {1..3}; do\n"
				 + "  echo \"Outer loop: $i\"\n"
				 + "  for j in {1..3}; do \n"
				 + "    if [ $j -eq 2 ]; then\n"
				 + "      break\n"
				 + "    fi\n"
				 + "    echo \"    Inner loop: $j\"\n"
				 + "  done\n"
				 + "done"
				;
		
		String expect = 
				"Outer loop: 1\n"
				+ "    Inner loop: 1\n"
				+ "Outer loop: 2\n"
				+ "    Inner loop: 1\n"
				+ "Outer loop: 3\n"
				+ "    Inner loop: 1\n"
				+ ""
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode,err);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testForStatent03_1() throws Exception{
		String cmd = 
				 "for (( i=1; i<=10; i++ ))\n"
				 + "do  \n"
				 + " echo \"Loop number:\" $i\n"
				 + "done"
				;
		
		String expect = 
				"Loop number: 1\n"
				+ "Loop number: 2\n"
				+ "Loop number: 3\n"
				+ "Loop number: 4\n"
				+ "Loop number: 5\n"
				+ "Loop number: 6\n"
				+ "Loop number: 7\n"
				+ "Loop number: 8\n"
				+ "Loop number: 9\n"
				+ "Loop number: 10\n"
				+ ""
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode,err);
		assertEquals(expect, out);
		assertEquals("", err);
	}
	
	@Test
	public void testForStatent03_2() throws Exception{
		String cmd = 
				 "for word in This is a list of words\n"
				 + "do\n"
				 + " echo $word\n"
				 + "done"
				;
		
		String expect = 
				"This\n"
				+ "is\n"
				+ "a\n"
				+ "list\n"
				+ "of\n"
				+ "words\n"
				+ ""
				
				;
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode,err);
		assertEquals(expect, out);
		assertEquals("", err);
	}

	@Test
	public void testForStatent03_3() throws Exception{
		String cmd = 
				 "for file in \"*.php\"\n"
				 + "do \n"
				 + " ls -lh \"$file\"\n"
				 + "done"
				;
		
		//System.out.println(cmd);
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode,err);
		String lines[] = out.split("\n");
		assertEquals(1, lines.length,err);
		assertTrue(lines[0].endsWith("AbcFile.php"));
		assertEquals("", err);
	}

	@Test
	public void testForStatent04_1() throws Exception{
		String cmd = 
				 "for file in *\n"
				 + "do \n"
				 + " echo \"$file\"\n"
				 + "done"
				;
		
		String expect [] = {"Folder01"
				, "Hotel California.txt"
				, "AbcFile.js"
				, "AbcFile.properties"
				, "AbcFile.php"
		};
		
		ExecuteResult res = executeCommand(cmd,"");
		String out = new String(res.bao.toByteArray());
		String err = new String(res.bae.toByteArray());
		assertEquals(0, res.exitCode,err);
		String lines[] = out.split("\n");
		assertEquals(5, lines.length,err);
		for(String val : expect) {
			assertTrue(out.contains(val));
		}
		
		assertEquals("", err);
	}
}
