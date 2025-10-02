package us.bringardner.shell.test;

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
		console = new Console();
	}

	@AfterAll
	public static void afterAll() {

	}


	@Test
	public void testTrap00() throws IOException {
		String code = "trap \"echo hello\" DEBUG\n"
				+ "echo line 1\n"
				+ "echo line 2\n"
				;
		ExecuteResult res = executeCommand(code, "");
		System.out.println("res="+res);


	}

	public void testTrap01() throws IOException {
		String code = "trap -lpP action SIGKILL KILL 9";
		code = "trap 'rm -f \"$tmpfile\"' EXIT\n"
				+ "trap 'exit 2' KILL HUP INT QUIT TERM"
				;
		ExecuteResult res = executeCommand(code, "");
		System.out.println(res);
		try (Scanner in = new Scanner(System.in)) {
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

				line = in.next();
			}
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
