package us.bringardner.fsh;

import java.awt.GraphicsEnvironment;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.JMRuntimeException;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;


import sun.misc.Signal;
import us.bringardner.parley.core.util.ThreadSafeDateFormat;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.IRandomAccessStream;
import us.bringardner.parley.files.fileproxy.FileProxy;
import us.bringardner.fsh.ShellContext.LoopControl;
import us.bringardner.fsh.signal.ExitException;
import us.bringardner.fsh.signal.FshException;
import us.bringardner.fsh.exec.Programs;
import us.bringardner.fsh.signal.LoopControlException;
import us.bringardner.fsh.commands.Alias;
import us.bringardner.fsh.commands.Bg;
import us.bringardner.fsh.commands.Cd;
import us.bringardner.fsh.commands.Clear;
import us.bringardner.fsh.commands.Connect;
import us.bringardner.fsh.commands.Cp;
import us.bringardner.fsh.commands.Dirs;
import us.bringardner.fsh.commands.Disown;
import us.bringardner.fsh.commands.Echo;
import us.bringardner.fsh.commands.Eval;
import us.bringardner.fsh.commands.Exec;
import us.bringardner.fsh.commands.Exit;
import us.bringardner.fsh.commands.Export;
import us.bringardner.fsh.commands.Expr;
import us.bringardner.fsh.commands.Fg;
import us.bringardner.fsh.commands.Find;
import us.bringardner.fsh.commands.Help;
import us.bringardner.fsh.commands.History;
import us.bringardner.fsh.commands.Jobs;
import us.bringardner.fsh.commands.Kill;
import us.bringardner.fsh.commands.Let;
import us.bringardner.fsh.commands.Ln;
import us.bringardner.fsh.commands.Ls;
import us.bringardner.fsh.commands.Mkdir;
import us.bringardner.fsh.commands.Mv;
import us.bringardner.fsh.commands.Popd;
import us.bringardner.fsh.commands.Pushd;
import us.bringardner.fsh.commands.Pwd;
import us.bringardner.fsh.commands.Read;
import us.bringardner.fsh.commands.Return;
import us.bringardner.fsh.commands.Rm;
import us.bringardner.fsh.commands.Set;
import us.bringardner.fsh.commands.Shift;
import us.bringardner.fsh.commands.Sleep;
import us.bringardner.fsh.commands.Source;
import us.bringardner.fsh.commands.Touch;
import us.bringardner.fsh.commands.Trap;
import us.bringardner.fsh.commands.Unalias;
import us.bringardner.fsh.commands.Unset;
import us.bringardner.fsh.commands.Unmount;
import us.bringardner.fsh.commands.Wait;
import us.bringardner.fsh.commands.Wc;
import us.bringardner.fsh.job.ForgroundJob;
import us.bringardner.fsh.job.IJob;
import us.bringardner.fsh.job.JobManager;
import us.bringardner.fsh.job.JobState;

public class Console extends SignalEnabledThread {

	/** umask and ulimit settings, as the commands that made them (see commands.ProcessSettings) */
	public final List<String> processSettings = new CopyOnWriteArrayList<>();

	/** builtins turned off with enable -n: the name runs a program (or nothing) instead */
	public final java.util.Set<String> disabledBuiltins = ConcurrentHashMap.newKeySet();

	/** the builtin named name, or null (none, or enable -n turned it off) */
	public Constructor<? extends ShellCommand> builtin(String name) {
		return disabledBuiltins.contains(name) ? null : commands.get(name);
	}

	/** $_: the last argument of the last simple command */
	public volatile String lastArgument = "";

	public static class FileDiscriptor {

		/** the source of a copy of another descriptor (exec 3>&1): closing the copy leaves the stream open */
		private static final Object SHARED = new Object();

		int id;
		InputStream in;
		PrintStream out;
		Object source;

		public static FileDiscriptor shared(int id, PrintStream out) {
			return new FileDiscriptor(id, out, SHARED);
		}

		public static FileDiscriptor shared(int id, InputStream in) {
			return new FileDiscriptor(id, in, SHARED);
		}

		public FileDiscriptor(int id, InputStream in) {
			this.id = id;
			this.in = in;
		}
		public FileDiscriptor(int id, InputStream in,Object source) {
			this(id, in);
			this.source = source;
		}
		public FileDiscriptor(int id, PrintStream out) {
			this.id = id;
			this.out = out;			
		}
		public FileDiscriptor(int id, PrintStream out,Object source) {
			this(id, out);
			this.source = source;
		}
		public InputStream getIn() {
			return in;
		}
		public void setIn(InputStream in) {
			this.in = in;
		}
		public PrintStream getOut() {
			return out;
		}
		public void setOut(PrintStream out) {
			this.out = out;
		}
		public Object getSource() {
			return source;
		}
		public void setSource(Object source) {
			this.source = source;
		}
		public int getId() {
			return id;
		}


	}

	private volatile Map<Integer,FileDiscriptor> files;

	public static class SuspendException extends FshException {

		public IJob job;



		public SuspendException(IJob job) {
			this.job = job;
		}

		private static final long serialVersionUID = 1L;

	}


	public static class ResumeException extends RuntimeException{

		public IJob job;



		public ResumeException(IJob job) {
			this.job = job;
		}

		private static final long serialVersionUID = 1L;

	}

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
		, MarkAllForExport("a", "allexport")
		, ReprtJobStausImediately("b", "notify")
		, ExitImediately("e", "errexit")
		, DisableFilenameExpansion("f", "noglob")
		, RistrictectShell ("r")
		, ExitAfterOne ("t", "onecmd")
		, NullParameterIsError ("u", "nounset")
		, PrintLinesAsRead ("v", "verbose")
		, PrintCommandTrace ("x", "xtrace")
		, DoBraceExpantion ("B", "braceexpand")
		, NoClobberRedirect ("C", "noclobber")
		, DontFollowLinks ("P", "physical")
		// set -o pipefail: a pipeline's status is the last failed stage's
		, PipeFail ("\u0000pipefail", "pipefail")
		// set -E: functions, ( ) and pipe stages inherit the ERR trap
		, ErrTrace ("E", "errtrace")
		// set -n: read commands without running them (a script's syntax check)
		, NoExec ("n", "noexec")
		// set -T: functions inherit the DEBUG and RETURN traps
		, FuncTrace ("T", "functrace")
		, KeyboardEcho ("kbecho")
		, VerboseError ("verboseError")
		;
		public final String label;
		/** the name for set -o (errexit for -e) */
		public final String longName;


		private Option(String label) {
			this(label, label);
		}

		private Option(String label, String longName) {
			this.label = label;
			this.longName = longName;
		}

		public static Option find(String name) {
			for(Option o : values()) {
				if( o.label.equals(name) || (!name.isEmpty() && o.longName.equals(name))) {
					return o;
				}
			}
			return Unsupported;
		}
	}

	public enum ConsoleState {ReadLine,Executing}

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

	public enum Prompt{
		Primary("PS1"),Secondary("PS2"),Select("PS3"),BeforeExecute("PS0"),EchoCommand("PS4");

		public final String name;
		private Prompt(String name) {
			this.name= name;			
		}

	}

	private static String defaultPath ;//= "/usr/bin:/bin:/usr/sbin:/sbin";
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
	/*
[n]<<[-]word
        here-document
delimiter
	 */
	public static boolean debugPositional = false;
	//terminal used for debugging
	public static PrintStream System_out = System.out;
	public static PrintStream System_err = System.err;
	public static InputStream System_in = System.in;
	private InputStream stdIn = System.in;
	private PrintStream stdOut = System.out;
	private PrintStream stdErr = System.err;
	public List<HistoryEntry> history = new ArrayList<>();
	private int lastExitCode;
	private String adminMessage;
	// read and written by the console thread, background jobs, signal handlers and the IDE
	private Map<String,List<PropertyChangeListener>> listners = new ConcurrentHashMap<>();

	public static Map<String,Constructor<? extends ShellCommand>> commands;


	boolean eof = false;
	Map<String,Object> alias = new ConcurrentSkipListMap<>();

	private VirtualFileSourceFactory mountFactory;
	public boolean forceHeadless=true;
	public boolean isInteractive=false;	
	private Map<String,ShellFunction> functions = new ConcurrentSkipListMap<>();

	Map<String,Object> variables = new ConcurrentSkipListMap<>();
	List<Object> positionalParameters = new ArrayList<>();
	public List<Option> options = new ArrayList<>();
	private Map<String,Object> environmentVariables = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
	DebugContext debugContext = new DebugContext();
	private int lastPid = 0;
	public JobManager jobManager = new JobManager();


	static {
		commands = new TreeMap<>();
		
		registerCommand(new Alias());

		registerCommand(new Bg());

		registerCommand(new Clear());
		registerCommand(new Cd());
		registerCommand(new Cp());
		registerCommand(new Connect());
		
		registerCommand(new Dirs());
		registerCommand(new Disown());
		
		registerCommand(new Eval());
		registerCommand(new Exit());
		registerCommand(new Exec());
		registerCommand(new Echo());
		registerCommand(new Export());
		registerCommand(new Expr());

		registerCommand(new Fg());
		registerCommand(new Find());
		
		registerCommand(new Help());
		registerCommand(new History());

		registerCommand(new Jobs());
		registerCommand(new Kill());

		registerCommand(new Let());
		registerCommand(new Ln());
		registerCommand(new Ls());

		registerCommand(new Mkdir());
		registerCommand(new Mv());
		
		registerCommand(new Popd());
		registerCommand(new Pushd());
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
		registerCommand(new us.bringardner.fsh.commands.True());
		registerCommand(new us.bringardner.fsh.commands.False());
		registerCommand(new us.bringardner.fsh.commands.Colon());
		registerCommand(new us.bringardner.fsh.commands.Test());
		registerCommand(new us.bringardner.fsh.commands.Readonly());
		registerCommand(new us.bringardner.fsh.commands.Printf());
		registerCommand(new us.bringardner.fsh.commands.Getopts());
		registerCommand(new us.bringardner.fsh.commands.Mapfile());
		registerCommand(new us.bringardner.fsh.commands.Readarray());
		registerCommand(new us.bringardner.fsh.commands.Shopt());
		registerCommand(new us.bringardner.fsh.commands.Type());
		registerCommand(new us.bringardner.fsh.commands.Builtin());
		registerCommand(new us.bringardner.fsh.commands.CommandCmd());
		registerCommand(new us.bringardner.fsh.commands.Caller());
		registerCommand(new us.bringardner.fsh.commands.BracketTest());
		registerCommand(new Unmount());
		registerCommand(new Unset());

		registerCommand(new Wait());
		registerCommand(new us.bringardner.fsh.commands.Compgen());
		registerCommand(new us.bringardner.fsh.commands.Times());
		registerCommand(new us.bringardner.fsh.commands.Enable());
		registerCommand(new us.bringardner.fsh.commands.Umask());
		registerCommand(new us.bringardner.fsh.commands.Ulimit());
		registerCommand(new Wc());

		registerSignals();
	}



	private void raiseSignal(Integer signum) {

		new Thread(()->{
			//System_out.println("Signal fired "+signum);
			List<ConsoleSignalHandler> tmp = osSignalHandlers.get(signum);

			if( tmp != null) {
				List<ConsoleSignalHandler> tmp2 = new ArrayList<>();
				for(int idx=0,sz=tmp.size(); idx < sz; idx++) {
					tmp2.add(tmp.get(idx));
				}
				for(ConsoleSignalHandler h : tmp2) {
					try {
						int ec = h.ctx.console.executeScript(h.action);
						if( ec !=0) {
							h.ctx.stderr.println("Signal handler "+h.action+" exit code="+ec);
						}
					} catch (Exception e) {
						h.ctx.stderr.println("Signal "+signum+" "+e.getLocalizedMessage());
					}
				}
			}

		}).start();


	}

	/**
	 * Handle all OS signals to prevent default behavior
	 */
	private static void registerSignals() {
		Map<Integer, String> signals = Trap.getLocalSignals();
		for(Integer  i : signals.keySet()) {
			String name = signals.get(i);
			Signal signal = new Signal(name);
			try {
				Signal.handle(signal,(s)->{
					//raiseSignal(s.getNumber());					
				});	
			} catch (Exception e) {
				if( !e.getLocalizedMessage().startsWith("Signal already used ")) {
					System_err.println(e.getLocalizedMessage());
				}
			}
		}
	}

	public static synchronized void setNextPid(int pid) {
		JobManager.setNextPid(pid);
	}



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

	private static int cmdCnt=0;
	private static synchronized int getCmdCnt() {
		return cmdCnt++;
	}

	public static class CommandThread extends SignalEnabledThread {
		public int exitCode=-1212;
		public long start;
		public long end;
		public Exception error;
		/** what runs */
		public ShellTask task;
		public ShellContext ctx;

		public CommandThread(ShellContext ctx, ShellTask task) {
			this.ctx = ctx;
			this.task = task;
			setName("Command "+getCmdCnt());
		}
		
		public Thread getThread() {
			return thread;
		}

		public void pause(boolean b) {
			ctx.setPause(b);
		}

		public boolean isPaused() {
			return ctx.isPaused();
		}

		public JobState getState() {
			JobState ret = isPaused()?JobState.Suspended:isRunning()?JobState.Running:JobState.Termnated;

			return ret;
		}

		public String toString() {
			return task.text();
		}

		@Override
		public void run() {
			started = running = true;
			start = System.currentTimeMillis();
			try {
				if( ctx.isIsolated()) {
					// a pipe stage: its own directory and options
					ctx.console.enterStage();
				}
				exitCode = task.run(ctx);
			} catch (Exception e) {
				error = e;
				// a stage or job that exits (exit 3, set -e) has that status; another error is 1
				exitCode = e instanceof ExitException ? ((ExitException) e).exitCode : 1;
			}
			if( terminatedBy != null ) {
				// ended by kill: 128 + the signal, as in bash (143 for TERM)
				exitCode = 128+terminatedBy;
			}
			ctx.stdout.flush();
			close(ctx.console,ctx.stdout);
			end = System.currentTimeMillis();
			running = false;
		}

		@Override
		public void stop() {
			
			ctx.setExecption(new ExitException(ctx, 1));
			ctx.setPause(true);
			super.stop();
		}

		/** the signal that ended this (kill), or null */
		private volatile Integer terminatedBy;

		@Override
		public void handleSignal(ConsoleSignal signal)  {
			//throw new RuntimeException("CommandThread Not implemented");
			switch (signal) {
			case Hup:
			case Interupt:
			case Terminate:
			case Kill:
				terminatedBy = signal.value;
				break;
			default:
				break;
			}
			List<CommandThread> kids = task.children();
			if( !kids.isEmpty()) {
				for(CommandThread kid : kids) {
					kid.handleSignal(signal);
				}
			} else if( signal == ConsoleSignal.Hup || signal == ConsoleSignal.Interupt || signal == ConsoleSignal.Terminate || signal == ConsoleSignal.Kill ) {
				ctx.setExecption(new LoopControlException(LoopControl.Break,-1));
			} else if( signal == ConsoleSignal.Suspend ) {
				ctx.setExecption(new SuspendException(null));
			}
		}
	}



	/** false when the shell runs inside another program (such as a test): exit then ends the console, not the JVM */
	public static volatile boolean exitJvm = true;

	public static void exit(Console console,int exitCode) {
		if( console.isRunning()) {
			console.stop();
		}
		KeyboardReader kb = console.getKeyboadReader(false);
		if (kb instanceof ConsoleFrame) {
			ConsoleFrame cf = (ConsoleFrame) kb;
			cf.dispose();
		}
		if( !console.isInteractive && exitJvm ) {
			StackTraceElement[] trace = Thread.currentThread().getStackTrace();
			for(StackTraceElement t  : trace) {
				if( t.getClassName().startsWith("org.junit")) {
					//  junit will stop testing prematurely if we call System.exit :-(
					return;
				}
			}

			System.exit(exitCode);
		}
	}

	public static void main(String args[]) throws IOException {

			Console c = new Console();
			Runtime.getRuntime().addShutdownHook(new Thread(){
				@Override
				public void run(){
					c.saveHistory();
				}
			});

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
				while(c.isAlive()) {
					try {
						c.join(0);
					} catch (InterruptedException e) {
					}
				}
				System.exit(c.lastExitCode);
			} else {
				Console.exit(c,ret);
			}
		

	}

	public static void close(Console console,OutputStream out) {
		if( console.stdOut == out ) {
			return;
		}
		if( out != System_out && out != System_err) {
			try {
				out.close();
			} catch (IOException e) {
			}
		}		
	}

	public static void close(Console console,InputStream in) {
		if( console.stdIn == in ) {
			return;
		}
		if( in != System_in) {
			try {
				in.close();
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
		listners.computeIfAbsent(name, n -> new CopyOnWriteArrayList<>()).add(listner);		
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
			ret = executeScript(tmp);
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
			String codeToRun = code.toString().trim();
			ret = executeScript(codeToRun);
		} else {
			isInteractive = true;
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
	public ConsoleState state;

	/** functions from the environment (BASH_FUNC_name%%), defined when the console is made */
	private final Map<String,String> importedFunctions = new java.util.LinkedHashMap<>();

	/** define the functions bash (or fsh) exported to this one, exported again */
	private void importFunctions() {
		for(Map.Entry<String,String> e : importedFunctions.entrySet()) {
			if( !e.getKey().matches("[A-Za-z_][A-Za-z0-9_.:-]*")) {
				continue;
			}
			try {
				ShellContext sc = new ShellContext(this);
				sc.stderr = new PrintStream(java.io.OutputStream.nullOutputStream());
				us.bringardner.fsh.exec.Executor.run(sc, e.getKey()+" "+e.getValue());
				ShellFunction f = getFunction(e.getKey());
				if( f != null ) {
					f.setExported(true);
				}
			} catch (Exception ex) {
				// a function that does not parse is left out, as bash does
			}
		}
		importedFunctions.clear();
	}

	/**
	 * The environment of a program: the exported variables, and the exported functions as bash
	 * passes them (BASH_FUNC_name%%=() { ... }).
	 */
	public Map<String,String> programEnvironment(ShellContext ctx) {
		Map<String,String> env = new java.util.LinkedHashMap<>();
		for(Map.Entry<String,Object> e : ctx.getEnvironmentVariables().entrySet()) {
			if( e.getValue() != null ) {
				env.put(e.getKey(), ""+e.getValue());
			}
		}
		for(ShellFunction f : getFunctions().values()) {
			if( f.isExported()) {
				env.put("BASH_FUNC_"+f.getName()+"%%", exportedBody(f));
			}
		}
		return env;
	}

	/** "() {  first;\n second\n}": a function's body as bash puts it in the environment */
	public static String exportedBody(ShellFunction f) {
		String [] lines = f.declaration().split("\n");
		StringBuilder ret = new StringBuilder("() {  ");
		// lines: "f () ", "{ ", the commands (indented 4), "}" and maybe its redirects
		for (int i = 2; i < lines.length-1; i++) {
			String line = lines[i].startsWith("    ") ? lines[i].substring(4) : lines[i];
			ret.append(i == 2 ? "" : " ").append(line).append(i < lines.length-2 ? "\n" : "");
		}
		return ret.append("\n").append(lines[lines.length-1]).toString();
	}

	public Console() {
		try {
			environmentVariables.putAll(System.getenv());
			// an inherited OLDPWD stays only if it is a directory, as in bash
			Object oldpwd = environmentVariables.get(VARIABLE_OLDPWD);
			if( oldpwd != null && !new java.io.File(oldpwd.toString()).isDirectory()) {
				environmentVariables.remove(VARIABLE_OLDPWD);
			}
			// functions bash exported (export -f): BASH_FUNC_name%%=() { ... }
			for(String key : new java.util.ArrayList<>(environmentVariables.keySet())) {
				if( key.startsWith("BASH_FUNC_") && key.endsWith("%%")) {
					importedFunctions.put(key.substring(10, key.length()-2), ""+environmentVariables.remove(key));
				}
			}
			if( defaultPath !=null) {
				environmentVariables.put(PATH, getDefaultPath());
			}
			environmentVariables.put(VARIABLE_HISTSIZE, 500);
			environmentVariables.put(VARIABLE_HISTFILE, "~/.fsh_history");


			mountFactory = new VirtualFileSourceFactory();			
			String home = System.getProperty("user.home");
			//  the java environment HOME does not match the java property user.home
			environmentVariables.put("HOME", home);
			
			FileSource tmp = mountFactory.createFileSource(home);
			homeDir=tmp;
			if( homeDir.exists()) {
				mountFactory.setCurrentDirectory(homeDir);
			}
			variables.put(VARIABLE_PWD, mountFactory.getCurrentDirectory().getAbsolutePath());
			// (OLDPWD is unset until cd, as in bash)
			variables.put(IFS, " \t\n");
			////Primary("PS1"),Secondary("PS2"),Select("PS3"),BeforeExecute("PS0"),EchoCommand("PS4");

			variables.put(Prompt.BeforeExecute.name, "");
			variables.put(Prompt.Primary.name, "\\s-\\v\\$ ");
			variables.put(Prompt.Secondary.name, "> ");
			variables.put(Prompt.Select.name, "#? ");
			variables.put(Prompt.EchoCommand.name, "+ ");
			variables.put(VARIABLE_HISTCHARS, "!^#");
			positionalParameters.add("fsh");
			options.add(Option.DoBraceExpantion);


			loadProfile();


		} catch (IOException e) {
		}
			importFunctions();
	}

	public void loadProfile() {
		try {

			FileSource file = homeDir.getChild(".fshrc");
			if( !file.exists()) {
				// the name before BjlShell became fsh
				FileSource old = homeDir.getChild(".fsshrc");
				if( old.exists()) {
					file = old;
				}
			}
			if( file.exists() && file.canGroupRead()) {
				//TODO: remove one testing is complete
				stdIn = System.in;
				executeScript("source "+file);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void registerCommand(ShellCommand cmd) {
		try {
			Class<? extends ShellCommand> cls = cmd.getClass();
			Constructor<? extends ShellCommand> con = cls.getConstructor();
			commands.put(cmd.getName(), con);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}		
	}


	public boolean mount(FileSource f, String mountPoint) throws IOException {
		if(mountPoint == null || mountPoint.isEmpty() || mountPoint.charAt(0) != '/'){
			throw new IOException("Invalid mountpoint");
		}
		String name = mountPoint.substring(1);
		return mountFactory.mount(name,f);
	}

	public FileSourceFactory getMountFactory() {
		return mountFactory;
	}

	public FshList getPositionalParameters() {
		FshList ret = new FshList();
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



	public void run() {
		KeyboardReader kb = getKeyboadReader(true);
		stdOut = kb.getStdOut();
		stdErr = kb.getStdErr();
		stdIn =  kb.getStdIn();

		readHistory();
		IJob job = null;
		started = running = true;
		JobState lastState = JobState.Idel;

		while(running && !stopping) {
			try {
				currentJob.set(job);
				if( job == null ){
					job = readLineToJob(kb);
					lastState = job.getState();
				}
				currentJob.set(job);


				synchronized (jobStateLock) {
					// woken by the listener added in readLineToJob; the timeout is only a safety net
					while( job.getState() == lastState && lastState!=JobState.Termnated) {
						try {
							jobStateLock.wait(500);
						} catch (InterruptedException e) {
						}
					}
				}

				JobState jobState = job.getState();

				switch (jobState	) {
				case Running: break;
				case Idel: break;
				case Suspended:
					if( job.getJobNumber()<0) {
						addJob(job);
					}
					job = null;		
					lastState = JobState.Idel;
					break;
				case Termnated: 
					if(job.getJobNumber()<0) {
						// simple command					
					} else {
						// this is a background job
						System.out.println("notify");
					}


					int exitCode = job.getExitCode();
					if( exitCode!=0 ) {
						if(isInteractive && isOptionEnabled(Option.ExitImediately)) {
							Console.exit(this,exitCode);
						}
					}
					job = null;					
					break;
				case Notified:break;
				default:
					throw new IllegalArgumentException("Unexpected value: " + job.getState());
				}
				lastState = jobState;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	private final Object jobStateLock = new Object();

	private IJob readLineToJob(KeyboardReader kb) {
		IJob ret = null;

		while(ret == null) {


			state = ConsoleState.ReadLine;
			currentJob.set(null);
			if(adminMessage!=null) {
				stdOut.println(adminMessage);
				adminMessage = null;
			}
			for(IJob job : jobManager.getJobs()) {
				if( job.getState()==JobState.Termnated) {
					//[1]  + done       sleep 50
					stdOut.println("["+job.getJobNumber()+"] done "+job.toString());	
					job.setState(JobState.Notified);
				}
			}
			String prompt = getPrompt(Prompt.Primary);					
			kb.setPrompt(prompt);
			String code;
			try {
				code = kb.readLine(this);
				boolean endOfInput = code == null;
				if( endOfInput ) {
					// end of input (Ctrl-D): leave like bash does
					code = "exit";
				}
				code = code.trim();
				if( !code.isEmpty()) {
					state = ConsoleState.Executing;
					if( !endOfInput ) {
						addHistory(code) ;
					}

					prompt = getPrompt(Prompt.BeforeExecute);
					if( prompt !=null && !prompt.isEmpty()) {
						stdOut.append(prompt);
					}



					if( isOptionEnabled(Option.PrintLinesAsRead)) {
						stdOut.println(getPrompt(Prompt.EchoCommand)+code);
					}

					ShellContext sc = new ShellContext(this);

					ret = new ForgroundJob(sc,code);					
					ret.addJobStateChangeListner((job,from,to)->{
						synchronized (jobStateLock) {
							jobStateLock.notifyAll();
						}
					});
					ret.start();
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}


	public void run1() {
		int exitCode = 0;
		KeyboardReader kb = getKeyboadReader(true);
		stdOut = kb.getStdOut();
		stdErr = kb.getStdErr();
		stdIn =  kb.getStdIn();

		readHistory();

		started = running = true;
		while(running && !stopping) {
			try {

				state = ConsoleState.ReadLine;
				currentJob.set(null);
				if(adminMessage!=null) {
					stdOut.println(adminMessage);
					adminMessage = null;
				}
				String prompt = getPrompt(Prompt.Primary);					
				kb.setPrompt(prompt);
				String code = kb.readLine(this).trim();
				if( !code.isEmpty()) {
					state = ConsoleState.Executing;
					addHistory(code) ;

					prompt = getPrompt(Prompt.BeforeExecute);
					if( prompt !=null && !prompt.isEmpty()) {
						stdOut.append(prompt);
					}

					ShellContext sc = new ShellContext(this);

					if( isOptionEnabled(Option.PrintLinesAsRead)) {
						sc.stdout.println(getPrompt(Prompt.EchoCommand)+code);
					}

					code = code.trim();

					IJob job = new ForgroundJob(sc,code);
					currentJob.set(job);
					job.start();

					exitCode = executeAsJob(job);	
					if( exitCode!=0) {
						if(isInteractive && isOptionEnabled(Option.ExitImediately)) {
							Console.exit(this,exitCode);
						}
					}
				}
			} catch (ResumeException e) {
				try {
					currentJob.set(e.job);
					exitCode = executeAsJob(e.job);
					if( exitCode!=0) {
						if(isInteractive && isOptionEnabled(Option.ExitImediately)) {
							Console.exit(this,exitCode);
						}
					}
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}	


			} catch (SuspendException e) {

				IJob job = currentJob.get();
				if( e.job !=null ) {
					job = e.job;
				}
				if( job !=null) {
					String tmp = positionalParameters.get(0)+": suspended  "+job.toString();
					stdOut.println(tmp);
					addJob(job);
				} 
			} catch(ExitException e) {
				handleMetaSignal(ConsoleMetaSignal.Exit);
				stdErr.println(e);
				stop();

				if(!isInteractive) {
					Console.exit(this,exitCode);
				} else {
					if (kb instanceof ConsoleFrame) {
						ConsoleFrame cf = (ConsoleFrame) kb;
						cf.dispose();
					}
				}
			} catch(Exception e) {
				//e.printStackTrace();
				exitCode = 1;
				stdErr.println(e);
				logError("", e);
				handleMetaSignal(ConsoleMetaSignal.Err);
			}
		}
		if(!isInteractive) {
			Console.exit(this,exitCode);
		} else {
			if (kb instanceof ConsoleFrame) {
				ConsoleFrame cf = (ConsoleFrame) kb;
				cf.dispose();
			}
		}
		running = false;
	}
	/*
	 * 1/16
	 * 308l stainless
	 * er70s-6 
	 * alum er4043 er5356
	 *  
	 * 
	 */

	public synchronized int executeAsJob(IJob job) throws Exception {
		if( currentJob.get() !=null) {
			throw new JMRuntimeException("Current job is already set");
		}

		while(!job.hasStarted()) {
			try {
				Thread.sleep(10);	
			} catch (Exception e) {
			}					
		}		

		while(job.getState()==JobState.Running) {
			try {
				Thread.sleep(10);	
			} catch (Exception e) {
			}
		}

		if( job.getError()!=null) {
			throw job.getError();
		}


		return job.getExitCode();
	}

	public String getPrompt(Prompt prompt) {
		String ret = "";
		Object val = getVariable(prompt.name);
		if( val !=null ) {
			try {
				ret = expandPrompt(""+val,new Date());
			} catch (RuntimeException e) {
				// a bad prompt must not stop the shell (it used to loop forever printing the error)
				ret = ""+val;
			}
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
				if( idx == chars.length-1) {
					ret.append("'%'");
					continue;
				}
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
					// not supported: keep it as text
					ret.append("'%"+c+"'");
				}
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}

	private static boolean isOctal(char c) {
		return c >= '0' && c <= '7';
	}

	private static volatile String localHostName;

	/**
	 * The host name is looked up once: the lookup can take seconds when the name doesn't resolve,
	 * and the prompt is drawn for every command.
	 */
	private static String getLocalHostName() {
		String ret = localHostName;
		if( ret == null ) {
			try {
				ret = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				ret = "localhost";
			}
			localHostName = ret;
		}
		return ret;
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
					if( idx+1 < chars.length && chars[idx+1] == '{') {
						idx++;
						StringBuilder tmp = new StringBuilder();
						for(idx++; idx < chars.length && chars[idx] != '}';idx++) {
							tmp.append(chars[idx]);
						}
						String fmt = strftimeToJava(tmp.toString());
						SimpleDateFormat df = new SimpleDateFormat(fmt);
						ret.append(df.format(date));
					} else {
						// the braces are required
						ret.append("\\D");
					}
					break;

					// \e 	An escape character.
				case 'e': ret.append((char)27); break;

				// \h 	The host name, up to the first ‘.’.
				case 'h':
					ret.append(getLocalHostName());

					break;
					// \H 	The host name.
				case 'H': 
					try {
						String host = getLocalHostName();
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
					ret.append(""+jobManager.getJobs().size());
					break;
					// \l	The base name of the shell’s terminal device name (e.g., "ttys0").
				case 'l':ret.append("fsh"); 
				break;
				// \n	A newline.
				case 'n': ret.append("\n"); 
				break;
				// \r 	A carriage return.
				case 'r': ret.append("\r");
				break;
				// \s	The name of the shell: the base name of $0 (the portion following the final slash).
				case 's':
					Object p = positionalParameters.get(0);
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
					// $HOME (was user.dir, the directory the shell started in)
					ret.append(abbreviateHome(""+getVariable(VARIABLE_PWD), System.getProperty("user.home")));

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
					for(idx++; idx < chars.length && chars[idx] != ']'; idx++) {
						ret.append(chars[idx]);
					}
					break;

				default:
					if(isOctal(next) && idx+2 < chars.length && isOctal(chars[idx+1]) && isOctal(chars[idx+2])) {
						String tmp = ""+chars[idx]+chars[++idx]+chars[++idx];
						ret.append((char)Integer.parseInt(tmp, 8));
					} else {
						// unknown escape: show it as typed, like bash
						ret.append('\\').append(next);
					}
				}

			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}

	public IJob findJob(int id) {
		IJob ret = null;
		List<IJob> ijobs = jobManager.getJobs();

		int sz = ijobs.size();
		if(id<= sz) {
			ret = jobManager.getJob(id-1);
		} else {
			for(IJob c : ijobs) {
				if( c.getPid() == id) {
					ret = c;
					break;
				}
			}
		}
		return ret;
	}

	KeyboardReader keyboardReader;

	/**
	 * Use this keyboard instead of the one Console picks itself (a Swing window, or the
	 * native terminal when headless), e.g. a remote terminal such as an SSH session. Set it
	 * before the console starts reading.
	 */
	public void setKeyboardReader(KeyboardReader reader) {
		this.keyboardReader = reader;
	}

	public KeyboardReader getKeyboadReader(boolean setVisible) {
		if( keyboardReader==null ) {
			synchronized (this) {
				if( keyboardReader==null ) {
					if( !GraphicsEnvironment.isHeadless()) {
						ConsoleFrame ret = new ConsoleFrame(this);
						ret.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
						if( setVisible) {
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
			FileSource file = createFileSource(expandHome(fileName));
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
						if( line.isEmpty()) {
							continue;
						}
						long time = loadTime;
						if( line.charAt(0) == hist_comment) {
							// "#<time>" is the time of the command on the next line; other comments are skipped
							Long stamp = parseHistoryTime(line.substring(1));
							if( stamp == null || lineNum+1 >= lines.length) {
								continue;
							}
							String next = lines[lineNum+1].trim();
							if( next.isEmpty() || next.charAt(0) == hist_comment) {
								continue;
							}
							time = stamp;
							line = next;
							lineNum++;
						}
						tmp.add(new HistoryEntry(time, true, line));
					}
					history = tmp;
				}
			}
			truncateHistory();
		} catch (IOException e) {
			logError("read history", e);
		}
	}

	private static Long parseHistoryTime(String text) {
		try {
			return Long.parseLong(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * @return fileName with a leading ~ replaced by $HOME
	 */
	private String expandHome(String fileName) {
		if( fileName.equals("~") || fileName.startsWith("~/") || fileName.startsWith("~\\")) {
			Object home = environmentVariables.get("HOME");
			if( home == null ) {
				home = System.getProperty("user.home");
			}
			return home+fileName.substring(1);
		}
		return fileName;
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
			FileSource file = createFileSource(expandHome(fileName));

			// the in-memory history includes what was read at startup, so replace the file
			try(OutputStream out = file.getOutputStream(false)) {
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
	 * The # that are operators: ${#x}, ${x#pat} ${x##pat}, ${x/#pat/r}, ${!#}. A # in a pattern
	 * or a word (${k%%#*}, ${v//#/X}, ${x:-#}) is text. (The first # of ## is | by then.)
	 * @param before the text between ${ and the #
	 */
	private static final Pattern HASH_OPERATOR = Pattern.compile("!?|!?([a-zA-Z_][a-zA-Z_0-9]*|[0-9]+|[@*?$!-])(\\[[^\\]]*\\])?([#|]|/)?");

	private static boolean isHashOperator(String before) {
		return HASH_OPERATOR.matcher(before).matches();
	}


	public static String [] splitForArgs(String line) {
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

	public Map<String, Object> getVariables() {
		return Collections.unmodifiableMap(variables);
	}

	public Object getVariable(String name) {
		return variables.get(name);
	}



	/**
	 * Listeners are called on this thread, one event at a time and in order.
	 */
	private static final ExecutorService propertyEvents = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Console property events");
		t.setDaemon(true);
		return t;
	});

	public void setVariable(String name, Object value) {
		// a null value unsets the variable (the map does not hold nulls)
		Object old = value == null ? variables.remove(name) : variables.put(name, value);		
		List<PropertyChangeListener> tmp = listners.get(name);
		if( tmp !=null) {
			PropertyChangeEvent event = new PropertyChangeEvent(Console.this, name, old, value);
			propertyEvents.execute(()->{
				for(PropertyChangeListener l : tmp) {
					l.propertyChange(event);
				}
			});
		}
	}



	/**
	 * What a subshell may change: variables, the environment, positional parameters, options,
	 * aliases, functions, the current directory and file descriptors above 2. ( ... ) and $( )
	 * take one before they run and restore it after, so their changes do not reach the shell, as
	 * in bash. ($? is not part of it: the subshell's status is the status of ( ... ).)
	 */
	public static final class Snapshot {
		private final Map<String,Object> variables;
		private final Map<String,Object> environment;
		private final List<Object> positional;
		private final List<Option> options;
		private final Map<String,Boolean> shellOptions;
		private final Map<String,Object> alias;
		private final Map<String,ShellFunction> functions;
		private final FileSource cwd;
		private final Map<Integer,FileDiscriptor> files;

		private Snapshot(Console c) throws IOException {
			// arrays by value: a subshell's a[1]=x or m[k]=v changes the arrays it shares with
			// the shell, and restore puts these copies back
			variables = new TreeMap<>();
			for(Map.Entry<String,Object> e : c.variables.entrySet()) {
				variables.put(e.getKey(), ShellContext.copyValue(e.getValue()));
			}
			environment = new TreeMap<>(c.environmentVariables);
			positional = new ArrayList<>(c.positionalParameters);
			options = new ArrayList<>(c.optionList());
			shellOptions = new TreeMap<>(c.getShellOptions());
			alias = new TreeMap<>(c.alias);
			functions = new TreeMap<>(c.functions);
			cwd = c.getCurrentDirectory();
			files = new TreeMap<>(c.getFiles());
		}
	}

	public Snapshot snapshot() throws IOException {
		return new Snapshot(this);
	}

	public void restore(Snapshot s) throws IOException {
		variables.clear();
		variables.putAll(s.variables);
		environmentVariables.clear();
		environmentVariables.putAll(s.environment);
		positionalParameters.clear();
		positionalParameters.addAll(s.positional);
		optionList().clear();
		optionList().addAll(s.options);
		getShellOptions().clear();
		getShellOptions().putAll(s.shellOptions);
		alias.clear();
		alias.putAll(s.alias);
		functions.clear();
		functions.putAll(s.functions);
		changeDirectory(s.cwd);
		synchronized (this) {
			// close what the subshell opened (exec 3>file) and put back what it closed or replaced
			for(Integer id : new ArrayList<>(files.keySet())) {
				if( id > 2 && files.get(id) != s.files.get(id)) {
					closeFileDistcriptor(id);
				}
			}
			for(Map.Entry<Integer,FileDiscriptor> e : s.files.entrySet()) {
				if( e.getKey() > 2 ) {
					files.put(e.getKey(), e.getValue());
				}
			}
		}
	}

	/**
	 * A pipe stage's own current directory and options: the stage runs on its own thread, at the
	 * same time as the shell, so cd, set -x and shopt there must not change the shell's (bash's
	 * stages are subshells). Threads a stage starts inherit it.
	 */
	public static final class StageState {
		FileSource cwd;
		List<Option> options;
		Map<String,Boolean> shellOptions;
	}

	private final InheritableThreadLocal<StageState> stage = new InheritableThreadLocal<>();

	/**
	 * This thread is a pipe stage: from now on it has its own copy of the directory and options.
	 */
	public void enterStage() throws IOException {
		StageState s = new StageState();
		s.cwd = getCurrentDirectory();
		s.options = new java.util.concurrent.CopyOnWriteArrayList<>(optionList());
		s.shellOptions = new ConcurrentHashMap<>(getShellOptions());
		stage.set(s);
	}

	/** the options in effect on this thread (a stage's own, or the shell's) */
	private List<Option> optionList() {
		StageState s = stage.get();
		return s != null ? s.options : options;
	}

	/** the options set now (set -e ... , $-) */
	public List<Option> getOptions() {
		return Collections.unmodifiableList(new ArrayList<>(optionList()));
	}

	public FileSource getCurrentDirectory() throws IOException {
		StageState s = stage.get();
		return s != null ? s.cwd : mountFactory.getCurrentDirectory();
	}

	/** set the directory without PWD and OLDPWD (a stage's, or the shell's) */
	private void changeDirectory(FileSource dir) throws IOException {
		StageState s = stage.get();
		if( s != null ) {
			s.cwd = dir;
		} else {
			mountFactory.setCurrentDirectory(dir);
		}
	}



	public char getSeperatorChar() {
		return mountFactory.getSeperatorChar();
	}



	public FileSource[] listRoots() throws IOException {		
		return mountFactory.listRoots();
	}



	public FileSource createFileSource(String path) throws IOException {
		if(path.equals(".")) {
			return getCurrentDirectory();
		}
		if( path.equals("..")) {
			return getCurrentDirectory().getParentFile();			
		}
		StageState s = stage.get();
		if( s != null && !path.isEmpty() && !path.startsWith("/") && !path.startsWith("~") && !(path.length() > 1 && path.charAt(1) == ':')) {
			// relative to the stage's own directory
			String dir = s.cwd.getAbsolutePath();
			return mountFactory.createFileSource(dir.endsWith("/") ? dir+path : dir+"/"+path);
		}
		return mountFactory.createFileSource(path);
	}



	/** change the directory and PWD (cd, pushd and popd set OLDPWD; starting in a directory does not) */
	public void setCurrentDirectory(FileSource dir) throws IOException {
		changeDirectory(dir);
		if( stage.get() == null ) {
			// (in a pipe stage cd sets the stage's PWD)
			setVariable(VARIABLE_PWD, dir.getAbsolutePath());
		}
	}

	public boolean isOptionEnabled(Option o) {
		return optionList().contains(o);
	}


	public void setOption(Option o,boolean enable) {
		if( !enable ) {
			optionList().remove(o);
		} else if(!optionList().contains(o)) {
			optionList().add(o);
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

	public AtomicReference<IJob> currentJob = new AtomicReference<>();

	private Stack<ConsoleMetaSignal> inProcess = new Stack<>();


	public void handleMetaSignal(ConsoleMetaSignal signal) {

		if( !inProcess.contains(signal)) {			
			List<String> actions = signalHandlers.get(signal);
			if( actions !=null) {
				inProcess.push(signal);
				for(int idx=0, sz=actions.size(); idx < sz; idx++ ) {			
					String code = actions.get(idx);

					try {
						executeScript(code);
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

	/** shopt's options, by name */
	private final Map<String,Boolean> shellOptions = new ConcurrentHashMap<>();
	{
		for(String n : new String[] {"autocd", "cdspell", "checkwinsize", "dotglob", "expand_aliases", "extglob",
				"failglob", "globstar", "histappend", "inherit_errexit", "lastpipe", "nocaseglob", "nocasematch",
				"nullglob", "sourcepath", "xpg_echo"}) {
			shellOptions.put(n, false);
		}
		shellOptions.put("checkwinsize", true);
		shellOptions.put("sourcepath", true);
	}

	public Map<String,Boolean> getShellOptions() {
		StageState s = stage.get();
		return s != null ? s.shellOptions : shellOptions;
	}

	/** getopts: OPTIND and where in that word the next letter is */
	private int getoptsIndex = 1;
	private int getoptsPos = 0;

	/** where in the word at optind getopts is (0 if OPTIND was changed by the script) */
	public int getoptsPosition(int optind) {
		return optind == getoptsIndex ? getoptsPos : 0;
	}

	public void setGetoptsPosition(int optind, int pos) {
		getoptsIndex = optind;
		getoptsPos = pos;
	}

	/** readonly variables: they cannot be set or unset */
	private final java.util.Set<String> readonlyVariables = ConcurrentHashMap.newKeySet();

	private final java.util.Set<String> readonlyFunctions = ConcurrentHashMap.newKeySet();

	/** readonly -f name */
	public boolean isReadonlyFunction(String name) {
		return readonlyFunctions.contains(name);
	}

	public void setReadonlyFunction(String name) {
		readonlyFunctions.add(name);
	}

	public boolean isReadonly(String name) {
		return readonlyVariables.contains(name);
	}

	public void setReadonly(String name) {
		readonlyVariables.add(name);
	}

	/** declare -l (l) and -u (u): an assigned value is made lower or upper case */
	private final Map<String,Character> caseVariables = new ConcurrentHashMap<>();

	public Character getCaseAttribute(String name) {
		return caseVariables.get(name);
	}

	public void setCaseAttribute(String name, Character attr) {
		if( attr == null ) {
			caseVariables.remove(name);
		} else {
			caseVariables.put(name, attr);
		}
	}

	/** variables declared with declare -i: an assignment's value is arithmetic */
	private final java.util.Set<String> integerVariables = ConcurrentHashMap.newKeySet();

	public boolean isInteger(String name) {
		return integerVariables.contains(name);
	}

	public void setInteger(String name, boolean integer) {
		if( integer ) {
			integerVariables.add(name);
		} else {
			integerVariables.remove(name);
		}
	}

	/** how many $( ) have run (an assignment's status is that of its $( ), if it has one) */
	private final java.util.concurrent.atomic.AtomicLong substitutions = new java.util.concurrent.atomic.AtomicLong();

	public long substitutionCount() {
		return substitutions.get();
	}

	public void substitutionDone(int code) {
		lastExitCode = code;
		substitutions.incrementAndGet();
	}

	public int getLastExitCode() {
		return lastExitCode;
	}


	public Object removeEnvironmentVariables(String name) {
		return environmentVariables.remove(name);
	}

	/**
	 * Where each alias was defined by the new executor (the script and its line): as in bash, an
	 * alias is not used on the line that defines it.
	 */
	public final Map<String,String> aliasLines = new java.util.concurrent.ConcurrentHashMap<>();

	public Object getAlias(String name) {
		return alias.get(name);
	}

	public Map<String, Object> getAliases() {

		return Collections.unmodifiableMap(alias);
	}

	public void setAlias(String name, Object val) {
		if( val == null ) {
			alias.remove(name);
		} else {
			alias.put(name, val);		
		}
	}

	public void removeAlias(String name) {
		alias.remove(name);		
	}

	public void clearAliases() {
		alias.clear();		
	}

	/**
	 * pwd with the home directory shown as "~", as in a bash prompt. Only whole path
	 * elements match: /home/tony2 is not under /home/tony.
	 */
	public static String abbreviateHome(String pwd, String home) {
		if( home == null || home.isEmpty() || !FileSourceFactory.isSameOrDescendant(home, pwd)) {
			return pwd;
		}
		return "~"+pwd.substring(home.length());
	}

	public void setMountFactory(VirtualFileSourceFactory mount) {
		mountFactory = mount;		
	}

	private Map<ConsoleMetaSignal,List<String>> signalHandlers = new ConcurrentSkipListMap<>();
	public void registerHandler(ConsoleMetaSignal signal, String action) {
		signalHandlers.computeIfAbsent(signal, k -> new CopyOnWriteArrayList<>()).add(action);		
	}

	/** trap action EXIT/ERR/RETURN/DEBUG: the action replaces the one before; null removes it */
	public void setTrap(ConsoleMetaSignal signal, String action) {
		if( action == null ) {
			signalHandlers.remove(signal);
		} else {
			signalHandlers.put(signal, new CopyOnWriteArrayList<>(List.of(action)));
		}
	}

	/** trap action SIG: the action replaces the one before; null removes it */
	public void setTrap(ShellContext ctx, Signal signal, String action) {
		if( action == null ) {
			osSignalHandlers.remove(signal.getNumber());
		} else {
			List<ConsoleSignalHandler> list = new CopyOnWriteArrayList<>();
			list.add(new ConsoleSignalHandler(ctx, action));
			osSignalHandlers.put(signal.getNumber(), list);
		}
	}

	/**
	 * kill -SIG $$: run the trap for the signal in ctx (as if the shell got it).
	 * @return false if there is no trap for it ('' ignores it: true)
	 */
	public boolean runOsTrap(int signum, ShellContext ctx) {
		List<ConsoleSignalHandler> handlers = osSignalHandlers.get(signum);
		if( handlers == null || handlers.isEmpty()) {
			return false;
		}
		String action = handlers.get(handlers.size()-1).action;
		int saved = getLastExitCode();
		Integer savedLine = ctx.trapLine;
		String savedCommand = ctx.currentCommand;
		ctx.trapLine = ctx.trapLine != null ? ctx.trapLine : ctx.currentLine();
		try {
			runCode(ctx, action);
		} catch (us.bringardner.fsh.signal.FshException e) {
			throw e;
		} catch (Exception e) {
			ctx.stderr.println(e.getMessage());
		} finally {
			setLastExitCode(saved);
			ctx.trapLine = savedLine;
			ctx.currentCommand = savedCommand;
		}
		return true;
	}

	/** the traps, as trap -p prints them: {name, action}, EXIT first, then by signal number */
	public List<String[]> traps() {
		List<String[]> ret = new ArrayList<>();
		java.util.function.BiConsumer<ConsoleMetaSignal,String> meta = (s, name) -> {
			List<String> a = signalHandlers.get(s);
			if( a != null && !a.isEmpty()) {
				ret.add(new String[] {name, a.get(a.size()-1)});
			}
		};
		meta.accept(ConsoleMetaSignal.Exit, "EXIT");
		Map<Integer,String> names = us.bringardner.fsh.commands.Trap.getLocalSignals();
		for(Map.Entry<Integer, List<ConsoleSignalHandler>> e : osSignalHandlers.entrySet()) {
			if( !e.getValue().isEmpty()) {
				String n = names.get(e.getKey());
				ret.add(new String[] {"SIG"+(n == null ? ""+e.getKey() : n), e.getValue().get(e.getValue().size()-1).action});
			}
		}
		meta.accept(ConsoleMetaSignal.Debug, "DEBUG");
		meta.accept(ConsoleMetaSignal.Return, "RETURN");
		meta.accept(ConsoleMetaSignal.Err, "ERR");
		return ret;
	}

	/**
	 * Run the ERR, RETURN (or DEBUG) trap in ctx, so $1 and local variables are the running
	 * function's; $? is kept. A trap does not run inside its own action.
	 */
	public void runTrap(ConsoleMetaSignal signal, ShellContext ctx) {
		List<String> actions = signalHandlers.get(signal);
		if( actions == null || actions.isEmpty() || inProcess.contains(signal)) {
			return;
		}
		int saved = getLastExitCode();
		inProcess.push(signal);
		Integer savedLine = ctx.trapLine;
		String savedCommand = ctx.currentCommand;
		ctx.trapLine = ctx.trapLine != null ? ctx.trapLine : ctx.currentLine();
		try {
			for(String code : actions) {
				runCode(ctx, code);
			}
		} catch (us.bringardner.fsh.signal.FshException e) {
			throw e;
		} catch (Exception e) {
			ctx.stderr.println(e.getMessage());
		} finally {
			inProcess.pop();
			setLastExitCode(saved);
			ctx.trapLine = savedLine;
			ctx.currentCommand = savedCommand;
		}
	}

	private  Map<Integer,List<ConsoleSignalHandler>> osSignalHandlers = new ConcurrentSkipListMap<>();

	public void registerHandler(ShellContext ctx,final Signal signal, String action) {
		ConsoleSignalHandler handler = new ConsoleSignalHandler(ctx,action);
		osSignalHandlers.computeIfAbsent(signal.getNumber(), k -> new CopyOnWriteArrayList<>()).add(handler);
	}

	public void addFunction(ShellFunction function) {
		functions.put(function.getName(), function);		
	}

	public FileDiscriptor getFileDistcriptor(int id) {
		return getFiles().get(id);
	}

	public void setFileDistcriptor(FileDiscriptor fd) {
		closeFileDistcriptor(fd.id);
		files.put(fd.id,fd);
	}

	public FileDiscriptor closeFileDistcriptor(int id) {
		@SuppressWarnings("unused")
		Map<Integer, FileDiscriptor> tmp = getFiles();
		FileDiscriptor ret = files.remove(id);
		if( ret != null) {
			if( ret.source == FileDiscriptor.SHARED ) {
				if( ret.out != null ) {
					ret.out.flush();
				}
			} else if (ret.source instanceof IRandomAccessStream) {
				IRandomAccessStream rad = (IRandomAccessStream) ret.source ;
				try {
					rad.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				if( ret.out != null) {
					close(this, ret.out);
				}
				if( ret.in !=null) {
					close(this,ret.in);
				}
			}
		}
		return ret;
	}

	/**
	 * Forget a file descriptor without closing its stream (n>&m- moved it to n).
	 */
	public FileDiscriptor removeFileDistcriptor(int id) {
		getFiles();
		return files.remove(id);
	}

	public Map<Integer,FileDiscriptor> getFiles() {
		if( files == null) {
			synchronized (this) {
				if( files == null) {
					files = new TreeMap<Integer, Console.FileDiscriptor>();
					files.put(0,new FileDiscriptor(0,getStdIn()));
					files.put(1,new FileDiscriptor(1,getStdOut()));
					files.put(2,new FileDiscriptor(2,getStdErr()));
				}
			}
		}
		return files;
	}

	public ShellFunction getFunction(String name) {
		return functions.get(name);
	}

	public Map<String, ShellFunction> getFunctions() {
		return Collections.unmodifiableMap(functions);
	}

	public boolean removeFunction(String name) {
		if( readonlyFunctions.contains(name)) {
			// readonly -f: it stays
			return false;
		}
		return functions.remove(name) !=null;
	}

	public void addJob(IJob job) {
		lastPid = jobManager.addJob(job);
	}

	@Override
	public void handleSignal(ConsoleSignal signal)  {
		if( state == null) {
			// non interactive console
		} else if( state == ConsoleState.ReadLine) {
			if(signal==ConsoleSignal.Hup) {
				/*
				 * The shell exits by default upon receipt of a SIGHUP. Before exiting, an interactive shell resends the SIGHUP to all jobs, running or stopped.
				 * 	 The shell sends SIGCONT to stopped jobs to ensure that they receive the SIGHUP (See Job Control, for more information about running and stopped jobs). 
				 */
				List<IJob> ijobs = jobManager.getJobs();

				for(IJob job :ijobs) {
					if( job.getState() != JobState.Termnated) {
						if( job.getState() == JobState.Suspended) {
							job.handleSignal(ConsoleSignal.Continue);
						}
						job.handleSignal(ConsoleSignal.Hup);
					}
				}
				throw new ExitException(null, lastExitCode);
			}			
		} else {
			IJob job = currentJob.get();
			if( job !=null) {
				if( signal==ConsoleSignal.Suspend) {
					job.setState(JobState.Suspended);
					Thread.yield();
				} else if( signal == ConsoleSignal.Interupt || signal == ConsoleSignal.Kill) {
					job.setState(JobState.Termnated);
					Thread.yield();
				}
			}

		}



	}	


	public void handleSignal(int pid,ConsoleSignal signal)  {
		raiseSignal(signal.value);
		IJob job = findJob(pid);
		if( job !=null) {
			job.handleSignal(signal);
		}

	}

	/**
	 * Run code in ctx (a trap's action, a sourced file, find -exec): a syntax error is reported,
	 * status 2.
	 */
	public int runCode(ShellContext ctx, String code) throws IOException {
		return us.bringardner.fsh.exec.Executor.run(ctx, code);
	}

	public int executeScript(ShellContext sc,String code)  {
		int ret = 0;
		try {

			if( isOptionEnabled(Option.PrintLinesAsRead)) {
				sc.stdout.println(getPrompt(Prompt.EchoCommand)+code);
			}

			code = code.trim();
			return us.bringardner.fsh.exec.Executor.script(sc, code);

		} catch(ExitException e) {
			ret = e.exitCode;
			handleMetaSignal(ConsoleMetaSignal.Exit);
			sc.stderr.println(e);
			stop();

			if(!isInteractive) {
				Console.exit(this,ret);
			} else {
				KeyboardReader kb = getKeyboadReader(false);
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
			handleMetaSignal(ConsoleMetaSignal.Err);
		}

		return ret;
	}

	/** how deep executeScript is (eval and source call it too): the EXIT trap runs at the end of the outermost */
	private int executeDepth = 0;

	/**
	 * Run the EXIT trap, once (when a script ends or exits).
	 */
	/**
	 * A subshell's own EXIT trap: the shell's is put aside while it runs (bash does not give it to
	 * the subshell) and the subshell's runs when it ends, with $? its status.
	 * @return the shell's EXIT trap, for {@link #endSubshellTrap(List, int)}
	 */
	public List<String> beginSubshellTrap() {
		return signalHandlers.remove(ConsoleMetaSignal.Exit);
	}

	public void endSubshellTrap(List<String> shells, int status) {
		if( signalHandlers.containsKey(ConsoleMetaSignal.Exit)) {
			setLastExitCode(status);
			runExitTrap();
		}
		if( shells != null ) {
			signalHandlers.put(ConsoleMetaSignal.Exit, shells);
		}
	}

	private void runExitTrap() {
		List<String> actions = signalHandlers.remove(ConsoleMetaSignal.Exit);
		if( actions != null ) {
			for(String action : actions) {
				try {
					executeScript(action);
				} catch (Exception e) {
					getStdErr().println(e.getMessage());
				}
			}
		}
	}

	public int executeScript(String code)  {
		executeDepth++;
		try {
			return executeScript0(code);
		} finally {
			if( --executeDepth == 0 && !isInteractive ) {
				// the script has ended
				runExitTrap();
			}
		}
	}

	private int executeScript0(String code)  {
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
				sc.stdout.println(getPrompt(Prompt.EchoCommand)+code);
			}

			code = code.trim();
			ret = us.bringardner.fsh.exec.Executor.script(sc, code);
			if( ret != 0 && isInteractive && isOptionEnabled(Option.ExitImediately)) {
				Console.exit(sc.console, ret);
			}
			return ret;

		} catch(ExitException e) {
			ret = e.exitCode;
			runExitTrap();
			if(e.message!=null) {
				sc.stderr.println(e.message);
			}
			stop();

			if(!isInteractive) {
				Console.exit(this,ret);
			} else {
				KeyboardReader kb = getKeyboadReader(false);
				if (kb instanceof ConsoleFrame) {
					ConsoleFrame cf = (ConsoleFrame) kb;
					cf.dispose();
				}
			}
		} catch(Exception e) {
			//e.printStackTrace();
			ret = 1;
			String msg = e.getMessage();
			if( msg== null) {
				msg = e.toString();
				int idx = msg.lastIndexOf('.');
				msg = msg.substring(idx+1);
			}
			sc.stderr.println(msg);
			if( isOptionEnabled(Option.VerboseError)) {
				e.printStackTrace(sc.stderr);
			}
			logError("", e);
			handleMetaSignal(ConsoleMetaSignal.Err);
		}

		return ret;
	}
}
