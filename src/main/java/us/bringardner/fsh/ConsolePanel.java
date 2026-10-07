package us.bringardner.fsh;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * A Swing console: shows what's written to its standard output (black) and error (red),
 * and sends what's typed to the shell's line reader or, while a script runs, to its
 * standard input. The workings are in {@link ConsoleIO}; this is the Swing view of it.
 */
public class ConsolePanel extends JPanel implements KeyboardReader, ConsoleIO.View {

	private static final long serialVersionUID = 1L;

	/** Text kept; older text is removed from the top. */
	static final int MAX_LENGTH = 2_000_000;

	private final ConsoleIO io = new ConsoleIO(SwingUtilities::invokeLater, SwingUtilities::isEventDispatchThread);
	private final JTextPane textArea;
	private final SimpleAttributeSet errorStyle = new SimpleAttributeSet();
	// where the line being typed starts; text before it is output and can't be edited
	private int inputStart;

	/**
	 * Create the panel.
	 */
	public ConsolePanel() {
		setLayout(new BorderLayout(0, 0));
		StyleConstants.setForeground(errorStyle, Color.red);

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		textArea = new JTextPane();
		textArea.setFont(new Font(Font.MONOSPACED, Font.ITALIC, 12));
		scrollPane.setViewportView(textArea);
		textArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				keyPressedInText(e);
			}

			@Override
			public void keyTyped(KeyEvent e) {
				// typing goes on the input line
				if( textArea.getCaretPosition() < inputStart ) {
					textArea.setCaretPosition(textArea.getDocument().getLength());
				}
			}
		});
		io.setView(this);
	}

	private void keyPressedInText(KeyEvent e) {
		int key = e.getKeyCode();
		int pos = textArea.getCaretPosition();

		if( e.isControlDown()) {
			e.consume();
			if( key == KeyEvent.VK_R) {
				Console console = io.getConsole();
				if( console != null && io.isReadingLine()) {
					HistorySearchDialog d = new HistorySearchDialog();
					d.setLocationRelativeTo(textArea);
					String cmd = d.showDialog(console);
					if( cmd !=null && !cmd.isEmpty()) {
						setInputLine(cmd);
					}
				}
			} else if( key == KeyEvent.VK_C) {
				io.signal(ConsoleSignal.Interupt);
			} else if( key == KeyEvent.VK_BACK_SLASH) {
				io.signal(ConsoleSignal.Terminate);
			} else if( key == KeyEvent.VK_Z) {
				io.signal(ConsoleSignal.Suspend);
			} else if( key == KeyEvent.VK_D) {
				io.signal(ConsoleSignal.Quit);
			}
			return;
		}

		switch (key) {
		case KeyEvent.VK_ESCAPE:
			e.consume();
			break;
		case KeyEvent.VK_BACK_SPACE:
		case KeyEvent.VK_LEFT:
			if( pos <= inputStart) {
				e.consume();
			}
			break;
		case KeyEvent.VK_DELETE:
			if( pos < inputStart) {
				e.consume();
			}
			break;
		case KeyEvent.VK_UP:
		case KeyEvent.VK_DOWN:
			e.consume();
			if( io.isReadingLine()) {
				String cmd = key == KeyEvent.VK_UP ? io.historyUp() : io.historyDown();
				if( cmd != null ) {
					setInputLine(cmd);
				}
			}
			break;
		case KeyEvent.VK_ENTER:
			e.consume();
			String line = inputLine();
			if( line.endsWith("\\")) {
				// continued on the next line
				insert(textArea.getDocument().getLength(), "\n", null);
				break;
			}
			insert(textArea.getDocument().getLength(), "\n", null);
			inputStart = textArea.getDocument().getLength();
			io.submitLine(line);
			break;
		default:
			if( pos < inputStart && !e.isMetaDown() && !e.isAltDown() && !e.isActionKey()) {
				textArea.setCaretPosition(textArea.getDocument().getLength());
			}
		}
	}

	private String inputLine() {
		StyledDocument doc = textArea.getStyledDocument();
		try {
			return doc.getText(inputStart, doc.getLength()-inputStart);
		} catch (BadLocationException e) {
			return "";
		}
	}

	private void setInputLine(String text) {
		StyledDocument doc = textArea.getStyledDocument();
		try {
			doc.remove(inputStart, doc.getLength()-inputStart);
		} catch (BadLocationException e) {
		}
		insert(inputStart, text, null);
	}

	private void insert(int offset, String text, SimpleAttributeSet style) {
		StyledDocument doc = textArea.getStyledDocument();
		try {
			doc.insertString(offset, text, style);
		} catch (BadLocationException e) {
		}
		textArea.setCaretPosition(doc.getLength());
	}

	// ---- ConsoleIO.View

	@Override
	public void append(String text, boolean error) {
		StyledDocument doc = textArea.getStyledDocument();
		// output goes before anything being typed, which stays on the input line
		String typed = inputLine();
		try {
			doc.remove(inputStart, doc.getLength()-inputStart);
		} catch (BadLocationException e) {
		}
		insert(doc.getLength(), text, error ? errorStyle : null);
		int extra = doc.getLength() - MAX_LENGTH;
		if( extra > 0 ) {
			try {
				doc.remove(0, extra);
			} catch (BadLocationException e) {
			}
		}
		inputStart = doc.getLength();
		if( !typed.isEmpty()) {
			insert(inputStart, typed, null);
		}
	}

	@Override
	public void startLine(String prompt, String text) {
		StyledDocument doc = textArea.getStyledDocument();
		int len = doc.getLength();
		try {
			if( len > 0 && !doc.getText(len-1, 1).equals("\n")) {
				insert(len, "\n", null);
			}
		} catch (BadLocationException e) {
		}
		insert(doc.getLength(), prompt, null);
		inputStart = doc.getLength();
		insert(inputStart, text, null);
	}

	@Override
	public void clearText() {
		textArea.setText("");
		inputStart = 0;
	}

	/** Clears the console, including output not shown yet. */
	public void clear() {
		io.clear();
	}

	// ---- KeyboardReader

	public void setConsole(Console c) {
		io.setConsole(c);
	}

	@Override
	public String readLine(Console console) throws IOException {
		return io.readLine(console);
	}

	@Override
	public void setPrompt(String prompt) {
		io.setPrompt(prompt);
	}

	@Override
	public void setEditLineText(String text) {
		io.setEditLineText(text);
	}

	@Override
	public PrintStream getStdErr() {
		return io.getStdErr();
	}

	@Override
	public PrintStream getStdOut() {
		return io.getStdOut();
	}

	@Override
	public InputStream getStdIn() {
		return io.getStdIn();
	}
}
