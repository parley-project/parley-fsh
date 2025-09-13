package us.bringardner.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class NativeKeyboard extends InputStream implements KeyboardReader 	{
	private native int getChar();
	private native int ready();


	// https://espterm.github.io/docs/VT100%20escape%20codes.html


	private  String readLineNative(Console console) {
		if( prompt == null ) {
			prompt = "";
		}

		System.out.print(prompt);
		System.out.flush();
		Integer line = console.history.size();
		List<String> lines = new ArrayList<>();
		StringBuilder buf = new StringBuilder();
		@SuppressWarnings("unused")
		String debug = "";
		long start = System.currentTimeMillis();
		boolean escaped = false;

		if( editLineText!=null) {
			buf.append(editLineText);
			System.out.print(editLineText);
		}

		int pos = buf.length();
		int key = getChar();

		while(true) {
			
				if(key>=0 && maxBytes_N<0 && (!escaped && key == lineTerminator)) {
					StringBuilder tmp = new StringBuilder();
					for(String l : lines) {
						tmp.append(l);
					}
					tmp.append(buf);
					print(((char)key));
					return  tmp.toString();

				} 
				escaped = false;
				switch (key) {
				case -1: break;
				case 0: break;

				case PAGE_UP:break;
				case PAGE_DOWN:break;

				case UP:
				case DN:
					if( console.history.size()>0 ) {

						if( key==UP) {
							if(line>0) {
								line --;
							}
						} else {
							if( line < (console.history.size()-1)) {
								line ++;
							}
						}

						if( line >=0 && line < console.history.size()) {
							String tmp = console.history.get(line).command;
							buf = new StringBuilder(tmp);

							print(CLEAR_LINE);
							print(prompt);
							print(tmp);
							pos = tmp.length();
						}
					}
					break;

				case LF:
					if( pos >0) {
						print(CURSOR_LEFT);
						pos--;
					}
					break;
				case RT:
					if( pos < buf.length()) {
						print(CURSOR_RIGHT);
						pos++;
					}
					break;
				case HOME:print(CURSOR_HOME);
				pos = 0;
				break;
				case 127:// delete key
					int target = pos-1;
					if( target >= 0 && target <= buf.length()) {
						pos--;
						buf.deleteCharAt(target);
						print(CLEAR_LINE);
						print(prompt);
						print(buf.toString());
						for(int i = buf.length(); i > pos; i-- ) {
							print(CURSOR_LEFT);
						}
					}
					break;
				case DELETE:
					if( pos > 0 && 
							pos < buf.length()-1) {
						buf.deleteCharAt(pos);
						print(CLEAR_LINE);
						print(buf.toString());
						for(int i = buf.length(); i > pos; i-- ) {
							print(CURSOR_LEFT);
						}
					}
					break;
				case END:print(CLEAR_LINE);
				print(prompt);
				print(buf.toString());
				pos = buf.length();
				break;

				case '\\':
					// escaped char goes to buffer

					if( honorEscape) {
						escaped = true;
						print(((char)key));
						key = getChar();

						if( key == lineTerminator) {
							print(CLEAR_LINE);
							print(buf.toString());					
							lines.add(buf.toString());
							buf.setLength(0);
							pos=0;					
							print(((char)key));
							break;
						} 
					}
					// fall through
				default:	
					if( pos>=buf.length()) {
						buf.append((char)key);
						pos++;
					} else {
						buf.insert(pos++, (char)key);
					}
					print(((char)key));
					line=null;
					for(int idx=pos; idx < buf.length(); idx++) {
						print(buf.charAt(idx));
					}
					for(int idx=pos; idx < buf.length(); idx++) {
						print(CURSOR_LEFT);
					}
				}
				if(timeout>0 && (System.currentTimeMillis()-start)>=timeout ) {
					key = lineTerminator;
				} else if(maxBytes_N>0 && maxBytes_N== buf.length() || maxBytes_n>0 && buf.length()>= maxBytes_n) {
					key = lineTerminator;
				} else {

					key = getChar();
					if( key >=0) {
						debug = ""+key;
					}
				}
			}
		

	}

	private void print(char c) {
		if( echo ) {
			System.out.print(c);
		}

	}
	private void print(String obj) {
		if( echo ) {
			System.out.print(obj);
		}

	}
	private void print(char[] obj) {
		if( echo ) {
			System.out.print(obj);
		}

	}


	private static Boolean availible=false;

	public static boolean isAvailible() {
		return availible;
	}


	static {
		try {
			System.loadLibrary("nativekeyboard");
			availible = true;
		} catch (Throwable e) {
			availible = false;
		}

	}

	public static final char [] CLEAR_LINE = {27,'[','2','K',27,'[','0','G'};
	public static final char [] CURSOR_HOME = {27,'[','0','G'};
	public static final char [] CURSOR_LEFT = {27,'[','D'};
	public static final char [] CURSOR_RIGHT = {27,'[','C'};
	public static final char [] DEL_CH = {27,'[','3'};


	public static final int UP = 500;
	public static final int DN = 501;
	public static final int RT = 502;
	public static final int LF = 503;
	public static final int HOME = 504;
	public static final int PAGE_UP = 505;
	public static final int PAGE_DOWN = 506;
	public static final int DELETE = 507;
	public static final int END = 508;
	private long timeout = 0;
	private boolean echo = true;
	private boolean honorEscape = true;
	private char lineTerminator = '\n';
	private int maxBytes_N = -1;
	private int maxBytes_n = -1;
	private String editLineText ;
	private String prompt = "prompt";



	public String getPrompt() {
		return prompt;
	}
	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
	public String getEditLineText() {
		return editLineText;
	}
	public void setEditLineText(String editLineText) {
		this.editLineText = editLineText;
	}
	public int getMaxBytes_N() {
		return maxBytes_N;
	}
	public void setMaxBytes_N(int maxBytes_N) {
		this.maxBytes_N = maxBytes_N;
	}
	public int getMaxBytes_n() {
		return maxBytes_n;
	}
	public void setMaxBytes_n(int maxBytes_n) {
		this.maxBytes_n = maxBytes_n;
	}
	public boolean isHonorEscape() {
		return honorEscape;
	}
	public void setHonorEscape(boolean honorEscape) {
		this.honorEscape = honorEscape;
	}
	public boolean isEcho() {
		return echo;
	}
	public void setEcho(boolean echo) {
		this.echo = echo;
	}

	public long getTimeout() {
		return timeout;
	}
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	public char getLineTerminator() {
		return lineTerminator;
	}
	public void setLineTerminator(char lineTerminator) {
		this.lineTerminator = lineTerminator;
	}
	

	public  String readLine(Console console) throws IOException {
		if( availible && System.in == Console.System_in) {
			return readLineNative(console);
		} else {
			return readLineConsole(console);
		}		
	}



	/**
	 * Use STDIN. no editing or history available
	 * @param console 
	 * @return
	 * @throws IOException
	 */
	private String readLineConsole(Console console) throws IOException {
		if( prompt !=null) {
			System.out.print(prompt);
		}

		StringBuffer ret = new StringBuffer();
		boolean done = false;
		int i = System.in.read();
		boolean escape=false;
		boolean eof = false;
		if( !eof) {
			while(i>=0 && !done) {
				/*
				if( isEcho()) {
					print((char)i);
				}
				*/
				char c = (char)i;
				if( c =='\\') {
					// escape next char
					escape = true;
				} else {
					if(escape) {
						ret.append(c);
						escape = false;
					} else {
						if( c == lineTerminator) {
							done = true;
						} else {
							ret.append(c);
						}
					}
				}
				if( !done) {
					i = System.in.read();
				}
			}
			if( i == -1) {
				eof = true;
			}
		}
		return ret.toString();
	}

	@Override
	public int read() throws IOException {
		int ret = getChar();
		return ret;
	}	

	@Override
	public void close() throws IOException {
		//  do nothing...

	}
	@Override
	public PrintStream getStdErr() {
		return System.err;
	}
	@Override
	public PrintStream getStdOut() {
		return System.out;
	}
}
