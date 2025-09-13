package us.bringardner.shell;

import java.awt.EventQueue;
import java.io.IOException;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;

public class ConsoleFrame extends JFrame implements KeyboardReader {

	private static final long serialVersionUID = 1L;

	


	private ConsolPanel consolePanel;
	

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Console console = new Console();
					console.readHistory();

					ConsoleFrame frame = new ConsoleFrame();
					frame.setLocationRelativeTo(null);
					
					frame.setVisible(true);
					new Thread(()->{
						while(true) {
							try {
								String tmp = frame.readLine(console);
								frame.getStdOut().println("You typed ~"+tmp+"~");
							} catch (IOException e) {								
								e.printStackTrace(frame.getStdErr());
							}
						}
					}).start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public ConsoleFrame(Console console) {
		this();
		consolePanel.setConsole(console);
	}

	/**
	 * Create the frame.
	 */
	public ConsoleFrame() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 1193, 739);
		consolePanel = new ConsolPanel();
		consolePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(consolePanel);
		
		
	}

	@Override
	public String readLine(Console console) throws IOException {
		return consolePanel.readLine(console);
	}

	@Override
	public void setPrompt(String prompt) {
		consolePanel.setPrompt(prompt);
		
	}

	@Override
	public PrintStream getStdErr() {
		return consolePanel.getStdErr();
	}

	@Override
	public PrintStream getStdOut() {
		return consolePanel.getStdOut();
	}

}
