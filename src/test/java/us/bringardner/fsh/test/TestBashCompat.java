package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import us.bringardner.fsh.Console;

/**
 * Bash compatibility: each case in src/test/resources/bash-compat/cases runs through BjlShell, and its
 * standard output and exit status must be what bash gave (NAME.out and NAME.status, written by
 * record.sh). Standard error is not compared: bash's messages start with the script name and line.
 * <p>
 * Cases that do not pass yet are listed in known-failures.txt and are skipped, so the build stays green
 * while the score shows how close BjlShell is. A listed case that passes fails the test, so the list
 * stays honest: remove it from the list. "NAME hang" marks a case that does not finish; it is not run.
 */
public class TestBashCompat extends AbstractConsoleTest {

	private static final Path DIR = Path.of("src/test/resources/bash-compat");
	private static final long TIMEOUT_SECONDS = 10;

	/** name -> reason ("" or "hang") */
	private static final Map<String,String> knownFailures = new TreeMap<>();
	private static final AtomicInteger passed = new AtomicInteger();
	private static final AtomicInteger failed = new AtomicInteger();
	private static final AtomicInteger skipped = new AtomicInteger();
	private static final List<String> results = new ArrayList<>();

	private static final ExecutorService runner = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "bash-compat case");
		t.setDaemon(true);
		return t;
	});

	@BeforeAll
	public static void beforeAll() throws IOException {
		AbstractConsoleTest.setup("TestFiles");
		// the cases run on their own threads, where the junit check in Console.exit does not see junit
		Console.exitJvm = false;
		for(String line : Files.readAllLines(DIR.resolve("known-failures.txt"))) {
			line = line.trim();
			if( line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String [] parts = line.split("\\s+", 2);
			knownFailures.put(parts[0], parts.length > 1 ? parts[1] : "");
		}
	}

	@AfterAll
	public static void afterAll() throws IOException {
		int total = passed.get()+failed.get();
		System.out.println("bash compatibility: "+passed.get()+" of "+total+" cases match bash"
				+(skipped.get() > 0 ? " ("+skipped.get()+" not run)" : ""));
		// one line per case, to update known-failures.txt from
		Files.createDirectories(Path.of("target"));
		synchronized (results) {
			results.sort(Comparator.naturalOrder());
			Files.write(Path.of("target/bash-compat-results.txt"), results);
		}
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
		String expectOut = Files.readString(DIR.resolve("cases/"+name+".out"));
		int expectStatus = Integer.parseInt(Files.readString(DIR.resolve("cases/"+name+".status")).trim());
		String known = knownFailures.get(name);

		if( "hang".equals(known)) {
			skipped.incrementAndGet();
			record(name, "hang");
			Assumptions.abort("known to hang");
		}
		if( code.startsWith("# needs:") && getOs() == OperatingSystem.Windows ) {
			skipped.incrementAndGet();
			record(name, "skipped");
			Assumptions.abort("needs "+code.substring(8, code.indexOf('\n')).trim());
		}

		ExecuteResult res = run(code);
		boolean ok = res != null && expectOut.equals(res.getStdOut()) && expectStatus == res.exitCode;
		if( ok ) {
			passed.incrementAndGet();
		} else {
			failed.incrementAndGet();
		}
		record(name, ok ? "pass" : res == null ? "timeout" : "fail");

		if( known != null ) {
			if( ok ) {
				fail(name+" matches bash now: remove it from known-failures.txt");
			}
			Assumptions.abort("known failure");
		}
		if( res == null ) {
			fail(name+" did not finish in "+TIMEOUT_SECONDS+"s");
		}
		assertEquals(expectOut, res.getStdOut(), name+": stdout (stderr: "+res.getStdErr()+")");
		assertEquals(expectStatus, res.exitCode, name+": exit status (stderr: "+res.getStdErr()+")");
	}

	private static void record(String name, String result) {
		synchronized (results) {
			results.add(name+" "+result);
		}
	}

	/**
	 * Run code in a new console, in a new empty directory.
	 * @return the result, or null if it did not finish in time
	 */
	private static ExecuteResult run(String code) throws Exception {
		File dir = Files.createTempDirectory("bash-compat").toFile();
		try {
			ExecuteResult ret = new ExecuteResult();
			Console console = new Console();
			console.setStdOut(new PrintStream(ret.getBao()));
			console.setStdErr(new PrintStream(ret.getBae()));
			console.setStdIn(new ByteArrayInputStream(new byte[0]));
			console.setCurrentDirectory(console.createFileSource(dir.getAbsolutePath()));
			// (the cases were recorded from bash running them as script files: FUNCNAME ends in main)
			console.scriptFile = "";

			Future<Integer> f = runner.submit(() -> console.executeScript(code));
			try {
				ret.exitCode = f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				f.cancel(true);
				return null;
			} catch (java.util.concurrent.ExecutionException e) {
				// the shell crashed (a StackOverflowError ...): the case fails, with the reason on stderr
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
