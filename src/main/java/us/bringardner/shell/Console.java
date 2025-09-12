package us.bringardner.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import us.bringardner.core.BaseThread;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.shell.antlr.FileSourceShVisitorImpl;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.ExitException;
import us.bringardner.shell.commands.Alias;
import us.bringardner.shell.commands.Cd;
import us.bringardner.shell.commands.Connect;
import us.bringardner.shell.commands.Cp;
import us.bringardner.shell.commands.DirStack;
import us.bringardner.shell.commands.Echo;
import us.bringardner.shell.commands.Eval;
import us.bringardner.shell.commands.Exit;
import us.bringardner.shell.commands.Export;
import us.bringardner.shell.commands.Help;
import us.bringardner.shell.commands.History;
import us.bringardner.shell.commands.Jobs;
import us.bringardner.shell.commands.Ln;
import us.bringardner.shell.commands.Ls;
import us.bringardner.shell.commands.Pwd;
import us.bringardner.shell.commands.Read;
import us.bringardner.shell.commands.Return;
import us.bringardner.shell.commands.Rm;
import us.bringardner.shell.commands.Set;
import us.bringardner.shell.commands.Shift;
import us.bringardner.shell.commands.Sleep;
import us.bringardner.shell.commands.Touch;
import us.bringardner.shell.commands.Unalias;
import us.bringardner.shell.commands.Unmount;
import us.bringardner.shell.commands.Wc;
import us.bringardner.shell.ide.ConsoleOutputFrame;

public class Console extends BaseThread {

// windows ?C4;ahNc&W?Tfk9KUBIX9oUrp4tGCB9r

	public enum Option {
		Unsupported("")
		, Option("o")
		, MarkAllForExport("a")
		, ReprtJobStausImediately("b")
		, ExitImediately("e")
		, DisableFilenameExpansion("f")
		, RistrictectShell ("r")
		, ExitAfterOne ("t")
		, NullParameterIsError ("u")
		, PrintLinesAsRead ("v")
		, PrintCommandTrace ("x")
		, DoBraceExpantion ("B")
		, NoClobberRedirect ("C")
		, DontFollowLinks ("P")
		, KeyboardEcho ("kbecho")
		;
		public final String label;


		private Option(String label) {
			this.label = label;
		}

		public static Option find(String name) {
			for(Option o : values()) {
				if( o.label.equals(name)) {
					return o;
				}
			}
			return Unsupported;
		}
	}


	private static String defaultPath = "/usr/bin:/bin:/usr/sbin:/sbin";
	public static final String PATH = "PATH";
	public static final String VARIABLE_OLDPWD = "OLDPWD";
	public static final String VARIABLE_PWD = "PWD";
	public static final String VARIABLE_TERMINAL_WIDTH = "TERMINAL_WIDTH";
	public static final String IFS = "IFS";
	public static final String VARIABLE_PS4 = "PS4";
	public static final String VARIABLE_HISTSIZE = "HISTSIZE";
	public static final String VARIABLE_HISTFILE = "HISTFILE";
	public static final String VARIABLE_HISTTIMEFORMAT = "HISTTIMEFORMAT";
	private static final String VARIABLE_HISTCHARS = "histchars";
	private static String bashOps [] = {"-eq", "-ne", "-lt", "-le", "-gt", "-ge"};
	private static String fsshOps [] = {"==" , "!=" , "<"  , "<=" , ">"  , ">="};

	public static Map<String,ShellCommand> commands;

	static {
		commands = new TreeMap<>();
		commands.put("dirs", new DirStack());
		commands.put("popd", new DirStack());
		commands.put("pushd", new DirStack());
		registerCommand(new Cd());
		registerCommand(new Eval());
		registerCommand(new History());
		registerCommand(new Shift());
		registerCommand(new Unalias());
		registerCommand(new Alias());
		registerCommand(new Read());
		registerCommand(new Exit());
		registerCommand(new Jobs());
		registerCommand(new Sleep());
		registerCommand(new Set());
		registerCommand(new Return());
		registerCommand(new Export());
		registerCommand(new Ls());
		registerCommand(new Wc());
		registerCommand(new Pwd());
		registerCommand(new Connect());
		registerCommand(new Unmount());
		registerCommand(new Help());
		registerCommand(new Wc());
		registerCommand(new Cp());
		registerCommand(new Rm());
		registerCommand(new Ln());
		registerCommand(new Touch());
		registerCommand(new Echo());
	}

	private static int nextPid = 0;

	public static synchronized int nextPid() {
		return nextPid++;
	}

	public static synchronized void setNextPid(int pid) {
		nextPid = pid;
	}
	public enum JobState {Running,Stopped,Termnated};

	public static class HistoryEntry {
		public long time;
		public boolean  saved;
		public String command;
		
		public HistoryEntry(long time, boolean saved,String command) {
			this.time = time;
			this.saved = saved;
			this.command = command;
		}
		
		public HistoryEntry(String command) {
			this.command = command;
			time = System.currentTimeMillis();
		}

		
		
	}

	public static class CommandThread extends BaseThread {
		public int pid = nextPid();
		public int exitCode;
		public long start;
		public long end;
		public Throwable error;
		public Statement cmd;
		public ShellContext ctx;

		public CommandThread(ShellContext ctx,Statement cmd) {
			this.ctx = ctx;
			this.cmd = cmd;
		}

		public void pause(boolean b) {
			ctx.setPause(b);
		}

		public boolean isPaused() {
			return ctx.isPaused();
		}

		public JobState getState() {
			JobState ret = isPaused()?JobState.Stopped:isRunning()?JobState.Running:JobState.Termnated;

			return ret;
		}

		public String toString() {
			ParserRuleContext p = cmd.getContext();
			StringBuilder ret = new StringBuilder();
			for(ParseTree pp : p.children) {
				ret.append(pp.getText()+" ");
			}

			return ret.toString()+" &";
		} 

		@Override
		public void run() {
			@SuppressWarnings("unused")
			int db = pid;
			started = running = true;
			start = System.currentTimeMillis();
			try {
				ctx.enterCommand();
				exitCode = cmd.process(ctx);
				ctx.exitCommand();
			} catch (Throwable e) {
				error = e;
			}
			ctx.stdout.flush();
			close(ctx.stdout);
			end = System.currentTimeMillis();
			running = false;
		}

	}



	public static List<CommandThread> jobs = new ArrayList<>();

	String prompt = "%s > ";
	boolean eof = false;
	Map<String,Object> alias = new TreeMap<>();
	public Map<String,FileSourceFactory> factories = new TreeMap<>();
	private MountFactory mountFactory;
	public boolean forceHeadless=true;
	public boolean isInteractive=true;
	Map<String,Object> variables = new TreeMap<>();
	List<Object> positionalParameters = new ArrayList<>();
	public List<Option> options = new ArrayList<>();
	private Map<String,Object> environmentVariables = new TreeMap<>();
	DebugContext debugContext = new DebugContext();

	public static void main(String args[]) throws IOException {

		try {

			Console c = new Console();
			int ret = c.execute(args);

			if(ret==0 && c.isInteractive) {
				/*
				EventQueue.invokeLater(new Runnable() {
					public void run() {
						try {
							Console.debugFrame.setVisible(true);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				 */
				c.setName("Console");
				c.setDaemon(false);
				c.start();
			} else {
				System.exit(ret);
			}
		} catch (ExitException e) {
			System.exit(e.exitCode);
		}
		 
	}

	public static void close(OutputStream out) {
		if( out != System_out && out != System_err) {
			try {
				out.close();
			} catch (IOException e) {
			}
		}		
	}

	public Console(ShellContext ctx) {
		this();
		factories = ctx.console.factories;
		mountFactory = ctx.console.mountFactory;
		positionalParameters.clear();
		if( ctx.args !=null) {
			for(String a : ctx.args) {
				positionalParameters.add(a);
			}
		}
		environmentVariables.clear();
		environmentVariables.putAll(ctx.console.environmentVariables);	

	}

	/**
	 * Only used for testing...
	 * @param args
	 */
	public Console(String ... args) {
		this();
		if( args !=null) {
			for (String a : args) {
				positionalParameters.add(a);
			}

		}
	}


	
	public InputStream getStdIn() {
		return stdIn;
	}

	public void setStdIn(InputStream stdIn) {
		this.stdIn = stdIn;
	}

	public PrintStream getStdOut() {
		return stdOut;
	}

	public void setStdOut(PrintStream stdOut) {
		this.stdOut = stdOut;
	}

	public PrintStream getStdErr() {
		return stdErr;
	}

	public void setStdErr(PrintStream stdErr) {
		this.stdErr = stdErr;
	}

	public DebugContext getDebugContext() {
		return debugContext;
	}

	public void setDebugContext(DebugContext debugContext) {
		this.debugContext = debugContext;
	}

	public int execute(String ... args) {
		
		//  called only from main and TestExecutionExternal
		int ret = 0;
		StringBuilder code = new StringBuilder();

		// Convert arguments to code
		int idx = 0;
		Integer idx2=null;
		for (; idx < args.length; idx++) {
			String a = args[idx];
			code.append(a);
			code.append(' ');
			if( idx2==null && (a.equals("-")||a.equals("--")|| !(a.startsWith("-")||a.startsWith("+")))) {
				idx2 = idx;
			}
		}

		if(!code.isEmpty()) {
			// set runtime options and the positional parameters
			code.insert(0, "set -main ");
			String tmp= code.toString().trim();
			ret = executeUsingAntlr(tmp);
			code.setLength(0);
		}

		
		if( ret == 0 && idx2!=null) {
			isInteractive = false;
			//  execute the code...  this is NOT an interactive invocation
			for (; idx2 < args.length; idx2++) {

				String a = args[idx2];
				code.append(a);
				code.append(' ');
			}
			ret = executeUsingAntlr(code.toString().trim());
		}

		return ret;
	}

	public void setPositionalParameters(boolean isMain, List<Object> args) {
		Object zero = positionalParameters.get(0);
		positionalParameters.clear();
		if(!isMain) {
			positionalParameters.add(zero);
		}

		for (Object o : args) {
			positionalParameters.add(o);				
		}
	}

	FileSource homeDir ;

	public Console() {
		try {
			environmentVariables.putAll(System.getenv());
			environmentVariables.put(PATH, getDefaultPath());
			environmentVariables.put(VARIABLE_HISTSIZE, 500);
			environmentVariables.put(VARIABLE_HISTFILE, "~/.fssh_history");


			mountFactory = new MountFactory();
			factories.put(mountFactory.getTypeId(), mountFactory);
			String home = System.getProperty("user.home");
			//  the java environment HOME does not match the java property user.home
			environmentVariables.put("HOME", home);
			homeDir = mountFactory.createFileSource(home);
			if( homeDir.exists()) {
				mountFactory.setCurrentDirectory(homeDir);
			}
			variables.put(VARIABLE_PWD, mountFactory.getCurrentDirectory().getAbsolutePath());
			variables.put(VARIABLE_OLDPWD, mountFactory.getCurrentDirectory().getAbsolutePath());
			variables.put(IFS, " \t\n");
			variables.put(VARIABLE_PS4, "+ ");
			variables.put(VARIABLE_HISTCHARS, "!^#");
			positionalParameters.add("fssh");
			options.add(Option.DoBraceExpantion);
		} catch (IOException e) {
		}
	}

	private static void registerCommand(ShellCommand cmd) {
		commands.put(cmd.getName(), cmd);		
	}


	public boolean mount(FileSourceFactory f, String mountPoint) throws IOException {
		if(mountPoint == null || mountPoint.isEmpty() || mountPoint.charAt(0) != '/'){
			throw new IOException("Invalid mountpoint");
		}
		String name = mountPoint.substring(1);
		return mountFactory.mount(f, name);
	}

	public MountFactory getMountFactory() {
		return mountFactory;
	}

	public FsshList getPositionalParameters() {
		FsshList ret = new FsshList();
		for(int idx=1,sz=positionalParameters.size(); idx < sz; idx++ ) {
			ret.add(positionalParameters.get(idx));
		}

		return ret;
	}

	public int getTerminalWidth() {
		Integer ret = (Integer) getVariable(VARIABLE_TERMINAL_WIDTH);
		if( ret == null ) {
			ret = 150;
		}
		return ret;
	}

	public void setTerminalWidth(int w) {
		variables.put(VARIABLE_TERMINAL_WIDTH, w);
	}

	public boolean isForceHeadless() {
		return forceHeadless;
	}


	public void setForceHeadless(boolean forceHeadless) {
		this.forceHeadless = forceHeadless;
	}


	public List<HistoryEntry> history = new ArrayList<>();
	private int lastExitCode;

	@Override
	public void run() {
		int exitCode = 0;
		stdOut = System.out;
		stdErr = System.err;
		stdIn = System.in;
		
		readHistory();

		try {
			started = running = true;
			while(running && !stopping) {
				try {
					String prompt2 = String.format(prompt,mountFactory.getCurrentDirectory().getAbsolutePath());
					@SuppressWarnings("resource")
					NativeKeyboard kb = new NativeKeyboard();
					kb.setPrompt(prompt2);
					String code = kb.readLine(this).trim();
					if( !code.isEmpty()) {
						addHistory(code) ;

						exitCode = executeUsingAntlr(code);
					}

				} catch (Throwable e) {
					showError(e);
				}
			}
			running = false;
		} finally {
			saveHistory();
		}

		System.exit(exitCode);
	}



	public void addHistory(String code) {
		history.add(new HistoryEntry(code));
		truncateHistory();
	}

	private void truncateHistory() {

		int max = 500;
		try {
			max =Integer.parseInt(""+environmentVariables.get(VARIABLE_HISTSIZE));
		} catch (Exception e) {
		}
		while(history.size()>max) {
			history.remove(0);
		}		
	}

	/**
	 * Read history... any errors are ignored.
	 */
	public void readHistory() {
		String fileName = ""+environmentVariables.get(VARIABLE_HISTFILE);
		readHistory(fileName);
	}

	/**
	 * Read history... any errors are ignored.
	 */
	public void readHistory(String fileName) {
		try {
			FileSource file = createFileSource(fileName);
			if( file.exists()) {
				char hist_comment = '#';
				Object xxx = getVariable(VARIABLE_HISTCHARS);
				if( xxx !=null) {
					String tmp = xxx.toString();
					if( tmp.length()>2) {
						hist_comment = tmp.charAt(2);
					}
				}
				
				try(InputStream in = file.getInputStream()) {
					long loadTime = System.currentTimeMillis();
					List<HistoryEntry> tmp = new ArrayList<>();
					String [] lines = new String(in.readAllBytes()).split("\n");
					for (int lineNum = 0; lineNum < lines.length; lineNum++) {
						String line = lines[lineNum].trim();						
						if( !line.isEmpty()) {
							long time = loadTime;
							if( line.length()>2 && line.charAt(0) == hist_comment && Character.isDigit(line.charAt(1))) {
								time = Long.parseLong(line.substring(1));
								line = lines[++lineNum].trim();
							} 
							if( line.charAt(0)!=hist_comment) {
								tmp.add(new HistoryEntry(time, true, line));
							}
						}
					}
					history = tmp;
				}
			}
			truncateHistory();
		} catch (IOException e) {
			logError("save history", e);
		}
	}

	/**
	 * Save history ... any errors are ignored
	 */
	public void saveHistory() {
		String fileName = ""+environmentVariables.get(VARIABLE_HISTFILE);
		saveHistory(fileName);
	}

	/**
	 * Save history ... any errors are ignored
	 */
	public void saveHistory(String fileName) {
		try {
			truncateHistory();
			FileSource file = createFileSource(fileName);

			try(OutputStream out = file.getOutputStream(true)) {
				char hist_comment = '#';
				Object xxx = getVariable(VARIABLE_HISTCHARS);
				if( xxx !=null) {
					String tmp = xxx.toString();
					if( tmp.length()>2) {
						hist_comment = tmp.charAt(2);
					}
				}
				
				for(HistoryEntry e : history) {
					out.write((""+hist_comment+e.time+"\n").getBytes());
					out.write((e.command+"\n").getBytes());
				}
			}
			
		} catch (IOException e) {
			logError("save history", e);
		}

	}


	/*
[n]<<[-]word
        here-document
delimiter
	 */
	private static final Pattern hereRx = Pattern.compile("(?<id>[123])?\\s?<<\\s?(?<dash>[-])?\\s?(?<word>([']?[a-zA-Z_][a-zA-Z_0-9]*[']?\\s?[\n]))");
	private static final Pattern expandBrace = Pattern.compile("(?<pre>[a-zA-Z_0-9\\-_]+)?(\\{)(?<start>[a-zA-Z0-9_]+)\\.\\.(?<end>[a-zA-Z0-9_]+)(\\.\\.(?<inc>[0-9]+))?\\}(?<post>[a-zA-Z0-9_]+)?", Pattern.CASE_INSENSITIVE);
	public static boolean debugPositional = false;
	//terminal used for debugging
	public static PrintStream System_out = System.out;
	public static PrintStream System_err = System.err;
	public static InputStream System_in = System.in;
	private InputStream stdIn = null;
	private PrintStream stdOut = System.out;
	private PrintStream stdErr = System.err;

	public static ConsoleOutputFrame debugFrame = new ConsoleOutputFrame();
	/**
	 * 
	 * @param code
	 * @return
	 */
	public String preProcess(String code,ShellContext ctx) {

		String ret0 = convertMathSymbols(code);
		String ret00 = convertHash(ret0);
		String ret1 = expandBrace(ret00,ctx);
		int start = code.indexOf("<<");
		List<String> ids = new ArrayList<>();

		while( start >=0 ) {
			Matcher m = hereRx.matcher(code);
			if( m.find()) {
				String word = m.group("word").trim();
				String dash = m.group("dash");
				int idx0 = dash == null?m.start("word"):m.start("dash");

				int idx1 = m.end("word");
				int idx2 = code.indexOf(word.trim(), idx1);

				if( idx2 >  0) {
					String here = code.substring(idx1, idx2);
					if( dash != null ) {
						String [] lines = here.split("\n");
						StringBuilder buf = new StringBuilder();
						for(String line : lines) {
							if( line.startsWith("\t")) {
								line = line.substring(1);
							}
							buf.append(line);
							buf.append('\n');
						}
						here = buf.toString();
					}
					Random r = new Random();
					long herePointer = r.nextLong();
					if( herePointer<0) {
						herePointer *= -1;

					}
					String word2 = word+herePointer;
					ids.add(word2);
					String left = code.substring(0,idx0-2);
					String right = code.substring(idx2+word.length(),code.length());
					ret1 = left+word2+" "+right;
					ctx.setValue(word2, here);
					code = ret1;
				}
			}
			start = code.indexOf("<<",start+1);
		}

		for(String id : ids) {
			ret1 = ret1.replaceAll(id, "<<"+id);
		}

		return ret1;
	}




	private String expandBrace(String code, ShellContext ctx) {
		if( code.indexOf('{')<0 || code.indexOf('}')<0) {
			return code;
		}

		Matcher m = expandBrace.matcher(code);
		StringBuilder sb = new StringBuilder();

		while( m.find()) {
			String pre = m.group("pre");
			if( pre == null ) {
				pre = "";
			}
			String tmpx = m.group("start");				
			boolean isChar = Character.isLetter(tmpx.charAt(0));
			int start = isChar?tmpx.charAt(0): Integer.parseInt( tmpx);
			tmpx = m.group("end");
			int end = isChar?tmpx.charAt(0): Integer.parseInt( tmpx);
			String incStr = m.group("inc");
			int inc = 1;
			if( incStr != null ) {
				inc = Integer.parseInt(incStr);
			}
			String post = m.group("post");
			if( post == null) {
				post = "";
			}
			StringBuilder list = new StringBuilder();
			for(int idx=start; idx <= end; idx+=inc) {
				if( !list.isEmpty()) {
					list.append(' ');
				}
				if( isChar) {
					list.append(pre+((char)idx)+post);
				} else {
					list.append(pre+idx+post);
				}
			}
			m.appendReplacement(sb, list.toString());
		}
		m.appendTail(sb);

		return sb.toString();
	}



	public String convertMathSymbols(String code) {
		boolean inQuote = false;
		StringBuilder ret = new StringBuilder();
		byte [] data = code.getBytes();
		int max = data.length-4;
		for (int idx = 0; idx < data.length; idx++) {
			char c = (char)data[idx];
			switch (c) {
			case '-':
				if(!inQuote && idx < max) {
					boolean matchFound = false;
					String tmp = new String(data, idx, 3);
					for (int idx2 = 0; idx2 < bashOps.length; idx2++) {
						if( bashOps[idx2].equals(tmp)) {
							if(Character.isWhitespace(data[idx-1]) && Character.isWhitespace(data[idx+3])) {
								ret.append(fsshOps[idx2]);
								idx+=2;
								matchFound=true;
								break;
							}
						}
					}

					if( !matchFound) {
						ret.append(c);
					}
				} else {
					ret.append(c);
				}
				break;
			case '"':
			case '\'':inQuote = !inQuote;
			default:
				ret.append(c);
			}
		}
		return ret.toString();
	}

	public static final Pattern convertHashRx = Pattern.compile("\\$\\{"
			+ "[^\\}]*"
			+ "#"
			+ "[^\\}]*"
			+ "\\}");

	public String convertHash(String code) {
		Matcher m = convertHashRx.matcher(code);

		while( m.find()) {
			int start = m.start();
			int end = m.end();
			String str = code.substring(start, end).replaceAll("#", "|");
			String left = code.substring(0,start);
			String right = code.substring(end);
			code = left+str+right;
		}

		return code;
	}

	public String [] splitForArgs(String line) {
		List<String> ret = new ArrayList<>();
		byte data [] = line.getBytes();
		StringBuilder buf = new StringBuilder();
		boolean inQuote = false;
		for (int idx = 0; idx < data.length; idx++) {
			char c = (char)data[idx];
			if( c == '"') {
				inQuote = !inQuote;
			} else {
				if( inQuote || !Character.isWhitespace(c)) {
					buf.append(c);
				} else {
					if( !buf.isEmpty()) {
						ret.add(buf.toString());
						buf.setLength(0);	
					}
				}
			}
		}
		if( !buf.isEmpty()) {
			ret.add(buf.toString());
		}

		return ret.toArray(new String[ret.size()]);
	}

	
	

	public static String getDefaultPath() {
		return defaultPath;
	}

	public static void setDefaultPath(String path) {
		defaultPath = path;
	}



	public InputStream getInputStream(String path) throws IOException {
		FileSource file = mountFactory.createFileSource(path);
		return file.getInputStream();
	}

	boolean isHidden(FileSource file) throws IOException {
		return file.isHidden() || file.getName().startsWith(".");
	}

	public OutputStream getOutputStream(String path, boolean append) throws IOException {
		FileSource file = mountFactory.createFileSource(path);
		return file.getOutputStream(append);
	}


	public String readCode(InputStream stdin) throws IOException {
		StringBuffer ret = new StringBuffer();
		boolean done = false;
		int i = stdin.read();
		boolean escape=false;
		if( !eof) {
			while(i>=0 && !done) {
				char c = (char)i;
				if( c =='\\') {
					// escape next char
					escape = true;
				} else {
					if(escape) {
						ret.append(c);
						escape = false;
					} else {
						if( c == '\n') {
							done = true;
						} else {
							ret.append(c);
						}
					}
				}
				if( !done) {
					i = stdin.read();
				}
			}
			if( i == -1) {
				eof = true;
			}
		}
		return ret.toString();
	}

	private void showError(Throwable e) {
		e.printStackTrace(System.err);

	}

	public Map<String, Object> getVariables() {
		return Collections.unmodifiableMap(variables);
	}

	public Object getVariable(String name) {
		return variables.get(name);
	}



	public void setVariable(String name, Object value) {
		variables.put(name, value);		
	}



	public FileSource getCurrentDirectory() throws IOException {		
		return mountFactory.getCurrentDirectory();
	}



	public char getSeperatorChar() {
		return mountFactory.getSeperatorChar();
	}



	public FileSource[] listRoots() throws IOException {		
		return mountFactory.listRoots();
	}



	public FileSource createFileSource(String path) throws IOException {

		return mountFactory.createFileSource(path);
	}



	public void setCurrentDirectory(FileSource dir) throws IOException {
		mountFactory.setCurrentDirectory(dir);		
	}

	public boolean isOptionEnabled(Option o) {
		return options.contains(o);
	}


	public void setOption(Option o,boolean enable) {
		if( !enable ) {
			options.remove(o);
		} else if(!options.contains(o)) {
			options.add(o);
		}
	}


	public boolean unmount(String mountPoint) throws IOException {
		return mountFactory.unmount(mountPoint);		
	}

	public Object getEvironmentVariables(String name) {		
		return environmentVariables.get(name);
	}

	public void setEnvironmentVariable(String name,Object value) {
		if( value == null ) {
			environmentVariables.remove(name);
		} else {
			environmentVariables.put(name, value);
		}
	}

	public Map<String, Object> getEnvironmentVariable() {
		return Collections.unmodifiableMap(environmentVariables);
	}


	public int executeUsingAntlr(String code)  {
		//TODO: REmove after testing is complete
		if( stdIn == null) {
			throw new RuntimeException("Streams have not been set");
		}
		int ret = 0;
		ShellContext sc = new ShellContext(this);
		
		sc.stdin = getStdIn();
		sc.stdout = getStdOut();
		sc.stderr = getStdErr();
		
		if( sc.stdin == System_in) {
			sc.stdin = new NativeKeyboard();
		}

		try {
			
			if( isOptionEnabled(Option.PrintLinesAsRead)) {
				sc.stdout.println(code);
			}

			code = code.trim();
			String ppCode = preProcess(code, sc);
			if( isOptionEnabled(Option.PrintCommandTrace)) {
				sc.stdout.println(ppCode);
			}

			List<Statement> stmts = FileSourceShVisitorImpl.parse(ppCode);
			if( stmts.size()>0) {				
				for(Statement stmt : stmts) {
					ret = stmt.process(sc);
					if( ret!=0) {
						if(isInteractive && options.contains(Option.ExitImediately)) {
							System.exit(ret);
						}
						return ret;
					}					
				}
			}
		} catch(ExitException e) {
			ret = e.exitCode;
			sc.stderr.println(e);
			stop();
		} catch(Exception e) {
			//e.printStackTrace();
			ret = 1;
			sc.stderr.println(e);
			logError("", e);
		}
		
		return ret;
	}

	public void setLastExitCode(int code) {
		lastExitCode = code;
	}

	public int getLastExitCode() {
		return lastExitCode;
	}


	public Object removeEnvironmentVariables(String name) {
		return environmentVariables.remove(name);
	}

	public Object getAlias(String name) {
		return alias.get(name);
	}

	public Map<String, Object> getAliases() {

		return Collections.unmodifiableMap(alias);
	}

	public void setAlias(String name, Object val) {
		alias.put(name, val);		
	}

	public void removeAlias(String name) {
		alias.remove(name);		
	}

	public void clearAliases() {
		alias.clear();		
	}


}
