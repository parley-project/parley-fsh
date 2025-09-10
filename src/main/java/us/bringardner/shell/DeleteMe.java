package us.bringardner.shell;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;

public class DeleteMe extends JPanel {

	private static final long serialVersionUID = 1L;

	public class TextAreaOutputStream extends OutputStream {

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




	private JTextArea textArea;
	private Console console;
	private JPanel debugPanel;
	private JLabel statusLabel;
	int lineStart = 0;
	int currentPos=0;


	public DeleteMe(Console console) {
		this();
		this.console = console;
	}

	
	public Console getConsole() {
		return console;
	}


	public void setConsole(Console console) {
		this.console = console;
	}


	/**
	 * Create the frame.
	 */
	public DeleteMe() {
		setLayout(new BorderLayout());
		textArea = new JTextArea();


		textArea.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				//System.out.println(e);
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
							//System.out.println("exit="+exitCode);
							lineStart = textArea.getText().length();
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
		
		debugPanel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) debugPanel.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);

		statusLabel = new JLabel("New label");
		debugPanel.add(statusLabel);

	}

}
