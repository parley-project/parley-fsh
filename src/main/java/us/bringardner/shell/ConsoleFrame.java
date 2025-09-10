package us.bringardner.shell;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;

public class ConsoleFrame extends JFrame {

	private static final long serialVersionUID = 1L;

	


	private ConsolPanel consolePanel;
	

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					ConsoleFrame frame = new ConsoleFrame(new Console());
					frame.setVisible(true);
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
		setBounds(100, 100, 450, 300);
		consolePanel = new ConsolPanel();
		consolePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(consolePanel);
		
		
	}

}
