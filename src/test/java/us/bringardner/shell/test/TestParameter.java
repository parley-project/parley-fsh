package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl;

public class TestParameter extends AbstractConsoleTest {

	@Test
	public void testParameter01() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("name", "tony");
		String expect = "hello tony";
		String code = "hello ${name-unset}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);

	}

	@Test
	public void testParameter02() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		//ctx.setVariable("name", "tony");
		String expect = "hello unset";
		String code = "hello ${name-unset}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);

	}
	@Test
	public void testParameter03() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("name", "tony");
		String expect = "hello tony";
		String code = "hello ${name:-unset}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);

	}

	@Test
	public void testParameter04() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		//ctx.setVariable("name", "tony");
		String expect = "hello unset";
		String code = "hello ${name:-unset}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter05() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		//ctx.setVariable("name", "tony");
		String expect = "hello tony";
		String code = "hello ${name:=tony}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);	
		String val = ""+ctx.getVariable("name");
		assertEquals("tony", val);
	}

	@Test
	public void testParameter06() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		int exitCode=0;		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();			
		
		ctx.console.setStdOut(new PrintStream(bao));
		ctx.console.setStdErr(new PrintStream(bae));
		ctx.console.setStdIn(new ByteArrayInputStream("".getBytes()));

		String code = "echo hello ${name:?bad stuff is happending}";
		exitCode= ctx.console.executeUsingAntlr(code);
		

		//ctx.setVariable("name", "tony");
		String expect = "";

		String err = new String(bae.toByteArray());
		String out = new String(bao.toByteArray());
		//System.out.println("err = "+err);
		//System.out.println("out = "+out);
		assertEquals(expect, out);
		assertEquals("bad stuff is happending\n", err);
		assertEquals(1, exitCode);
	}

	@Test
	public void testParameter07() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "7890abcdefgh";
		String code = "${string:7}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);	

	}

	@Test
	public void testParameter07_1() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		ctx.setVariable("start", "7");
		String expect = "7890abcdefgh";
		String code = "${string:$start}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);	

	}

	@Test
	public void testParameter08() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "";
		String code = "${string:7:0}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);	

	}

	
	
	@Test
	public void testParameter09() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "78";
		String code = "${string:7:2}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);	

	}
	@Test
	public void testParameter10() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "7890abcdef";
		String code = "${string:7:-2}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);	

	}

	@Test
	public void testParameter11() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "bcdefgh";
		String code = "${string: -7}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter12() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "";
		String code = "${string: -7:0}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter13() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "bc";
		String code = "${string: -7:2}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter14() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("string", "01234567890abcdefgh");
		String expect = "bcdef";
		String code = "${string: -7:-2}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter15() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("array",0, "01234567890abcdefgh");
		String expect = "bc";
		String code = "${array[0]: -7:2}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter16() throws IOException {
		ShellContext ctx = new ShellContext(new Console());
		ctx.setVariable("array",0, "01234567890abcdefgh");
		String expect = "bcdef";
		String code = "${array[0]: -7:-2}";
		String actual = FileSourceShPreProcessorVisitorImpl.processString(code, ctx);
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter17() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "tony\n";
		String code = ""
				+ "d=tony\n"
				+ "declare -A array=( \n"
				+ "\n"
				+ "[0]=\"Messi\"\n"
				+ "\n"
				+ " [Brazil]=$d\n"
				+ "\n"
				+ " [England]=\"Rooney\"\n"
				+ "\n"
				+ ")\n"
				+ "echo ${array[Brazil]}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter18() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "7 8 9 0 a b c d e f g h\n";
		String code = ""
				+ "array = (0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${array[@]:7}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter19() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "7 8\n";
		String code = ""
				+ "array = (0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${array[@]:7:2}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter20() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "b c\n";
		String code = ""
				+ "array = (0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${array[@]: -7:2}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter21() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "java.lang.RuntimeException: -2: substring expression < 0\n";
		String code = ""
				+ "array = (0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${array[@]: -7:-2}\n";
		//System.out.println(code);
		boolean tmp = showError;showError=false;
		ExecuteResult res = executeCommand(code, "");
		showError=tmp;
		String actual = new String(res.bae.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter22() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h\n";
		String code = ""
				+ "array = (0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${array[@]:0}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter23() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "0 1\n";
		String code = ""
				+ "array = (0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${array[@]:0:2}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter24() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "\n";
		String code = ""
				+ "array = (0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${array[@]: -7:0}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter25() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "var1 var2 var3\n";
		String code = ""
				+ "var1=1\n"
				+ "var2=2\n"
				+ "var3=3\n"
				+ "echo ${!var*}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter26() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18\n";
		String code = "array=(0 1 2 3 4 5 6 7 8 9 0 a b c d e f g h)\n"
				+ "echo ${!array[*]}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter27() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "0\n";
		String code = ""
				+ "echo ${!array[*]}\n";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter28() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "22\n";
		String code = "array=(0 1 2 3 4 5 6 7 8 9 0 a b b b b c d e f g h)\n"				
				+ "echo ${#array[@]}\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter29() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "5\n";
		String code = "var=\"hello\"\n"				
				+ "echo ${#var}\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter30() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "resolv.conf\n";
		String code = "var=\"/etc/resolv.conf\"\n"
				+ "echo ${var#/etc/}\n"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter31() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "/dns.measurement-factory.com/tools/dnstop/src/dnstop-20090128.tar.gz\n"
				+ "dnstop-20090128.tar.gz\n";
		String code = "_version=\"20090128\"\n"
				+ "_url=\"http://dns.measurement-factory.com/tools/dnstop/src/dnstop-${_version}.tar.gz\"\n"
				+ "echo \"${_url#*/}\"\n"
				+ "echo \"${_url##*/}\"\n"

				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter32() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "_one two\n";
		String code = "array=(tony_one tonytwo)\n"				
				+ "echo ${array[@]#tony}\n"
				;

		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter33() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "xcache\n";
		String code = "FILE=xcache.tar.gz\n"
				+ "echo ${FILE%.tar.gz}"
				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter34() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "hello:world:of\n"
				+ "hello\n";
		String code = "a='hello:world:of:tomorrow'\n"
				+ "echo \"${a%:*}\"\n"
				+ "echo \"${a%%:*}\"\n"

				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter35() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "hello_world_of\n"
				+ "hello\n";
		String code = "a='hello_world_of_tomorrow'\n"
				+ "echo \"${a%_*}\"\n"
				+ "echo \"${a%%_*}\"\n"

				+ "";
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter36() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "hello_world_of\n"
				+ "hello\n";
		String code = "array=(hello_world_of_tomorrow)\n"				
				+ "echo ${array[@]%_*}\n"
				+ "echo ${array[@]%%_*}\n"
				;

		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter37() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "linux is great. Use unix or die or kill unix\n"
				+ "linux is great. Use linux or die or kill linux\n"
				+ "linux is great. Use unix or die or kill unix\n"
				+ "unix is great. Use unix or die or kill linux\n"
				+ "linux\n"
				+ "linux is great. Use unix or die or kill unix\n"
				+ "linux is great. Use linux or die or kill linux\n"

				;
		String code = "x=\"unix is great. Use unix or die or kill unix\"\n"
				+ "echo ${x/unix/linux}\n"
				+ "echo ${x//unix/linux}\n"
				+ "echo ${x/#unix/linux}\n"
				+ "echo ${x/%unix/linux}\n"
				+ "echo ${x/un*x/linux}\n"
				+ "echo ${x/un?x/linux}\n"
				+ "echo ${x//un[a-z]x/linux}\n"
				;

		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter38() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "Tony\n"
				+ "TONY\n"
				+ "Hi, tony\n"
				+ "Hi, Tony\n"
				+ "Hi, TONY\n"
				+ "path: every good boy does fine\n"
				+ "tONY\n"

				;
		String code = "name=\"tony\"\n"
				+ "echo \"${name^}\" \n"
				+ "# Turn to uppercase\n"
				+ "echo \"${name^^}\" \n"
				+ "echo \"Hi, $name\"\n"
				+ "echo \"Hi, ${name^}\"\n"
				+ "echo \"Hi, ${name^^}\"\n"
				+ "# Convert everything to lowercase \n"
				+ "saying=\"EveRy Good boy DOes fine\"\n"
				+ "echo \"path: ${saying,,}\"\n"
				+ "# Convert only first character to lowercase \n"
				+ "name=\"TONY\"\n"
				+ "echo \"${name,}\"\n"
				+ ""
				;

		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);		
	}

	@Test
	public void testParameter39() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expect = "bad things will happen\n"
				+ "value\n"
				+ "value\n"
				;
		String code = "echo ${var:-bad things will happen}\n"
				+ "var=value\n"
				+"echo ${var:-bad things will happen}\n"
				+"echo ${var-bad things will happen}\n"
				;

		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actual = new String(res.bao.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actual);	
		assertEquals(0, res.exitCode);
		
		
	}
	
	@Test
	public void testParameter40() throws IOException {
		AbstractConsoleTest.console = new Console();

		String expectErr = "bad things will happen\n";
		String expectOut = "";
		String code = "echo ${var:?bad things will happen}\n"
				;

		boolean tmp = showError;showError=false;
		ExecuteResult res = executeCommand(code, "");
		showError=tmp;
		String actualOut = new String(res.bao.toByteArray());
		String actualErr = new String(res.bae.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expectOut, actualOut);
		assertEquals(expectErr, actualErr);
		assertEquals(1, res.exitCode);
		
		
	}
	
	@Test
	public void testParameter41() throws IOException {
		AbstractConsoleTest.console = new Console();

		String code = "string=\"01234567890abcdefgh\"\n"
				+ "start=7\n"
				+ "echo ${string:$start}";
		String expect = "7890abcdefgh\n";
		
		//System.out.println(code);
		ExecuteResult res = executeCommand(code, "");
		String actualOut = new String(res.bao.toByteArray());
		//String actualErr = new String(res.bae.toByteArray());
		//System.out.println("actual = "+actual);
		assertEquals(expect, actualOut);
		assertEquals(0, res.exitCode);
		
		
	}

}
