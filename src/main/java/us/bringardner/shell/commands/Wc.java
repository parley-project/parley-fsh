package us.bringardner.shell.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Wc extends ShellCommand{
	enum Type {maxLine,ByteCount,LineCount,CharCount,WordCount}
	class Counts {
		long maxLine;
		long byteCount;
		long lineCount;
		long charCount;
		long wordCount;

		public void add(Counts counts) {
			maxLine = Math.max(maxLine, counts.maxLine);
			byteCount += counts.byteCount;
			lineCount += counts.lineCount;
			charCount += counts.charCount;
			wordCount += counts.wordCount;
		}
	}

	static String name = "wc";
	static String help = "count words or lines in the input\n"
			+ " -L      Write the length of the line containing the most bytes (default) or characters "
			+ "	(when -m is provided) to standard output.  When more than one file argument is specified, the longest\n"
			+ "             input line of all files is reported as the value of the final “total”.\n"
			+ "\n"
			+ "     -c      The number of bytes in each input file is written to the standard output.  This will cancel out any prior usage of the -m option.\n"
			+ "\n"
			+ "     -l      The number of lines in each input file is written to the standard output.\n"
			+ "\n"
			+ "     -m      The number of characters in each input file is written to the standard output.  "
			+ "This will cancel out any prior usage of the -c option.\n"
			+ "\n"
			+ "     -w      The number of words in each input file is written to the standard output.";

	public Wc() {
		super(name, help);
	}

	private static final Type[] defaultOptions = {Type.ByteCount,Type.LineCount,Type.WordCount};
	@Override
	public int process(ShellContext ctx) throws IOException {
		List<Type> options = new ArrayList<>();
		List<String> files = new ArrayList<>();

		int ret = 0;
		//byte [] data = ctx.stdin.readAllBytes();
		/*
		 *  When an option is specified, wc only reports the information requested by that option.  The order of output always takes the form of line, word, byte, and file name.  
		 *  The default action is
     equivalent to specifying the -c, -l and -w options.

     newline, word, character,  byte,  maximum
		 */
		boolean debug = false;
		for(int idx=0; idx < args.length; idx++ ) {
			String arg = ""+args[idx].getValue(ctx);
			if( arg.startsWith("-")) {
				if( arg.equals("-debug")) {
					debug = true;
				}
				else {
					if(arg.contains("L")) {
						options.add(Type.maxLine);
					} 
					
					if(arg.contains("c")) {					
						if( !debug) options.remove(Type.CharCount);
						options.add(Type.ByteCount);
					} 
					if(arg.contains("l")) {
						options.add(Type.LineCount);
					} 
					if(arg.contains("m")) {
						if( !debug) options.remove(Type.ByteCount);
						options.add(Type.CharCount);
					} 
					if(arg.contains("w")) {
						options.add(Type.WordCount);
					}
				}
			} else {
				files.add(arg);
			}			
		}

		if(options.size() ==  0 ) {
			options = Arrays.asList(defaultOptions);
		}

		// special case, no need to read the data
		boolean useFileSize = options.size()==1 && options.get(0) == Type.ByteCount;
		if( debug ) {
			printHeaders(ctx,options);
		}

		if( files.size()==0) {
			Counts counts = new Counts();
			doCount(counts,ctx,options,ctx.stdin);
			print(ctx,(String)null,options,counts);
		} else {
			Counts totals = new Counts();
			List<FileSource> processed = new ArrayList<>();
			for(String path : files) {
				List<FileSource> list = getFiles(ctx, path);
				for(FileSource file : list) {

					Counts counts = new Counts();
					if( useFileSize) {
						counts.byteCount = file.length();
					} else {
						try(InputStream in = file.getInputStream()) {
							doCount(counts,ctx, options, in);
						}
					}
					totals.add(counts);
					print(ctx,file.getName(),options,counts);
					processed.add(file);
				}
			}
			if( processed.size()>1) {
				print(ctx,"total",options,totals);
			}

		}

		return ret;
	}

	private void printHeaders(ShellContext ctx, List<Type> options) {
		for(Type t : options) {
			ctx.stdout.print(t+" ");
		}
		ctx.stdout.println();		
	}

	/*
  4   9 179 copy.sh
 11  24 467 test.sh
 15  33 646 total
	 */
	private static String format = "% 8d";

	private void print(ShellContext ctx,String file,List<Type> options, Counts cnt) {
		//newline, word, character,  byte,  maximum, file
		StringBuilder buf = new StringBuilder();
		if( options.contains(Type.LineCount)) {
			buf.append(String.format(format, cnt.lineCount));

		}
		if( options.contains(Type.WordCount)) {			
			buf.append(String.format(format, cnt.wordCount));			
		}
		if( options.contains(Type.CharCount)) {			
			buf.append(String.format(format, cnt.charCount));			
		}
		if( options.contains(Type.ByteCount)) {
			buf.append(String.format(format, cnt.byteCount));
		}
		if( options.contains(Type.maxLine)) {
			buf.append(String.format(format, cnt.maxLine));
		}
		if( file != null ) {
			buf.append(' ');
			buf.append(file);			
		}
		ctx.stdout.println(buf);
	}

	private void doCount(Counts ret,ShellContext ctx, List<Type> options, InputStream stdin) throws IOException {
		InputStream in = new BufferedInputStream(stdin);

		int lineSize = 0;
		boolean inWord = false;
		int i = in.read();

		while( i >=0 ) {
			ret.byteCount++;
			lineSize ++;

			if(i == 10 ) {
				lineSize--;
				ret.lineCount++;
				ret.maxLine = Math.max(lineSize, ret.maxLine);
				lineSize=0;
			} 
			if( Character.isDefined(i)) {

				ret.charCount++;
				if( isWhitespace(i)) {
					if( inWord ) {
						ret.wordCount++;
					}
					inWord = false;
				} else {
					inWord=true;
				}
			} else {
				// not defined ???
				throw new IOException("i="+i+" "+Integer.toBinaryString(i));
			}			
			i = in.read();
		}

		if( inWord ) {
			ret.wordCount++;
		}		
		ret.maxLine = Math.max(lineSize, ret.maxLine);

	}


	private boolean isWhitespace(int i) {
		boolean ret = false;
		switch (i) {
		case 9:
		case 10:
		case 11:
		case 13:		
		case 32:
		case 133:
		case 160:
			ret = true;
			break;

		default:
			break;
		}
		return ret;
	}

}
