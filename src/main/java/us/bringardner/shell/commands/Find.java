package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;

public class Find extends ShellCommand{
	static String name = "find";
	// physical 
	static String help = "find file in a directory structure\n"
			+ "find [-P] starting-point... [expression]\n"
			+ "Tests (expresiion)\n"
			+ "A numeric argument n can be specified to tests (like -amin,-mtime and -size) as\n"
			+ "       +n     for greater than n,\n"
			+ "       -n     for less than n,\n"
			+ "       n      for exactly n.\n"
			+ "\n"
			+ "Supported tests:"
			+ "-name pattern\n"
			+ "              Base of file name (the path with the leading directories\n"
			+ "              removed) matches shell pattern pattern.\n"
			+ "-atime n\n"
			+ "              File was last accessed less than, more than or exactly n*24\n"
			+ "              hours ago.  When find figures out how many 24-hour periods\n"
			+ "              ago the file was last accessed, any fractional part is\n"
			+ "              ignored, so to match -atime +1, a file has to have been\n"
			+ "              accessed at least two days ago.\n"
			+ "-amin n\n"
			+ "              File was last accessed less than, more than or exactly n\n"
			+ "              minutes ago.\n"
			+ "-mmin n\n"
			+ "              File's data was last modified less than, more than or\n"
			+ "              exactly n minutes ago.\n"
			+ "\n"
			+ "       -mtime n\n"
			+ "              File's data was last modified less than, more than or\n"
			+ "              exactly n*24 hours ago.  See the comments for -atime to\n"
			+ "              understand how rounding affects the interpretation of file\n"
			+ "              modification times.\n"
			+ "\n"
			+ "       -name pattern\n"
			+ "              Base of file name (the path with the leading directories\n"
			+ "              removed) matches shell pattern pattern.  Because the\n"
			+ "              leading directories of the file names are removed, the\n"
			+ "              pattern should not include a slash, because `-name a/b'\n"
			+ "              will never match anything\n"
			+ "-size n[cwbkMG]\n"
			+ "              File uses less than, more than or exactly n units of space,\n"
			+ "              rounding up.  The following suffixes can be used:\n"
			+ "\n"
			+ "              `b'    for 512-byte blocks (this is the default if no\n"
			+ "                     suffix is used)\n"
			+ "\n"
			+ "              `c'    for bytes\n"
			+ "\n"
			+ "              `k'    for kibibytes (KiB, units of 1024 bytes)\n"
			+ "\n"
			+ "              `M'    for mebibytes (MiB, units of 1024 * 1024 = 1048576\n"
			+ "                     bytes)\n"
			+ "\n"
			+ "              `G'    for gibibytes (GiB, units of 1024 * 1024 * 1024 =\n"
			+ "                     1073741824 bytes)\n"
			+ "\n"
			+ "              The size is simply the st_size member of the struct stat\n"
			+ "              populated by the lstat (or stat) system call, rounded up as\n"
			+ "              shown above.  In other words, it's consistent with the\n"
			+ "              result you get for ls -l.  Bear in mind that the `%k' and\n"
			+ "              `%b' format specifiers of -printf handle sparse files\n"
			+ "              differently.  The `b' suffix always denotes 512-byte blocks\n"
			+ "              and never 1024-byte blocks, which is different to the\n"
			+ "              behaviour of -ls.\n"
			+ "\n"
			+ "              The + and - prefixes signify greater than and less than, as\n"
			+ "              usual; i.e., an exact size of n units does not match.  Bear\n"
			+ "              in mind that the size is rounded up to the next unit.\n"
			+ "              Therefore -size -1M is not equivalent to -size -1048576c.\n"
			+ "              The former only matches empty files, the latter matches\n"
			+ "              files from 0 to 1,048,575 bytes.";

	public Find() {
		super(name, help);
	}

	class FindNumber {
		char sign = '=';
		long numerator;
		long denominator;
	}
	
	private static final long DAY = 60000*60*24;
	
	class FindContext {

		public FindContext(ShellContext ctx) {
			sc = ctx;
		}
		
		StringBuilder out = new StringBuilder();
		ShellContext sc;
		boolean followLinks = false;
		Pattern name;
		FindNumber amin;
		FindNumber mmin;
		FindNumber mtime;
		FindNumber atime;
		FindNumber size;
		
		public boolean haseTests() {
			return !(name == null && mmin ==  null && mtime == null && size == null);
		}
		
		public boolean test(FileSource file) throws IOException {

			boolean ret = false;
			if(name !=null) {
				Matcher m = name.matcher(file.getName());
				ret = m.matches();
			} else if(amin!=null) {
				//-amin n File was last accessed less than, more than or exactly n minutes ago.
				long time = file.lastAccessTime();
				long now  = System.currentTimeMillis();
				long delta = now - time;
				long minutes = delta/6000;				
				ret = test(amin,minutes);				
			} else if(atime!=null) {
				long time = file.lastAccessTime();
				long now  = System.currentTimeMillis();
				long delta = now - time;
				long days = delta/DAY;				
				ret = test(atime,days);			
			} else if(mmin!=null) {
				long time = file.lastModified();
				long now  = System.currentTimeMillis();
				long delta = now - time;
				long minutes = delta/6000;				
				ret = test(mmin,minutes);
			} else if(mtime!=null) {
			} else if(size!=null) {
				
			} else {
				ret = false;
			}
			return ret;
		}

		private boolean test(FindNumber arg, long value) {
			boolean ret = false;
			long val = value / arg.denominator;
			
			if( arg.sign=='=') {
				ret = val == arg.numerator;
			} else if( arg.sign=='-') {
				ret = val < arg.numerator;
			} else {
				ret = val > arg.numerator;
			}
			
			return ret;
		}

		public void find(FileSource file) throws IOException {			
			if( test(file)) {
				print(file);
			}
			if( file.isDirectory()) {
				if(!followLinks) {
					FileSource lnk = file.getLinkedTo();
					if( lnk !=null) {
						return;
					}
				}
				FileSource[] kids = file.listFiles();
				if( kids !=null) {
					for(FileSource kid : kids) {
						find(kid);
					}
				}
			}
		}

		private void print(FileSource file) {
			//sc.stdout.println(file);
			out.append(file.getAbsolutePath());
			out.append('\n');
		}

	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		FindContext fctx = new FindContext(ctx);
		List<String> paths = new ArrayList<String>();

		for(int idx=0; idx < args.length; idx++ ) {
			String arg = (""+args[idx].getValue(ctx)).trim();

			if( arg.equals("-P")) {
				fctx.followLinks = true;
			} else if( arg.equals("-name")) {
				String val = (""+args[++idx].getValue(ctx)).trim();
				val = prepWildCards(val);
				fctx.name = Pattern.compile(val);
			} else if( arg.equals("-mmin")) {
				String val = (""+args[++idx].getValue(ctx)).trim();
				fctx.mmin = parseNumber(val);
			} else if( arg.equals("-mtime")) {
				String val = (""+args[++idx].getValue(ctx)).trim();
				fctx.mtime = parseNumber(val);
			} else if( arg.equals("-size")) {
				String val = (""+args[++idx].getValue(ctx)).trim();
				fctx.size = parseNumber(val);
			} else if( arg.startsWith("-")) {
				throw new IOException("Invalid argument = "+arg);
			} else {
				paths.add(arg);
			}
		}

		if( paths.size()==0) {
			ctx.stderr.println("usage: find [-P] path ... [expression]");
			return 1;
		}

		if( !fctx.haseTests() ) {
			fctx.name = Pattern.compile(".*");
		}


		for(String startPoint : paths) {
			List<FileSource> dirs = getFiles(ctx, startPoint);
			for(FileSource kid : dirs) {
				fctx.find(kid);				
			}
		}

		ctx.stdout.print(fctx.out.toString());
		return ret;
	}

	private FindNumber parseNumber(String val) {
		FindNumber ret = new FindNumber();
		String number = val;
		int idx=0;
		
		if( val.charAt(0)=='+' || val.charAt(0)=='-') {
			ret.sign = val.charAt(0);
			number = val.substring(1);
		}
		
		while(idx<number.length() && !Character.isDigit(number.charAt(idx))) {
			idx++;
		}
		
		if(idx > 0 ) {
			String tmp = number.substring(idx);
			number = number.substring(0, idx);
			if( !tmp.isEmpty()) {
				char type = tmp.charAt(0);
				switch (type) {
				case 'b': ret.denominator = 512;break;
				case 'c': ret.denominator = 1;break;
				case 'w': ret.denominator = 2;break;
				case 'k': ret.denominator = 1024;break;
				case 'M': ret.denominator = 1048576;break;
				case 'G': ret.denominator = 1073741824;break;
				default:
					throw new IllegalArgumentException("Unexpected value: " + type);
				}
			}
		}
		
		
		ret.numerator = Long.parseLong(number);
		
		
		
		return null;
	}



}
