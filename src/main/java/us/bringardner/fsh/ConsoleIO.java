package us.bringardner.fsh;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import sun.misc.Signal;

/**
 * The workings of a GUI console, for any UI toolkit: standard output and error shown in
 * the UI, standard input fed by what the user types, and the shell's line editing
 * (prompt, history). The UI implements {@link View} to show things, and calls
 * {@link #submitLine(String)}, {@link #historyUp()} and so on as the user types.
 * <p>
 * Output is decoded as UTF-8 and handed to the view in batches on the UI's thread, so a
 * script that prints a lot doesn't wait on the UI for every write. A script that prints
 * faster than the UI can show waits once a large amount is pending.
 */
public class ConsoleIO implements KeyboardReader {

	/** What the UI shows. Every method is called on the UI's thread. */
	public interface View {
		/** Adds output at the end. */
		void append(String text, boolean error);

		/** Shows prompt at the start of a new line, then lets the user edit a line starting with text. */
		void startLine(String prompt, String text);

		/** Removes everything shown. */
		void clearText();
	}

	/** Output waiting for the UI beyond which a writer waits (unless it's on the UI's thread). */
	static final int MAX_PENDING = 1 << 20;

	private record Segment(StringBuilder text, boolean error) {}

	private final Executor uiThread;
	private final BooleanSupplier onUiThread;
	private View view;

	private final Object lock = new Object();
	private final List<Segment> pending = new ArrayList<>();
	private int pendingChars;
	private boolean flushScheduled;

	private final TypedInput in = new TypedInput();
	private final PrintStream out = new PrintStream(new ViewOutputStream(false), true, StandardCharsets.UTF_8);
	private final PrintStream err = new PrintStream(new ViewOutputStream(true), true, StandardCharsets.UTF_8);

	private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
	private volatile boolean inReadLine;
	private volatile String prompt = "% ";
	private volatile String editLineText;
	private volatile Console console;
	private int historyIndex;

	/**
	 * @param uiThread runs a task on the UI's thread (SwingUtilities::invokeLater, Platform::runLater)
	 * @param onUiThread true on the UI's thread (SwingUtilities::isEventDispatchThread, Platform::isFxApplicationThread)
	 */
	public ConsoleIO(Executor uiThread, BooleanSupplier onUiThread) {
		this.uiThread = uiThread;
		this.onUiThread = onUiThread;
	}

	public void setView(View view) {
		this.view = view;
	}

	/** The console the line editing serves (for its history and signals), or null before the first readLine. */
	public Console getConsole() {
		return console;
	}

	public void setConsole(Console c) {
		if( c != null && c != console ) {
			console = c;
			c.registerHandler(new ShellContext(c), new Signal("INT"), "exit");
		}
	}

	// ---- output

	private class ViewOutputStream extends OutputStream {
		private final boolean error;
		private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);
		// bytes of a character split across writes
		private ByteBuffer leftover = ByteBuffer.allocate(0);

		ViewOutputStream(boolean error) {
			this.error = error;
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[] {(byte) b}, 0, 1);
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) throws IOException {
			ByteBuffer bytes;
			if( leftover.hasRemaining()) {
				bytes = ByteBuffer.allocate(leftover.remaining()+len);
				bytes.put(leftover).put(b, off, len).flip();
			} else {
				bytes = ByteBuffer.wrap(b, off, len);
			}
			CharBuffer chars = CharBuffer.allocate(len+leftover.remaining()+1);
			decoder.decode(bytes, chars, false);
			leftover = ByteBuffer.allocate(bytes.remaining()).put(bytes).flip();
			chars.flip();
			if( chars.hasRemaining()) {
				output(chars.toString(), error);
			}
		}
	}

	private void output(String text, boolean error) throws InterruptedIOException {
		synchronized (lock) {
			// let the UI catch up, unless this is the UI (it would wait for itself)
			while( pendingChars > MAX_PENDING && !onUiThread.getAsBoolean()) {
				try {
					lock.wait(100);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new InterruptedIOException();
				}
			}
			Segment last = pending.isEmpty() ? null : pending.get(pending.size()-1);
			if( last != null && last.error() == error ) {
				last.text().append(text);
			} else {
				pending.add(new Segment(new StringBuilder(text), error));
			}
			pendingChars += text.length();
			if( flushScheduled ) {
				return;
			}
			flushScheduled = true;
		}
		uiThread.execute(this::flush);
	}

	/** Hands pending output to the view. Called on the UI's thread. */
	private void flush() {
		List<Segment> segments;
		synchronized (lock) {
			segments = new ArrayList<>(pending);
			pending.clear();
			pendingChars = 0;
			flushScheduled = false;
			lock.notifyAll();
		}
		View v = view;
		if( v != null ) {
			for(Segment s : segments) {
				v.append(s.text().toString(), s.error());
			}
		}
	}

	/** Drops pending output and clears the view. */
	public void clear() {
		synchronized (lock) {
			pending.clear();
			pendingChars = 0;
			lock.notifyAll();
		}
		uiThread.execute(()->{
			View v = view;
			if( v != null ) {
				v.clearText();
			}
		});
	}

	@Override
	public PrintStream getStdOut() {
		return out;
	}

	@Override
	public PrintStream getStdErr() {
		return err;
	}

	// ---- input

	/** Standard input: what the user types while the shell isn't reading a command line. */
	private class TypedInput extends InputStream implements InteractiveInput {
		private final BlockingQueue<Byte> data = new LinkedBlockingQueue<>();

		@Override
		public int read() throws IOException {
			Byte b = null;
			while( b == null ) {
				try {
					b = data.poll(100, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					// a stopped script waiting for input
					Thread.currentThread().interrupt();
					throw new InterruptedIOException();
				}
			}
			return b & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if( len == 0 ) {
				return 0;
			}
			b[off] = (byte) read();
			int cnt = 1;
			Byte next;
			while( cnt < len && (next = data.poll()) != null ) {
				b[off+cnt++] = next;
			}
			return cnt;
		}

		@Override
		public int available() throws IOException {
			return data.size();
		}

		void type(String text) {
			for(byte b : text.getBytes(StandardCharsets.UTF_8)) {
				data.add(b);
			}
		}
	}

	@Override
	public InputStream getStdIn() {
		return in;
	}

	/**
	 * The user entered line (without its newline). It answers the shell's readLine if the
	 * shell is waiting for one; otherwise it goes to standard input, for a script's read.
	 */
	public void submitLine(String line) {
		if( inReadLine ) {
			inReadLine = false;
			lines.add(line);
		} else {
			in.type(line+"\n");
		}
	}

	/** Sends a signal (Ctrl+C and so on) to the console, if there is one. */
	public void signal(ConsoleSignal signal) {
		Console c = console;
		if( c != null ) {
			c.handleSignal(signal);
		}
	}

	// ---- line editing

	@Override
	public String readLine(Console console) throws IOException {
		if( inReadLine ) {
			throw new IOException("Invalid read state");
		}
		setConsole(console);
		String text = editLineText == null ? "" : editLineText;
		editLineText = null;
		String p = prompt;
		inReadLine = true;
		uiThread.execute(()->{
			synchronized (lock) {
				historyIndex = console.history.size();
			}
			flush();
			View v = view;
			if( v != null ) {
				v.startLine(p, text);
			}
		});
		try {
			return lines.take();
		} catch (InterruptedException e) {
			inReadLine = false;
			Thread.currentThread().interrupt();
			throw new InterruptedIOException();
		}
	}

	/** True while the shell waits for a command line. */
	public boolean isReadingLine() {
		return inReadLine;
	}

	/** The previous history entry, for the line being edited; null if there's none. */
	public String historyUp() {
		Console c = console;
		if( c == null || c.history.isEmpty()) {
			return null;
		}
		synchronized (lock) {
			if( historyIndex > 0 ) {
				historyIndex--;
			}
			historyIndex = Math.min(historyIndex, c.history.size()-1);
			return c.history.get(historyIndex).command;
		}
	}

	/** The next history entry; "" past the newest; null if there's no history. */
	public String historyDown() {
		Console c = console;
		if( c == null || c.history.isEmpty()) {
			return null;
		}
		synchronized (lock) {
			if( historyIndex < c.history.size()) {
				historyIndex++;
			}
			return historyIndex < c.history.size() ? c.history.get(historyIndex).command : "";
		}
	}

	@Override
	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	@Override
	public void setEditLineText(String text) {
		this.editLineText = text;
	}
}
