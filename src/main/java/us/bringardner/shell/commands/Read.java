package us.bringardner.shell.commands;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.shell.Console;
import us.bringardner.shell.NativeKeyboard;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;

public class Read extends ShellCommand{
	enum Options {e,r,s,a,d,i,n,N,p,t,u};

	static String name = "read";
	static String help = " read [-ers] [-a aname] [-d delim] [-i text] [-n nchars] [-N  nchars] [-p prompt] "
			+ "[-t timeout] [-u fd] [name ...]\n"
			+ "	One line is read from the standard input, or from the file descriptor fd supplied as an argument to the -u option,\n"
			+ "	split into words as described under Word Splitting, and the first word is assigned to the first name,\n"
			+ " the	second word to the second name, and so on.  If there are\n"
			+ "	more words than names, the remaining words and their\n"
			+ "	intervening delimiters are assigned to the last name.  If\n"
			+ "	there are fewer words read from the input stream than\n"
			+ "	names, the remaining names are assigned empty values.  \n"
			+ "The characters in IFS are used to split the line into words using the same rules the shell uses for expansion\n"
			+ "	(described under Word Splitting).  The backslash\n"
			+ "	character (\\) may be used to remove any special meaning for\n"
			+ "	the next character read and for line continuation."
			+ "\n"
			+ " If no names are supplied, the line read, without the ending\n"
			+ "              delimiter but otherwise unmodified, is assigned to the\n"
			+ "              variable REPLY.  The exit status is zero, unless end-of-\n"
			+ "              file is encountered, read times out (in which case the\n"
			+ "              status is greater than 128), a variable assignment error\n"
			+ "              (such as assigning to a readonly variable) occurs, or an\n"
			+ "              invalid file descriptor is supplied as the argument to -u."
			+ "\tsee 'man read' for more information\";"
			;

	public Read() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		List<Options> options = new ArrayList<>();
		String editLineText="";
		String prompt= "";

		char lineDelim = '\n';

		String arrayName = null;
		List<String> names = new ArrayList<>();
		int n = -1;
		int N = -1;
		int timeout = -1;
		
		/*
		 * e & i are for command line editing
		 */

		for(int idx1=0; idx1 < args.length; idx1++) {
			Argument arg = args[idx1];
			String tmp = ""+arg.getValue(ctx);
			if( tmp.startsWith("-")) {
				for(int idx2=1;idx2< tmp.length(); idx2++ ) {
					char c = tmp.charAt(idx2);
					switch (c) {
					case 'a':arrayName = ""+args[++idx1].getValue(ctx); break;
					case 'd':lineDelim = (""+args[++idx1].getValue(ctx)).charAt(0); break;
					case 'e':options.add(Options.e);break;
					case 'i':editLineText = ""+args[++idx1].getValue(ctx); break;
					case 'n':n = Integer.parseInt(""+args[++idx1].getValue(ctx));break;
					case 'N':N = Integer.parseInt(""+args[++idx1].getValue(ctx));break;
					case 'p':prompt = ""+args[++idx1].getValue(ctx); break;
					case 'r':options.add(Options.r);break;
					case 's':options.add(Options.s);break;
					case 't':timeout = Integer.parseInt(""+args[++idx1].getValue(ctx));break;
					case 'u':int fd = Integer.parseInt(""+args[++idx1].getValue(ctx));
					// TODO: Set input from fd
						if( fd !=0) {
							throw new IOException("fd other than stdin is not implemented. fd="+fd);
						}
					break;
					default:
						throw new IllegalArgumentException("Unexpected value: " + c);
					}

				}
			} else {
				names.add(""+tmp);
			}
		}
		

		
		String line = readLine(ctx,prompt,lineDelim,timeout,editLineText,n, N, options);
		if(arrayName == null &&  names.size()==0) {
			ctx.setVariable("REPLY", line);
		} else { 
			StringBuilder buf = new StringBuilder();

			char[] chars = (""+ctx.getVariable(Console.IFS)).toCharArray();
			buf.setLength(0);
			for(char c : chars) {
				if( buf.length()>0) {
					buf.append('|');
				}
				buf.append(c);
			}

			String [] words = line.split(buf.toString());
			if( arrayName !=null ) {
				for(int idx=0; idx < words.length; idx++ ) {
					ctx.setVariable(arrayName, idx, words[idx]);
				}
			} else {
				List<String> values = new ArrayList<>();
				for(int idx=0; idx < words.length; idx++ ) {
					if(names.size()> idx) {
						values.add(words[idx]);
					} else {
						String val = values.getLast()+" "+words[idx];
						values.set(values.size()-1, val);
					}
				}
				for(int idx=0; idx < names.size(); idx++ ) {
					if( values.size()>idx) {
						ctx.setVariable(names.get(idx), values.get(idx));
					} else {
						ctx.setVariable(names.get(idx), "");
					}
				}
			}
		}
		
		return ret;
	}

	public String readLine(ShellContext ctx,String prompt) throws IOException {
		List<Options> options = new ArrayList<>();
		String editLineText="";
		char lineDelim = '\n';

		int n = -1;
		int N = -1;
		int timeout = -1;
		
		return readLine(ctx, prompt, lineDelim, timeout, editLineText, n, N, options);
	}
	
	public String readLine(ShellContext ctx,String prompt, char lineDelim, int timeout, String editLineText,int n,int N, List<Options> options) throws IOException {
		if(NativeKeyboard.isAvailible() && ctx.stdin == Console.System_in || ctx.stdin instanceof NativeKeyboard) {
			return readLineNative(ctx,prompt, lineDelim, timeout, editLineText, n, N, options);
		} else {
			return readLineConsole(ctx,prompt, lineDelim, timeout, editLineText, n, N, options);
		}
	}
	
	@SuppressWarnings("resource")
	private String readLineNative(ShellContext ctx,String prompt, char lineDelim, int timeout, String editLineText, int n, int N,List<Options> options) throws IOException {
		NativeKeyboard kb = new NativeKeyboard();
		kb.setLineTerminator(lineDelim);
		kb.setEcho(!options.contains(Options.s));
		kb.setEditLineText(editLineText);
		kb.setHonorEscape(!options.contains(Options.r));
		kb.setMaxBytes_n(n);
		kb.setMaxBytes_N(N);
		kb.setTimeout(timeout);
		kb.setPrompt(prompt);
		
		return kb.readLine(ctx.console);
	}

	private String readLineConsole(ShellContext ctx,String prompt, char lineDelim, int timeout, String editLineText,int n,int N, List<Options> options) throws IOException {
		if(prompt!=null && !prompt.isEmpty()) {
			ctx.stdout.print(prompt);
		}
		
		StringBuilder buf = new StringBuilder();

		int i = 0;
		while((i=ctx.stdin.read())!=-1) {
			if(i == '\\' && !options.contains(Options.r)) {
				buf.append((char)ctx.stdin.read());
			} else if(N<0 && i == lineDelim ) {
				break;
			} else  {
				buf.append((char)i);
			}
			if(N>0 && N== buf.length() || n>0 && buf.length()>= n) {
				break;
			}
		}
		if( i<0 && buf.length()==0) {
			throw new EOFException();
		}
		
		String line = buf.toString();
		return line;
	}

}
