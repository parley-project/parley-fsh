package us.bringardner.shell;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.JTextArea;

public class ConsolePanel extends JPanel {

	private static final long serialVersionUID = 1L;
	public static class TextAreaOutputString extends OutputStream {

		JTextArea textArea;
		
		@Override
		public void write(int b) throws IOException {
			textArea.append(""+((char)b));
		}
		
		@Override
		public void write(byte[] b) throws IOException {
			textArea.append(new String(b));			
		}
		
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			textArea.append(new String(b,off,len));
		}
		
	}

	public static void main(String [] args) throws IOException {
		
	}
	/**
	 * Create the panel.
	 */
	public ConsolePanel() {
		setLayout(new BorderLayout(0, 0));
		
		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane);
		
		JTextArea textArea = new JTextArea();
		scrollPane.setViewportView(textArea);
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				char b = e.getKeyChar();
				textArea.append(""+((char)b));
			}
		});
	}

}
