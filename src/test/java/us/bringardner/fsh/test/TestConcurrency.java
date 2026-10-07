package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.FileSourceShVisitorImpl;
import us.bringardner.fsh.antlr.Statement;
import us.bringardner.fsh.antlr.signal.ExitException;

public class TestConcurrency {

	private static ShellContext newContext() {
		Console console = new Console();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.setStdOut(new PrintStream(out, true));
		console.setStdErr(new PrintStream(out, true));
		ShellContext ctx = new ShellContext(console);
		ctx.stdin = new ByteArrayInputStream(new byte[0]);
		return ctx;
	}

	/**
	 * Run a statement on its own thread (Console.exit would call System.exit off the JUnit thread).
	 */
	private static Thread start(String code, ShellContext ctx) throws Exception {
		Statement stmt = FileSourceShVisitorImpl.parse(code).get(0);
		Thread t = new Thread(() -> {
			try {
				stmt.process(ctx);
			} catch (Exception e) {
				// ExitException is expected
			}
		});
		t.setDaemon(true);
		t.start();
		return t;
	}

	@Test
	public void testStopReachesEveryPipelineStage() throws Exception {
		ShellContext ctx = newContext();
		long start = System.currentTimeMillis();
		Thread job = start("sleep 30 | sleep 30", ctx);
		Thread.sleep(500);
		ctx.setExecption(new ExitException(ctx, 1));
		job.join(10000);

		assertFalse(job.isAlive());
		assertTrue(System.currentTimeMillis()-start < 10000);
	}

	@Test
	public void testSuspendedJobCanBeStopped() throws Exception {
		ShellContext ctx = newContext();
		ctx.setPause(true);
		Thread waiting = new Thread(ctx::waitWhilePaused);
		waiting.setDaemon(true);
		waiting.start();
		Thread.sleep(200);
		assertTrue(waiting.isAlive(), "waits while paused");

		ctx.setExecption(new ExitException(ctx, 1));
		waiting.join(5000);
		assertFalse(waiting.isAlive(), "a stop ends the wait");
	}

	@Test
	public void testVariablesFromManyThreads() throws Exception {
		Console console = new Console();
		AtomicReference<Throwable> error = new AtomicReference<>();
		List<Thread> writers = new ArrayList<>();
		for (int w = 0; w < 8; w++) {
			final int id = w;
			Thread t = new Thread(() -> {
				try {
					for (int i = 0; i < 2000; i++) {
						console.setVariable("v_"+id+"_"+i, i);
					}
				} catch (Throwable e) {
					error.set(e);
				}
			});
			writers.add(t);
			t.start();
		}
		// read while they write
		while (writers.stream().anyMatch(Thread::isAlive)) {
			for (String name : console.getVariables().keySet()) {
				name.length();
			}
		}
		for (Thread t : writers) {
			t.join();
		}

		assertEquals(null, error.get());
		for (int w = 0; w < 8; w++) {
			assertEquals(1999, console.getVariable("v_"+w+"_1999"));
		}
	}
}
