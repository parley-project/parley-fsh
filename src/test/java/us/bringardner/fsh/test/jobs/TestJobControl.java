package us.bringardner.fsh.test.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.KeyboardReader;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobManager;
import us.bringardner.fsh.job.JobSpecs;
import us.bringardner.fsh.job.JobState;
import us.bringardner.fsh.job.ProcessSignals;

/**
 * Job control in an interactive shell, as bash does it: job numbers and the current (+) and
 * previous (-) job, the notices before a prompt, Ctrl-Z and Ctrl-C for the job in the
 * foreground, fg, bg, and exit with stopped jobs.
 */
public class TestJobControl {

	/** lines typed when the test gives them; null is the end of input */
	private static class Keyboard implements KeyboardReader {
		final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
		final PrintStream out;

		Keyboard(PrintStream out) {
			this.out = out;
		}

		@Override
		public String readLine(Console console) {
			try {
				String line = lines.poll(20, TimeUnit.SECONDS);
				return line == null || line.equals("<EOF>") ? null : line;
			} catch (InterruptedException e) {
				return null;
			}
		}

		@Override
		public void setPrompt(String prompt) {
			out.print("$ ");
		}

		@Override
		public void setEditLineText(String text) {
		}

		@Override
		public PrintStream getStdErr() {
			return out;
		}

		@Override
		public PrintStream getStdOut() {
			return out;
		}

		@Override
		public InputStream getStdIn() {
			return new ByteArrayInputStream(new byte[0]);
		}
	}

	private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	private final PrintStream out = new PrintStream(bytes, true);
	private Console console;
	private Keyboard kb;

	private void startShell() {
		Console.exitJvm = false;
		console = new Console();
		console.isInteractive = true;
		console.setStdOut(out);
		console.setStdErr(out);
		kb = new Keyboard(out);
		console.setKeyboardReader(kb);
		console.history.clear();
		Console.setNextPid(100000);
		console.start();
		try {
			waitFor(() -> prompts() >= 1);
		} catch (InterruptedException e) {
		}
	}

	/** type a line and wait until the shell is back at its prompt */
	private void type(String line) throws InterruptedException {
		int prompts = prompts();
		kb.lines.add(line);
		waitFor(() -> prompts() > prompts);
	}

	private int prompts() {
		String text = bytes.toString();
		int count = 0;
		for(int i = text.indexOf("$ "); i >= 0; i = text.indexOf("$ ", i+2)) {
			count++;
		}
		return count;
	}

	/** type a line that runs until the test stops it (Ctrl-Z, Ctrl-C) */
	private void typeAndRun(String line) throws InterruptedException {
		kb.lines.add(line);
		waitFor(() -> console.state == Console.ConsoleState.Executing);
		Thread.sleep(200);
	}

	private static void waitFor(java.util.function.BooleanSupplier done) throws InterruptedException {
		long until = System.currentTimeMillis()+10000;
		while( !done.getAsBoolean()) {
			assertTrue(System.currentTimeMillis() < until, "timed out");
			Thread.sleep(20);
		}
		Thread.sleep(50);
	}

	private void endShell() throws InterruptedException {
		kb.lines.add("<EOF>");
		kb.lines.add("<EOF>");
		console.join(10000);
	}

	@Test
	public void ctrlZStopsTheJobAndFgBgContinueIt() throws Exception {
		startShell();
		try {
			type("sleep 30 &");
			typeAndRun("sleep 20");
			// Ctrl-Z
			console.handleSignal(ConsoleSignal.Suspend);
			waitFor(() -> bytes.toString().contains("Stopped"));
			type("echo \"st=$?\"");
			type("jobs");
			type("bg");
			type("jobs %%");
			int before = prompts();
			typeAndRun("fg %2");
			// Ctrl-C
			console.handleSignal(ConsoleSignal.Interupt);
			waitFor(() -> prompts() > before);
			type("echo \"st=$?\"");
			type("jobs");
			type("kill %1");
			type("true");
			type("jobs");
			String expect = "$ [1] 100000\n"
					+ "$ [2]+  Stopped                    sleep 20\n"
					+ "$ st="+ProcessSignals.stoppedStatus()+"\n"
					+ "$ [1]-  Running                    sleep 30 &\n"
					+ "[2]+  Stopped                    sleep 20\n"
					+ "$ [2]+ sleep 20 &\n"
					+ "$ [2]+  Running                    sleep 20 &\n"
					+ "$ sleep 20\n"
					+ "$ st=130\n"
					+ "$ [1]+  Running                    sleep 30 &\n"
					+ "$ [1]+  "+String.format("%-27s", ProcessSignals.describe(15))+"sleep 30\n"
					+ "$ $ $ ";
			assertEquals(expect, bytes.toString());
		} finally {
			endShell();
		}
	}

	@Test
	public void exitWithStoppedJobs() throws Exception {
		startShell();
		typeAndRun("sleep 20");
		console.handleSignal(ConsoleSignal.Suspend);
		waitFor(() -> bytes.toString().contains("Stopped"));
		type("exit");
		type("jobs");
		IJob job = console.jobManager.getJob(1);
		kb.lines.add("exit");
		console.join(10000);
		assertTrue(!console.isAlive());
		assertEquals("$ [1]+  Stopped                    sleep 20\n"
				+ "$ exit\n"
				+ "There are stopped jobs.\n"
				+ "$ [1]+  Stopped                    sleep 20\n"
				+ "$ exit\n", bytes.toString());
		// as bash leaves, its stopped jobs get SIGHUP
		assertEquals(JobState.Termnated, job.getState());
		assertEquals(129, job.getExitCode());
	}

	@Test
	public void doneNoticesBeforeThePrompt() throws Exception {
		startShell();
		try {
			type("sleep 0.5 &");
			type("(sleep 0.5; exit 3) &");
			Thread.sleep(1200);
			type("true");
			type("true");
			assertEquals("$ [1] 100000\n"
					+ "$ [2] 100001\n"
					+ "$ [1]-  Done                       sleep 0.5\n"
					+ "[2]+  Exit 3                     ( sleep 0.5; exit 3 )\n"
					+ "$ $ ", bytes.toString());
		} finally {
			endShell();
		}
	}

	@Test
	public void jobNumbersAndSpecs() throws Exception {
		Console.exitJvm = false;
		Console c = new Console();
		c.setStdOut(out);
		c.setStdErr(out);
		c.isInteractive = true;
		Console.setNextPid(100000);
		JobManager jm = c.jobManager;
		c.executeScript("sleep 5 & sleep 6 & sleep 7 &");
		List<IJob> jobs = jm.getJobs();
		assertEquals(3, jobs.size());
		assertEquals(jobs.get(2), jm.current());
		assertEquals(jobs.get(1), jm.previous());
		assertEquals(jobs.get(0), JobSpecs.find(jm, "%1"));
		assertEquals(jobs.get(0), JobSpecs.find(jm, "100000"));
		assertEquals(jobs.get(2), JobSpecs.find(jm, "%%"));
		assertEquals(jobs.get(1), JobSpecs.find(jm, "%-"));
		assertEquals(jobs.get(2), JobSpecs.find(jm, "%?7"));
		assertNull(JobSpecs.find(jm, "%4"));
		try {
			JobSpecs.find(jm, "%sleep");
			assertTrue(false, "ambiguous");
		} catch (JobSpecs.Ambiguous e) {
			assertEquals("sleep: ambiguous job spec", e.getMessage());
		}
		// a stopped job is the current one
		jobs.get(0).stopJob("Stopped");
		assertEquals(jobs.get(0), jm.current());
		assertEquals(jobs.get(2), jm.previous());
		// a job that ends keeps its number; the next one gets one more than the highest
		c.executeScript("kill %2; wait %2");
		c.executeScript("sleep 8 &");
		assertEquals(4, jm.getJobs().get(2).getJobNumber());
		c.executeScript("kill %1 %3 %4");
		jm.clear();
		c.executeScript("sleep 9 &");
		assertEquals(1, jm.getJobs().get(0).getJobNumber());
		jm.clear();
	}
}
