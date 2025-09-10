package us.bringardner.shell;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import us.bringardner.shell.antlr.signal.ExitException;

public class ConsolPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private class TextAreaOutputStream extends OutputStream {

		JTextArea textArea;

		public TextAreaOutputStream(JTextArea text) {
			textArea = text;
		}

		@Override
		public void write(int b) throws IOException {
			SwingUtilities.invokeLater(()->{
				textArea.append(""+((char)b));
				lineStart = currentPos = textArea.getCaretPosition();
			});
		}

	}

	private class QueueInputStream extends InputStream  {
		private ArrayBlockingQueue<Integer> data = new ArrayBlockingQueue<>(1024, true);
		private long timeout = 500;
		private AtomicBoolean closed = new AtomicBoolean();

		@Override
		public int read() throws IOException {
			Integer ret = null;
			while(!closed.get() && ret == null ) {
				try {
					ret = data.poll(timeout, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
				}
			}

			return ret==null?-1:ret.intValue();
		}

		@Override
		public void close() throws IOException {
			closed.set(true);
			super.close();
		}

	}


	QueueInputStream consoleIn;
	TextAreaOutputStream consoleOut;

	private JTextArea textArea;
	private Console console;
	private JLabel statusLabel;
	private int lineStart = 0;
	private int currentPos=0;

	/**
	 * Create the panel.
	 */
	public ConsolPanel() {
		setLayout(new BorderLayout(0, 0));

		JPanel debugPanel = new JPanel();
		FlowLayout flowLayout_1 = (FlowLayout) debugPanel.getLayout();
		flowLayout_1.setAlignment(FlowLayout.LEFT);
		add(debugPanel, BorderLayout.SOUTH);

		JLabel debugLabel = new JLabel("Debug");
		debugPanel.add(debugLabel);

		JPanel statusPanel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) statusPanel.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		add(statusPanel, BorderLayout.NORTH);
		statusLabel = new JLabel();
		statusLabel.setText("Status");
		statusPanel.add(statusLabel);

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		textArea = new JTextArea();
		scrollPane.setViewportView(textArea);
		textArea.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				//System.out.println(e);
				Console.System_out.println(e);
				StringBuilder db = new StringBuilder();

				int pos = textArea.getCaretPosition();
				db.append("cp="+currentPos+" ls="+lineStart+" pos="+pos);

				if( pos < lineStart) {
					e.consume();
					return;
				}


				switch (e.getKeyCode()) {
				case 37: //left
					if( pos<= lineStart) {
						e.consume();
					} else {
						currentPos--;
						return;
					}
					break;
				case 38:// up
				case 40:// down 
					e.consume(); 
					break;
				case 10: pos = textArea.getCaretPosition();
				try {
					String last = textArea.getText(pos-1,1);
					if(last.charAt(0)!='\\') {							
						String all = textArea.getText();
						String cmd = all.substring(lineStart);
						new Thread(()->{
							PrintStream out = System.out;								
							System.setOut(new PrintStream( new TextAreaOutputStream(textArea)));
							int exitCode = console.executeUsingAntlr(cmd);
							System.setOut(out);
							SwingUtilities.invokeLater(()->{
								debugLabel.setText("exit code="+exitCode);
								lineStart = textArea.getText().length();
								textArea.setCaretPosition(lineStart);
							});

						}).start();
						lineStart = all.length();
						textArea.setCaretPosition(lineStart);
						db.append(" new start="+lineStart);

					} 
				} catch (BadLocationException e1) {
					e1.printStackTrace();
				}
				break;
				default:
					if(pos < lineStart) {
						textArea.setCaretPosition(currentPos);
					} else {
						currentPos=pos+1;
					}

				}
				statusLabel.setText(db.toString());
			}
		});

		QueueInputStream in = new QueueInputStream();
		System.setIn(in);
		TextAreaOutputStream out = new TextAreaOutputStream(textArea);
		System.setOut(new PrintStream(out));

	}

	public void setConsole(Console c) {
		this.console = c;
	
		c.setName("ConsolePanel");
		c.setDaemon(false);
		c.start();



	}

}
