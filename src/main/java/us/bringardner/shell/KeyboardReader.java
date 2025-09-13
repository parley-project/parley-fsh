package us.bringardner.shell;

import java.io.IOException;
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
	};
	
	public static KeyboardReader getKeyboadReader() {
		
		return null;
	}
	
	public  String readLine(Console console) throws IOException;	
	public void setPrompt(String prompt);

	public PrintStream getStdErr();

	public PrintStream getStdOut();
	

}
