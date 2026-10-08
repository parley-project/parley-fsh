package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.exec.Executor;


@TestMethodOrder(OrderAnnotation.class)
public class TestExpantion {

	

	@Test
	public void testStringLiterals()throws Exception{
		String cmd = 
				"v=value\n"
				+ "s1=\"this is a double quoted string $v\"\n"
				+ "s2='this is a single quoted string $v'\n"
				+ "echo $s1 \n"
				+ "echo $s2 \n"
						;
		
		//conf # :rri491aak452
		//desk 740-922-0774
		//System.out.println(cmd);
		Console console = new Console();
		ShellContext ctx = new ShellContext(console);
		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		
		ctx.stdout=(new PrintStream(bao));
		
		
		Executor.run(ctx, cmd);
		
		String expect = "this is a double quoted string value\n"
				+ "this is a single quoted string $v\n";
		String actual = new String(bao.toByteArray()).replaceAll("\r", "");
		assertEquals(expect, actual);
		
	}

}
