package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;

/**
 * fsh started as bash is: fsh -c command [name [args]], fsh file [args], fsh -s [args] and
 * commands on standard input (a command at a time, so they read the lines after them); set's
 * options; $- and $0. (Each expected result is bash 5.3's.)
 */
public class TestInvocation {

	private record Result(int status, String out, String err) {
	}

	private static Result run(String stdin, String ... args) {
		Console.exitJvm = false;
		Console c = new Console();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		c.setStdOut(new PrintStream(out, true));
		c.setStdErr(new PrintStream(err, true));
		c.setStdIn(new ByteArrayInputStream(stdin.getBytes()));
		int status = c.execute(args);
		return new Result(status, out.toString(), err.toString());
	}

	@Test
	public void commandString() {
		Result r = run("", "-c", "echo \"a=$0 b=$1 c=$# $-\"", "nm", "x", "y");
		assertEquals("a=nm b=x c=2 hBc\n", r.out());
		assertEquals(0, r.status());
		assertEquals("fsh\n", run("", "-c", "echo \"$0\"").out());
		assertEquals(4, run("", "-c", "exit 4").status());
		Result e = run("", "-e", "-c", "false; echo no");
		assertEquals("", e.out());
		assertEquals(1, e.status());
	}

	@Test
	public void aSyntaxErrorAfterCommandsThatRan() {
		Result r = run("", "-c", "echo start\nfi\necho after", "myname");
		assertEquals("start\n", r.out());
		assertEquals("myname: -c: line 2: syntax error near unexpected token `fi'\nmyname: -c: line 2: `fi'\n", r.err());
		assertEquals(2, r.status());
	}

	@Test
	public void commandsFromStandardInput() {
		String in = "echo \"from stdin $-\"\nread x\necho \"x=$x\"\nhello\necho \"line=$LINENO\"\nif true\nthen echo multi\nfi\nexit 3\necho never\n";
		Result r = run(in);
		// read takes the line after it (echo "x=$x"); hello is then a command
		assertEquals("from stdin hBs\nline=4\nmulti\n", r.out());
		assertEquals("fsh: line 3: hello: command not found\n", r.err());
		assertEquals(3, r.status());
		assertEquals("args=a1 a2 fsh\n", run("echo \"args=$* $0\"\n", "-s", "a1", "a2").out());
		Result se = run("echo start\nfi\necho after\n");
		assertEquals("start\n", se.out());
		assertEquals(2, se.status());
	}

	@Test
	public void aScriptFile() throws Exception {
		File dir = Files.createTempDirectory("inv").toFile();
		File f = new File(dir, "s.sh");
		Files.writeString(f.toPath(), "echo \"s0=$0 1=$1 n=$#\"\necho \"flags=$-\"\n");
		Result r = run("", "-x", f.getAbsolutePath(), "q");
		assertEquals("s0="+f.getAbsolutePath()+" 1=q n=1\nflags=hxB\n", r.out(), r.err());
		Result missing = run("", new File(dir, "nosuch.sh").getAbsolutePath());
		assertEquals(127, missing.status());
		assertEquals("fsh: "+new File(dir, "nosuch.sh").getAbsolutePath()+": No such file or directory\n", missing.err());
		f.delete();
		dir.delete();
	}

	@Test
	public void badOptions() {
		Result z = run("", "-z");
		assertEquals(2, z.status());
		assertEquals(true, z.err().startsWith("fsh: -z: invalid option\nUsage:"));
		Result o = run("", "-o", "bogus", "-c", "true");
		assertEquals("fsh: line 0: fsh: bogus: invalid option name\n", o.err());
		assertEquals(2, o.status());
		Result c = run("", "-c");
		assertEquals("fsh: -c: option requires an argument\n", c.err());
		assertEquals(2, c.status());
	}
}
