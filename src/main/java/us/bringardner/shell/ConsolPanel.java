package us.bringardner.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import sun.misc.Signal;

public class ConsolPanel extends JPanel implements KeyboardReader {



	private static final long serialVersionUID = 1L;

	private class TextAreaOutputStream extends OutputStream {

		JTextPane textArea;
		private Color color;

		public TextAreaOutputStream(JTextPane text,Color color) {
			textArea = text;
			this.color = color;

		}



		@Override
		public void write(int b) throws IOException {
			try {
				SwingUtilities.invokeAndWait(()->{
					StyledDocument document = (StyledDocument) textArea.getDocument();
					try {
						document.insertString(document.getLength(), ""+((char)b), null);
						lineStart = currentPos = textArea.getCaretPosition();
					} catch (BadLocationException e) {
					}


				});
			} catch (InvocationTargetException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			final String str = new String(b,off,len);

			try {
				SwingUtilities.invokeAndWait(()->{
					appendToPane(textArea, str, color);												
				});
			} catch (InvocationTargetException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

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



	private JTextPane textArea;
	private Console console;
	private JLabel statusLabel;
	private int lineStart = 0;
	private int currentPos=0;
	private QueueInputStream in;
	private TextAreaOutputStream out;
	private String prompt = "% ";
	private AtomicReference<String> line = new AtomicReference<>();
	private AtomicBoolean inReadline = new AtomicBoolean(false);
	private Integer lineIndex;
	private TextAreaOutputStream err;
	private String editLineText;


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

		textArea = new JTextPane();
		textArea.setFont(new Font(Font.MONOSPACED, Font.ITALIC, 12));
		
		textArea.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				System.out.println("Gained focus");
			}
			@Override
			public void focusLost(FocusEvent e) {
				System.out.println("Lost focus");
			}
		});
		scrollPane.setViewportView(textArea);
		textArea.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				//System.out.println(e);
				Console.System_out.println(e);

				StringBuilder db = new StringBuilder("inReadline="+inReadline+"\n");

				if( !inReadline.get()) {
					e.consume();
					return;
				}

				int key = e.getKeyCode();
				if( key == KeyEvent.VK_C) {
					System.out.println("C");
				}
				int pos = textArea.getCaretPosition();
				db.append("cp="+currentPos+" ls="+lineStart+" pos="+pos);

				if( e.isControlDown()) {
					e.consume();
					if( key == KeyEvent.VK_R) {
						HistorySearchDialog d = new HistorySearchDialog();
						d.setLocationRelativeTo(textArea);
						String cmd = d.showDialog(console);
						if( cmd !=null && !cmd.isEmpty()) {
							String all = textArea.getText();
							if( all.length()> lineStart) {
								all = all.substring(0,lineStart-1);
							}
							all += cmd;
							textArea.setText(all);
							currentPos = all.length();
						}
					} else if( key == KeyEvent.VK_C) {
						Signal signal = new Signal("INT");
						Signal.raise(signal);
					} else if( key == KeyEvent.VK_BACK_SLASH) {
						Signal signal = new Signal("TERM");
						Signal.raise(signal);
					} else if( key == KeyEvent.VK_Z) {
						Signal signal = new Signal("TSTP");
						Signal.raise(signal);
					} else if( key == KeyEvent.VK_D) {
						System.out.println("cntr="+key);
						Signal signal = new Signal("QUIT");
						Signal.raise(signal);
					} else {
						// some control code what to do???
						System.out.println("cntr="+key);						
					}
				}


				switch (key) {
				case KeyEvent.VK_UNDEFINED:
						break;
				case KeyEvent.VK_ESCAPE:
					e.consume();
					break;
				case KeyEvent.VK_DELETE:
				case KeyEvent.VK_BACK_SPACE:
				case KeyEvent.VK_LEFT: 
					if( pos<= lineStart) {
						e.consume();
					} else {
						currentPos--;
					}
					break;
				case KeyEvent.VK_RIGHT:
					currentPos++;
					if( currentPos> textArea.getText().length()) {
						currentPos = textArea.getText().length();
					}
					break;
				case KeyEvent.VK_UP: 
				case KeyEvent.VK_DOWN: 
					e.consume(); 
					if( console.history.size()>0 ) {

						if( e.getKeyCode()==KeyEvent.VK_UP) {
							if(lineIndex>0) {
								lineIndex --;
							}
						} else {
							if( lineIndex < (console.history.size())) {
								lineIndex ++;
							}
						}

						if( lineIndex >=0 && lineIndex < console.history.size()) {
							String tmp = console.history.get(lineIndex).command;
							String all = textArea.getText();
							if( all.length()> lineStart) {
								all = all.substring(0,lineStart);
							}
							all += tmp;
							textArea.setText(all);
						} else if( lineIndex == console.history.size()) {
							String all = textArea.getText();
							if( all.length()> lineStart) {
								all = all.substring(0,lineStart);
							}							
							textArea.setText(all);
						}

					}
					break;
				case KeyEvent.VK_ENTER: 
					try {
						String last = textArea.getText(pos-1,1);
						if(last.charAt(0)!='\\') {							
							String all = textArea.getText();
							String line = all.substring(lineStart);
							ConsolPanel.this.line.set(line);
							inReadline.set(false);						
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

		in = new QueueInputStream();		
		out = new TextAreaOutputStream(textArea,null);
		err = new TextAreaOutputStream(textArea,Color.red);

	}

	public void setConsole(Console c) {
		this.console = c;
		c.registerHandler(new ShellContext(c), new Signal("INT"), "exit");

	}

	@Override
	public String readLine(Console console) throws IOException {
		if( inReadline.get()) {
			throw new IOException("Invalid read state");
		}
		setConsole(console);
		line.set("");
		inReadline.set(true);

		try {
			SwingUtilities.invokeAndWait(()->{
				// make sure we're at a line start
				int pos = textArea.getCaretPosition();
				if( pos != lineStart) {
					appendToPane(textArea,"\n",null);			
				}

				appendToPane(textArea,prompt,null);
				lineStart = textArea.getCaretPosition();
				currentPos=lineStart;
				lineIndex = console.history.size();
				if( editLineText !=null ) {
					StyledDocument document = (StyledDocument) textArea.getDocument();
					try {
						document.insertString(currentPos, editLineText, null);
					} catch (BadLocationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					textArea.setCaretPosition(currentPos+editLineText.length());
				}
				System.out.println("pos="+pos+" lisn="+lineStart+" len="+textArea.getText().length()+" idx="+lineIndex); 


			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}

		while(inReadline.get()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
		editLineText = null;

		return line.get();
	}

	private void append(JTextPane textArea2, String string) {
		if( string != null) {
			StyledDocument document = (StyledDocument) textArea2.getDocument();
			try {
				int start = document.getLength();
				int end = start+ string.length();

				document.insertString(start, string, null);
				textArea2.setCaretPosition(end);
				lineStart = currentPos = end;
				System.out.println("set pos ="+end);
			} catch (BadLocationException e) {
			}
		}


	}

	private void appendToPane(JTextPane tp, String msg, Color c){
		if( msg !=null) {
			int len = tp.getDocument().getLength();

			if( c !=null) {
				StyleContext sc = StyleContext.getDefaultStyleContext();
				AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
				aset = sc.addAttribute(aset, StyleConstants.FontFamily, "Lucida Console");
				aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);
				tp.setCaretPosition(len);
				tp.setCharacterAttributes(aset, false);
				tp.replaceSelection(msg);

			} else {
				append(tp, msg);
			}
			lineStart = currentPos = len+msg.length();
		}
	}

	@Override
	public void setPrompt(String prompt) {
		this.prompt = prompt;

	}



	@Override
	public PrintStream getStdErr() {
		return new PrintStream(err);
	}

	@Override
	public PrintStream getStdOut() {
		return new PrintStream(out);
	}

	public void clear() {
		try {
			SwingUtilities.invokeAndWait(()->{
				prompt = "";
				textArea.setText("");
				lineStart=currentPos=0;			
			});
		} catch (InvocationTargetException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



	}

	@Override
	public InputStream getStdIn() {
		return in;
	}

	@Override
	public void setEditLineText(String text) {
		this.editLineText= text;
		
	}

}
