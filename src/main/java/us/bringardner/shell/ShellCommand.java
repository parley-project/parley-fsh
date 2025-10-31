package us.bringardner.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.shell.antlr.Argument;


public abstract class ShellCommand {
	public static final String ROOT_FACTORY_ID = "factory_id";
	/*
 https://www.gnu.org/software/bash/manual/html_node/Shell-Parameter-Expansion.html
 https://sourceforge.net/projects/javacurses/
	 */
	//ASCII
	private static final String[] posixClass = {"[!","[:word:]","[:ascii:]","[:lower:]","[:upper:]","[:alpha:]","[:digit:]","[:alnum:]","[:punct:]","[:graph:]","[:print:]","[:blank:]","[:cntrl:]","[:xdigit:]","[:space:]"};
	private static final String[] javaClass  = {"[^","\\w","\\p{ASCII}","\\p{Lower}","\\p{Upper}","\\p{Alpha}","\\p{Digit}","\\p{Alnum}","\\p{Punct}","\\p{Graph}","\\p{Print}","\\p{Blank}","\\p{Cntrl}","\\p{XDigit}","\\p{Space}"};
	@SuppressWarnings("unused")
	private static final String[] description = {"Negate character","A lower-case alphabetic character: [a-z]","An upper-case alphabetic character:[A-Z]","An alphabetic character:[\\p{Lower}\\p{Upper}]"," A decimal digit: [0-9]"," An alphanumeric character:[\\p{Alpha}\\p{Digit}]","Punctuation: One of !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~","A visible character: [\\p{Alnum}\\p{Punct}]"," A printable character: [\\p{Graph}\\x20]"," A space or a tab: [ \\t]"," A control character: [\\x00-\\x1F\\x7F]","A hexadecimal digit: [0-9a-fA-F]"," A whitespace character: [ \\t\\n\\x0B\\f\\r]"};
	String name;
	String help;
	protected Argument[] args;

	public ShellCommand(String name,String help) {
		this.name = name;
		this.help = help;
	}

	public String getName() {
		return name;
	}

	public String getHelp() {
		return help;
	}

	public abstract int process(ShellContext ctx) throws IOException;

	public class ShellArgument {
		public List<Object> options = new ArrayList<>();
		public List<String> paths = new ArrayList<>();
	}


	protected ShellArgument parserArgs(ShellContext ctx,Class<?> cls) throws IOException {
		ShellArgument ret = new ShellArgument();
		if( args.length>0) {
			try {
				Method m = cls.getDeclaredMethod("valueOf", String.class);
				try {
					//  look at set command
					m = cls.getDeclaredMethod("find", String.class);					
				} catch (Throwable e) {
				}
				m.setAccessible(true);
				for(int idx=0; idx < args.length; idx++ ) {
					String arg = (""+args[idx].getValue(ctx)).trim();
					if( arg.startsWith("-")) {
						arg = arg.substring(1);
						for(char c : arg.toCharArray()) {
							try {
								Object a = m.invoke(null, ""+c);
								ret.options.add(a);
							} catch (Exception e) {
								throw new IOException("Unknown argument "+c);
							}
						}
					} else {
						ret.paths.add(arg);
					}
				}
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IOException(e);
			}
		}
		return ret;
	}




	private static void glob2a(ShellContext ctx,List<FileSource> ret,FileSource dir,String [] segments, int segmentIdx) throws IOException {
		if( segmentIdx>= segments.length) {
			FileSource[] list = dir.listFiles();
			if( list !=null && list.length>0) {
				for(FileSource f : list) {
					ret.add(f);
				}
			}
			
			return;
		}

		String segment = segments[segmentIdx];
		if( segment.isEmpty()) {
			glob2a(ctx, ret, dir, segments, segmentIdx+1);
		} else {
			segment = expandTilde(ctx,segment);		
			String cleanPath = FileSourceFactory.expandDots(segment, dir.getFileSourceFactory().getSeperatorChar());
			cleanPath = cleanPath.replaceAll("/./", "/");

			if( !hasWildcard(cleanPath)) {
				FileSource file = null;

				if( isRelative(cleanPath)) {
					file = dir.getChild(cleanPath);
				} else {
					file = ctx.console.createFileSource(cleanPath);					
				}

				if( file.isDirectory()) {
					glob2a(ctx, ret, file, segments, segmentIdx+1);
				} else {
					// if we get here, this file must be included in the result set
					ret.add(file);
				}
			} else {
				// has a wild card
				List<FileSource> files = expandWildcards(ctx, dir, cleanPath);	
				if( segmentIdx == segments.length-1) {
					ret.addAll(files);
				} else {
					for(FileSource file : files) {
						glob2a(ctx, ret, file, segments, segmentIdx+1);
					}
				}
			}
		}
	}

	private static List<FileSource>  expandWildcards(ShellContext ctx,FileSource dir,String cleanPath) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		String path = prepWildCards(cleanPath);

		Pattern p = Pattern.compile(path);

		FileSource[] kids = dir.listFiles();
		if( kids !=null ) {
			for(FileSource file : kids) {
				String name = file.getName();
				Matcher m = p.matcher(name);
				if( m.matches()) {
					ret.add(file);
				}
			}
		}

		return ret;
	}

	public static String prepWildCards(String cleanPath) {
		return prepWildCards(cleanPath, true);
	}

	public static String prepWildCards(String cleanPath,boolean greedy) {
		String ret1 = posixToJava(cleanPath);
		
		StringBuilder ret = new StringBuilder();
		char [] data = ret1.toCharArray();
		for (char c : data) {
			switch (c) {
			case '?': ret.append('.');
			if( !greedy) {
				ret.append('?');
			}
			break;
			case '.':ret.append("[.]");break;
			case '*':ret.append(".*");
			if( !greedy) {
				ret.append('?');
			}
			break;
			default:
				ret.append(c);
			}
		}
		return ret.toString();

	}

	public static String posixToJava(String cleanPath) {
		String ret = cleanPath;
		for (int idx1 = 0; idx1 < posixClass.length; idx1++) {
			String tmp = replaceAll(ret,posixClass[idx1], javaClass[idx1]);
			ret = tmp;
		}

		return ret;
	}

	/**
	 * https://www.gnu.org/software/bash/manual/html_node/Tilde-Expansion.html
	 * @param segment
	 * @return
	 * @throws IOException 
	 */
	protected static String expandTilde(ShellContext ctx,String segment) throws IOException {
		/*
		 * If a word begins with an unquoted tilde character (‘~’), all of the characters up to the 
		 * first unquoted slash (or all characters, if there is no unquoted slash) are considered a tilde-prefix. 
		 */
		String ret = segment;
		if(ret != null && !ret.isEmpty()) {
			if( segment.charAt(0) == '~') {
				String home = System.getProperty("user.home");
				String user = System.getProperty("user.name");
				if( segment.startsWith("~+")) {
					home = ctx.console.getCurrentDirectory().getAbsolutePath();
				} else if( segment.startsWith("~-")) {
					Object obj = ctx.console.variables.get(Console.VARIABLE_OLDPWD);
					if (obj instanceof FileSource) {
						home = ((FileSource)obj).getAbsolutePath();
					}
				}
				int idx = findFirstUnquotedSlash(ret);
				if( idx < 0) {
					idx = segment.length();
				}
				String prefix = ret.substring(1, idx);
				if( !prefix.isEmpty()) {
					home = home.replaceAll(user, prefix);
				}

				String right = ret.substring(idx);

				ret = home;
				if( !right.isEmpty()) {
					ret = ret+right;
				}
			}
		}
		return ret;
	}

	private static int findFirstUnquotedSlash(String value) {
		int ret = -1;
		int ch = (int)'/';
		byte[] data = value.getBytes();
		for (int idx = 0,sz = data.length-1; idx < sz; idx++) {
			if( data[idx] == ch && data[idx+1] != ch) {
				ret = idx;
				break;
			}
		}
		return ret;
	}

	private static String replaceAll(String path, String posix, String java) {
		String ret = path;
		int idx = ret.indexOf(posix);
		while( idx >=0 ) {
			String left = path.substring(0, idx);
			String right = path.substring(idx+posix.length());
			ret = left+java+right;
			idx = ret.indexOf(posix);
		}

		return ret;
	}

	public static final char wildcards[] = {'*','?','[','~'};

	public static String removeWildcards(String str) {
		StringBuilder ret = new StringBuilder();
		byte [] data = str.getBytes();
		for (int idx = 0; idx < data.length; idx++) {
			char c = (char)data[idx];
			switch (c) {
			case '*': 
			case '?':
			case '~':
				break;
			default:
				ret.append(c);
			}
		}
		return ret.toString();
	}
	
	public List<FileSource>  globOld(ShellContext ctx,String path) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		FileSource cwd = null;
		path = expandTilde(ctx, path);
		if( !hasWildcard(path)) {
			System.out.println("Here");
			FileSource tmpf = ctx.console.getMountFactory().
					createFileSource(path);
			ret.add(tmpf);
			return ret;
		}


		if( isRelative(path)) {
			cwd = ctx.console.getCurrentDirectory();
		} else {
			cwd = ctx.console.getMountFactory().listRoots()[0];
			path = path.substring(1);
		}

		if( path.equals(".")) {
			ret.add( ctx.console.getCurrentDirectory());
			return ret;
		} else if( path.equals("..")) {
			cwd = ctx.console.getCurrentDirectory();
			ret.add(cwd.getParentFile());
			return ret;
		}
		String pathSegments [] = path.split("["+cwd.getFileSourceFactory().getSeperatorChar()+"]");
		//  these are all the files that match this segment
		globDir(ctx,ret, cwd, pathSegments, 0);



		return ret;
	}

	public static List<FileSource>  getFiles(ShellContext ctx,String path) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		if( hasWildcard(path)) {
			ret = glob(ctx, path);
		} else {
			ret.add(ctx.console.createFileSource(path));
		}

		return ret;
	}

	private static List<FileSource>  glob(ShellContext ctx,String path) throws IOException {
		List<FileSource> ret = new ArrayList<>();
		FileSource cwd = null;
		if( path.startsWith("~")) {
			path = expandTilde(ctx, path);
		}
		if( isRelative(path)) {
			cwd = ctx.console.getCurrentDirectory();
		} else {						
			cwd = ctx.console.createFileSource("/");
		}

		String pathSegments [] = path.split("["+cwd.getFileSourceFactory().getSeperatorChar()+"]");
		//  these are all the files that match this segment
		glob2a(ctx,ret, cwd, pathSegments, 0);



		return ret;
	}

	public static  boolean hasWildcard(String seg) {
		for(char c : wildcards) {
			if( seg.indexOf(c)>=0) {
				return true;
			}
		}
		return false;
	};

	protected static boolean isRelative(String path) {
		return !path.startsWith("/");
	}

	protected String promptAndGetReponse(ShellContext ctx, String prompt) throws IOException {
		return promptAndGetReponse(ctx.stdout, ctx.stdin, prompt);
	}

	protected String promptAndGetReponse(PrintStream out,InputStream in, String prompt) throws IOException {

		out.print(prompt);
		String ret = readLine(in);
		return ret;
	}



	protected String readLine(InputStream in) throws IOException {
		StringBuilder ret = new StringBuilder();
		int i = in.read();
		while(i>=0 && i !='\n') {
			ret.append((char)i);
			i = in.read();
		}
		return ret.toString();
	}

	private void globDir(ShellContext ctx,List<FileSource> ret,FileSource cwd,String [] segments, int segmentIdx) throws IOException {
		//System.out.println("Enter globDir for "+cwd+" idx="+segmentIdx+" seg="+segments[segmentIdx]);
		if( cwd.isDirectory()) {
			// expand dots
			String segment = segments[segmentIdx];
			segment = expandTilde(ctx,segment);
			String path = FileSourceFactory.expandDots(segment, cwd.getFileSourceFactory().getPathSeperatorChar());

			for (int idx1 = 0; idx1 < posixClass.length; idx1++) {
				String tmp = replaceAll(path,posixClass[idx1], javaClass[idx1]);
				path = tmp;
			}

			String val1 = path.replace(".", "[.]");
			String val2 = val1.replace("*", ".*");

			Pattern p = Pattern.compile(val2);

			FileSource[] kids = cwd.listFiles();
			if( kids !=null ) {
				for(FileSource file : kids) {
					String name = file.getName();
					Matcher m = p.matcher(name);
					if( m.matches()) {
						if( segmentIdx < (segments.length-1)) {
							globDir(ctx,ret, file, segments,segmentIdx+1);
						} else {
							ret.add(file);
						}
					} else {
						if( segmentIdx < (segments.length-1)) {
							globDir(ctx,ret, file, segments,segmentIdx+1);
						}
					}
				}
			}
		}
		//System.out.println("Exit globDir for "+cwd+" idx="+segmentIdx+" seg="+segments[segmentIdx]);
	}

	public void setArgs(Argument[] args) {
		this.args = args;
		
	}

	public String toColumns(ShellContext ctx, List<String> out) {
			StringBuilder ret = new StringBuilder();
			int w = ctx.console.getTerminalWidth();
			int max = 0;
			for(String line : out) {
				max = Math.max(max, line.length());
			}
			// count for line end
			max+=2;
			int cols = w / max;
			StringBuilder tmp = new StringBuilder();
			for(int idx=0,sz=out.size(); idx < sz; idx++ ) {
				if(idx>0 && idx % cols == 0 ) {
					ret.append('\n');
				}
				tmp.setLength(0);
				tmp.append(out.get(idx));
				while(tmp.length() < max) {
					tmp.append(' ');
				}
				ret.append(tmp);
			}
			
			return ret.toString();
	}

}
