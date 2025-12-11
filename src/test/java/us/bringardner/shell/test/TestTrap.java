package us.bringardner.shell.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Scanner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import sun.misc.Signal;
import us.bringardner.shell.Console;

public class TestTrap extends AbstractConsoleTest {


	@BeforeAll
	public static void beforeAll() throws IOException {
		waitForJobs = true;
		console = new Console();
	}

	@AfterAll
	public static void afterAll() {

	}




	@Test
	public void testTrap00() throws IOException {
		String code = "trap \"echo hello from trap debug\" DEBUG\n"
				+ "echo simple echo line 1\n"
				+ "echo simple echo line 2\n"
				;
		String expect = "hello from trap debug\n"
				+ "simple echo line 1\n"
				+ "hello from trap debug\n"
				+ "simple echo line 2\n"
				;
		
		ExecuteResult res = executeCommand(code, "");
		assertEquals(0,res.exitCode);
		String out = res.getStdOut();
		String err = res.getStdErr();
		assertEquals(expect, out);
		assertEquals("", err);
		


	}

	/**
	 * This is not a unit test.  Run it manually and enter signal names in the keyboard.
	 *  
	 * @throws IOException
	 */
	
	public void testTrap01() throws IOException {
		String code = "trap -lpP action SIGKILL KILL 9";
		code = "trap 'rm -f \"$tmpfile\"' EXIT\n"
				+ "trap 'exit 2' KILL HUP INT QUIT TERM"
				;
		ExecuteResult res = executeCommand(code, "");
		System.out.println("testTrap01 needs work res="+res);
		try (Scanner in = new Scanner(System.in)) {
			System.out.print("Enter Signal: ");
			String line = in.next();
			while(line !=null) {
				
				line = line.toUpperCase();
				if( line.startsWith("SIG")) {
					line = line.substring(3);
				}
				Signal signal = new Signal(line);
				try {
					Signal.raise(signal);	
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.out.print("Enter Signal: ");
				line = in.next();
			}
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
