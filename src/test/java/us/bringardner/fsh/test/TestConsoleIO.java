package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ConsoleIO;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.job.ForgroundJob;

/** The UI-neutral console: output to a view, typed input, line editing. No UI toolkit. */
public class TestConsoleIO {

	/** Tasks for "the UI thread", run when the test says so. */
	private final ConcurrentLinkedQueue<Runnable> uiTasks = new ConcurrentLinkedQueue<>();
	private volatile boolean onUi;
	private final ConsoleIO io = new ConsoleIO(uiTasks::add, ()->onUi);
	private final RecordingView view = new RecordingView();

	{
		io.setView(view);
	}

	static class RecordingView implements ConsoleIO.View {
		final List<String> calls = new ArrayList<>();

		@Override
		public void append(String text, boolean error) {
			calls.add((error ? "err:" : "out:")+text);
		}

		@Override
		public void startLine(String prompt, String text) {
			calls.add("line:"+prompt+"|"+text);
		}

		@Override
		public void clearText() {
			calls.add("clear");
		}
	}

	private int runUi() {
		int cnt = 0;
		onUi = true;
		Runnable r;
		while((r = uiTasks.poll()) != null) {
			r.run();
			cnt++;
		}
		onUi = false;
		return cnt;
	}

	@Test
	public void outputInOrderInOneUpdate() {
		io.getStdOut().print("a");
		io.getStdErr().print("b");
		io.getStdOut().print("c");
		io.getStdOut().print("d");
		assertEquals(1, uiTasks.size());
		runUi();
		assertEquals(List.of("out:a", "err:b", "out:cd"), view.calls);
	}

	@Test
	public void charactersSplitAcrossWrites() {
		PrintStream out = io.getStdOut();
		for(byte b : "é → 日本".getBytes(StandardCharsets.UTF_8)) {
			out.write(b);
		}
		runUi();
		assertEquals(List.of("out:é → 日本"), view.calls);
	}

	@Test
	public void readLineWaitsForTheUser() throws Exception {
		io.setPrompt("$ ");
		io.setEditLineText("ls");
		CompletableFuture<String> line = CompletableFuture.supplyAsync(()->{
			try {
				return io.readLine(new Console());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		while(!io.isReadingLine()) {
			Thread.sleep(5);
		}
		runUi();
		assertEquals(List.of("line:$ |ls"), view.calls);
		assertFalse(line.isDone());
		io.submitLine("ls -l");
		assertEquals("ls -l", line.get(5, TimeUnit.SECONDS));
		assertFalse(io.isReadingLine());
	}

	@Test
	public void typedLinesGoToStandardInput() throws Exception {
		io.submitLine("abc");
		io.submitLine("é");
		InputStream in = io.getStdIn();
		assertEquals(7, in.available());
		byte[] buf = new byte[20];
		int cnt = in.read(buf);
		assertEquals("abc\né\n", new String(buf, 0, cnt, StandardCharsets.UTF_8));
	}

	@Test
	public void aStoppedReadEnds() throws Exception {
		CompletableFuture<Throwable> result = new CompletableFuture<>();
		Thread t = new Thread(()->{
			try {
				io.getStdIn().read();
				result.complete(null);
			} catch (Throwable e) {
				result.complete(e);
			}
		});
		t.start();
		Thread.sleep(200);
		t.interrupt();
		assertInstanceOf(InterruptedIOException.class, result.get(5, TimeUnit.SECONDS));
	}

	@Test
	public void history() throws Exception {
		Console console = new Console();
		console.history.clear();
		console.addHistory("one");
		console.addHistory("two");
		CompletableFuture.runAsync(()->{
			try {
				io.readLine(console);
			} catch (Exception e) {
			}
		});
		while(!io.isReadingLine()) {
			Thread.sleep(5);
		}
		runUi();
		assertEquals("two", io.historyUp());
		assertEquals("one", io.historyUp());
		assertEquals("one", io.historyUp());
		assertEquals("two", io.historyDown());
		assertEquals("", io.historyDown());
		io.submitLine("");
	}

	@Test
	public void clearDropsOutputNotShownYet() {
		io.getStdOut().print("old");
		io.clear();
		runUi();
		assertEquals(List.of("clear"), view.calls);
	}

	@Test
	public void aFastWriterWaitsForTheUi() throws Exception {
		char[] chunk = new char[64*1024];
		java.util.Arrays.fill(chunk, 'x');
		String text = new String(chunk);
		CompletableFuture<Void> writer = CompletableFuture.runAsync(()->{
			for(int i=0; i < 40; i++) {
				io.getStdOut().print(text);
			}
		});
		Thread.sleep(300);
		// about 1MB is waiting for the UI, so the writer is held up
		assertFalse(writer.isDone());
		while(!writer.isDone()) {
			runUi();
			Thread.sleep(1);
		}
		runUi();
		int total = view.calls.stream().mapToInt(c->c.length()-"out:".length()).sum();
		assertEquals(40*text.length(), total);
	}

	@Test
	public void theUiThreadNeverWaits() {
		char[] chunk = new char[3*1024*1024];
		java.util.Arrays.fill(chunk, 'x');
		onUi = true;
		io.getStdOut().print(new String(chunk));
		onUi = false;
		assertEquals(1, uiTasks.size());
	}

	@Test
	public void aScriptReadsWhatsTyped() throws Exception {
		Console.exitJvm = false;
		Console console = new Console();
		console.setStdIn(io.getStdIn());
		console.setStdOut(io.getStdOut());
		console.setStdErr(io.getStdErr());
		FshList args = new FshList();
		args.add(new Argument("test"));
		console.setPositionalParameters(true, args);
		ForgroundJob job = new ForgroundJob(new ShellContext(console), "#!fsh\nread x\necho \"got $x\"\n");
		job.start();
		Thread.sleep(200);
		assertTrue(job.isAlive());
		io.submitLine("hello there");
		job.join(10000);
		assertFalse(job.isAlive());
		runUi();
		assertEquals(List.of("out:got hello there\n"), view.calls);
	}
}
