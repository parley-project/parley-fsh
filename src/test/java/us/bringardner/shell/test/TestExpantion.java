package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static us.bringardner.shell.test.TestVisitor.parse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Statement;


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

		//System.out.println(cmd);
		List<Statement> stmts = parse(cmd);
		assertEquals(5, stmts.size());

		Console console = new Console();
		ShellContext ctx = new ShellContext(console);
		
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		
		ctx.stdout=(new PrintStream(bao));
		
		
		for(Statement stmt : stmts) {
			stmt.process(ctx);
		}
		
		String expect = "this is a double quoted string value\n"
				+ "this is a single quoted string $v\n";
		String actual = new String(bao.toByteArray());
		assertEquals(expect, actual);
		
	}

}
