package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import us.bringardner.shell.Console;

public class TestConsoleArgAndInputParseing {

	
	@Test
	public void testReadCode() throws IOException {
		String [] linesOfCode = {
				"line01 line02\n",
				"line01\\\nline02\n",
		};
		
		String [] expect = {
				"line01 line02",
				"line01\nline02",
		};
		
		Console console = new Console();
		
		for (int idx = 0; idx < linesOfCode.length; idx++) {
			String code = linesOfCode[idx];
			InputStream stdin = new ByteArrayInputStream(code.getBytes());
			String line = console.readCode(stdin);
			//System.out.println(line);
			assertEquals(expect[idx],line);
			
		}
	}

	@Test
	public void testReadArgs() throws IOException {
		
			
		
		String args[] = Console.splitForArgs("part1 part2 part3 part4");
		assertEquals(4, args.length,"Args are wrong len");
		String expect[] = {"part1","part2","part3","part4"};
		for (int idx = 0; idx < expect.length; idx++) {
			assertEquals(expect[idx], args[idx]);
		}
		args = Console.splitForArgs("part1 \"part2 part3\" part4");
		
		assertEquals(3, args.length,"Args are wrong len");
		String expect2[] = {"part1","part2 part3","part4"};
		for (int idx = 0; idx < expect2.length; idx++) {
			assertEquals(expect2[idx], args[idx]);
		}
		
	}

}
