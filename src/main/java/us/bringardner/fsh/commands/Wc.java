package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

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
	static String help = "wc [-clmwL] [FILE ...]\n"
			+ "\tCount the lines, words and bytes in each FILE, or in standard input when there is none; with\n"
			+ "\tseveral files a total follows.\n"
			+ "\t-l   lines\n"
			+ "\t-w   words\n"
			+ "\t-c   bytes\n"
			+ "\t-m   characters\n"
			+ "\t-L   the length of the longest line\n"
			+ "\tWith no option it counts as -lwc does.";

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
		boolean optionsEnded = false;
		for(int idx=0; idx < args.length; idx++ ) {
			String arg = ""+args[idx].getValue(ctx);
			if( optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
				files.add(arg);
			} else if( arg.equals("--")) {
				optionsEnded = true;
			} else if( arg.equals("-debug")) {
				debug = true;
			} else {
				for(char c : arg.substring(1).toCharArray()) {
					switch (c) {
					case 'L': add(options, Type.maxLine); break;
					case 'l': add(options, Type.LineCount); break;
					case 'w': add(options, Type.WordCount); break;
					// -c and -m: the last one given counts
					case 'c': options.remove(Type.CharCount); add(options, Type.ByteCount); break;
					case 'm': options.remove(Type.ByteCount); add(options, Type.CharCount); break;
					default:
						throw new UnsupportedOption("Unknown argument "+c);
					}
				}
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
			int counted = 0;
			for(String path : files) {
				if( path.equals("-")) {
					// - is standard input
					Counts counts = new Counts();
					doCount(counts,ctx,options,ctx.stdin);
					totals.add(counts);
					print(ctx,"-",options,counts);
					counted++;
					continue;
				}
				List<FileSource> list = getFiles(ctx, path);
				for(FileSource file : list) {
					if( !file.exists()) {
						ctx.stderr.println(name+": "+path+": No such file or directory");
						ret = 1;
						continue;
					}
					if( file.isDirectory()) {
						ctx.stderr.println(name+": "+path+": Is a directory");
						ret = 1;
						continue;
					}
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
					counted++;
				}
			}
			if( counted>1) {
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

	private static void add(List<Type> options, Type t) {
		if( !options.contains(t)) {
			options.add(t);
		}
	}

	/**
	 * Counts the bytes, the characters (UTF-8: every byte that doesn't continue a character), the
	 * lines, the words (runs of anything but space, tab, newline, carriage return, vertical tab and
	 * form feed) and the longest line (in characters with -m, else in bytes; without the line end).
	 */
	private void doCount(Counts ret,ShellContext ctx, List<Type> options, InputStream stdin) throws IOException {
		InputStream in = new BufferedInputStream(stdin);
		boolean chars = options.contains(Type.CharCount);
		long lineSize = 0;
		boolean inWord = false;
		int i;
		while( (i = in.read()) >= 0 ) {
			ret.byteCount++;
			boolean startsChar = (i & 0xC0) != 0x80;
			if( startsChar ) {
				ret.charCount++;
			}
			if( i == '\n' ) {
				ret.lineCount++;
				ret.maxLine = Math.max(lineSize, ret.maxLine);
				lineSize = 0;
			} else if( i != '\r' && (startsChar || !chars)) {
				lineSize++;
			}
			if( i == ' ' || (i >= 9 && i <= 13)) {
				if( inWord ) {
					ret.wordCount++;
				}
				inWord = false;
			} else {
				inWord = true;
			}
		}
		if( inWord ) {
			ret.wordCount++;
		}		
		ret.maxLine = Math.max(lineSize, ret.maxLine);
	}
}
