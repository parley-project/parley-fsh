package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import us.bringardner.fsh.Console;

/**
 * The bash-compat cases (see TestBashCompat) run by the new front end (us.bringardner.fsh.syntax,
 * .expand and .exec) instead of the ANTLR one. Every case must match bash.
 */
public class TestNewFrontEnd extends AbstractConsoleTest {

	private static final Path DIR = Path.of("src/test/resources/bash-compat");
	private static final long TIMEOUT_SECONDS = 10;
	private static boolean before;

	private static final ExecutorService runner = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "new front end case");
		t.setDaemon(true);
		return t;
	});

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		Console.exitJvm = false;
		before = Console.newFrontEnd;
		Console.newFrontEnd = true;
	}

	@AfterAll
	public static void afterAll() {
		Console.newFrontEnd = before;
	}

	@TestFactory
	public Stream<DynamicTest> cases() throws IOException {
		List<Path> scripts;
		try (Stream<Path> files = Files.list(DIR.resolve("cases"))) {
			scripts = files.filter(p -> p.toString().endsWith(".sh")).sorted().toList();
		}
		return scripts.stream().map(script -> {
			String name = script.getFileName().toString().replaceAll("\\.sh$", "");
			return DynamicTest.dynamicTest(name, () -> runCase(name, script));
		});
	}

	private static void runCase(String name, Path script) throws Exception {
		String code = Files.readString(script);
		if( code.startsWith("# needs:") && getOs() == OperatingSystem.Windows ) {
			Assumptions.abort("needs "+code.substring(8, code.indexOf('\n')).trim());
		}
		String expectOut = Files.readString(DIR.resolve("cases/"+name+".out"));
		int expectStatus = Integer.parseInt(Files.readString(DIR.resolve("cases/"+name+".status")).trim());
		ExecuteResult res = run(code);
		if( res == null ) {
			fail(name+" did not finish in "+TIMEOUT_SECONDS+"s");
		}
		assertEquals(expectOut, res.getStdOut(), name+": stdout (stderr: "+res.getStdErr()+")");
		assertEquals(expectStatus, res.exitCode, name+": exit status (stderr: "+res.getStdErr()+")");
	}

	/** run code in a new console, in a new empty directory; null if it did not finish in time */
	private static ExecuteResult run(String code) throws Exception {
		File dir = Files.createTempDirectory("new-front-end").toFile();
		try {
			ExecuteResult ret = new ExecuteResult();
			Console console = new Console();
			console.setStdOut(new PrintStream(ret.getBao()));
			console.setStdErr(new PrintStream(ret.getBae()));
			console.setStdIn(new ByteArrayInputStream(new byte[0]));
			console.setCurrentDirectory(console.createFileSource(dir.getAbsolutePath()));
			Future<Integer> f = runner.submit(() -> console.executeUsingAntlr(code));
			try {
				ret.exitCode = f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				f.cancel(true);
				return null;
			} catch (java.util.concurrent.ExecutionException e) {
				new PrintStream(ret.getBae()).println("crashed: "+e.getCause());
				ret.exitCode = -1;
			}
			return ret;
		} finally {
			deleteTree(dir);
		}
	}

	private static void deleteTree(File file) {
		File [] kids = file.listFiles();
		if( kids != null ) {
			for(File kid : kids) {
				deleteTree(kid);
			}
		}
		file.delete();
	}
}
