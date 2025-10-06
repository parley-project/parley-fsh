package us.bringardner.shell;

import java.awt.GraphicsEnvironment;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Stack;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import sun.misc.Signal;
import sun.misc.SignalHandler;
import us.bringardner.core.BaseThread;
import us.bringardner.core.util.ThreadSafeDateFormat;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.fileproxy.FileProxy;
import us.bringardner.shell.antlr.FileSourceShVisitorImpl;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.ExitException;
import us.bringardner.shell.antlr.statement.FunctionDefStatement;
import us.bringardner.shell.commands.Alias;
import us.bringardner.shell.commands.Cd;
import us.bringardner.shell.commands.Clear;
import us.bringardner.shell.commands.Connect;
import us.bringardner.shell.commands.Cp;
import us.bringardner.shell.commands.DirStack;
import us.bringardner.shell.commands.Echo;
import us.bringardner.shell.commands.Eval;
import us.bringardner.shell.commands.Exec;
import us.bringardner.shell.commands.Exit;
import us.bringardner.shell.commands.Export;
import us.bringardner.shell.commands.Help;
import us.bringardner.shell.commands.History;
import us.bringardner.shell.commands.Jobs;
import us.bringardner.shell.commands.Kill;
import us.bringardner.shell.commands.Ln;
import us.bringardner.shell.commands.Ls;
import us.bringardner.shell.commands.Mkdir;
import us.bringardner.shell.commands.Pwd;
import us.bringardner.shell.commands.Read;
import us.bringardner.shell.commands.Return;
import us.bringardner.shell.commands.Rm;
import us.bringardner.shell.commands.Set;
import us.bringardner.shell.commands.Shift;
import us.bringardner.shell.commands.Sleep;
import us.bringardner.shell.commands.Source;
import us.bringardner.shell.commands.Touch;
import us.bringardner.shell.commands.Trap;
import us.bringardner.shell.commands.Unalias;
import us.bringardner.shell.commands.Unmount;
import us.bringardner.shell.commands.Wc;

public class Console extends BaseThread {

	public static class ConsoleSignalHandler {
		ShellContext ctx;
		String action;
		public ConsoleSignalHandler(ShellContext ctx,String action) {
			this.ctx = ctx;
			this.action = action;
		}
		
	}

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

	/**
	 * Define only signals specified by https://www.gnu.org/software/bash/manual/bash.html#index-trap 
	 */
	public enum ConsoleMetaSignal {
		UnKnown(-1,"")
		, Exit(0,"EXIT")
		, Err(3,"ERR")
		, Debug(1,"DEBUG")
		, Return(2,"RETURN")

		;

		public final String label;
		public final int value;

		private ConsoleMetaSignal(int value,String label) {
			this.label = label;
			this.value = value;
		}

		public static ConsoleMetaSignal find(String name) {
			for(ConsoleMetaSignal o : values()) {
				if( o.label.equals(name)) {
					return o;
				}
			}
			return UnKnown;
		}
	}

	private static String defaultPath = "/usr/bin:/bin:/usr/sbin:/sbin";
	public static final String PATH = "PATH";
	public static final String VARIABLE_OLDPWD = "OLDPWD";
	public static final String VARIABLE_PWD = "PWD";
	public static final String VARIABLE_TERMINAL_WIDTH = "TERMINAL_WIDTH";
	public static final String IFS = "IFS";
	public static final String VARIABLE_PS0 = "PS0";
	public static final String VARIABLE_PS1 = "PS1";
	public static final String VARIABLE_PS2 = "PS2";
	public static final String VARIABLE_PS3 = "PS3";
	public static final String VARIABLE_PS4 = "PS4";


	public static final String VARIABLE_HISTSIZE = "HISTSIZE";
	public static final String VARIABLE_HISTFILE = "HISTFILE";
	public static final String VARIABLE_HISTTIMEFORMAT = "HISTTIMEFORMAT";
	private static final String VARIABLE_HISTCHARS = "histchars";
	public static final String VERSION = "0.01";
	private static String bashOps [] = {"-eq", "-ne", "-lt", "-le", "-gt", "-ge"};
	private static String fsshOps [] = {"==" , "!=" , "<"  , "<=" , ">"  , ">="};
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
	public List<HistoryEntry> history = new ArrayList<>();
	private int lastExitCode;
	private String adminMessage;
	private Map<String,List<PropertyChangeListener>> listners = new TreeMap<>();

	public static Map<String,ShellCommand> commands;

	public static Map<Integer,CommandThread> jobs = new TreeMap<>();
	

	boolean eof = false;
	Map<String,Object> alias = new TreeMap<>();

	private MountFactory mountFactory;
	public boolean forceHeadless=true;
	public boolean isInteractive=false;	
	private Map<String,FunctionDefStatement> functions = new TreeMap<>();

	Map<String,Object> variables = new TreeMap<>();
	List<Object> positionalParameters = new ArrayList<>();
	public List<Option> options = new ArrayList<>();
	private Map<String,Object> environmentVariables = new TreeMap<>();
	DebugContext debugContext = new DebugContext();
	private int lastPid = 0;
	
	static {
		commands = new TreeMap<>();
		commands.put("dirs", new DirStack());
		commands.put("popd", new DirStack());
		commands.put("pushd", new DirStack());
		registerCommand(new Alias());

		registerCommand(new Clear());
		registerCommand(new Cd());
		registerCommand(new Cp());
		registerCommand(new Connect());

		registerCommand(new Eval());
		registerCommand(new Exit());
		registerCommand(new Exec());
		registerCommand(new Echo());
		registerCommand(new Export());

		registerCommand(new Help());
		registerCommand(new History());

		registerCommand(new Jobs());
		registerCommand(new Kill());
		
		registerCommand(new Ln());
		registerCommand(new Ls());

		registerCommand(new Mkdir());

		registerCommand(new Pwd());


		registerCommand(new Return());
		registerCommand(new Rm());
		registerCommand(new Read());

		registerCommand(new Set());
		registerCommand(new Shift());
		registerCommand(new Sleep());
		registerCommand(new Source());


		registerCommand(new Touch());
		registerCommand(new Trap());

		registerCommand(new Unalias());
		registerCommand(new Unmount());

		registerCommand(new Wc());
		
		registerSignals();
	}

	//The upper limit for a PID is 32768
	private static int nextPid = 100000;

	public static synchronized int nextPid() {
		return nextPid++;
	}

	public static void raiseSignal(Integer signum) {

		new Thread(()->{
			//System_out.println("Signal fired "+signum);
			List<ConsoleSignalHandler> tmp = osSignalHandlers.get(signum);
			
			if( tmp == null) {
				//System_out.println("No handler for "+signum);
			} else {
				//System_out.println("Enter handler for "+signum+" sz="+tmp.size());
				List<ConsoleSignalHandler> tmp2 = new ArrayList<>();
				for(int idx=0,sz=tmp.size(); idx < sz; idx++) {
					tmp2.add(tmp.get(idx));
				}
				//System_out.println("tmp2 "+signum+" sz="+tmp2.size());
				for(ConsoleSignalHandler h : tmp2) {
					//System_out.println("execute "+h.action);
					try {
						int ec = h.ctx.console.executeUsingAntlr(h.action);
						//System_out.println(h.action+" result="+ec);
					} catch (Exception e) {
						System_err.println("Signal "+signum+" "+e.getLocalizedMessage());
					}
				}
				//System_out.println("Exit handler for "+signum+" sz="+tmp.size());
			}
			
		}).start();
		
		
	}

	private static void registerSignals() {
		Map<Integer, String> signals = Trap.getLocalSignals();
		for(Integer  i : signals.keySet()) {
			String name = signals.get(i);
			Signal signal = new Signal(name);
			try {
				Signal.handle(signal,(s)->{
					raiseSignal(s.getNumber());					
				});	
			} catch (Exception e) {
				if( !e.getLocalizedMessage().startsWith("Signal already used ")) {
					System_err.println(e.getLocalizedMessage());
				}
			}
		}
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
		public int pid ;
		public int exitCode;
		public long start;
		public long end;
		public Throwable error;
		public Statement cmd;
		public ShellContext ctx;

		public CommandThread(ShellContext ctx,Statement cmd) {
			this.ctx = ctx;
			this.cmd = cmd;
			pid = nextPid();
			setName("Command "+pid);
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



	public static void exit(Console console,int exitCode) {
		System.out.println("exit??");
		//System.exit(exitCode);
	}
	
	public static void main(String args[]) throws IOException {

		try {
			 Runtime.getRuntime().addShutdownHook(new Thread()
		        {
		            @Override
		            public void run()
		            {
		                System.out.println("Shutdown hook ran!");
		            }
		        });
			 

			Console c = new Console();
			ShellContext ctx = new ShellContext(c);
			c.registerHandler(ctx,new Signal("INT"), "echo -n '^C '");
			// Dont't forget: TERM & QUIT both exit but QUIT dumps core and Java won't let us handle QUIT
			c.registerHandler(ctx,new Signal("TERM"), "echo -n '^\\ '");
			c.registerHandler(ctx,new Signal("TSTP"), "echo -n '^Z '");
			c.setStdIn(System.in);
			
			int ret = c.execute(args);

			if(ret==0 && c.isInteractive) {
				c.setName("Console");
				c.setDaemon(false);
				c.start();
			} else {
				Console.exit(c,ret);
			}
		} catch (ExitException e) {
			Console.exit(null,e.exitCode);
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
	
	public int getLastPid() {
		return lastPid;
	}

	public void addChangeListner(String name,PropertyChangeListener listner) {		
		List<PropertyChangeListener> tmp = listners.get(name);
		if( tmp == null) {
			tmp = new ArrayList<>();
			listners.put(name,tmp);
		}
		tmp.add(listner);		
	}

	public void removePropertyChangeListener(PropertyChangeListener l) {
		for(String name: listners.keySet()) {
			List<PropertyChangeListener> ll = listners.get(name);
			if( ll != null) {
				ll.remove(l);
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

			variables.put(VARIABLE_PS0, "");
			variables.put(VARIABLE_PS1, "\\s-\\v\\$ ");
			variables.put(VARIABLE_PS2, "> ");
			variables.put(VARIABLE_PS3, "#? ");
			variables.put(VARIABLE_PS4, "+ ");
			variables.put(VARIABLE_HISTCHARS, "!^#");
			positionalParameters.add("fssh");
			options.add(Option.DoBraceExpantion);
			
			loadProfile();
			
		
		} catch (IOException e) {
		}
	}

	public void loadProfile() {
		try {
			
			FileSource file = homeDir.getChild(".fsshrc");
			if( file.exists() && file.canGroupRead()) {
				//TODO: remove one testing is complete
				stdIn = System.in;
				executeUsingAntlr("source "+file);
			}
			
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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



	@Override
	public void run() {
		int exitCode = 0;
		KeyboardReader kb = getKeyboadReader();
		stdOut = kb.getStdOut();
		stdErr = kb.getStdErr();
		stdIn =  kb.getStdIn();

		readHistory();

		try {
			started = running = true;
			while(running && !stopping) {
				try {
					if(adminMessage!=null) {
						stdOut.println(adminMessage);
						adminMessage = null;
					}
					String prompt = getPrompt(1);					
					kb.setPrompt(prompt);
					String code = kb.readLine(this).trim();
					if( !code.isEmpty()) {
						addHistory(code) ;
						prompt = getPrompt(0);
						if( prompt !=null && !prompt.isEmpty()) {
							stdOut.append(prompt);
						}

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

		if(!isInteractive) {
			Console.exit(this,exitCode);
		} else {
			if (kb instanceof ConsoleFrame) {
				ConsoleFrame cf = (ConsoleFrame) kb;
				cf.dispose();
			}
		}
	}


	public String getPrompt(int i) {
		String ret = "";
		String name = "PS"+i;
		Object val = getVariable(name);
		if( val !=null ) {
			ret = expandPrompt(""+val,new Date());

		}
		return ret;
	}

	ThreadSafeDateFormat fmt = new ThreadSafeDateFormat("EE MMM dd");

	public String strftimeToJava (String val) {
		StringBuilder ret = new StringBuilder();

		char [] chars = val.toCharArray();
		for (int idx = 0; idx < chars.length; idx++) {
			char c = chars[idx];
			if( c == '%') {
				c = chars[++idx];
				switch (c) {
				// %a	Abbreviated weekday name	Sun
				case 'a': ret.append("E"); 
				break;
				// %A	Full weekday name	Sunday
				case 'A': ret.append("EEEE"); 
				break;

				// %b	Abbreviated month name	Mar
				case 'b': ret.append("MMM"); 
				break;

				// %B	Full month name	March
				case 'B': ret.append("MMMM"); 
				break;

				// %c	Date and time representation	Sun Aug 19 02:56:02 2012
				case 'c': ret.append("EE MMM dd HH:mm:ss yyyy"); 
				break;				
				// %d	Day of the month (01-31)	19
				case 'd': ret.append("dd"); 
				break;

				// %H	Hour in 24h format (00-23)	14
				case 'H': ret.append("HH"); 
				break;

				// %I	Hour in 12h format (01-12)	05
				case 'I': ret.append("hh"); 
				break;

				// %j	Day of the year (001-366)	231
				case 'j': ret.append("DDD"); 
				break;

				// %m	Month as a decimal number (01-12)	08
				case 'm': ret.append("MM"); 
				break;

				// %M	Minute (00-59)	55
				case 'M': ret.append("mm"); 
				break;

				// %p	AM or PM designation	PM
				case 'p': ret.append("a"); 
				break;

				// %S	Second (00-61)	02
				case 'S': ret.append("ss"); 
				break;

				// %U	Week number with the first Sunday as the first day of week one (00-53)	33
				case 'U': ret.append("www"); 
				break;

				// %w	Weekday as a decimal number with Sunday as 0 (0-6)	4
				case 'w': ret.append("uu"); 
				break;

				// %W	Week number with the first Monday as the first day of week one (00-53)	34
				case 'W': ret.append("www"); 
				break;

				// %x	Date representation	08/19/12
				case 'x': ret.append("MM/dd/yy"); 
				break;

				// %X	Time representation	02:50:06
				case 'X': ret.append("HH:mm:ss"); 
				break;

				// %y	Year, last two digits (00-99)	01
				case 'y': ret.append("yy"); 
				break;

				// %Y	Year	2012
				case 'Y': ret.append("yyyy"); 
				break;

				// %Z	Timezone name or abbreviation	CDT
				case 'Z': ret.append("z"); 
				break;

				// %%	A % sign	%
				case '%': ret.append("%"); 
				break;

				default:
					throw new IllegalArgumentException("Unexpected value: " + c);
				}
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}

	public String expandPrompt(String val,Date date) {
		StringBuilder ret = new StringBuilder();

		char [] chars = val.toCharArray();

		for (int idx = 0; idx < chars.length; idx++) {
			char c = chars[idx];
			if( c == '\\') {
				if( idx == chars.length-1) {
					ret.append(c);
					continue;
				}

				char next = chars[++idx];

				switch (next) {

				// \a 	A bell character.
				case 'a':
					ret.append(((char)7));
					break;
					// \d 	The date, in "Weekday Month Date" format (e.g., "Tue May 26").
				case 'd': 
					ret.append(fmt.format(date));
					break;
					// \D{format} The format is passed to strftime(3) and the result is inserted into the prompt string; an empty format results in a locale-specific time representation. The braces are required.
				case 'D': 
					if( chars[++idx] == '{') {
						StringBuilder tmp = new StringBuilder();
						for(idx++; idx < chars.length && chars[idx] != '}';idx++) {
							tmp.append(chars[idx]);
						}
						String fmt = strftimeToJava(tmp.toString());
						SimpleDateFormat df = new SimpleDateFormat(fmt);
						ret.append(df.format(date));
					}
					break;

					// \e 	An escape character.
				case 'e': ret.append((char)27); break;

				// \h 	The hostname, up to the first ‘.’.
				case 'h':
					try {
						String name = InetAddress.getLocalHost().getHostName();
						int ii = name.indexOf('.');
						if( ii > 0 ) {
							name = name.substring(0,ii);
						}
						ret.append(name);
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					break;
					// \H 	The hostname.
				case 'H': 
					try {
						String host = InetAddress.getLocalHost().getHostName();
						FileSource cwd = getCurrentDirectory();
						if (!(cwd instanceof FileProxy)) {
							Properties prop = cwd.getFileSourceFactory().getConnectProperties();
							String tmp = prop.getProperty("Host");
							if( tmp == null ) {
								tmp = prop.getProperty("host");
							}
							if( tmp != null) {
								host = tmp;
							}							
						}
						ret.append(host);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} 
					break;
					// \j	The number of jobs currently managed by the shell.
				case 'j':
					ret.append(""+jobs.size());
					break;
					// \l	The basename of the shell’s terminal device name (e.g., "ttys0").
				case 'l':ret.append("fssh"); 
				break;
				// \n	A newline.
				case 'n': ret.append("\n"); 
				break;
				// \r 	A carriage return.
				case 'r': ret.append("\r");
				break;
				// \s	The name of the shell: the basename of $0 (the portion following the final slash).
				case 's':
					Object p = getPositionalParameters().get(0);
					if(p !=null ) {
						ret.append(""+p);
					} else {
						ret.append("");
					}
					break;

					// \t	The time, in 24-hour HH:MM:SS format.
				case 't':
					SimpleDateFormat tfmt1 = new SimpleDateFormat("HH:mm:ss");
					ret.append(tfmt1.format(date));
					break;
					// \T	The time, in 12-hour HH:MM:SS format.
				case 'T': 
					SimpleDateFormat tfmt2 = new SimpleDateFormat("hh:mm:ss");
					ret.append(tfmt2.format(date)); 
					break;
					// \@ 	The time, in 12-hour am/pm format.
				case '@':
					SimpleDateFormat tfmt3 = new SimpleDateFormat("hh:mm:ss a");
					ret.append(tfmt3.format(date));
					break;
					// \A	The time, in 24-hour HH:MM format.
				case 'A':
					SimpleDateFormat tfmt4 = new SimpleDateFormat("HH:mm");
					ret.append(tfmt4.format(date));
					break;
					// \\u	The username of the current user.
				case 'u':
					ret.append(System.getProperty("user.name"));
					break;
					// \v	The Bash version (e.g., 2.00).
				case 'v': 
					ret.append(Console.VERSION);
					break;
					// \V	The Bash release, version + patchlevel (e.g., 2.00.0).
				case 'V': 
					ret.append(Console.VERSION);
					break;

					// \w	The value of the PWD shell variable ($PWD), with $HOME abbreviated with a tilde (uses the $PROMPT_DIRTRIM variable).
					// \W	The basename of $PWD, with $HOME abbreviated with a tilde.
				case 'w':
				case 'W':
					String home = System.getProperty("user.dir");

					String pwd = ""+getVariable(VARIABLE_PWD);
					if( pwd.startsWith(home)) {
						pwd = pwd.substring(home.length());
						pwd = "~"+pwd;
					}
					ret.append(pwd);

					break;

					// \!	The history number of this command.
				case '!': 
					// \#	The command number of this command.
				case '#': 
					//  not really supported
					ret.append(""+history.size());
					break;
					// \$ If the effective uid is 0, #, otherwise $.
				case '$':
					ret.append("$");
					break;
					// \nnn	The character whose ASCII code is the octal value nnn.
					// \\ A backslash.
				case '\\':
					ret.append("\\");
					break;

					// \[Begin a sequence of non-printing characters. This could be used to embed a terminal control sequence into the prompt.
					// \]End a sequence of non-printing characters.

				case '[':
					char tc = chars[++idx];
					while(tc != ']') {
						ret.append(tc);
						tc = chars[++idx];
					}
					break;

				default:
					if(Character.isDigit(next) ) {
						if(idx < chars.length-2) {
							String tmp = ""+chars[idx]+chars[++idx]+chars[++idx];
							int ascii = Integer.parseInt(tmp, 8);
							char c2 = (char)ascii;
							ret.append(c2);
							continue;
						}
					}
					throw new IllegalArgumentException("Unexpected value: " + next);
				}

			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}

	KeyboardReader keyboardReader;

	public KeyboardReader getKeyboadReader() {
		if( keyboardReader==null ) {
			synchronized (this) {
				if( keyboardReader==null ) {
					if( !GraphicsEnvironment.isHeadless()) {
						ConsoleFrame ret = new ConsoleFrame(this);
						try {
							if( SwingUtilities.isEventDispatchThread()) {
								ret.setLocationRelativeTo(null);			
								ret.setVisible(true);											
							} else SwingUtilities.invokeAndWait(()->{
								ret.setLocationRelativeTo(null);			
								ret.setVisible(true);			
							});
						} catch (InvocationTargetException | InterruptedException e) {
						}	
						keyboardReader = ret;
					} else {
						keyboardReader = new NativeKeyboard();
					}
				}
			}
		}



		return keyboardReader;
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
		Object old = variables.get(name);
		variables.put(name, value);		
		List<PropertyChangeListener> tmp = listners.get(name);
		if( tmp !=null) {
			new Thread(()->{
				PropertyChangeEvent event = new PropertyChangeEvent(Console.this, name, old, value);
				for(PropertyChangeListener l : tmp) {
					l.propertyChange(event);
				}
			}).start();
		}
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
		setVariable(VARIABLE_OLDPWD, getCurrentDirectory().getAbsolutePath());
		mountFactory.setCurrentDirectory(dir);				
		setVariable(VARIABLE_PWD, dir.getAbsolutePath());
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


	public String getAdminMessage() {
		return adminMessage;
	}

	public void setAdminMessage(String adminMessage) {
		this.adminMessage = adminMessage;
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

		Statement lastStatement=null;
		
		try {

			if( isOptionEnabled(Option.PrintLinesAsRead)) {
				sc.stdout.println(getPrompt(4)+code);
			}

			code = code.trim();
			String ppCode = preProcess(code, sc);
			if( isOptionEnabled(Option.PrintCommandTrace)) {
				sc.stdout.println(ppCode);
			}

			List<Statement> stmts = FileSourceShVisitorImpl.parse(ppCode);
			if( stmts.size()>0) {				
				for(Statement stmt : stmts) {
					lastStatement=stmt;
					handleMetaSignal(ConsoleMetaSignal.Debug,stmt);
					ret = stmt.process(sc);
					if( ret!=0) {
						handleMetaSignal(ConsoleMetaSignal.Err,stmt);
						if(isInteractive && options.contains(Option.ExitImediately)) {
							Console.exit(this,ret);
						}
						return ret;
					}					
				}
			}
		} catch(ExitException e) {
			ret = e.exitCode;
			handleMetaSignal(ConsoleMetaSignal.Exit,lastStatement);
			sc.stderr.println(e);
			stop();

			if(!isInteractive) {
				Console.exit(this,ret);
			} else {
				KeyboardReader kb = getKeyboadReader();
				if (kb instanceof ConsoleFrame) {
					ConsoleFrame cf = (ConsoleFrame) kb;
					cf.dispose();
				}
			}
		} catch(Exception e) {
			//e.printStackTrace();
			ret = 1;
			sc.stderr.println(e);
			logError("", e);
			handleMetaSignal(ConsoleMetaSignal.Err,lastStatement);
		}

		return ret;
	}

	private Stack<ConsoleMetaSignal> inProcess = new Stack<>();


	private void handleMetaSignal(ConsoleMetaSignal signal, Statement stmt) {

		if( !inProcess.contains(signal)) {			
			List<String> actions = signalHandlers.get(signal);
			if( actions !=null) {
				inProcess.push(signal);
				for(int idx=0, sz=actions.size(); idx < sz; idx++ ) {			
					String code = actions.get(idx);

					try {
						executeUsingAntlr(code);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				inProcess.pop();
			}
		}
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

	public void setMountFactory(MountFactory mount) {
		mountFactory = mount;		
	}

	private Map<ConsoleMetaSignal,List<String>> signalHandlers = new TreeMap<>();
	public void registerHandler(ConsoleMetaSignal signal, String action) {
		List<String> actions = signalHandlers.get(signal);
		if( actions == null) {
			actions = new ArrayList<>();
			signalHandlers.put(signal, actions);
		}
		actions.add(action);		
	}

	private static Map<Integer,List<ConsoleSignalHandler>> osSignalHandlers = new TreeMap<>();
	
	public void registerHandler(ShellContext ctx,final Signal signal, String action) {
		ConsoleSignalHandler handler = new ConsoleSignalHandler(ctx,action);
		List<ConsoleSignalHandler> actions = osSignalHandlers.get(signal.getNumber());
		if( actions == null) {
			actions = new ArrayList<>();
			osSignalHandlers.put(signal.getNumber(), actions);
		}
		actions.add(handler);
	}

	public void addFunction(FunctionDefStatement function) {
		functions.put(function.getName(), function);		
	}

	public FunctionDefStatement getFunction(String name) {
		return functions.get(name);
	}

	public Map<String, FunctionDefStatement> getFunctions() {
		return Collections.unmodifiableMap(functions);
	}

	public boolean removeFunction(String name) {
		return functions.remove(name) !=null;
	}

	public void addJob(int pid, CommandThread thread) {
		jobs.put(pid,thread);
		lastPid = pid;
	}

	

}
