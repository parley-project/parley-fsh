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
import us.bringardner.fsh.job.ProcessSignals;

public class Console extends SignalEnabledThread {

	/** umask and ulimit settings, as the commands that made them (see commands.ProcessSettings) */
	public final List<String> processSettings = new CopyOnWriteArrayList<>();

	/** functions with the trace attribute (declare -ft): the DEBUG and RETURN traps run in them */
	public final java.util.Set<String> tracedFunctions = ConcurrentHashMap.newKeySet();

	/** pushd's stack below the current directory, oldest first */
	public final List<String> dirStack = new CopyOnWriteArrayList<>();

	/** the umask (null until umask asks the system for it) */
	public volatile Integer umask;

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
		// set -H: history expansion of typed lines (!!, !$, ^old^new); on in an interactive shell
		, HistExpand ("H", "histexpand")
		// set -o history: commands go into the history (typed ones, or a script's); on in an interactive shell
		, History ("\u0000history", "history")
		// bash's, kept so set -o and $- show them: -h -k -m -p and the names
		, Hashall ("h", "hashall")
		, Keyword ("k", "keyword")
		, Monitor ("m", "monitor")
		, Privileged ("p", "privileged")
		, Posix ("\u0000posix", "posix")
		, Emacs ("\u0000emacs", "emacs")
		, Vi ("\u0000vi", "vi")
		, IgnoreEof ("\u0000ignoreeof", "ignoreeof")
		, InteractiveComments ("\u0000interactive-comments", "interactive-comments")
		, NoLog ("\u0000nolog", "nolog")
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

	/** HISTSIZE or HISTFILE came from the environment (a shell that is not interactive keeps them) */
	private boolean historyFromEnvironment;
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
	// (names differ by case, except on Windows: A and a are two variables)
	private Map<String,Object> environmentVariables = System.getProperty("os.name", "").toLowerCase().startsWith("windows")
			? new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER) : new ConcurrentSkipListMap<>();
	DebugContext debugContext = new DebugContext();
	private int lastPid = 0;
	public JobManager jobManager = new JobManager();


	static {
		commands = new TreeMap<>();
		
		registerCommand(new Alias());
		registerCommand(new us.bringardner.fsh.commands.Logout());
		registerCommand(new us.bringardner.fsh.commands.Fc());

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
		registerCommand(new us.bringardner.fsh.commands.Hash());
		registerCommand(new us.bringardner.fsh.commands.Dot());
		registerCommand(new us.bringardner.fsh.commands.Builtin());
		registerCommand(new us.bringardner.fsh.commands.CommandCmd());
		registerCommand(new us.bringardner.fsh.commands.Caller());
		registerCommand(new us.bringardner.fsh.commands.BracketTest());
		registerCommand(new Unmount());
		registerCommand(new Unset());

		registerCommand(new Wait());
		registerCommand(new us.bringardner.fsh.commands.Compgen());
		registerCommand(new us.bringardner.fsh.commands.Complete());
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
					Console c = shell;
					if( c != null ) {
						c.osSignal(s.getNumber(), name);
					}
				});	
			} catch (Exception e) {
				if( !e.getLocalizedMessage().startsWith("Signal already used ")) {
					System_err.println(e.getLocalizedMessage());
				}
			}
		}
	}

	/**
	 * The shell program (not one inside another program) starts as bash does: in the directory
	 * it was started in, with HOME from the environment.
	 */
	private void startAsBash() {
		try {
			String home = System.getenv("HOME");
			if( home != null && !home.isEmpty()) {
				environmentVariables.put("HOME", home);
				homeDir = mountFactory.createFileSource(home);
			}
			FileSource start = mountFactory.createFileSource(System.getProperty("user.dir"));
			if( start.isDirectory()) {
				setCurrentDirectory(start);
			}
		} catch (IOException e) {
			// it stays in HOME
		}
	}

	/** the shell this program is (Console.main): signals for the process are its own; null when fsh runs inside another program */
	private static volatile Console shell;
	/** when SIGINT last came while a program had the terminal (System.nanoTime) */
	private static volatile long lastInterrupt;

	/**
	 * Ctrl-C was typed for a program that had the terminal since nanos (System.nanoTime). The
	 * program may end of it before the shell hears of it, so this waits a little for that.
	 */
	public static boolean interruptTypedSince(long nanos) {
		long until = System.currentTimeMillis()+250;
		while( true ) {
			long last = lastInterrupt;
			if( last != 0 && last-nanos >= 0 ) {
				return true;
			}
			if( System.currentTimeMillis() >= until ) {
				return false;
			}
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				return false;
			}
		}
	}

	/** signals with a trap, run when the command that is running is done (as bash runs them) */
	private final java.util.Queue<Integer> pendingSignals = new java.util.concurrent.ConcurrentLinkedQueue<>();

	public boolean hasPendingSignal() {
		return !pendingSignals.isEmpty();
	}

	/** a trap is set for the signal */
	public boolean hasTrap(int signum) {
		List<ConsoleSignalHandler> h = osSignalHandlers.get(signum);
		return h != null && !h.isEmpty();
	}

	/** the next signal that came (its trap not run yet), or -1 */
	public int nextPendingSignal() {
		Integer s = pendingSignals.poll();
		return s == null ? -1 : s;
	}

	/** the signal came: its trap runs when the shell is between commands */
	public void queueSignal(int signum) {
		pendingSignals.add(signum);
	}

	/** run the traps of the signals that came */
	public void runPendingTraps(ShellContext ctx) {
		Integer signum;
		while( (signum = pendingSignals.poll()) != null ) {
			runOsTrap(signum, ctx);
		}
	}

	/**
	 * A signal for this process. Interactive: Ctrl-C and Ctrl-Z are keys the shell reads, except
	 * while a program has the terminal; then they signal the program, and Ctrl-Z stops its job.
	 * Other signals for an interactive shell are ignored, as bash ignores INT, QUIT, TERM and TSTP,
	 * but SIGHUP ends it. A script runs its trap for the signal, or ends (128 + the signal).
	 */
	private void osSignal(int signum, String name) {
		if( name.equals("CHLD") || name.equals("WINCH") || name.equals("CONT") || name.equals("URG") || name.equals("INFO")) {
			return;
		}
		if( isInteractive && !name.equals("HUP")) {
			if( name.equals("INT")) {
				// (the program it ended may have given the terminal back already)
				lastInterrupt = System.nanoTime();
			} else if( NativeKeyboard.isTerminalLent()) {
				IJob job = foregroundJobs.peekLast();
				if( name.equals("TSTP") && job != null ) {
					// the program stopped (the terminal echoed ^Z): so does its job
					System_out.println();
					System_out.flush();
					Thread t = new Thread(() -> job.stopJob("Stopped"), "Ctrl-Z");
					t.setDaemon(true);
					t.start();
				}
			}
			return;
		}
		if( osSignalHandlers.containsKey(signum)) {
			pendingSignals.add(signum);
			return;
		}
		if( name.equals("TSTP") || name.equals("TTIN") || name.equals("TTOU")) {
			return;
		}
		if( isInteractive ) {
			// SIGHUP: as bash, the jobs get it too
			for(IJob job : jobManager.getJobs()) {
				if( !job.isIgnoreSignal(ConsoleSignal.Hup)) {
					job.signalJob(signum);
				}
			}
		}
		handleMetaSignal(ConsoleMetaSignal.Exit);
		System_out.flush();
		System_err.flush();
		System.exit(128+signum);
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

	/** export name of a variable with no value: exported when it gets one */
	public final java.util.Set<String> pendingExports = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** the variables commands running now were given (var=1 cmd), with what they will get back */
	public final java.util.Deque<List<Object[]>> temporaryAssignments = new java.util.concurrent.ConcurrentLinkedDeque<>();

	/** this thread runs a pipe stage or a background job (not the shell's own commands) */
	public static final ThreadLocal<Boolean> IN_COMMAND_THREAD = ThreadLocal.withInitial(() -> false);

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
			IN_COMMAND_THREAD.set(true);
			try {
				if( ctx.isIsolated()) {
					// a pipe stage: its own directory and options
					ctx.console.enterStage();
				}
				exitCode = task.run(ctx);
			} catch (Exception e) {
				error = e;
				// a stage or job that exits (exit 3, set -e) has that status; another error is 1
				// (return in a pipe stage ends that stage with its status, as in bash)
				exitCode = e instanceof ExitException ? ((ExitException) e).exitCode
						: e instanceof us.bringardner.fsh.signal.ReturnException r ? r.exitCode
						: e instanceof us.bringardner.fsh.signal.SignalException ? ((us.bringardner.fsh.signal.SignalException) e).exitCode() : 1;
			}
			if( terminatedBy != null ) {
				// ended by kill: 128 + the signal, as in bash (143 for TERM)
				exitCode = 128+terminatedBy;
			} else if( ctx.isIsolated()) {
				exitCode = ctx.console.endStage(ctx, exitCode);
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
		console.runLogout();
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
			shell = c;
			c.startAsBash();
			Runtime.getRuntime().addShutdownHook(new Thread(){
				@Override
				public void run(){
					c.saveHistory();
				}
			});

			// Dont't forget: TERM & QUIT both exit but QUIT dumps core and Java won't let us handle QUIT
			if( NativeKeyboard.inputIsPipeOrFile()
					|| (GraphicsEnvironment.isHeadless() && NativeKeyboard.isAvailible() && !NativeKeyboard.inputIsTerminal())) {
				// a pipe, a file or /dev/null: read as bash reads it, and shared with the programs it
				// runs (only a terminal is a keyboard; without one, the window is, unless headless)
				System_in = new ProcessStdin();
			}
			c.setStdIn(System_in);

			// called as sh (or with POSIXLY_CORRECT in the environment): posix mode, as bash's
			String argv0 = System.getProperty("fsh.argv0", "");
			String base = argv0.substring(argv0.lastIndexOf('/')+1);
			if( base.equals("sh") || base.equals("-sh") || System.getenv("POSIXLY_CORRECT") != null ) {
				c.setOption(Option.Posix, true);
			}

			int ret = c.execute(args);

			if( c.readsTypedCommands()) {
				c.setName("Console");
				c.setDaemon(false);
				c.start();
				while(c.isAlive()) {
					try {
						c.join(0);
					} catch (InterruptedException e) {
					}
				}
				c.runLogout();
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

	/** in is the process's standard input, and it is a terminal (the keyboard) */
	public static boolean isKeyboard(InputStream in) {
		return in == System_in && !(in instanceof ProcessStdin);
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

	/** started as a login shell (-l, --login): the profile files at the start, ~/.fsh_logout at the end */
	public boolean isLogin;
	/** where the commands come from, for $-: 'c' (-c), 's' (standard input) or 0 (a script file) */
	public char readsFrom;

	/** the script file the shell runs (BASH_SOURCE's main frame), or null (-c, standard input) */
	public String scriptFile;

	/**
	 * Start as bash starts with these arguments (see Invocation): the options, $0 and the
	 * positional parameters, interactive or not, the startup files; then run -c's command, the
	 * script file, or the commands on standard input (unless it is interactive: the caller then
	 * reads them, see run).
	 * @return the status
	 */
	public int execute(String ... args) {
		Invocation inv;
		try {
			inv = Invocation.parse(args);
		} catch (Invocation.Bad e) {
			stdErr.println("fsh: "+e.getMessage());
			if( e.getMessage().endsWith("invalid option")) {
				stdErr.println(Invocation.USAGE);
			}
			return 2;
		}
		if( inv.help || inv.version ) {
			stdOut.println("fsh, version "+VERSION);
			if( inv.help ) {
				stdOut.println(Invocation.USAGE);
			}
			return 0;
		}
		for(String [] o : inv.options) {
			boolean on = o[1].equals("-");
			if( o[0].startsWith("shopt ")) {
				executeQuietly("shopt "+(on ? "-s " : "-u ")+o[0].substring(6));
				continue;
			}
			Option option = Option.find(o[0]);
			if( option == Option.Unsupported || option == Option.Option ) {
				if( o[0].length() == 1 ) {
					stdErr.println("fsh: "+o[1]+o[0]+": invalid option");
					stdErr.println(Invocation.USAGE);
				} else {
					// as bash says it
					stdErr.println("fsh: line 0: fsh: "+o[0]+": invalid option name");
				}
				return 2;
			}
			setOption(option, on);
		}
		isLogin = inv.login;
		shellOptions.put("login_shell", isLogin);

		FshList params = new FshList();
		if( inv.command != null ) {
			// -c command [name [arguments]]: name is $0
			readsFrom = 'c';
			// (with no name: how the shell was called, if the launcher says so (fsh.argv0), as bash's argv[0])
			params.add(inv.args.isEmpty() ? System.getProperty("fsh.argv0", "fsh") : inv.args.get(0));
			for (int i = 1; i < inv.args.size(); i++) {
				params.add(inv.args.get(i));
			}
		} else if( inv.file != null ) {
			params.add(inv.file);
			params.addAll(inv.args);
			scriptFile = inv.file;
		} else {
			readsFrom = 's';
			params.add("fsh");
			params.addAll(inv.args);
		}
		setPositionalParameters(true, params);

		// interactive: -i, or commands from standard input that is the keyboard
		isInteractive = inv.interactive || (inv.command == null && inv.file == null && isKeyboard(stdIn));
		if( isInteractive ) {
			// history expansion (!!, !$ ...) and the history are on in an interactive shell, as in bash
			options.add(Option.HistExpand);
			options.add(Option.History);
			options.add(Option.Emacs);
			if( NativeKeyboard.terminal()) {
				// job control
				options.add(Option.Monitor);
			}
		} else {
			// as bash: a shell that is not interactive has no PS0-PS3, history variables or histchars
			for(Prompt p : new Prompt[] {Prompt.BeforeExecute, Prompt.Primary, Prompt.Secondary, Prompt.Select}) {
				if( !environmentVariables.containsKey(p.name)) {
					variables.remove(p.name);
				}
			}
			variables.remove(VARIABLE_HISTCHARS);
			if( !historyFromEnvironment ) {
				environmentVariables.remove(VARIABLE_HISTSIZE);
				environmentVariables.remove(VARIABLE_HISTFILE);
			}
		}
		runStartupFiles(inv);

		if( inv.command != null ) {
			inCommandString = true;
			try {
				return runCommands(inv.command);
			} finally {
				inCommandString = false;
			}
		}
		if( inv.file != null ) {
			return runFile(inv.file);
		}
		if( isInteractive ) {
			return lastExitCode;
		}
		return runStandardInput();
	}

	/** after execute: the shell goes on to read typed commands (run) */
	public boolean readsTypedCommands() {
		return isInteractive && readsFrom == 's' && !stopping;
	}

	/** the option letters of $-, in bash's order (h is always on; i and m in an interactive shell) */
	public String optionFlags() {
		StringBuilder ret = new StringBuilder();
		List<Option> on = getOptions();
		for(char c : "abefhikmnprtuvxBCEHPT".toCharArray()) {
			boolean set = switch (c) {
			case 'i' -> isInteractive;
			default -> {
				Option o = Option.find(String.valueOf(c));
				yield o != Option.Unsupported && on.contains(o);
			}
			};
			if( set ) {
				ret.append(c);
			}
		}
		if( readsFrom != 0 ) {
			ret.append(readsFrom);
		}
		return ret.toString();
	}

	/**
	 * The startup files, as bash reads its own: a login shell /etc/profile, then the first of
	 * ~/.fsh_profile, ~/.fsh_login and ~/.profile (not with --noprofile); an interactive shell
	 * that is not a login shell ~/.fshrc (or --rcfile's; not with --norc); a shell that is not
	 * interactive the file $FSH_ENV names.
	 */
	public void runStartupFiles(Invocation inv) {
		if( isLogin ) {
			if( !inv.noprofile ) {
				sourceStartup("/etc/profile");
				for(String name : new String[] {".fsh_profile", ".fsh_login", ".profile"}) {
					if( sourceStartup(home(name))) {
						break;
					}
				}
			}
		} else if( isInteractive ) {
			if( !inv.norc ) {
				if( inv.rcfile != null ) {
					sourceStartup(inv.rcfile);
				} else {
					loadProfile();
				}
			}
		} else {
			ShellContext sc = new ShellContext(this);
			Object env = sc.getVariable("FSH_ENV");
			if( env != null && !env.toString().isEmpty()) {
				String path;
				try {
					// as BASH_ENV: its value is expanded
					path = us.bringardner.fsh.exec.Executor.expandWord(sc, env.toString());
				} catch (Exception e) {
					path = env.toString();
				}
				sourceStartup(path);
			}
		}
	}

	/** a file in HOME */
	private String home(String name) {
		Object home = new ShellContext(this).getVariable("HOME");
		return (home == null ? System.getProperty("user.home") : home.toString())+"/"+name;
	}

	/** source path if it is a readable file; true if it is */
	private boolean sourceStartup(String path) {
		java.io.File f = new java.io.File(path);
		if( !f.isAbsolute()) {
			try {
				f = new java.io.File(getCurrentDirectory().getAbsolutePath(), path);
			} catch (Exception e) {
			}
		}
		if( !f.isFile() || !f.canRead()) {
			return false;
		}
		InputStream in = stdIn;
		boolean shared = isKeyboard(in) || in instanceof ProcessStdin || in instanceof SharedInput;
		if( !shared ) {
			// input that is not shared (an ssh channel): a program the file runs would take what
			// is typed meanwhile, so it has none
			stdIn = new java.io.ByteArrayInputStream(new byte[0]);
		}
		try {
			executeQuietly("source "+singleQuoted(f.getPath()));
		} finally {
			stdIn = in;
		}
		return true;
	}

	/** run code as part of starting or leaving (not a script: its end does not run the EXIT trap) */
	private void executeQuietly(String code) {
		executeDepth++;
		try {
			executeScript0(code, 1);
		} finally {
			executeDepth--;
		}
	}

	private static String singleQuoted(String s) {
		return "'"+s.replace("'", "'\\''")+"'";
	}

	/** a login shell is leaving: ~/.fsh_logout, once */
	public void runLogout() {
		// as bash: an interactive login shell, or a login shell's exit builtin
		if( isLogin && !loggedOut && (isInteractive || exitBuiltinRan)) {
			loggedOut = true;
			sourceStartup(home(".fsh_logout"));
		}
	}

	private boolean loggedOut;
	/** the exit builtin ended the shell */
	private volatile boolean exitBuiltinRan;

	/**
	 * fsh file: the file's commands, with $0 the file. As bash, a name with no / that is not in
	 * the current directory is looked for in PATH.
	 */
	private int runFile(String name) {
		try {
			ShellContext sc = new ShellContext(this);
			FileSource file = sc.getFileSource(name);
			if( !file.exists() && !name.contains("/")) {
				FileSource found = us.bringardner.fsh.exec.Programs.which(name, sc);
				if( found != null ) {
					file = found;
				}
			}
			java.io.File local = new java.io.File(name);
			if( !file.exists() && local.isAbsolute() && local.isFile()) {
				// the command line names a local file (the shell's files may be another file system)
				return runCommands(java.nio.file.Files.readString(local.toPath()));
			}
			if( !file.exists()) {
				stdErr.println("fsh: "+name+": No such file or directory");
				return 127;
			}
			if( file.isDirectory()) {
				stdErr.println("fsh: "+name+": Is a directory");
				return 126;
			}
			String code;
			try (InputStream in = file.getInputStream()) {
				code = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			}
			return runCommands(code);
		} catch (IOException e) {
			stdErr.println("fsh: "+name+": "+e.getMessage());
			return 126;
		}
	}

	/**
	 * Commands from standard input (not a terminal): read a line at a time, and each command run
	 * once it is whole, as bash does, so what the commands read from standard input is the
	 * lines after them.
	 */
	private int runStandardInput() {
		InputStream in = stdIn;
		return runCommands(() -> readRawLine(in));
	}

	/** -c's command, or a script's text: a command at a time, as bash runs them */
	private int runCommands(String text) {
		unterminated = !text.isEmpty() && !text.endsWith("\n");
		java.util.Iterator<String> lines = java.util.Arrays.asList(text.split("\n", -1)).iterator();
		if( text.endsWith("\n")) {
			// (no empty last line)
			java.util.List<String> all = new java.util.ArrayList<>(java.util.Arrays.asList(text.split("\n", -1)));
			all.remove(all.size()-1);
			lines = all.iterator();
		}
		java.util.Iterator<String> it = lines;
		return runCommands(() -> it.hasNext() ? it.next() : null);
	}

	/** true while -c's command runs (its syntax errors say -c:, as bash's) */
	public volatile boolean inCommandString;

	/**
	 * Run the commands of the lines next gives (null at the end), each once it is whole: a
	 * syntax error ends it (status 2), as it ends bash's script. The EXIT trap runs at the end.
	 */
	private int runCommands(java.util.function.Supplier<String> next) {
		commandsContext = null;
		executeDepth++;
		try {
			StringBuilder code = new StringBuilder();
			int line = 0;
			int first = 1;
			String ahead = null;
			while( !stopping ) {
				String l = ahead != null ? ahead : next.get();
				ahead = null;
				if( l == null ) {
					String rest = code.toString();
					if( rest.endsWith("\\\n") && (rest.length()-rest.replaceAll("\\\\+\n$", "").length()) % 2 == 0 ) {
						if( unterminated ) {
							// echo x\ with no newline after it: the backslash is a character, as in bash
							rest = rest.substring(0, rest.length()-1);
						} else {
							// a backslash-newline at the very end is dropped, as bash drops it
							rest = rest.substring(0, rest.length()-2)+"\n";
						}
					}
					if( !rest.isBlank()) {
						// (an unfinished command: its syntax error, status 2)
						int status = runChunk(rest, first);
						lastExitCode = status < 0 ? 2 : status;
					}
					break;
				}
				line++;
				if( code.length() == 0 && l.isBlank()) {
					first = line+1;
					continue;
				}
				if( !isInteractive && isOptionEnabled(Option.HistExpand) && isOptionEnabled(Option.History)
						&& (l.indexOf('!') >= 0 || l.startsWith("^")) && !us.bringardner.fsh.syntax.Parser.inHereDocument(code.toString())) {
					// set -H with the history on in a script: each line is history-expanded as it is read
					List<String> commands = new ArrayList<>();
					for(HistoryEntry e : history) {
						commands.add(e.command);
					}
					HistoryExpansion.Result r = HistoryExpansion.expand(l, commands, historyBase, isOptionEnabled(Option.Posix));
					if( commandsContext == null ) {
						commandsContext = scriptContext();
					}
					if( r.error != null ) {
						// (said, and the line is not run)
						commandsContext.line = line;
						commandsContext.error(r.error);
						lastExitCode = 1;
						if( code.length() == 0 ) {
							first = line+1;
						}
						continue;
					}
					if( r.changed ) {
						commandsContext.stderr.println(r.line);
						l = r.line;
						if( r.printOnly ) {
							rememberCommand(l);
							if( code.length() == 0 ) {
								first = line+1;
							}
							continue;
						}
					}
				}
				code.append(l);
				boolean continued = (l.length()-l.replaceAll("\\\\+$", "").length()) % 2 == 1;
				if( continued ) {
					// a backslash at the end: the next line goes on with it, but with none (and no
					// newline after it) the backslash is a character, as in bash
					ahead = next.get();
					if( ahead != null || !unterminated ) {
						code.append('\n');
					}
					if( ahead != null ) {
						// (not whole yet: the next line is part of it)
						continue;
					}
				} else {
					code.append('\n');
				}
				if( us.bringardner.fsh.syntax.Parser.isComplete(code.toString())) {
					int status = runChunk(code.toString(), first);
					if( status < 0 ) {
						// a syntax error ends the script
						lastExitCode = 2;
						break;
					}
					lastExitCode = status;
					code.setLength(0);
					first = line+1;
				}
			}
		} finally {
			if( --executeDepth == 0 ) {
				runExitTrap();
			}
		}
		return lastExitCode;
	}

	/** run a whole command; -1 if it has a syntax error (reported) */
	/** the context the commands of runCommands run in */
	private ShellContext commandsContext;

	private int runChunk(String code, int firstLine) {
		if( commandsContext == null ) {
			commandsContext = scriptContext();
		}
		try {
			us.bringardner.fsh.syntax.Parser.parse(code, firstLine);
		} catch (us.bringardner.fsh.syntax.SyntaxError e) {
			executeScript0(code, firstLine, commandsContext);
			return e.recoverable ? 1 : -1;
		}
		return executeScript0(code, firstLine, commandsContext);
	}

	/** the text's last line had no newline after it */
	private boolean unterminated;

	/** a line of in without its newline, read a byte at a time (nothing after it is taken); null at the end */
	private String readRawLine(InputStream in) {
		java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
		try {
			int b;
			while( (b = in.read()) >= 0 ) {
				if( b == '\n' ) {
					return line.toString(java.nio.charset.StandardCharsets.UTF_8);
				}
				line.write(b);
			}
		} catch (IOException e) {
		}
		unterminated = line.size() > 0;
		return line.size() > 0 ? line.toString(java.nio.charset.StandardCharsets.UTF_8) : null;
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
				String code = e.getKey()+" "+e.getValue();
				// only a function definition, and nothing after it (bash's fix for CVE-2014-6271)
				us.bringardner.fsh.syntax.Ast.Sequence seq = us.bringardner.fsh.syntax.Parser.parse(code);
				if( seq.items.size() != 1 || seq.items.get(0).background || seq.items.get(0).command.pipelines.size() != 1
						|| seq.items.get(0).command.pipelines.get(0).commands.size() != 1
						|| !(seq.items.get(0).command.pipelines.get(0).commands.get(0) instanceof us.bringardner.fsh.syntax.Ast.FunctionDef fd)
						|| !fd.name.equals(e.getKey())) {
					continue;
				}
				us.bringardner.fsh.exec.Executor.run(sc, code);
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
			Object v = ctx.exportedValue(e.getKey(), e.getValue());
			if( v != null ) {
				env.put(e.getKey(), ""+v);
			}
		}
		for(ShellFunction f : getFunctions().values()) {
			if( f.isExported() && !f.getName().contains("=")) {
				env.put("BASH_FUNC_"+f.getName()+"%%", exportedBody(f));
			}
		}
		return env;
	}

	/** "() {  first;\n second\n}": a function's body as bash puts it in the environment */
	public static String exportedBody(ShellFunction f) {
		if( f.exportedBody() != null ) {
			return f.exportedBody();
		}
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
		us.bringardner.fsh.syntax.Parser.posixMode = () -> isOptionEnabled(Option.Posix);
		// (fsh, unlike bash, expands aliases in scripts without shopt -s expand_aliases)
		us.bringardner.fsh.syntax.Parser.aliases = n -> {
			Object v = getAliases().get(n);
			return v == null ? null : v.toString();
		};
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
			// (the environment's, as bash keeps them)
			historyFromEnvironment = environmentVariables.containsKey(VARIABLE_HISTSIZE) || environmentVariables.containsKey(VARIABLE_HISTFILE);
			environmentVariables.putIfAbsent(VARIABLE_HISTSIZE, 500);
			environmentVariables.putIfAbsent(VARIABLE_HISTFILE, "~/.fsh_history");


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
			// getopts starts at 1 and says what is wrong, as bash's (OPTIND is an integer)
			variables.put("OPTIND", "1");
			setInteger("OPTIND", true);
			variables.put("OPTERR", "1");
			////Primary("PS1"),Secondary("PS2"),Select("PS3"),BeforeExecute("PS0"),EchoCommand("PS4");

			variables.put(Prompt.BeforeExecute.name, "");
			if( !environmentVariables.containsKey(Prompt.Primary.name)) {
				// (one from the environment stays, as in bash)
				variables.put(Prompt.Primary.name, "\\s-\\v\\$ ");
			}
			if( !environmentVariables.containsKey(Prompt.Secondary.name)) {
				// (one from the environment stays, as in bash)
				variables.put(Prompt.Secondary.name, "> ");
			}
			if( !environmentVariables.containsKey(Prompt.Select.name)) {
				// (one from the environment stays, as in bash)
				variables.put(Prompt.Select.name, "#? ");
			}
			if( !environmentVariables.containsKey(Prompt.EchoCommand.name)) {
				// (one from the environment stays, as in bash)
				variables.put(Prompt.EchoCommand.name, "+ ");
			}
			variables.put(VARIABLE_HISTCHARS, "!^#");
			positionalParameters.add("fsh");
			options.add(Option.DoBraceExpantion);
			options.add(Option.Hashall);
			options.add(Option.InteractiveComments);




		} catch (IOException e) {
		}
			importFunctions();
	}

	/** ~/.fshrc: what an interactive shell (not a login shell) runs at the start */
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
			if( file.exists()) {
				sourceStartup(file.getAbsolutePath());
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
		if( kb instanceof NativeKeyboard nk ) {
			// a typed backslash stays in the command, as in bash's line editor (the parser reads
			// it, and one at the end of a line asks for the next)
			nk.setHonorEscape(false);
			nk.setLineEditing(true);
		}
		stdOut = kb.getStdOut();
		stdErr = kb.getStdErr();
		stdIn =  kb.getStdIn();

		readHistory();
		started = running = true;
		watchKeyboard(kb);

		while(running && !stopping) {
			try {
				IJob job = readLineToJob(kb);
				int exitCode = waitForeground(job, true);
				setLastExitCode(exitCode);
				if( exitCode!=0 && isOptionEnabled(Option.ExitImediately)) {
					Console.exit(this,exitCode);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		NativeKeyboard.controlKeys = null;
		// as bash leaves: its stopped jobs get SIGHUP (and SIGCONT, so they see it)
		for(IJob job : jobManager.getJobs()) {
			if( job.getState() == JobState.Suspended && !job.isIgnoreSignal(ConsoleSignal.Hup)) {
				job.signalJob(1);
			}
		}
	}

	/** the jobs in the foreground: the one typed at the prompt, and what fg brought back (last) */
	private final java.util.Deque<IJob> foregroundJobs = new java.util.concurrent.ConcurrentLinkedDeque<>();

	/**
	 * Ctrl-C, Ctrl-Z and Ctrl-\ are keys the shell reads (not signals to it and every program it
	 * started): Ctrl-C ends the foreground job and Ctrl-Z stops it, as the terminal does it for
	 * bash. While a job runs, what is typed is read here, so they are seen when the job does not
	 * read the keyboard; the rest is kept for whoever reads next.
	 */
	private void watchKeyboard(KeyboardReader kb) {
		if( !(kb instanceof NativeKeyboard) || !NativeKeyboard.isAvailible()) {
			return;
		}
		NativeKeyboard.signalKeys(true);
		NativeKeyboard.controlKeys = key -> {
			IJob job = foregroundJobs.peekLast();
			if( job == null || state != ConsoleState.Executing ) {
				return false;
			}
			if( key == NativeKeyboard.CTRL_C ) {
				// as the terminal echoes it
				System.out.print("^C\n");
				System.out.flush();
				Thread t = new Thread(() -> job.signalJob(ProcessSignals.number("INT") < 0 ? 2 : ProcessSignals.number("INT")), "Ctrl-C");
				t.setDaemon(true);
				t.start();
			} else if( key == NativeKeyboard.CTRL_Z ) {
				System.out.print("^Z\n");
				System.out.flush();
				Thread t = new Thread(() -> job.stopJob("Stopped"), "Ctrl-Z");
				t.setDaemon(true);
				t.start();
			}
			// Ctrl-\ is ignored, as bash ignores SIGQUIT
			return true;
		};
		Thread watcher = new Thread(() -> {
			while( running && !stopping ) {
				if( state == ConsoleState.Executing ) {
					NativeKeyboard.pollTyped();
				}
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
			}
		}, "keyboard watcher");
		watcher.setDaemon(true);
		watcher.start();
	}

	/**
	 * Wait while job runs in the foreground: until it ends (its status) or is stopped (Ctrl-Z:
	 * 128 + SIGTSTP, and it goes into the job table if it was not there). typed: the command
	 * typed at the prompt (else fg brought it back).
	 */
	private int waitForeground(IJob job, boolean typed) {
		if( !foregroundJobs.contains(job)) {
			foregroundJobs.addLast(job);
		}
		try {
			while( true ) {
				JobState state = job.getState();
				if( state == JobState.Suspended ) {
					// the shell has the terminal again
					for(Object user : job.getTerminalUsers()) {
						NativeKeyboard.reclaimTerminal(user);
					}
					if( !jobManager.contains(job)) {
						addJob(job);
					}
					jobManager.touch(job);
					return ProcessSignals.stoppedStatus();
				}
				if( state == JobState.Termnated || state == JobState.Notified ) {
					break;
				}
				synchronized (jobStateLock) {
					try {
						jobStateLock.wait(50);
					} catch (InterruptedException e) {
					}
				}
			}
			// what it still does as it ends (Ctrl-C: the commands unwinding) comes before the prompt
			Thread t = job.getThread();
			if( t != null && t != Thread.currentThread()) {
				try {
					t.join(2000);
				} catch (InterruptedException e) {
				}
			}
			if( !typed ) {
				jobManager.remove(job);
			}
			return job.getExitCode();
		} finally {
			foregroundJobs.remove(job);
		}
	}

	/** what runs in ctx may read the keyboard: its job is in the foreground and not stopped */
	public boolean readsKeyboard(ShellContext ctx) {
		IJob job = ctx.job;
		if( job == null || !isInteractive ) {
			return true;
		}
		return job.getState() != JobState.Suspended && foregroundJobs.contains(job);
	}

	/** the command line exit was warned on (There are stopped jobs.), and the line before the current one */
	private IJob exitWarnedOn;
	/** the command line jobs ran on: exit after it does not warn of stopped jobs */
	private IJob jobsListedOn;

	public void jobsListed(IJob line) {
		jobsListedOn = line;
	}
	private IJob previousLine;
	private IJob currentLine;

	/**
	 * exit at the prompt: true (and it does not exit) if there are stopped jobs and the line
	 * before was not an exit that was warned of them, as in bash.
	 */
	public boolean stoppedJobsWarning(IJob line) {
		boolean stopped = false;
		for(IJob job : jobManager.getJobs()) {
			stopped |= job.getState() == JobState.Suspended;
		}
		if( !stopped || (previousLine != null && (exitWarnedOn == previousLine || jobsListedOn == previousLine))) {
			return false;
		}
		exitWarnedOn = line;
		return true;
	}

	/**
	 * fg: job runs in the foreground again; the shell waits for it.
	 * @return its status, or 128 + SIGTSTP if it is stopped again
	 */
	public int foreground(IJob job) {
		jobManager.touch(job);
		job.setStopNoticeDue(false);
		// its programs that had the terminal have it again
		for(Object user : job.getTerminalUsers()) {
			NativeKeyboard.lendTerminal(user);
		}
		if( job.getProcessGroup() != 0 && !job.getTerminalUsers().isEmpty()) {
			NativeKeyboard.giveTerminal(job.getProcessGroup());
		}
		job.continueJob();
		return waitForeground(job, false);
	}

	private final Object jobStateLock = new Object();

	private IJob readLineToJob(KeyboardReader kb) {
		IJob ret = null;

		while(ret == null) {


			state = ConsoleState.ReadLine;
			if(adminMessage!=null) {
				stdOut.println(adminMessage);
				adminMessage = null;
			}
			// as bash does before a prompt: [1]+  Done                    sleep 5
			for(String notice : jobManager.notices()) {
				stdOut.println(notice);
			}
			stdOut.flush();
			runPromptCommand();
			String code;
			try {
				code = readCommand(kb);
				boolean endOfInput = code == null;
				if( endOfInput ) {
					// end of input (Ctrl-D): leave like bash does
					code = "exit";
				}
				if( !endOfInput && isOptionEnabled(Option.History)) {
					rememberCommand(code);
				}
				code = code.stripLeading();
				if( !code.isEmpty()) {
					state = ConsoleState.Executing;

					String prompt = getPrompt(Prompt.BeforeExecute);
					if( prompt !=null && !prompt.isEmpty()) {
						stdOut.append(prompt);
					}



					if( isOptionEnabled(Option.PrintLinesAsRead)) {
						stdOut.println(getPrompt(Prompt.EchoCommand)+code);
					}

					ShellContext sc = new ShellContext(this);

					ret = new ForgroundJob(sc,code);
					commandNumber++;
					previousLine = currentLine;
					currentLine = ret;
					ret.addJobStateChangeListner((job,from,to)->{
						synchronized (jobStateLock) {
							jobStateLock.notifyAll();
						}
					});
					// Ctrl-C and Ctrl-Z reach it from the start
					foregroundJobs.addLast(ret);
					ret.start();
				}

			} catch (NativeKeyboard.LineCancelled e) {
				// Ctrl-C at the prompt
				setLastExitCode(130);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}


	/*
	 * 1/16
	 * 308l stainless
	 * er70s-6 
	 * alum er4043 er5356
	 *  
	 * 
	 */

	public String getPrompt(Prompt prompt) {
		String ret = "";
		Object val = getVariable(prompt.name);
		if( val !=null ) {
			try {
				ret = expandPrompt(new ShellContext(this), ""+val);
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

	/** a prompt string's backslash escapes (\\u, \\w, \\$ ...), as bash expands them */
	public String expandPrompt(String val,Date date) {
		return promptEscapes(val, date == null ? new Date() : date, false);
	}

	/**
	 * A prompt string as bash expands it (PS0, PS1, PS2, PS3, ${x@P}): the backslash escapes,
	 * then parameter, command and arithmetic expansion (what the escapes give is not expanded).
	 */
	public String expandPrompt(ShellContext sc, String val) {
		if( !Boolean.TRUE.equals(shellOptions.get("promptvars"))) {
			return promptEscapes(val, new Date(), false);
		}
		String escaped = promptEscapes(val, new Date(), true);
		try {
			return us.bringardner.fsh.exec.Executor.expandWord(sc, escaped);
		} catch (RuntimeException e) {
			return promptEscapes(val, new Date(), false);
		}
	}

	/**
	 * Before each primary prompt: PROMPT_COMMAND's commands (each element of it, if it is an
	 * array), as bash runs them; $? stays the last command's.
	 */
	private void runPromptCommand() {
		Object pc = new ShellContext(this).getVariable("PROMPT_COMMAND");
		if( pc == null ) {
			return;
		}
		java.util.List<String> commands = new java.util.ArrayList<>();
		if( pc instanceof java.util.Collection<?> list ) {
			for(Object o : list) {
				commands.add(String.valueOf(o));
			}
		} else if( pc instanceof java.util.Map<?,?> map ) {
			for(Object o : map.values()) {
				commands.add(String.valueOf(o));
			}
		} else {
			commands.add(pc.toString());
		}
		int status = lastExitCode;
		for(String c : commands) {
			if( !c.isBlank()) {
				executeQuietly(c);
				setLastExitCode(status);
			}
		}
	}

	/** the number of the command typed next (\\#) */
	private int commandNumber = 1;

	private String promptEscapes(String val, Date date, boolean quote) {
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
				int mark = ret.length();

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
						// an empty format is the locale's time (%X)
						String fmt = strftimeToJava(tmp.length() == 0 ? "%X" : tmp.toString());
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
				case 'h': {
					String host = getLocalHostName();
					int dot = host.indexOf('.');
					ret.append(dot > 0 ? host.substring(0, dot) : host);
				}

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
				case 'l':ret.append(NativeKeyboard.ttyName()); 
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
						String zero = ""+p;
						ret.append(zero.substring(zero.lastIndexOf('/')+1));
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
				case 'W': {
					ShellContext vars = new ShellContext(this);
					String pwd = ""+vars.getVariable(VARIABLE_PWD);
					Object homeVar = vars.getVariable("HOME");
					String home = homeVar == null ? "" : homeVar.toString();
					if( next == 'W' ) {
						// its last part; ~ for HOME itself
						if( !home.isEmpty() && pwd.equals(home)) {
							ret.append("~");
						} else if( pwd.equals("/")) {
							ret.append("/");
						} else {
							ret.append(pwd.substring(pwd.lastIndexOf('/')+1));
						}
					} else {
						ret.append(dirTrim(abbreviateHome(pwd, home), vars.getVariable("PROMPT_DIRTRIM")));
					}
				}

					break;

					// \!	The history number of this command.
				case '!':
					ret.append(""+(history.size()+1));
					break;
					// \#	The command number of this command.
				case '#':
					ret.append(""+commandNumber);
					break;
					// \$ If the effective uid is 0, #, otherwise $.
				case '$':
					ret.append("root".equals(System.getProperty("user.name")) ? "#" : "$");
					break;
					// \nnn	The character whose ASCII code is the octal value nnn.
					// \\ A backslash.
				case '\\':
					ret.append("\\");
					break;

					// \[Begin a sequence of non-printing characters. This could be used to embed a terminal control sequence into the prompt.
					// \]End a sequence of non-printing characters.

				case '[':
				case ']':
					// they only mark where the terminal shows nothing
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

				if( quote ) {
					// what an escape gives is not expanded afterward
					String piece = ret.substring(mark);
					ret.setLength(mark);
					for(char ch : piece.toCharArray()) {
						if( ch == '\\' || ch == '$' || ch == '`' || ch == '"' ) {
							ret.append('\\');
						}
						ret.append(ch);
					}
				}
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}

	/** PROMPT_DIRTRIM=n: \\w keeps the last n directories, after ... */
	private static String dirTrim(String path, Object trim) {
		int n;
		try {
			n = trim == null ? 0 : Integer.parseInt(trim.toString().trim());
		} catch (NumberFormatException e) {
			n = 0;
		}
		if( n <= 0 ) {
			return path;
		}
		String head = path.startsWith("~") ? "~/" : "";
		String rest = path.substring(head.length() == 0 ? 0 : 1);
		String [] parts = rest.split("/");
		java.util.List<String> dirs = new java.util.ArrayList<>();
		for(String part : parts) {
			if( !part.isEmpty()) {
				dirs.add(part);
			}
		}
		if( dirs.size() <= n ) {
			return path;
		}
		return head+".../"+String.join("/", dirs.subList(dirs.size()-n, dirs.size()));
	}

	/** the job with process id id, or else the job numbered id */
	public IJob findJob(int id) {
		IJob ret = jobManager.getJobByPid(id);
		return ret != null ? ret : jobManager.getJob(id);
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

	/**
	 * One command as it is typed: PS1, then PS2 for each further line it needs (an if with no fi
	 * yet, an open quote, a here-document, a trailing | or \), each line history-expanded
	 * first (set -H). null at the end of the input; "" if there is nothing to run (a blank
	 * line, a history error, :p).
	 */
	public String readCommand(KeyboardReader kb) throws IOException {
		StringBuilder code = new StringBuilder();
		boolean first = true;
		while( true ) {
			kb.setPrompt(getPrompt(first ? Prompt.Primary : Prompt.Secondary));
			String line = kb.readLine(this);
			if( line == null ) {
				if( first ) {
					return null;
				}
				stdErr.println("fsh: syntax error: unexpected end of file");
				return "";
			}
			if( isOptionEnabled(Option.HistExpand)) {
				List<String> commands = new ArrayList<>();
				for(HistoryEntry e : history) {
					commands.add(e.command);
				}
				HistoryExpansion.Result r = HistoryExpansion.expand(line, commands, historyBase);
				if( r.error != null ) {
					stdErr.println("fsh: "+r.error);
					return "";
				}
				if( r.changed ) {
					// as bash shows it (on standard error)
					stdErr.println(r.line);
				}
				if( r.printOnly ) {
					rememberCommand(r.line);
					return "";
				}
				line = r.line;
			}
			if( code.length() > 0 ) {
				code.append('\n');
			}
			code.append(line);
			if( code.toString().isBlank() || us.bringardner.fsh.syntax.Parser.isComplete(code.toString())) {
				return code.toString();
			}
			first = false;
		}
	}

	/**
	 * A typed command goes into the history, as bash keeps it: HISTCONTROL (ignorespace,
	 * ignoredups, ignoreboth, erasedups) and HISTIGNORE (patterns separated by :) leave some
	 * out; a command of several lines is one entry, its lines joined by ; or a space where that
	 * means the same.
	 */
	public void rememberCommand(String typed) {
		if( typed == null || typed.isBlank()) {
			return;
		}
		String control = String.valueOf(shellOrEnvironment("HISTCONTROL"));
		boolean ignoreSpace = control.contains("ignorespace") || control.contains("ignoreboth");
		boolean ignoreDups = control.contains("ignoredups") || control.contains("ignoreboth");
		if( ignoreSpace && (typed.startsWith(" ") || typed.startsWith("\t"))) {
			return;
		}
		// (as typed: its blanks are kept, only a newline at its end goes)
		String entry = historyLine(typed.endsWith("\n") ? typed.substring(0, typed.length()-1) : typed);
		historyLastLineAdded = false;
		Object ignore = shellOrEnvironment("HISTIGNORE");
		if( ignore != null && !ignore.toString().isEmpty()) {
			for(String pattern : ignore.toString().split(":")) {
				if( pattern.equals("&") ? !history.isEmpty() && history.get(history.size()-1).command.equals(entry)
						: !pattern.isEmpty() && GlobPattern.compile(pattern.replace("\\&", "&")).matches(entry)) {
					// (& is the line before)
					return;
				}
			}
		}
		if( ignoreDups && !history.isEmpty() && history.get(history.size()-1).command.equals(entry)) {
			return;
		}
		if( control.contains("erasedups")) {
			history.removeIf(e -> e.command.equals(entry));
		}
		addHistory(entry);
	}

	/** a command of several lines as one history line: ; or a space between them where that means the same */
	static String historyLine(String code) {
		if( code.indexOf('\n') < 0 ) {
			return code;
		}
		String meaning;
		try {
			meaning = us.bringardner.fsh.syntax.AstPrinter.print(us.bringardner.fsh.syntax.Parser.parse(code));
		} catch (RuntimeException e) {
			return code;
		}
		String ret = code;
		int at = ret.indexOf('\n');
		while( at >= 0 ) {
			String joined = null;
			for(String sep : new String[] {"; ", " "}) {
				String candidate = ret.substring(0, at)+sep+ret.substring(at+1);
				try {
					if( meaning.equals(us.bringardner.fsh.syntax.AstPrinter.print(us.bringardner.fsh.syntax.Parser.parse(candidate)))) {
						joined = candidate;
						break;
					}
				} catch (RuntimeException e) {
				}
			}
			if( joined != null ) {
				ret = joined;
				at = ret.indexOf('\n');
			} else {
				at = ret.indexOf('\n', at+1);
			}
		}
		return ret;
	}

	public void addHistory(String code) {
		history.add(new HistoryEntry(code));
		historyLinesThisSession++;
		historyLastLineAdded = true;
		truncateHistory();
	}

	/** the number of the first entry (it grows as HISTSIZE drops the oldest) */
	public int historyBase = 1;
	/** entries added since the history file was last read or written (history -a writes them) */
	public int historyLinesThisSession;
	/** the lines of the history file read (history -n reads those after them) */
	public int historyLinesInFile;
	/** the command line just read went into the history (history -s and -p take it out again) */
	public boolean historyLastLineAdded;

	/** history -s, history -p: the line they were on is not kept */
	public void deleteLastHistory() {
		if( !history.isEmpty()) {
			history.remove(history.size()-1);
			if( historyLinesThisSession > 0 ) {
				historyLinesThisSession--;
			}
		}
	}

	/** HISTSIZE (a shell variable, or the environment's), or -1: no limit */
	private int historySize() {
		Object v = getVariable(VARIABLE_HISTSIZE);
		if( v == null ) {
			v = environmentVariables.get(VARIABLE_HISTSIZE);
		}
		if( v == null ) {
			return -1;
		}
		try {
			int n = Integer.parseInt(v.toString().trim());
			return n < 0 ? -1 : n;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void truncateHistory() {
		int max = historySize();
		while( max >= 0 && history.size() > max ) {
			history.remove(0);
			historyBase++;
		}
	}

	/** a variable of the shell, or else of its environment (an exported one a parent gave) */
	public Object shellOrEnvironment(String name) {
		Object v = getVariable(name);
		return v != null ? v : environmentVariables.get(name);
	}

	/** $HISTFILE (a shell variable, or the environment's), or null */
	public String historyFile() {
		Object v = getVariable(VARIABLE_HISTFILE);
		if( v == null ) {
			v = environmentVariables.get(VARIABLE_HISTFILE);
		}
		return v == null || v.toString().isEmpty() ? null : v.toString();
	}

	/** set -o history in a script: the history file is read (once), as bash does */
	public void loadHistory() {
		String f = historyFile();
		if( f != null ) {
			historyLinesInFile = readHistoryFile(f, 0);
		}
	}

	/**
	 * The lines of a history file from line skip on, added to the history ("#time" lines are the
	 * times of the next ones). @return how many lines the file has
	 */
	public int readHistoryFile(String fileName, int skip) {
		try {
			FileSource file = createFileSource(expandHome(fileName));
			if( !file.exists() || file.isDirectory()) {
				return 0;
			}
			char comment = historyComment();
			String [] lines;
			try(InputStream in = file.getInputStream()) {
				lines = new String(in.readAllBytes()).split("\n");
			}
			long time = System.currentTimeMillis();
			int count = 0;
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				if( line.length() > 1 && line.charAt(0) == comment && Character.isDigit(line.charAt(1))) {
					Long stamp = parseHistoryTime(line.substring(1));
					if( stamp != null ) {
						time = stamp;
						continue;
					}
				}
				count++;
				if( count <= skip || line.isEmpty()) {
					continue;
				}
				history.add(new HistoryEntry(time, true, line));
			}
			truncateHistory();
			return count;
		} catch (IOException e) {
			logError("read history", e);
			return 0;
		}
	}

	/**
	 * The last count entries (all of them if count is -1) written to a history file, after what is
	 * there (append) or in place of it, with "#time" lines when HISTTIMEFORMAT is set (as bash).
	 */
	public boolean writeHistoryFile(String fileName, int count, boolean append) {
		try {
			FileSource file = createFileSource(expandHome(fileName));
			boolean times = getVariable(VARIABLE_HISTTIMEFORMAT) != null;
			char comment = historyComment();
			int from = count < 0 ? 0 : Math.max(0, history.size()-count);
			try(OutputStream out = file.getOutputStream(append)) {
				for (int i = from; i < history.size(); i++) {
					HistoryEntry e = history.get(i);
					if( times ) {
						out.write((""+comment+(e.time/1000)+"\n").getBytes());
					}
					out.write((e.command+"\n").getBytes());
				}
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private char historyComment() {
		Object chars = getVariable(VARIABLE_HISTCHARS);
		return chars != null && chars.toString().length() > 2 ? chars.toString().charAt(2) : '#';
	}

	/**
	 * Read history... any errors are ignored.
	 */
	public void readHistory() {
		String fileName = historyFile();
		if( fileName == null ) {
			// (as bash: no HISTFILE, no history file)
			return;
		}
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
		String fileName = historyFile();
		if( fileName == null ) {
			// (as bash: no HISTFILE, no history file)
			return;
		}
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
		private final List<String> processSettings;
		private final List<String> dirStack;
		private final Integer umask;
		private final Map<String,Object []> hashTable;
		/** attributes: a subshell's readonly, -i, -l/-u and readonly functions are its own */
		private final java.util.Set<String> readonly, integer, readonlyFunctions, declaredUnset, pendingExports;
		private final Map<String,Character> cases;

		private Snapshot(Console c) throws IOException {
			readonly = new java.util.HashSet<>(c.readonlyVariables);
			integer = new java.util.HashSet<>(c.integerVariables);
			readonlyFunctions = new java.util.HashSet<>(c.readonlyFunctions);
			declaredUnset = new java.util.HashSet<>(c.declaredUnset);
			pendingExports = new java.util.HashSet<>(c.pendingExports);
			cases = new java.util.HashMap<>(c.caseVariables);
			processSettings = new ArrayList<>(c.processSettings);
			dirStack = new ArrayList<>(c.dirStack);
			umask = c.umask;
			hashTable = new java.util.LinkedHashMap<>(c.hashTable);
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
		// (umask, ulimit and hash in a subshell are its own)
		processSettings.clear();
		processSettings.addAll(s.processSettings);
		readonlyVariables.clear();
		readonlyVariables.addAll(s.readonly);
		integerVariables.clear();
		integerVariables.addAll(s.integer);
		readonlyFunctions.clear();
		readonlyFunctions.addAll(s.readonlyFunctions);
		declaredUnset.clear();
		declaredUnset.addAll(s.declaredUnset);
		pendingExports.clear();
		pendingExports.addAll(s.pendingExports);
		caseVariables.clear();
		caseVariables.putAll(s.cases);
		dirStack.clear();
		dirStack.addAll(s.dirStack);
		umask = s.umask;
		hashTable.clear();
		hashTable.putAll(s.hashTable);
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
		/** the stage's own EXIT trap (it does not change the shell's) */
		List<String> exitTrap;
		boolean ownExitTrap;
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
	public void changeDirectory(FileSource dir) throws IOException {
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
		if( o == Option.Posix && enable ) {
			// as bash: posix mode turns on expand_aliases
			getShellOptions().put("expand_aliases", true);
		}
		if( o == Option.History && enable && !isInteractive && historyLinesThisSession == 0 && !optionList().contains(o)) {
			// set -o history in a script: the history file is read, as bash's
			loadHistory();
		}
		if( o == Option.IgnoreEof ) {
			// as bash: set -o ignoreeof is IGNOREEOF=10
			if( enable ) {
				variables.put("IGNOREEOF", "10");
			} else {
				variables.remove("IGNOREEOF");
			}
		}
		if( !enable ) {
			optionList().remove(o);
		} else if(!optionList().contains(o)) {
			optionList().add(o);
		}
	}

	/** an option on or off, without what setOption does besides */
	public void enableOptionQuietly(Option o, boolean enable) {
		if( !enable ) {
			optionList().remove(o);
		} else if( !optionList().contains(o)) {
			optionList().add(o);
		}
	}

	/** the set -o options on now (local - keeps them, to put back) */
	public List<Option> snapshotOptions() {
		return new ArrayList<>(optionList());
	}

	/** set -o options as they were (a function with local - returns) */
	public void restoreOptions(List<Option> saved) {
		for(Option o : Option.values()) {
			boolean want = saved.contains(o);
			if( want != optionList().contains(o)) {
				setOption(o, want);
			}
		}
	}

	/** $SHELLOPTS: the set -o options that are on, by name, : between */
	public String shellOpts() {
		java.util.TreeSet<String> on = new java.util.TreeSet<>();
		for(Option o : optionList()) {
			if( o.longName.length() > 1 && Character.isLowerCase(o.longName.charAt(0)) && o != Option.KeyboardEcho && o != Option.VerboseError ) {
				on.add(o.longName);
			}
		}
		return String.join(":", on);
	}

	/** $BASHOPTS: the shopt options that are on, : between */
	public String bashOpts() {
		java.util.TreeSet<String> on = new java.util.TreeSet<>();
		for(Map.Entry<String, Boolean> e : getShellOptions().entrySet()) {
			if( Boolean.TRUE.equals(e.getValue())) {
				on.add(e.getKey());
			}
		}
		return String.join(":", on);
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

	/** complete's specs, by command (-D, -E, -I for the default, an empty line, a command's name) */
	private final Map<String, Completion.Spec> completions = new java.util.TreeMap<>();

	public Map<String, Completion.Spec> getCompletions() {
		return completions;
	}

	/** the spec for command (its path, then its name), else complete -D's, or null */
	public Completion.Spec getCompletion(String command) {
		Completion.Spec ret = completions.get(command);
		if( ret == null && command.contains("/")) {
			ret = completions.get(command.substring(command.lastIndexOf('/')+1));
		}
		return ret != null ? ret : completions.get("-D");
	}

	/** shopt's options, by name */
	private final Map<String,Boolean> shellOptions = new ConcurrentHashMap<>();
	{
		// bash's, with bash's defaults
		for(String n : new String[] {"array_expand_once", "assoc_expand_once", "autocd", "bash_source_fullpath",
				"cdable_vars", "cdspell", "checkhash", "checkjobs", "compat31", "compat32", "compat40", "compat41",
				"compat42", "compat43", "compat44", "direxpand", "dirspell", "dotglob", "execfail", "expand_aliases",
				"extdebug", "extglob", "failglob", "globstar", "gnu_errfmt", "histappend", "histreedit", "histverify",
				"huponexit", "inherit_errexit", "lastpipe", "lithist", "localvar_inherit", "localvar_unset",
				"mailwarn", "no_empty_cmd_completion", "nocaseglob", "nocasematch", "noexpand_translation", "nullglob",
				"progcomp_alias", "restricted_shell", "shift_verbose", "varredir_close", "xpg_echo"}) {
			shellOptions.put(n, false);
		}
		for(String n : new String[] {"checkwinsize", "cmdhist", "complete_fullquote", "extquote", "force_fignore",
				"globasciiranges", "globskipdots", "hostcomplete", "interactive_comments", "patsub_replacement",
				"progcomp", "sourcepath"}) {
			shellOptions.put(n, true);
		}
		// set by how the shell started (-l)
		shellOptions.put("login_shell", false);
		// a prompt's $x, $(cmd) and $((n)) are expanded
		shellOptions.put("promptvars", true);
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

	/** {OPTIND, the letter in that word} getopts is at */
	public int[] getoptsState() {
		return new int[] {getoptsIndex, getoptsPos};
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
		// (SHELLOPTS and BASHOPTS: set -o and shopt change them)
		return readonlyVariables.contains(name) || (name.equals("SHELLOPTS") || name.equals("BASHOPTS")) && !unsetSpecials.contains(name);
	}

	public void setReadonly(String name) {
		readonlyVariables.add(name);
	}

	/** a function's readonly local is gone: its name is not readonly (unless it was before) */
	public void clearReadonly(String name) {
		readonlyVariables.remove(name);
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

	/** hash: name -> {path, int[] {hits}}, the programs found on PATH (PATH=... forgets them) */
	public final Map<String,Object []> hashTable = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

	/** RANDOM, SECONDS ... after unset: ordinary variables from then on, as in bash */
	public final java.util.Set<String> unsetSpecials = ConcurrentHashMap.newKeySet();

	/** bash's generator for $RANDOM (the minimal standard one), so RANDOM=n gives what bash gives */
	private long randomSeed = (System.nanoTime() ^ ProcessHandle.current().pid()) & 0x7fffffffL;
	private int lastRandom;

	public synchronized void seedRandom(long seed) {
		randomSeed = seed & 0xffffffffL;
		lastRandom = 0;
	}

	/** the next $RANDOM: 0-32767, never the same twice in a row */
	public synchronized int random() {
		int ret;
		do {
			long r = randomSeed == 0 ? 123459876 : randomSeed;
			long h = r/127773;
			long l = r%127773;
			long t = 16807*l-2836*h;
			randomSeed = t < 0 ? t+0x7fffffff : t;
			ret = (int) (((randomSeed >> 16) ^ (randomSeed & 65535)) & 32767);
		} while( ret == lastRandom );
		lastRandom = ret;
		return ret;
	}

	/** $SECONDS counts from here (SECONDS=n moves it) */
	private volatile long secondsStart = System.currentTimeMillis();

	public long seconds() {
		return (System.currentTimeMillis()-secondsStart)/1000;
	}

	public void setSeconds(long n) {
		secondsStart = System.currentTimeMillis()-n*1000;
	}

	/** declare x, declare -a a: names declared with no value (unset takes them off) */
	public final java.util.Set<String> declaredUnset = ConcurrentHashMap.newKeySet();

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
		if( home != null && home.length() > 1 && home.endsWith("/")) {
			home = home.substring(0, home.length()-1);
		}
		// (HOME=/ abbreviates nothing, as in bash)
		if( home == null || home.isEmpty() || home.equals("/") || !(pwd.equals(home) || pwd.startsWith(home+"/"))) {
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
		StageState st = stage.get();
		if( signal == ConsoleMetaSignal.Exit && st != null ) {
			st.ownExitTrap = true;
			st.exitTrap = action == null ? null : new CopyOnWriteArrayList<>(List.of(action));
			return;
		}
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
		int savedLineDepth = ctx.trapLineDepth;
		if( ctx.trapLine == null || ctx.functionDepth() > ctx.trapLineDepth ) {
			ctx.trapLine = ctx.currentLine();
			ctx.trapLineDepth = ctx.functionDepth();
		}
		// $BASH_TRAPSIG: the signal's number while its trap runs
		Object savedSig = ctx.getVariable("BASH_TRAPSIG");
		ctx.setVariable("BASH_TRAPSIG", String.valueOf(signum));
		Integer savedTrapStatus = ctx.trapStatus;
		int savedTrapDepth = ctx.trapFunctionDepth;
		ctx.trapStatus = saved;
		ctx.trapFunctionDepth = ctx.functionDepth();
		try {
			runCode(ctx, action);
		} catch (us.bringardner.fsh.signal.FshException e) {
			throw e;
		} catch (Exception e) {
			ctx.stderr.println(e.getMessage());
		} finally {
			setLastExitCode(saved);
			ctx.trapLine = savedLine;
			ctx.trapLineDepth = savedLineDepth;
			ctx.currentCommand = savedCommand;
			ctx.setVariable("BASH_TRAPSIG", savedSig);
			ctx.trapStatus = savedTrapStatus;
			ctx.trapFunctionDepth = savedTrapDepth;
		}
		return true;
	}

	/** the traps, as trap -p prints them: {name, action}, EXIT first, then by signal number */
	public List<String[]> traps() {
		List<String[]> ret = new ArrayList<>();
		StageState st = stage.get();
		java.util.function.BiConsumer<ConsoleMetaSignal,String> meta = (s, name) -> {
			List<String> a = s == ConsoleMetaSignal.Exit && st != null && st.ownExitTrap ? st.exitTrap : signalHandlers.get(s);
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
	 * @return the trap's status (0 if it did not run)
	 */
	public int runTrap(ConsoleMetaSignal signal, ShellContext ctx) {
		List<String> actions = signalHandlers.get(signal);
		if( actions == null || actions.isEmpty() || inProcess.contains(signal)) {
			return 0;
		}
		if( signal == ConsoleMetaSignal.Return && inProcess.contains(ConsoleMetaSignal.Debug)) {
			// (not for the functions the DEBUG trap calls, as bash's)
			return 0;
		}
		int saved = getLastExitCode();
		int status = 0;
		inProcess.push(signal);
		Integer savedLine = ctx.trapLine;
		String savedCommand = ctx.currentCommand;
		// (the line the shell is on: the command the trap ran for goes on with it)
		int shellLine = ctx.line;
		int savedLineDepth = ctx.trapLineDepth;
		if( ctx.trapLine == null || ctx.functionDepth() > ctx.trapLineDepth ) {
			ctx.trapLine = ctx.currentLine();
			ctx.trapLineDepth = ctx.functionDepth();
		}
		try {
			for(String code : actions) {
				runCode(ctx, code);
			}
			status = getLastExitCode();
		} catch (us.bringardner.fsh.signal.FshException e) {
			throw e;
		} catch (Exception e) {
			ctx.stderr.println(e.getMessage());
		} finally {
			inProcess.pop();
			setLastExitCode(saved);
			ctx.trapLine = savedLine;
			ctx.trapLineDepth = savedLineDepth;
			ctx.currentCommand = savedCommand;
			ctx.line = shellLine;
		}
		return status;
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
			IJob job = foregroundJobs.peekLast();
			if( job !=null) {
				job.handleSignal(signal);
			}

		}



	}	


	public void handleSignal(int pid,ConsoleSignal signal)  {
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
		// (set -x shows a trap's commands one level in, as bash's)
		ctx.substitutionLevel++;
		String builtin = ctx.builtin;
		ctx.builtin = null;
		try {
			return us.bringardner.fsh.exec.Executor.run(ctx, code);
		} finally {
			ctx.substitutionLevel--;
			ctx.builtin = builtin;
		}
	}

	public int executeScript(ShellContext sc,String code)  {
		int ret = 0;
		try {

			if( isOptionEnabled(Option.PrintLinesAsRead)) {
				sc.stdout.println(getPrompt(Prompt.EchoCommand)+code);
			}

			code = code.stripLeading();
			return us.bringardner.fsh.exec.Executor.script(sc, code);

		} catch(us.bringardner.fsh.signal.SignalException e) {
			// Ctrl-C or kill ended the job
			return e.exitCode();
		} catch(ExitException e) {
			ret = e.exitCode;
			handleMetaSignal(ConsoleMetaSignal.Exit);
			if( e.message != null ) {
				sc.stderr.println(e);
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
			sc.stderr.println(e.getMessage() != null ? e.getMessage() : e.toString());
			if( isOptionEnabled(Option.VerboseError)) {
				logError("", e);
			}
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

	public int endSubshellTrap(List<String> shells, int status) {
		int ret = status;
		List<String> actions = signalHandlers.remove(ConsoleMetaSignal.Exit);
		if( actions != null ) {
			// (exit in it ends just the subshell, with its status)
			setLastExitCode(status);
			for(String action : actions) {
				try {
					us.bringardner.fsh.exec.Executor.run(new ShellContext(this), action);
				} catch (ExitException e) {
					ret = e.exitCode;
					break;
				} catch (Exception e) {
					getStdErr().println(e.getMessage());
				}
			}
		}
		if( shells != null ) {
			signalHandlers.put(ConsoleMetaSignal.Exit, shells);
		}
		return ret;
	}

	/** a pipe stage ends: the EXIT trap it set runs (an exit in it gives the stage's status) */
	public int endStage(ShellContext ctx, int status) {
		StageState st = stage.get();
		if( st == null || st.exitTrap == null ) {
			return status;
		}
		List<String> actions = st.exitTrap;
		st.exitTrap = null;
		int ret = status;
		setLastExitCode(status);
		for(String action : actions) {
			try {
				us.bringardner.fsh.exec.Executor.run(ctx, action);
			} catch (ExitException e) {
				ret = e.exitCode;
				break;
			} catch (Exception e) {
				getStdErr().println(e.getMessage());
			}
		}
		return ret;
	}

	/**
	 * exit: the EXIT trap runs there (in a function, $FUNCNAME is it), as bash's.
	 * @return the status to exit with (an exit in the trap gives its own)
	 */
	public int runExitTrapAt(ShellContext ctx, int status) {
		List<String> actions = signalHandlers.remove(ConsoleMetaSignal.Exit);
		if( actions == null ) {
			return status;
		}
		setLastExitCode(status);
		for(String action : actions) {
			try {
				us.bringardner.fsh.exec.Executor.run(ctx, action);
			} catch (ExitException e) {
				return e.exitCode;
			} catch (Exception e) {
				getStdErr().println(e.getMessage());
			}
		}
		return status;
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
		return executeScript0(code, 1);
	}

	/** a new context for a script (its standard input, output and error the console's) */
	private ShellContext scriptContext() {
		ShellContext sc = new ShellContext(this);
		sc.stdin = getStdIn();
		sc.stdout = getStdOut();
		sc.stderr = getStdErr();
		if( isKeyboard(sc.stdin)) {
			sc.stdin = new NativeKeyboard();
		}
		return sc;
	}

	private int executeScript0(String code, int firstLine)  {
		return executeScript0(code, firstLine, scriptContext());
	}

	/** code in sc (a script read a command at a time runs them all in one: exec >file stays) */
	private int executeScript0(String code, int firstLine, ShellContext sc)  {
		int ret = 0;

		try {

			if( isOptionEnabled(Option.PrintLinesAsRead)) {
				sc.stdout.println(getPrompt(Prompt.EchoCommand)+code);
			}

			code = code.stripLeading();
			ret = us.bringardner.fsh.exec.Executor.script(sc, code, firstLine);
			if( ret != 0 && isInteractive && isOptionEnabled(Option.ExitImediately)) {
				Console.exit(sc.console, ret);
			}
			return ret;

		} catch(ExitException e) {
			ret = e.exitCode;
			exitBuiltinRan = true;
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
			handleMetaSignal(ConsoleMetaSignal.Err);
		}

		return ret;
	}
}
