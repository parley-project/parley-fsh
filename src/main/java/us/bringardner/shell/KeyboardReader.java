package us.bringardner.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public interface KeyboardReader {

	public static final KeyboardReader reader=new KeyboardReader() {
		KeyboardReader localReader;
		
		@Override
		public void setPrompt(String prompt) {
			localReader.setPrompt(prompt);			
		}
		
		@Override
		public String readLine(Console console) throws IOException {
			return localReader.readLine(console);
		}


		@Override
		public PrintStream getStdErr() {
			return localReader.getStdErr();
		}

		@Override
		public PrintStream getStdOut() {
			return localReader.getStdOut();
		}

		@Override
		public InputStream getStdIn() {
			return localReader.getStdIn();
		}

		@Override
		public void setEditLineText(String text) {
			localReader.setEditLineText(text);
			
		}
	};
	
	public static KeyboardReader getKeyboadReader() {
		
		return null;
	}
	
	public  String readLine(Console console) throws IOException;	
	public void setPrompt(String prompt);
	public void setEditLineText(String text);
	public PrintStream getStdErr();

	public PrintStream getStdOut();

	public InputStream getStdIn();
	

}
