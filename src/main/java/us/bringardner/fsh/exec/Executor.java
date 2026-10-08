package us.bringardner.fsh.exec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.Console.CommandThread;
import us.bringardner.fsh.Console.ConsoleMetaSignal;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.Glob;
import us.bringardner.fsh.Pipe;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellContext.LoopControl;
import us.bringardner.fsh.ShellFunction;
import us.bringardner.fsh.ShellTask;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.expand.Arithmetic;
import us.bringardner.fsh.ClosedStream;
import us.bringardner.fsh.signal.ExitException;
import us.bringardner.fsh.signal.FshException;
import us.bringardner.fsh.signal.ReturnException;
import us.bringardner.fsh.exec.Programs;
import us.bringardner.fsh.signal.LoopControlException;
import us.bringardner.fsh.commands.Read;
import us.bringardner.fsh.expand.ExpansionError;
import us.bringardner.fsh.expand.Expander;
import us.bringardner.fsh.job.BackgroundJob;
import us.bringardner.fsh.job.JobState;
import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Parser;
import us.bringardner.fsh.syntax.SyntaxError;
import us.bringardner.fsh.syntax.Word;

/**
 * Runs the syntax tree of a script (see us.bringardner.fsh.syntax) as bash does, on the shell's
 * runtime: ShellContext for variables, functions and streams, the builtins, Console for options,
 * traps and jobs. Words are expanded by Expander, which calls back here for $( ).
 * <p>
 * An Executor belongs to the text it runs, so a function it defines can print itself.
 */
public final class Executor {

	private final String source;

	public Executor(String source) {
		this.source = source;
	}

	// ------------------------------------------------------------------ entry points

	/**
	 * Parse code and run it in sc (eval, source, a trap, $( ) that bash reads late). A syntax
	 * error is reported: status 2.
	 */
	public static int run(ShellContext sc, String code) throws IOException {
		return run(sc, code, null, 1);
	}

	/** run, its syntax errors said as who's (eval: line N:), its lines counted from firstLine */
	static int run(ShellContext sc, String code, String who, int firstLine) throws IOException {
		// (a line at a time, as bash reads eval's and a sourced file's text)
		Parser.Reader reader = new Parser.Reader(code, firstLine);
		int ret = 0;
		while( true ) {
			Ast.Sequence seq;
			try {
				seq = reader.next();
			} catch (SyntaxError e) {
				syntaxError(sc, e, code, firstLine, who);
				if( e.recoverable ) {
					reader.skipLine();
					ret = 1;
					sc.console.setLastExitCode(1);
					continue;
				}
				return 2;
			}
			if( seq == null ) {
				return ret;
			}
			warnings(sc, seq);
			ret = new Executor(seq.source).list(seq, sc);
		}
	}

	/**
	 * A script: parsed whole, then each command run in turn, the DEBUG trap before each. A syntax
	 * error stops it before anything runs (status 2).
	 */
	public static int script(ShellContext sc, String code) throws IOException {
		return script(sc, code, 1);
	}

	/** text with its parameters, commands and arithmetic expanded (as in "...") */
	public static String expandWord(ShellContext sc, String text) {
		return expanderFor(sc).string(Parser.fragment(text, Parser.Fragment.QUOTED));
	}

	/** a script whose text starts on line firstLine of the input (commands read one at a time) */
	public static int script(ShellContext sc, String code, int firstLine) throws IOException {
		Parser.Reader reader = new Parser.Reader(code, firstLine);
		int ret = 0;
		while( true ) {
			Ast.Sequence seq;
			try {
				seq = reader.next();
			} catch (SyntaxError e) {
				syntaxError(sc, e, code, firstLine);
				if( e.recoverable ) {
					// x=(a & b): said, and the next line runs
					reader.skipLine();
					ret = 1;
					sc.console.setLastExitCode(1);
					continue;
				}
				return 2;
			}
			if( seq == null ) {
				return ret;
			}
			warnings(sc, seq);
			ret = items(seq, new Executor(seq.source), sc, ret);
		}
	}

	/** what bash warns about as it reads (a here-document's end of file) */
	private static void warnings(ShellContext sc, Ast.Sequence seq) {
		for(Object [] w : seq.warnings) {
			sc.stderr.println(prefix(sc, (Integer) w[0])+w[1]);
		}
	}

	/** a line's commands, each in turn (an error that abandons the line skips the rest of it) */
	private static int items(Ast.Sequence seq, Executor ex, ShellContext sc, int ret) throws IOException {
		for(Ast.Item item : seq.items) {
			if( !sc.console.isInteractive && sc.console.isOptionEnabled(Option.History)) {
				// set -o history in a script: each command it runs is kept, as bash keeps them
				sc.console.rememberCommand(ex.text(item.command).trim()+(item.background ? " &" : ""));
			}
			try {
				ret = ex.item(item, sc);
			} catch (AbandonLine e) {
				// the rest of the line is not run (in a function too, as bash's)
				ret = 1;
				sc.console.setLastExitCode(1);
				break;
			}
		}
		return ret;
	}

	/** an error after which bash does not run the rest of the line (x=1 of a readonly x) */
	public static final class AbandonLine extends us.bringardner.fsh.signal.FshException {
		private static final long serialVersionUID = 1L;
		final int line;

		public AbandonLine(int line) {
			this.line = line;
		}
	}

	/** the source text of a node, as written */
	String text(Ast.Node n) {
		return text(n.start, n.end);
	}

	String text(int start, int end) {
		start = Math.max(0, Math.min(start, source.length()));
		end = Math.max(start, Math.min(end, source.length()));
		return source.substring(start, end);
	}

	// ------------------------------------------------------------------ messages

	/** what comes before an error message found on line (see ShellContext.errorPrefix) */
	/**
	 * A syntax error, as bash reports it: the message, and for an unexpected token the line it is
	 * on (fsh: line 2: `fi'). In -c's command, after $0 comes -c:.
	 */
	private static void syntaxError(ShellContext sc, SyntaxError e, String code, int firstLine) {
		syntaxError(sc, e, code, firstLine, null);
	}

	private static void syntaxError(ShellContext sc, SyntaxError e, String code, int firstLine, String who) {
		String prefix = commandStringPrefix(sc, prefix(sc, e.line));
		if( who != null ) {
			// eval: line N:
			prefix = prefix.replaceFirst("(line \\d+: )$", who+": $1");
		}
		sc.stderr.println(prefix+e.getMessage());
		if( e.near != null ) {
			// [[ ]]: where, and the line
			sc.stderr.println(prefix+"syntax error near `"+e.near+"'");
		}
		if( e.eofFrom != null ) {
			String p2 = commandStringPrefix(sc, prefix(sc, e.eofLine));
			sc.stderr.println(p2+"syntax error: unexpected end of file from `"+e.eofFrom+"' command on line "+e.eofFromLine);
		}
		if( e.getMessage().startsWith("syntax error near unexpected token") || e.near != null ) {
			String [] lines = code.split("\n", -1);
			int i = e.line-firstLine;
			if( i >= 0 && i < lines.length ) {
				sc.stderr.println(prefix+"`"+lines[i]+"'");
			}
		}
	}

	/** in -c's command, after $0 comes -c: */
	private static String commandStringPrefix(ShellContext sc, String prefix) {
		if( sc.console.inCommandString && !sc.console.isInteractive ) {
			String zero = String.valueOf(sc.getVariable("$0"));
			if( prefix.startsWith(zero+": ")) {
				return zero+": -c: "+prefix.substring(zero.length()+2);
			}
		}
		return prefix;
	}

	static String prefix(ShellContext sc, int line) {
		int saved = sc.line;
		sc.line = line;
		try {
			return sc.errorPrefix();
		} finally {
			sc.line = saved;
		}
	}

	private static void error(ShellContext sc, String message) {
		sc.error(message);
	}

	/** an expansion error: a script that is not interactive ends (as in bash); otherwise status 1 */
	/**
	 * An expansion error, as bash handles it: reported, then a script ends (${x:?}, set -u), the
	 * rest of the line is not run (a bad substitution, an arithmetic error), or the command fails
	 * (failglob).
	 */
	private static int expansionError(ShellContext sc, ExpansionError e) {
		error(sc, e.getMessage());
		sc.console.setLastExitCode(1);
		if( e.kind == ExpansionError.Kind.FATAL && !sc.console.isInteractive ) {
			throw new ExitException(sc, e.status);
		}
		if( e.kind == ExpansionError.Kind.FAIL ) {
			return 1;
		}
		throw new AbandonLine(sc.line);
	}

	private Expander expander(ShellContext sc) {
		return new Expander(sc, new Host(sc));
	}

	/**
	 * Whether code (a command, such as [ $i -gt 3 ], or a list) succeeds in sc: its status is 0.
	 * Its output is discarded; set -e and the ERR trap do not apply (a debugger's breakpoint
	 * condition).
	 * @throws SyntaxError if code does not parse
	 */
	public static boolean test(ShellContext sc, String code) throws IOException {
		Ast.Sequence seq = Parser.parse(code);
		PrintStream out = sc.stdout;
		sc.stdout = new PrintStream(java.io.OutputStream.nullOutputStream());
		sc.conditionDepth++;
		try {
			return new Executor(seq.source).list(seq, sc) == 0;
		} finally {
			sc.conditionDepth--;
			sc.stdout = out;
		}
	}

	/** an Expander for words in sc, whose $( ) run here */
	public static Expander expanderFor(ShellContext sc) {
		return new Executor("").expander(sc);
	}

	// ------------------------------------------------------------------ lists

	/** commands separated by ; & or newlines: the status of the last */
	int list(Ast.Sequence seq, ShellContext sc) throws IOException {
		int ret = 0;
		for(Ast.Item item : seq.items) {
			ret = item(item, sc);
		}
		return ret;
	}

	private int item(Ast.Item item, ShellContext sc) throws IOException {
		if( item.background ) {
			background(item.command, sc);
			sc.console.setLastExitCode(0);
			return 0;
		}
		return andOr(item.command, sc);
	}

	/** a && b || c: left to right, each after the one before succeeded (&&) or failed (||) */
	private int andOr(Ast.AndOr ao, ShellContext sc) throws IOException {
		int n = ao.pipelines.size();
		int ret = pipeline(ao.pipelines.get(0), sc, n > 1);
		for (int i = 1; i < n; i++) {
			boolean and = ao.ops.get(i-1).equals("&&");
			if( and == (ret == 0)) {
				ret = pipeline(ao.pipelines.get(i), sc, i < n-1);
			}
		}
		return ret;
	}

	/**
	 * A pipeline, and what follows its status: $?, the ERR trap and set -e (not for a condition:
	 * the left of && ||, after !, in if and while).
	 */
	private int pipeline(Ast.Pipeline p, ShellContext sc, boolean condition) throws IOException {
		boolean cond = condition || p.negated;
		long start = System.nanoTime();
		int ret;
		if( cond ) {
			sc.conditionDepth++;
		}
		try {
			if( p.commands.isEmpty()) {
				ret = 0;
			} else if( p.commands.size() == 1 ) {
				ret = command(p.commands.get(0), sc);
				if( !(p.commands.get(0) instanceof Ast.FunctionDef)) {
					FshList statuses = new FshList();
					statuses.add(String.valueOf(ret));
					sc.setVariable("PIPESTATUS", statuses);
				}
			} else {
				ret = pipe(p, sc);
				// the shell's line: the pipeline's last command's ($LINENO in an ERR trap)
				Ast.Node last = p.commands.get(p.commands.size()-1);
				if( last.line > 0 ) {
					sc.line = last.line;
				}
			}
		} finally {
			if( cond ) {
				sc.conditionDepth--;
			}
		}
		if( p.negated ) {
			ret = ret == 0 ? 1 : 0;
		}
		if( p.timed ) {
			time(p, start, sc);
		}
		sc.console.setLastExitCode(ret);
		if( ret != 0 && !cond && sc.conditionDepth == 0 && failureCounts(p)) {
			if( sc.errTrapBlocked == 0 ) {
				sc.console.runTrap(ConsoleMetaSignal.Err, sc);
			}
			if( sc.console.isOptionEnabled(Option.ExitImediately)) {
				throw new ExitException(sc, ret);
			}
		}
		return ret;
	}

	/**
	 * Whether a failed pipeline runs the ERR trap and ends a set -e script: not a compound
	 * command (if, a loop, { }), which failed through a command in it that was already checked.
	 */
	private static boolean failureCounts(Ast.Pipeline p) {
		if( p.commands.size() != 1 ) {
			return true;
		}
		Ast.Command c = p.commands.get(0);
		return c instanceof Ast.SimpleCommand || c instanceof Ast.Subshell || c instanceof Ast.Arith || c instanceof Ast.Cond;
	}

	/** time: as bash prints it, on standard error */
	private static void time(Ast.Pipeline p, long start, ShellContext sc) {
		double secs = (System.nanoTime()-start)/1e9;
		if( p.timePosix ) {
			sc.stderr.printf("real %.2f%nuser 0.00%nsys 0.00%n", secs);
		} else {
			long min = (long) (secs/60);
			sc.stderr.printf("%nreal\t%dm%.3fs%nuser\t0m0.000s%nsys\t0m0.000s%n", min, secs-min*60);
		}
	}

	/**
	 * a | b | c: each stage a subshell on its own thread, as in bash (echo a | read x does not set
	 * x), except the last with shopt -s lastpipe in a script.
	 */
	private int pipe(Ast.Pipeline p, ShellContext sc) throws IOException {
		int n = p.commands.size();
		CommandThread [] threads = new CommandThread[n];
		List<CommandThread> kids = java.util.Arrays.asList(threads);
		PrintStream callerOut = sc.stdout;
		PrintStream callerErr = sc.stderr;
		InputStream callerIn = sc.stdin;
		boolean lastInShell = !sc.console.isInteractive && !sc.isIsolated() && Glob.option(sc, "lastpipe");
		List<Pipe> pipes = new ArrayList<>();
		try {
			for (int i = 0; i < n; i++) {
				Ast.Command c = p.commands.get(i);
				ShellContext ctx = lastInShell && i == n-1 ? sc : sc.isolatedSubShell();
				threads[i] = new CommandThread(ctx, new ShellTask() {
					@Override
					public int run(ShellContext stage) throws IOException {
						return command(c, stage);
					}

					@Override
					public String text() {
						return Executor.this.text(c).trim();
					}
				});
			}
			for (int i = 0; i < n-1; i++) {
				Pipe pipe = new Pipe();
				pipes.add(pipe);
				threads[i].ctx.stdout = new PrintStream(pipe.out);
				threads[i+1].ctx.stdin = pipe.in;
				if( p.stderrToo.get(i)) {
					threads[i].ctx.stderr = threads[i].ctx.stdout;
				}
			}
			// a stage closes its stdout when done; the last one's is the caller's
			CommandThread last = threads[n-1];
			last.ctx.stdout = new KeptOpen(last.ctx.stdout);
			for(CommandThread t : threads) {
				if( !lastInShell || t != last ) {
					t.start();
				}
			}
			if( lastInShell ) {
				sc.errTrapBlocked++;
				try {
					last.run();
				} finally {
					sc.errTrapBlocked--;
				}
			}
			for(CommandThread t : threads) {
				while( t.isAlive()) {
					forwardControl(sc, threads);
					try {
						t.join(50);
					} catch (InterruptedException e) {
					}
				}
			}
			int ret = threads[n-1].exitCode;
			FshList statuses = new FshList();
			for(CommandThread t : threads) {
				statuses.add(String.valueOf(t.exitCode));
				if( t.exitCode != 0 && sc.console.isOptionEnabled(Option.PipeFail)) {
					ret = t.exitCode;
				}
			}
			sc.setVariable("PIPESTATUS", statuses);
			for(CommandThread t : kids) {
				t.handleSignal(us.bringardner.fsh.ConsoleSignal.ChildStopped);
			}
			return ret;
		} finally {
			sc.stdout = callerOut;
			sc.stderr = callerErr;
			sc.stdin = callerIn;
			for(Pipe pipe : pipes) {
				pipe.in.close();
			}
		}
	}

	/** pass a stop or a suspend of the pipeline on to its stages */
	private static void forwardControl(ShellContext sc, CommandThread [] threads) {
		RuntimeException stop = sc.getException();
		boolean paused = sc.isPaused();
		for(CommandThread t : threads) {
			if( stop != null && t.ctx.getException() == null ) {
				t.ctx.setExecption(stop);
			}
			if( t.ctx.isPaused() != paused ) {
				t.ctx.setPause(paused);
			}
		}
	}

	/** cmd &: a job, in a subshell with no input */
	private void background(Ast.AndOr ao, ShellContext sc) {
		try {
			ShellContext ctx = sc.subShell();
			ctx.stdin = new ByteArrayInputStream(new byte[0]);
			String text = text(ao).trim();
			CommandThread thread = new CommandThread(ctx, new ShellTask() {
				@Override
				public int run(ShellContext job) throws IOException {
					return andOr(ao, job);
				}

				@Override
				public String text() {
					return text;
				}
			});
			BackgroundJob job = new BackgroundJob(thread);
			if( !sc.console.isInteractive ) {
				// as in bash, without job control a job started with & ignores SIGINT and SIGQUIT
				job.addIgnoreSignal(us.bringardner.fsh.ConsoleSignal.Interupt);
				job.addIgnoreSignal(us.bringardner.fsh.ConsoleSignal.Quit);
			}
			sc.console.addJob(job);
			job.start();
			while( job.getState() == JobState.Idel ) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
			if( sc.console.isInteractive ) {
				sc.stdout.println("["+job.getJobNumber()+"] "+job.pid);
			}
		} catch (Exception e) {
			sc.stderr.println(e);
		}
	}

	/**
	 * coproc NAME command: command runs in the background; NAME[0] is a descriptor to read its
	 * output from, NAME[1] one to write its input to (63 and 60 if free, as bash picks them),
	 * NAME_PID its process id.
	 */
	private int coproc(Ast.Coproc k, ShellContext sc) {
		if( !isName(k.name)) {
			error(sc, "`"+k.name+"': not a valid identifier");
			return 1;
		}
		Pipe toCoproc = new Pipe();
		Pipe fromCoproc = new Pipe();
		int readFd = freeDescriptor(sc, 63, -1);
		int writeFd = freeDescriptor(sc, 60, readFd);
		sc.console.setFileDistcriptor(new Console.FileDiscriptor(readFd, fromCoproc.in, null));
		sc.console.setFileDistcriptor(new Console.FileDiscriptor(writeFd, new PrintStream(toCoproc.out, true), null));
		ShellContext ctx = sc.subShell();
		ctx.stdin = toCoproc.in;
		ctx.stdout = new PrintStream(fromCoproc.out, true);
		String text = text(k).trim();
		CommandThread thread = new CommandThread(ctx, new ShellTask() {
			@Override
			public int run(ShellContext job) throws IOException {
				return command(k.body, job);
			}

			@Override
			public String text() {
				return text;
			}
		});
		BackgroundJob job = new BackgroundJob(thread);
		sc.console.addJob(job);
		job.start();
		FshList fds = new FshList();
		fds.add(String.valueOf(readFd));
		fds.add(String.valueOf(writeFd));
		// (a readonly name is said, and keeps its value)
		if( sc.console.isReadonly(sc.readonlyName(k.name))) {
			error(sc, sc.readonlyName(k.name)+": readonly variable");
		} else {
			sc.setVariable(k.name, fds);
		}
		if( sc.console.isReadonly(sc.readonlyName(k.name+"_PID"))) {
			error(sc, sc.readonlyName(k.name+"_PID")+": readonly variable");
		} else {
			sc.setVariable(k.name+"_PID", String.valueOf(job.pid));
		}
		return 0;
	}

	/** the first descriptor at or below from that is not open (and not except) */
	private static int freeDescriptor(ShellContext sc, int from, int except) {
		int fd = from;
		while( fd > 10 && (fd == except || sc.console.getFileDistcriptor(fd) != null)) {
			fd--;
		}
		return fd;
	}

	// ------------------------------------------------------------------ commands

	/** one command, with the redirects after it */
	int command(Ast.Command c, ShellContext sc) throws IOException {
		if( sc.console.isOptionEnabled(Option.NoExec) && !sc.console.isInteractive ) {
			// set -n: nothing runs any more (not even set +n), as in bash
			return 0;
		}
		int afterCommand = sc.afterCommand.size();
		try {
			// a debugger stops here; a stop or suspend of the job takes effect
			sc.enterNode(c, source);
			if( c instanceof Ast.SimpleCommand s ) {
				return simple(s, sc);
			}
			if( c instanceof Ast.FunctionDef f ) {
				return define(f, sc);
			}
			if( c.redirects.isEmpty()) {
				return compound(c, sc);
			}
			Redirects.Saved saved = Redirects.Saved.of(sc);
			List<Closeable> opened;
			try {
				opened = Redirects.apply(c.redirects, sc, expander(sc));
			} catch (ExpansionError e) {
				return expansionError(sc, e);
			} catch (IOException | RuntimeException e) {
				error(sc, message(e));
				return 1;
			}
			try {
				return compound(c, sc);
			} finally {
				saved.restore(sc);
				Redirects.close(opened);
			}
		} finally {
			sc.exitNode(c, source);
			// its <(cmd) and >(cmd) end
			while( sc.afterCommand.size() > afterCommand ) {
				sc.afterCommand.remove(afterCommand).run();
			}
		}
	}

	private int compound(Ast.Command c, ShellContext sc) throws IOException {
		sc.line = c.line;
		switch (c) {
		case Ast.BraceGroup g -> {
			return list(g.body, sc);
		}
		case Ast.Subshell s -> {
			return subshell(s.body, sc);
		}
		case Ast.If f -> {
			return ifCommand(f, sc);
		}
		case Ast.Loop l -> {
			return loop(l, sc);
		}
		case Ast.For f -> {
			return forCommand(f, sc);
		}
		case Ast.ArithFor f -> {
			return arithFor(f, sc);
		}
		case Ast.Select s -> {
			return select(s, sc);
		}
		case Ast.Case k -> {
			return caseCommand(k, sc);
		}
		case Ast.Arith a -> {
			return arith(a, sc);
		}
		case Ast.Cond k -> {
			return cond(k, sc);
		}
		case Ast.Coproc k -> {
			return coproc(k, sc);
		}
		default -> throw new IllegalStateException("not a compound command: "+c.getClass());
		}
	}

	/** ( list ): a subshell, so its changes (x=1, cd, exit, set --, exec 3>f ...) stay inside */
	private int subshell(Ast.Sequence body, ShellContext sc) throws IOException {
		ShellContext sub = sc.subShell();
		Console.Snapshot saved = sc.console.snapshot();
		List<String> trap = sc.console.beginSubshellTrap();
		int ret = 1;
		try {
			ret = list(body, sub);
		} catch (ExitException e) {
			ret = e.exitCode;
		} catch (us.bringardner.fsh.signal.ReturnException e) {
			// ( return 5 ) in a function: the subshell ends with 5, the function goes on
			ret = e.exitCode;
		} catch (AbandonLine e) {
			// (an error that ends the line ends the subshell, as bash's)
			ret = 1;
		} finally {
			// its EXIT trap runs as it ends (an exit in it is the subshell's status)
			ret = sc.console.endSubshellTrap(trap, ret);
			sc.console.restore(saved);
		}
		return ret;
	}

	/** a condition (if, while): set -e and the ERR trap do not apply to it */
	private int condition(Ast.Sequence seq, ShellContext sc) throws IOException {
		sc.conditionDepth++;
		try {
			return list(seq, sc);
		} finally {
			sc.conditionDepth--;
		}
	}

	private int ifCommand(Ast.If f, ShellContext sc) throws IOException {
		for (int i = 0; i < f.conditions.size(); i++) {
			if( condition(f.conditions.get(i), sc) == 0 ) {
				return list(f.bodies.get(i), sc);
			}
		}
		return f.elseBody == null ? 0 : list(f.elseBody, sc);
	}

	/**
	 * A loop's body: null, or Break or Continue if it ended that way (break 2 goes on to the loop
	 * outside). status[0] is the body's status (0 after break and continue).
	 */
	private LoopControl body(Ast.Sequence body, ShellContext sc, int [] status) throws IOException {
		try {
			status[0] = list(body, sc);
			return null;
		} catch (LoopControlException e) {
			status[0] = 0;
			if( e.howFar > 1 ) {
				throw new LoopControlException(e.type, e.howFar-1);
			}
			return e.type;
		}
	}

	private int loop(Ast.Loop l, ShellContext sc) throws IOException {
		int [] status = {0};
		sc.loopDepth++;
		try {
			while( (condition(l.condition, sc) == 0) != l.until ) {
				if( body(l.body, sc, status) == LoopControl.Break ) {
					break;
				}
			}
			return status[0];
		} finally {
			sc.loopDepth--;
		}
	}

	private int forCommand(Ast.For f, ShellContext sc) throws IOException {
		List<String> values;
		if( f.words == null ) {
			values = new ArrayList<>();
			for(Object o : sc.getPositionalParameterValues()) {
				values.add(String.valueOf(o));
			}
		} else {
			try {
				values = expander(sc).words(f.words);
			} catch (ExpansionError e) {
				return expansionError(sc, e);
			}
		}
		if( !isName(f.variable)) {
			error(sc, "`"+f.variable+"': not a valid identifier");
			return 1;
		}
		StringBuilder header = new StringBuilder("for "+f.variable);
		StringBuilder shown = new StringBuilder(header);
		if( f.words != null ) {
			header.append(" in");
			shown.append(" in");
			for(Word w : f.words) {
				header.append(' ').append(w.raw);
			}
			// (set -x shows the words as written, as bash does)
			shown = header;
		}
		int [] status = {0};
		sc.loopDepth++;
		try {
			for(String v : values) {
				sc.line = f.line;
				debugTrap(sc, header.toString());
				if( tracing(sc)) {
					trace(sc, sc.stderr, shown.toString());
				}
				if( sc.rawVariable(f.variable) instanceof ShellContext.NameRef ) {
					// a nameref names each word in turn (bash's)
					if( !ShellContext.validReference(v)) {
						error(sc, "`"+v+"': not a valid identifier");
						status[0] = 1;
						continue;
					}
					sc.retarget(f.variable, v);
				} else {
					sc.setVariable(f.variable, v);
				}
				if( body(f.body, sc, status) == LoopControl.Break ) {
					break;
				}
			}
			return status[0];
		} finally {
			sc.loopDepth--;
		}
	}

	/** for (( init; condition; step )): an empty condition is true */
	private int arithFor(Ast.ArithFor f, ShellContext sc) throws IOException {
		Expander ex = expander(sc);
		int [] status = {0};
		sc.loopDepth++;
		try {
			sc.line = f.line;
			arithmetic(f.init, ex, sc);
			while( f.condition.raw.isBlank() || Arithmetic.isTrue(arithmetic(f.condition, ex, sc))) {
				if( body(f.body, sc, status) == LoopControl.Break ) {
					break;
				}
				sc.line = f.line;
				arithmetic(f.step, ex, sc);
			}
			return status[0];
		} catch (ExpansionError e) {
			error(sc, "((: "+e.getMessage());
			return 1;
		} finally {
			sc.loopDepth--;
		}
	}

	/** a part of for (( ; ; )): the DEBUG trap and set -x see it as (( part )) */
	private static Number arithmetic(Word w, Expander ex, ShellContext sc) {
		if( w.raw.isBlank()) {
			return 0L;
		}
		debugTrap(sc, "(("+w.raw+"))");
		String text = ex.arithmeticText(w);
		if( tracing(sc)) {
			// (the blanks after it as written: bash's (( i++  )))
			trace(sc, sc.stderr, "(( "+text.trim()+w.raw.substring(w.raw.stripTrailing().length())+" ))");
		}
		return ex.evaluate(text);
	}

	/** select name in words: a numbered menu on standard error, a choice read from standard input */
	private int select(Ast.Select s, ShellContext sc) throws IOException {
		List<String> entries;
		if( s.words == null ) {
			entries = new ArrayList<>();
			for(Object o : sc.getPositionalParameterValues()) {
				entries.add(String.valueOf(o));
			}
		} else {
			try {
				entries = expander(sc).words(s.words);
			} catch (ExpansionError e) {
				return expansionError(sc, e);
			}
		}
		if( entries.isEmpty()) {
			// nothing to choose from: as bash, the loop does not run
			return 0;
		}
		Object ps3 = sc.getVariable("PS3");
		String prompt = ps3 == null ? "#? " : ps3.toString();
		Read reader = new Read();
		int [] status = {0};
		sc.loopDepth++;
		try {
			while( true ) {
				menu(entries, sc);
				String reply;
				try {
					reply = reader.readLine(sc, prompt);
				} catch (java.io.EOFException e) {
					reply = null;
				}
				if( reply == null ) {
					// the end of input: as bash, a newline and status 1
					sc.stderr.println();
					return 1;
				}
				sc.setVariable("REPLY", reply);
				if( reply.isEmpty()) {
					continue;
				}
				String choice = "";
				try {
					int n = Integer.parseInt(reply.trim());
					if( n >= 1 && n <= entries.size()) {
						choice = entries.get(n-1);
					}
				} catch (NumberFormatException e) {
				}
				if( sc.rawVariable(s.variable) instanceof ShellContext.NameRef ) {
					// a nameref names the choice (one that is no name ends the select)
					if( !ShellContext.validReference(choice)) {
						error(sc, "`"+choice+"': not a valid identifier");
						return 1;
					}
					sc.retarget(s.variable, choice);
				} else {
					sc.setVariable(s.variable, choice);
				}
				if( body(s.body, sc, status) == LoopControl.Break ) {
					return status[0];
				}
			}
		} finally {
			sc.loopDepth--;
		}
	}

	/** the menu in columns (down, then across) as wide as $COLUMNS, at least 11 lines a column */
	/**
	 * select's menu, laid out as bash's (on standard error): the entries down the columns that fit
	 * in $COLUMNS (a single column if they fit in one row), tabs between the columns.
	 */
	private static void menu(List<String> entries, ShellContext sc) {
		int sz = entries.size();
		int maxLen = 0;
		for(String s : entries) {
			maxLen = Math.max(maxLen, s.length());
		}
		int indices = String.valueOf(sz).length();
		maxLen += indices+2+2;
		int width = 80;
		try {
			width = Integer.parseInt(String.valueOf(sc.getVariable("COLUMNS")).trim());
		} catch (NumberFormatException e) {
		}
		int cols = maxLen > 0 ? Math.max(1, width/maxLen) : 1;
		int rows = sz > 0 ? sz/cols+(sz % cols != 0 ? 1 : 0) : 1;
		cols = sz > 0 ? sz/rows+(sz % rows != 0 ? 1 : 0) : 1;
		if( rows == 1 ) {
			rows = cols;
			cols = 1;
		}
		int firstIndices = String.valueOf(rows).length();
		StringBuilder out = new StringBuilder();
		for (int row = 0; row < rows; row++) {
			int ind = row;
			int pos = 0;
			while( true ) {
				int w = pos == 0 ? firstIndices : indices;
				String item = String.format("%"+w+"d) %s", ind+1, entries.get(ind));
				out.append(item);
				int elem = item.length();
				ind += rows;
				if( ind >= sz ) {
					break;
				}
				// tabs and spaces to the next column, as bash's indent
				int from = pos+elem;
				int to = pos+maxLen;
				while( from < to ) {
					if( to/8 > from/8 ) {
						out.append('\t');
						from += 8-from % 8;
					} else {
						out.append(' ');
						from++;
					}
				}
				pos += maxLen;
			}
			out.append('\n');
		}
		sc.stderr.print(out);
		sc.stderr.flush();
	}


	/** case word in pattern) ...: ;; ends it, ;& runs the next list too, ;;& tests the next patterns */
	private int caseCommand(Ast.Case k, ShellContext sc) throws IOException {
		Expander ex = expander(sc);
		String header = "case "+k.subject.raw+" in";
		debugTrap(sc, header+" ");
		if( tracing(sc)) {
			trace(sc, sc.stderr, header);
		}
		String subject;
		try {
			subject = ex.string(k.subject);
		} catch (ExpansionError e) {
			return expansionError(sc, e);
		}
		int ret = 0;
		boolean fallInto = false;
		for(Ast.CaseClause cl : k.clauses) {
			boolean match = fallInto;
			for (int i = 0; !match && i < cl.patterns.size(); i++) {
				try {
					match = matches(ex.pattern(cl.patterns.get(i)), subject, sc);
				} catch (ExpansionError e) {
					return expansionError(sc, e);
				}
			}
			if( !match ) {
				continue;
			}
			ret = list(cl.body, sc);
			if( cl.terminator == null || cl.terminator.equals(";;")) {
				return ret;
			}
			fallInto = cl.terminator.equals(";&");
		}
		return ret;
	}

	/** a pattern (case, [[ == ]]) matches all of text (shopt -s nocasematch: in any case) */
	private static boolean matches(String pattern, String text, ShellContext sc) {
		return us.bringardner.fsh.GlobPattern.compile(pattern, Glob.option(sc, "nocasematch")).matches(text);
	}

	/** (( expression )): 0 if it is not 0 */
	private int arith(Ast.Arith a, ShellContext sc) {
		debugTrap(sc, text(a).trim());
		try {
			Expander ex = expander(sc);
			String expr = ex.arithmeticText(a.expression);
			if( tracing(sc)) {
				// the spaces inside (( )) as written
				String inner = text(a).trim();
				inner = inner.substring(2, inner.length()-2);
				String lead = inner.substring(0, inner.length()-inner.stripLeading().length());
				String trail = inner.substring(inner.stripTrailing().length());
				trace(sc, sc.stderr, "(( "+lead+expr+trail+" ))");
			}
			return Arithmetic.isTrue(ex.evaluate(expr)) ? 0 : 1;
		} catch (ExpansionError e) {
			error(sc, "((: "+e.getMessage());
			return 1;
		}
	}

	// ------------------------------------------------------------------ [[ ]]

	/** an invalid [[ ]] (a regular expression after =~, -t's descriptor): status 2 */
	private static final class BadRegex extends RuntimeException {
		private static final long serialVersionUID = 1L;

		BadRegex(String message) {
			super(message);
		}
	}

	private int cond(Ast.Cond c, ShellContext sc) {
		debugTrap(sc, text(c).trim());
		try {
			return test(c.expression, sc, expander(sc)) ? 0 : 1;
		} catch (BadRegex e) {
			error(sc, "[[: "+e.getMessage());
			return 2;
		} catch (Arithmetic.ArithmeticError e) {
			error(sc, "[[: "+e.getMessage());
			return 1;
		} catch (ExpansionError e) {
			return expansionError(sc, e);
		}
	}

	private boolean test(Ast.CondExpr e, ShellContext sc, Expander ex) {
		switch (e) {
		case Ast.CondAnd a -> {
			return test(a.left(), sc, ex) && test(a.right(), sc, ex);
		}
		case Ast.CondOr o -> {
			return test(o.left(), sc, ex) || test(o.right(), sc, ex);
		}
		case Ast.CondNot n -> {
			return !test(n.expression(), sc, ex);
		}
		case Ast.CondWord w -> {
			String v = ex.string(w.word());
			traceTest(sc, v);
			return !v.isEmpty();
		}
		case Ast.CondUnary u -> {
			String v = ex.string(u.operand());
			traceTest(sc, u.op()+" "+traced(v));
			if( u.op().equals("-t") && !v.trim().matches("[-+]?[0-9]+")) {
				// (status 2, as bash)
				throw new BadRegex(v+": integer expected");
			}
			return us.bringardner.fsh.commands.Test.unaryTest(u.op(), v, sc);
		}
		case Ast.CondBinary b -> {
			if( tracing(sc)) {
				traceTest(sc, traced(ex.string(b.left()))+" "+b.op()+" "+traced(ex.string(b.right())));
			}
			return binary(b, sc, ex);
		}
		}
	}

	/** a [[ ]] operand as set -x shows it: as it is, '' if empty */
	private static String traced(String v) {
		return v.isEmpty() ? "''" : v;
	}

	/** set -x: each test of [[ ]] as it is made, its words expanded */
	private static void traceTest(ShellContext sc, String text) {
		if( tracing(sc)) {
			trace(sc, sc.stderr, "[[ "+text+" ]]");
		}
	}

	private boolean binary(Ast.CondBinary b, ShellContext sc, Expander ex) {
		String l = ex.string(b.left());
		switch (b.op()) {
		case "==":
		case "=":
			return matches(ex.pattern(b.right()), l, sc);
		case "!=":
			return !matches(ex.pattern(b.right()), l, sc);
		case "=~":
			return regex(l, ex.regex(b.right()), sc);
		case "<":
			return l.compareTo(ex.string(b.right())) < 0;
		case ">":
			return l.compareTo(ex.string(b.right())) > 0;
		case "-nt":
		case "-ot":
		case "-ef":
			return us.bringardner.fsh.commands.Test.fileCompare(l, b.op(), ex.string(b.right()), sc);
		default: {
			Number x = Arithmetic.evaluate(l, sc);
			Number y = Arithmetic.evaluate(ex.string(b.right()), sc);
			int cmp = Double.compare(x.doubleValue(), y.doubleValue());
			return switch (b.op()) {
			case "-eq" -> cmp == 0;
			case "-ne" -> cmp != 0;
			case "-lt" -> cmp < 0;
			case "-le" -> cmp <= 0;
			case "-gt" -> cmp > 0;
			default -> cmp >= 0;
			};
		}
		}
	}

	/** text =~ rx: BASH_REMATCH has the match and its groups */
	private static boolean regex(String text, String rx, ShellContext sc) {
		Pattern p;
		try {
			p = Pattern.compile(posixClasses(rx), Glob.option(sc, "nocasematch") ? Pattern.CASE_INSENSITIVE : 0);
		} catch (PatternSyntaxException e) {
			// as bash says it (regcomp's words for the common ones)
			String d = e.getDescription();
			String why = d.startsWith("Illegal character range") ? "invalid character range"
					: d.startsWith("Unclosed group") || d.startsWith("Unmatched closing ')'") ? "parentheses not balanced"
					: d.startsWith("Unclosed character class") ? "brackets ([ ]) not balanced"
					: d.startsWith("Unexpected internal error") || d.contains("escape sequence") || d.contains("trailing backslash") ? "trailing backslash (\\)"
					: d.substring(0, 1).toLowerCase()+d.substring(1);
			throw new BadRegex("invalid regular expression `"+rx+"': "+why);
		}
		Matcher m = p.matcher(text);
		FshList groups = new FshList();
		boolean ret = m.find();
		if( ret ) {
			for (int i = 0; i <= m.groupCount(); i++) {
				groups.add(m.group(i) == null ? "" : m.group(i));
			}
		}
		sc.setVariable("BASH_REMATCH", groups);
		return ret;
	}

	/** [:alpha:] in a bracket expression as Java's \p{Alpha} ([:^alpha:], an fsh extension, is \P{Alpha}) */
	private static final String [][] CLASSES = {
			{"word", "\\w", "\\W"}, {"ascii", "\\p{ASCII}", "\\P{ASCII}"}, {"lower", "\\p{Lower}", "\\P{Lower}"},
			{"upper", "\\p{Upper}", "\\P{Upper}"}, {"alpha", "\\p{Alpha}", "\\P{Alpha}"}, {"digit", "\\p{Digit}", "\\P{Digit}"},
			{"alnum", "\\p{Alnum}", "\\P{Alnum}"}, {"punct", "\\p{Punct}", "\\P{Punct}"}, {"graph", "\\p{Graph}", "\\P{Graph}"},
			{"print", "\\p{Print}", "\\P{Print}"}, {"blank", "\\p{Blank}", "\\P{Blank}"}, {"cntrl", "\\p{Cntrl}", "\\P{Cntrl}"},
			{"xdigit", "\\p{XDigit}", "\\P{XDigit}"}, {"space", "\\s", "\\S"}};

	/**
	 * A POSIX extended regular expression as Java's: its bracket expressions ([...]: a \ in one is
	 * itself, [:class:], [=c=] and [.c.]; fsh's [:^class:] too) written for Java.
	 */
	private static String posixClasses(String rx) {
		StringBuilder out = new StringBuilder();
		int n = rx.length();
		for (int i = 0; i < n; i++) {
			char c = rx.charAt(i);
			if( c == '\\' && i+1 < n ) {
				out.append(c).append(rx.charAt(++i));
				continue;
			}
			if( c == '[' ) {
				int end = posixBracketEnd(rx, i);
				if( end > 0 ) {
					out.append(bracket(rx.substring(i+1, end)));
					i = end;
					continue;
				}
			}
			out.append(c);
		}
		return out.toString();
	}

	/** the ] that ends the bracket expression at open, or -1 */
	private static int posixBracketEnd(String rx, int open) {
		int n = rx.length();
		int k = open+1;
		if( k < n && rx.charAt(k) == '^' ) {
			k++;
		}
		if( k < n && rx.charAt(k) == ']' ) {
			k++;
		}
		for (; k < n; k++) {
			char c = rx.charAt(k);
			if( c == '[' && k+1 < n && ":=.".indexOf(rx.charAt(k+1)) >= 0 ) {
				int close = rx.indexOf(rx.charAt(k+1)+"]", k+2);
				if( close > 0 ) {
					k = close+1;
					continue;
				}
			}
			if( c == ']' ) {
				return k;
			}
		}
		return -1;
	}

	/** a bracket expression's inside as a Java character class */
	private static String bracket(String body) {
		StringBuilder out = new StringBuilder("[");
		int k = 0;
		int n = body.length();
		if( k < n && body.charAt(k) == '^' ) {
			out.append('^');
			k++;
		}
		boolean first = true;
		while( k < n ) {
			char c = body.charAt(k);
			String item = null;
			if( c == '[' && k+1 < n && ":=.".indexOf(body.charAt(k+1)) >= 0 ) {
				char kind = body.charAt(k+1);
				int close = body.indexOf(kind+"]", k+2);
				if( close > 0 ) {
					String name = body.substring(k+2, close);
					k = close+2;
					if( kind == ':' ) {
						boolean negated = name.startsWith("^");
						String cls = negated ? name.substring(1) : name;
						String java = null;
						for(String [] e : CLASSES) {
							if( e[0].equals(cls)) {
								java = negated ? e[2] : e[1];
							}
						}
						if( java == null ) {
							throw new PatternSyntaxException("Invalid character class", body, -1);
						}
						out.append(java);
						first = false;
						continue;
					}
					// [=c=] and [.c.]: the character
					item = name;
				}
			}
			if( item == null ) {
				item = String.valueOf(c);
				k++;
			}
			if( !item.isEmpty() && k+1 < n && body.charAt(k) == '-') {
				// a range a-z
				char to = body.charAt(k+1);
				out.append(classChar(item.charAt(0))).append('-').append(classChar(to));
				k += 2;
			} else {
				for(char ch : item.toCharArray()) {
					out.append(classChar(ch));
				}
			}
			first = false;
		}
		return out.append(']').toString();
	}

	/** a character as itself in a Java character class */
	private static String classChar(char c) {
		return "\\[]&^-".indexOf(c) >= 0 ? "\\"+c : String.valueOf(c);
	}

	// ------------------------------------------------------------------ functions

	private int define(Ast.FunctionDef f, ShellContext sc) {
		if( sc.console.isOptionEnabled(Option.Posix) && SPECIAL_BUILTINS.contains(f.name)) {
			// posix mode: not the name of a special builtin (and the shell ends, as bash's), said at
			// the line it ends on
			sc.line = f.line+(int) text(f.start, f.end).chars().filter(c -> c == '\n').count();
			error(sc, "`"+f.name+"': is a special builtin");
			throw new ExitException(sc, 2);
		}
		if( f.quotedName ) {
			// as bash: a quoted or expanded name, said at the line it ends on
			int saved = sc.line;
			sc.line = f.line+(int) text(f.start, f.end).chars().filter(c -> c == '\n').count();
			try {
				error(sc, "`"+f.name+"': not a valid identifier");
			} finally {
				sc.line = saved;
			}
			return 1;
		}
		if( sc.console.isReadonlyFunction(f.name)) {
			error(sc, f.name+": readonly function");
			return 1;
		}
		sc.addFunction(new AstFunction(f, this));
		return 0;
	}

	// ------------------------------------------------------------------ simple commands

	/** commands whose name=value arguments are assignments */
	private static boolean isDeclaration(String name) {
		return name.equals("declare") || name.equals("typeset") || name.equals("local") || name.equals("export") || name.equals("readonly");
	}

	/**
	 * x=1 cmd args <in >out: the words are expanded (a declaration command's name=value ones as
	 * assignments), then the assignments before the command, then the redirects; then the
	 * command runs: an alias, a function, a builtin or a program.
	 */
	private int simple(Ast.SimpleCommand c, ShellContext sc) throws IOException {
		sc.line = c.line;
		String text = commandText(c);
		if( sc.trapLine == null ) {
			sc.currentCommand = text;
		}
		debugTrap(sc, text);
		Expander ex = expander(sc);
		long substitutions = sc.console.substitutionCount();
		List<Object> args = new ArrayList<>();
		// set -k: name=value after the command's name is an assignment for it too, as in bash
		List<String[]> keywordAssignments = new ArrayList<>();
		boolean keyword = sc.console.isOptionEnabled(Console.Option.Keyword);
		try {
			boolean declaration = false;
			for (int i = 0; i < c.words.size(); i++) {
				Word w = c.words.get(i);
				if( declaration && w.assignment != null ) {
					args.add(w.assignment);
					continue;
				}
				if( keyword && !declaration && w.raw.matches("[A-Za-z_][A-Za-z0-9_]*=(?s).*")) {
					String value = String.join(" ", ex.expand(w));
					int eq = value.indexOf('=');
					if( eq > 0 ) {
						keywordAssignments.add(new String[] {value.substring(0, eq), value.substring(eq+1)});
						continue;
					}
				}
				List<String> fields = ex.expand(w);
				if( args.isEmpty() && !fields.isEmpty()) {
					declaration = isDeclaration(fields.get(0)) && w.isPlain();
				}
				args.addAll(fields);
			}
		} catch (ExpansionError e) {
			return expansionError(sc, e);
		}
		if( args.isEmpty()) {
			// (set -k: name=value words with no command left set the shell's variables too)
			for(String [] a : keywordAssignments) {
				if( sc.console.isReadonly(sc.readonlyName(a[0]))) {
					error(sc, sc.readonlyName(a[0])+": readonly variable");
					continue;
				}
				sc.setVariable(a[0], a[1]);
			}
			return assignments(c, sc, ex, substitutions);
		}
		String name = (String) args.remove(0);
		// (the redirects come before the assignments, as bash's: FOO=bar cat < <(echo $FOO) does
		// not see bar)
		Redirects.Saved early = null;
		List<Closeable> earlyOpened = null;
		if( !c.redirects.isEmpty() && (!c.assignments.isEmpty() || !keywordAssignments.isEmpty()) && !name.equals("exec")) {
			early = Redirects.Saved.of(sc);
			try {
				earlyOpened = Redirects.apply(c.redirects, sc, ex);
			} catch (ExpansionError e) {
				early.restore(sc);
				return expansionError(sc, e);
			} catch (FshException e) {
				early.restore(sc);
				throw e;
			} catch (Exception e) {
				early.restore(sc);
				error(sc, message(e));
				sc.console.setLastExitCode(1);
				return 1;
			}
		}
		try {
			return withAssignments(name, args, c, sc, ex, keywordAssignments, early != null);
		} finally {
			if( early != null ) {
				early.restore(sc);
				Redirects.close(earlyOpened);
			}
		}
	}

	/** the command with its assignments (redirected already, if redirected) */
	private int withAssignments(String name, List<Object> args, Ast.SimpleCommand c, ShellContext sc, Expander ex,
			List<String[]> keywordAssignments, boolean redirected) throws IOException {
		List<Object[]> saved = null;
		if( !keywordAssignments.isEmpty()) {
			saved = new ArrayList<>();
			for(String [] a : keywordAssignments) {
				if( sc.console.isReadonly(sc.readonlyName(a[0]))) {
					error(sc, sc.readonlyName(a[0])+": readonly variable");
					continue;
				}
				saved.add(new Object[] {a[0], sc.console.getVariable(a[0]), sc.getEvironmentVariable(a[0])});
				sc.setVariable(a[0], a[1]);
				sc.setEnvironmentVariable(a[0], a[1]);
			}
		}
		if( !c.assignments.isEmpty()) {
			// IFS=: read a b: set (and exported) for the command, then put back
			if( saved == null ) {
				saved = new ArrayList<>();
			}
			try {
				for(Ast.Assignment a : c.assignments) {
					String v = a.value == null ? "" : ex.assignment(a.value);
					if( a.append ) {
						Object before = ShellContext.firstElement(sc.getVariable(a.name));
						v = sc.console.isInteger(sc.readonlyName(a.name)) ? String.valueOf(arithmeticValue((before == null ? "0" : before)+"+("+v+")", sc))
								: (before == null ? "" : before.toString())+v;
					}
					if( tracing(sc)) {
						trace(sc, sc.stderr, a.name+"="+assigned(v));
					}
					if( sc.console.isReadonly(sc.readonlyName(a.name))) {
						// as bash: said, and the command runs without it
						error(sc, sc.readonlyName(a.name)+": readonly variable");
						continue;
					}
					boolean exported = name.equals("export") || name.equals("declare") && args.stream().anyMatch(x -> x instanceof String o && o.matches("-[a-zA-Z]*x[a-zA-Z]*"));
					if( exported && sc.getFunction(name) == null ) {
						// foo= export foo, FOO=1 declare -x FOO: the assignment stays (and is exported), as in bash
						sc.setVariable(a.name, v);
						continue;
					}
					if( sc.console.isOptionEnabled(Console.Option.Posix) && SPECIAL_BUILTINS.contains(name) && sc.getFunction(name) == null ) {
						// posix mode: an assignment before a special builtin stays (past the
						// temporary ones of the commands it is in: var=1 f, with var=2 return in f)
						sc.setVariable(a.name, v);
						for(List<Object[]> outer : sc.console.temporaryAssignments) {
							for(Object[] o : outer) {
								if( o[0].equals(a.name)) {
									o[1] = v;
								}
							}
						}
						continue;
					}
					// (through a nameref: the variable it names)
					String target = sc.resolveName(a.name);
					if( sc.rawVariable(a.name) instanceof ShellContext.NameRef r && r.target().isEmpty()) {
						// r=/ f of a nameref with no value: a plain r while it runs
						saved.add(new Object[] {a.name, sc.rawVariable(a.name), sc.getEvironmentVariable(a.name)});
						sc.setPlain(a.name, v);
						sc.setEnvironmentVariable(a.name, v);
						continue;
					}
					// (put back where it was: a local the command makes of it, as local x does, keeps
					// the value)
					saved.add(new Object[] {target, sc.console.getVariable(target), sc.getEvironmentVariable(target), !sc.hasLocal(target)});
					sc.setVariable(target, v);
					sc.setEnvironmentVariable(target, v);
				}
			} catch (ExpansionError e) {
				restore(saved, sc);
				return expansionError(sc, e);
			}
		}
		if( saved != null ) {
			sc.console.temporaryAssignments.push(saved);
		}
		try {
			return redirected ? runRedirected(name, args, c, sc, ex) : run(name, args, c, sc, ex);
		} finally {
			if( saved != null ) {
				sc.console.temporaryAssignments.remove(saved);
				restore(saved, sc);
			}
		}
	}

	private static void restore(List<Object[]> saved, ShellContext sc) {
		for (int i = saved.size()-1; i >= 0; i--) {
			Object [] s = saved.get(i);
			if( s.length > 3 && Boolean.TRUE.equals(s[3])) {
				sc.setGlobal((String) s[0], s[1]);
				sc.setEnvironmentVariable((String) s[0], s[2]);
				continue;
			}
			sc.setVariable((String) s[0], s[1]);
			sc.setEnvironmentVariable((String) s[0], s[2]);
		}
	}

	/** the command as bash shows it in $BASH_COMMAND: as written, a space after a file redirect */
	private String commandText(Ast.SimpleCommand c) {
		String t = text(c).trim();
		return t.replaceAll("(?<![<>&|])(\\d*)(>>|>\\||&>>|&>|>|<)(?![&>(])\\s*", "$1$2 ");
	}

	/**
	 * Assignments with no command (x=1 y=$x, and the redirects of > file): they set the shell's
	 * variables; the status is the last $( )'s, or 0.
	 */
	private int assignments(Ast.SimpleCommand c, ShellContext sc, Expander ex, long substitutions) throws IOException {
		sc.console.lastArgument = "";
		Redirects.Saved streams = Redirects.Saved.of(sc);
		List<Closeable> opened = null;
		try {
			opened = Redirects.apply(c.redirects, sc, ex);
			// (traced with PS4 as it was before: PS4=... shows the old one, as in bash)
			String prefix = tracing(sc) ? ps4(sc) : null;
			java.util.Map<Ast.Assignment,String> before = new java.util.HashMap<>();
			for(Ast.Assignment a : c.assignments) {
				if( sc.console.isReadonly(sc.readonlyName(a.name)) && a.index != null && a.array == null
						&& sc.getVariable(a.name) instanceof FshList list && !a.index.isBlank() && !a.index.equals("@") && !a.index.equals("*")) {
					// c[-2]=v of a readonly array that has no element there: the subscript is the
					// error (bash's)
					long idx = ex.arithmetic(Parser.fragment(a.index, Parser.Fragment.WORD)).longValue();
					long top = list.isEmpty() ? 0 : list.getIndexes().get(list.size()-1)+1;
					if( idx < 0 && idx+top < 0 ) {
						error(sc, a.name+"["+a.index+"]: bad array subscript");
						sc.console.setLastExitCode(1);
						throw new AbandonLine(c.line);
					}
				}
				if( sc.console.isReadonly(sc.readonlyName(a.name))) {
					error(sc, sc.readonlyName(a.name)+": readonly variable");
					sc.console.setLastExitCode(1);
					// as in bash, the rest of the line is not run
					throw new AbandonLine(c.line);
				}
				if( a.append && a.array == null && a.index == null && prefix != null ) {
					Object old = ShellContext.firstElement(sc.getVariable(a.name));
					before.put(a, old == null ? "" : old.toString());
				}
				assign(a, sc, ex, false);
			}
			if( prefix != null && !expandingPs4.get()) {
				for(Ast.Assignment a : c.assignments) {
					Object v = sc.getVariable(a.name);
					String now = String.valueOf(ShellContext.firstElement(v) == null ? "" : ShellContext.firstElement(v));
					String old = before.get(a);
					if( old != null ) {
						// x+=v shows what was added, as bash's
						now = now.startsWith(old) ? now.substring(old.length()) : a.value == null ? "" : a.value.raw;
					}
					// (an array's words as written, as bash shows them)
					String shown = a.array != null ? "("+String.join(" ", a.array.stream().map(CommandPrinter::asKept).toList())+")"
							: assigned(now);
					traceStream(sc, streams.err()).println(prefix+a.name+(a.append ? "+=" : "=")+shown);
				}
			}
		} catch (ExpansionError e) {
			return expansionError(sc, e);
		} catch (IOException e) {
			error(sc, message(e));
			return 1;
		} finally {
			streams.restore(sc);
			Redirects.close(opened);
		}
		return sc.console.substitutionCount() != substitutions ? sc.console.getLastExitCode() : 0;
	}

	/** run the command name with its arguments, its redirects applied around it */
	/** run, its redirects applied already */
	private int runRedirected(String name, List<Object> args, Ast.SimpleCommand c, ShellContext sc, Expander ex) throws IOException {
		Ast.SimpleCommand plain = new Ast.SimpleCommand();
		plain.words.addAll(c.words);
		plain.assignments.addAll(c.assignments);
		plain.line = c.line;
		plain.start = c.start;
		plain.end = c.end;
		return run(name, args, plain, sc, ex);
	}

	private int run(String name, List<Object> args, Ast.SimpleCommand c, ShellContext sc, Expander ex) throws IOException {
		boolean keepRedirects = name.equals("exec") && args.isEmpty();
		Redirects.Saved streams = Redirects.Saved.of(sc);
		List<Closeable> opened = null;
		Integer status = null;
		int ret = 0;
		try {
			if( tracing(sc)) {
				// on the shell's standard error, not the command's (2>&1 does not take it)
				trace(name, args, sc);
			}
			opened = Redirects.apply(c.redirects, sc, ex);
			ret = dispatch(name, args, sc, ex);
		} catch (ReturnException e) {
			status = e.exitCode;
			if( sc.isInFunction() || sc.sourceDepth > 0 ) {
				throw e;
			}
		} catch (ExitException e) {
			status = e.exitCode;
			throw e;
		} catch (FshException e) {
			throw e;
		} catch (ExpansionError e) {
			status = expansionError(sc, e);
		} catch (Programs.NotExecutable e) {
			error(sc, e.getMessage());
			status = 126;
		} catch (Exception e) {
			error(sc, message(e));
			status = 1;
		} finally {
			if( !keepRedirects ) {
				streams.restore(sc);
				Redirects.close(opened);
			}
			if( status != null ) {
				ret = status;
			}
			// (a function's commands moved it)
			sc.line = c.line;
			sc.console.setLastExitCode(ret);
			Object last = args.isEmpty() ? name : args.get(args.size()-1);
			sc.console.lastArgument = last instanceof Ast.Assignment a ? a.name : String.valueOf(last);
		}
		return ret;
	}

	private static String message(Exception e) {
		String msg = e.getMessage();
		if( msg == null ) {
			msg = e.toString();
			int dot = msg.lastIndexOf('.');
			if( dot > 0 ) {
				msg = msg.substring(dot+1);
			}
		}
		// Java's "file (reason)" is bash's "file: reason"
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*) \\(([A-Z][^()]*)\\)$").matcher(msg);
		if( e instanceof java.io.FileNotFoundException && m.matches()) {
			msg = m.group(1)+": "+m.group(2);
		}
		return msg;
	}

	private static boolean tracing(ShellContext sc) {
		return sc.console.isOptionEnabled(Option.PrintCommandTrace);
	}

	/** PS4 is being expanded (a $( ) in it is not traced) */
	private static final ThreadLocal<Boolean> expandingPs4 = ThreadLocal.withInitial(() -> false);

	/**
	 * What comes before a set -x line: PS4 expanded ("+ " if it is unset), its first character
	 * repeated once more for each $( ) this is in.
	 */
	private static String ps4(ShellContext sc) {
		Object raw = sc.getVariable("PS4");
		String ps4 = raw == null ? "+ " : raw.toString();
		if( ps4.indexOf('$') >= 0 || ps4.indexOf('`') >= 0 ) {
			expandingPs4.set(true);
			try {
				ps4 = expanderFor(sc).string(Parser.fragment(ps4, Parser.Fragment.QUOTED));
			} catch (RuntimeException e) {
				// as written
			} finally {
				expandingPs4.set(false);
			}
		}
		if( ps4.isEmpty() || sc.substitutionLevel == 0 ) {
			return ps4;
		}
		return String.valueOf(ps4.charAt(0)).repeat(sc.substitutionLevel)+ps4;
	}

	/** set -x: a line on err (the shell's standard error) */
	private static void trace(ShellContext sc, PrintStream err, String text) {
		if( !expandingPs4.get()) {
			traceStream(sc, err).println(ps4(sc)+text);
		}
	}

	/** where set -x writes: the descriptor $BASH_XTRACEFD names, if it is open, else err */
	static PrintStream traceStream(ShellContext sc, PrintStream err) {
		Object fd = sc.getVariable("BASH_XTRACEFD");
		if( fd != null && fd.toString().trim().matches("[0-9]{1,9}")) {
			int n = Integer.parseInt(fd.toString().trim());
			if( n == 1 ) {
				return sc.stdout;
			}
			if( n > 2 ) {
				Console.FileDiscriptor d = sc.console.getFileDistcriptor(n);
				if( d != null && d.getOut() != null ) {
					return d.getOut();
				}
			}
		}
		return err;
	}

	/** set -x: the command as it runs */
	private void trace(String name, List<Object> args, ShellContext sc) {
		StringBuilder line = new StringBuilder(quote(name));
		for(Object a : args) {
			line.append(' ').append(a instanceof Ast.Assignment as ? text(as) : quote(String.valueOf(a)));
		}
		trace(sc, sc.stderr, line.toString());
	}

	/**
	 * The DEBUG trap, before a command (text is $BASH_COMMAND): not in a function, ( ) or $( )
	 * unless set -T.
	 */
	private static void debugTrap(ShellContext sc, String text) {
		if( sc.trapLine == null ) {
			// ($BASH_COMMAND: in a function too, where the trap does not run)
			sc.currentCommand = text;
		}
		if( sc.debugBlocked > 0 && !sc.console.isOptionEnabled(Option.FuncTrace) && !sc.debugTrapHere()) {
			return;
		}
		sc.console.runTrap(ConsoleMetaSignal.Debug, sc);
	}

	/** a value as set -x shows it after name= (an empty one is nothing) */
	private static String assigned(String v) {
		return v.isEmpty() ? "" : quote(v);
	}

	/**
	 * A word as set -x shows it (and as the shell reads it back): 'quoted' if it is empty, has
	 * blanks or special characters, or starts with ~ or #.
	 */
	private static String quote(String s) {
		if( s.chars().anyMatch(Character::isISOControl)) {
			// $'\t' for what does not print (bash's ansic_quote)
			return Declarations.quote(s);
		}
		boolean plain = !s.isEmpty() && !s.startsWith("~") && !s.startsWith("#");
		for (int i = 0; plain && i < s.length(); i++) {
			plain = " \t\n'\"\\|&;()<>!{}*[?]$`".indexOf(s.charAt(i)) < 0;
		}
		return plain ? s : "'"+s.replace("'", "'\\''")+"'";
	}

	/** the builtins the executor runs itself (not a ShellCommand) */
	private static final java.util.Set<String> OWN_BUILTINS = java.util.Set.of("break", "continue", "eval", "declare",
			"typeset", "local", "readonly", "export", "exec");

	private int dispatch(String name, List<Object> args, ShellContext sc, Expander ex) throws IOException {
		return dispatch(name, args, sc, ex, true);
	}

	private int dispatch(String name, List<Object> args, ShellContext sc, Expander ex, boolean functions) throws IOException {
		if( (name.equals("command") || name.equals("builtin")) && !args.isEmpty()) {
			// command typeset ..., builtin declare ...: one the executor runs itself
			int k = 0;
			while( k < args.size() && name.equals("command") && "-p".equals(String.valueOf(args.get(k)))) {
				k++;
			}
			if( k < args.size() && "--".equals(String.valueOf(args.get(k)))) {
				k++;
			}
			if( k < args.size() && args.get(k) instanceof String b && OWN_BUILTINS.contains(b)) {
				boolean command = name.equals("command");
				if( command ) {
					// (a special builtin's failure does not end the shell)
					sc.viaCommand++;
				}
				try {
					return dispatch(b, new ArrayList<>(args.subList(k+1, args.size())), sc, ex, false);
				} finally {
					if( command ) {
						sc.viaCommand--;
					}
				}
			}
		}
		// (aliases are expanded when the command is read: see Parser.aliases)
		// a function comes before a builtin of its name, but in posix mode not before a special builtin
		ShellFunction fn = functions ? sc.getFunction(name) : null;
		if( fn != null && !(sc.console.isOptionEnabled(Option.Posix) && SPECIAL_BUILTINS.contains(name))) {
			return fn.invoke(arguments(strings(args)), sc);
		}
		switch (name) {
		case "[": {
			// [ expr ]: test, which needs the ]
			List<String> words = strings(args);
			if( words.isEmpty() || !words.get(words.size()-1).equals("]")) {
				error(sc, "[: missing `]'");
				return 2;
			}
			args = new ArrayList<>(words.subList(0, words.size()-1));
			name = "__bracket_test";
			break;
		}
		case "break":
		case "continue":
			return loopControl(name, strings(args), sc);
		case "eval": {
			String code = String.join(" ", strings(args)).trim();
			// (read as bash reads it: a newline at its end)
			if( code.isEmpty()) {
				return 0;
			}
			// (set -x shows its commands one level in)
			sc.substitutionLevel++;
			try {
				return run(sc, code+"\n", "eval", Math.max(1, sc.currentLine()));
			} finally {
				sc.substitutionLevel--;
			}
		}
		case "declare":
		case "typeset":
		case "local":
			return new Declarations(this, sc, ex).declare(name, args);
		case "readonly":
			if( !args.contains("-f")) {
				// readonly is declare -gr (its listing too)
				List<Object> all = new ArrayList<>(args);
				all.add(0, "-gr");
				return new Declarations(this, sc, ex).declare(name, all);
			}
			break;
		case "export":
			if( args.stream().anyMatch(a -> a instanceof String o && o.startsWith("-") && o.matches("-[aAilu]+"))) {
				// export -a x=(..): declare -gx
				List<Object> all = new ArrayList<>(args);
				all.add(0, "-gx");
				return new Declarations(this, sc, ex).declare(name, all);
			}
			break;
		case "exec":
			if( args.isEmpty()) {
				// exec >file: the redirects stay
				return 0;
			}
			break;
		default:
		}
		ShellFunction f = sc.getFunction(name);
		if( f != null && !(sc.console.isOptionEnabled(Option.Posix) && SPECIAL_BUILTINS.contains(name))) {
			return f.invoke(arguments(strings(args)), sc);
		}
		if( name.equals("export") || name.equals("readonly")) {
			// export x=1 a=(1 2): assigned here, then the builtin sees the names
			List<String> names = new ArrayList<>();
			for(Object a : args) {
				if( a instanceof Ast.Assignment as ) {
					if( sc.console.isReadonly(sc.readonlyName(as.name))) {
						error(sc, sc.readonlyName(as.name)+": readonly variable");
						return 1;
					}
					assign(as, sc, ex, false);
					names.add(as.name);
				} else {
					names.add(String.valueOf(a));
				}
			}
			args = new ArrayList<>(names);
		}
		Constructor<? extends ShellCommand> con = sc.console.builtin(name);
		if( con != null ) {
			ShellCommand cmd;
			try {
				cmd = con.newInstance();
			} catch (ReflectiveOperationException e) {
				throw new IOException(e);
			}
			cmd.setArgs(arguments(strings(args)));
			String outer = sc.builtin;
			sc.builtin = name;
			int ret;
			try {
				ret = cmd.process(sc);
			} finally {
				sc.builtin = outer;
			}
			if( !BASH_BUILTINS.contains(name)) {
				// a program in bash (sleep, ls ...): its end is a child's end for a SIGCHLD trap
				Programs.childEnded(sc);
			}
			if( name.equals("alias")) {
				for(String a : strings(args)) {
					int eq = a.indexOf('=');
					if( eq > 0 ) {
						sc.console.aliasLines.put(a.substring(0, eq), where(sc));
					}
				}
			}
			if( ret == 0 && sc.stdout instanceof ClosedStream && sc.stdout.checkError()) {
				// echo hi >&-
				error(sc, name+": write error: Bad file descriptor");
				ret = 1;
			}
			return ret;
		}
		return Programs.run(name, strings(args), sc);
	}

	/** this script's line now, as aliasLines keeps it */
	private String where(ShellContext sc) {
		return System.identityHashCode(this)+":"+sc.line;
	}

	/** break [n], continue [n] */
	private static int loopControl(String name, List<String> args, ShellContext sc) {
		int n = 1;
		if( !args.isEmpty() && args.get(0).equals("--")) {
			args = args.subList(1, args.size());
		}
		if( !args.isEmpty()) {
			try {
				n = Integer.parseInt(args.get(0));
			} catch (NumberFormatException e) {
				error(sc, name+": "+args.get(0)+": numeric argument required");
				return 1;
			}
			if( n < 1 ) {
				error(sc, name+": "+args.get(0)+": loop count out of range");
				return 1;
			}
		}
		if( sc.loopDepth == 0 ) {
			if( !sc.console.isOptionEnabled(Option.Posix)) {
				// (posix mode says nothing)
				error(sc, name+": only meaningful in a `for', `while', or `until' loop");
			}
			return 0;
		}
		throw new LoopControlException(name.equals("break") ? LoopControl.Break : LoopControl.Continue, Math.min(n, sc.loopDepth));
	}

	private static List<String> strings(List<Object> args) {
		List<String> ret = new ArrayList<>();
		for(Object a : args) {
			ret.add(String.valueOf(a));
		}
		return ret;
	}

	private static Argument [] arguments(List<String> args) {
		Argument [] ret = new Argument[args.size()];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = new Argument(args.get(i));
		}
		return ret;
	}

	public static boolean isName(String s) {
		return s.matches("[A-Za-z_][A-Za-z0-9_]*");
	}

	// ------------------------------------------------------------------ assignments

	/**
	 * name=value, name+=value, name[i]=value, name=(words): an indexed array's subscript is
	 * arithmetic, an associative array's is text; declare -i makes a value arithmetic.
	 */
	/** posix's special builtins */
	/** bash's builtins: fsh's other builtins are programs there */
	static final java.util.Set<String> BASH_BUILTINS = java.util.Set.of(".", ":", "[", "alias", "bg", "bind", "break",
			"builtin", "caller", "cd", "command", "compgen", "complete", "compopt", "continue", "declare", "dirs",
			"disown", "echo", "enable", "eval", "exec", "exit", "export", "false", "fc", "fg", "getopts", "hash",
			"help", "history", "jobs", "kill", "let", "local", "logout", "mapfile", "popd", "printf", "pushd", "pwd",
			"read", "readarray", "readonly", "return", "set", "shift", "shopt", "source", "suspend", "test", "times",
			"trap", "true", "type", "typeset", "ulimit", "umask", "unalias", "unset", "wait");

	static final java.util.Set<String> SPECIAL_BUILTINS = java.util.Set.of("break", ":", ".", "continue", "eval", "exec", "exit",
			"export", "readonly", "return", "set", "shift", "times", "trap", "unset");

	void assign(Ast.Assignment a, ShellContext sc, Expander ex, boolean local) {
		if( !local && !a.append && sc.rawVariable(a.name) instanceof ShellContext.NameRef r && r.target().isEmpty()) {
			// a nameref with no value yet: the value is what it names (as written: not evaluated
			// for -i), r[0]=v is an error
			if( a.index != null ) {
				sc.error("`': not a valid identifier");
				return;
			}
			if( a.array == null ) {
				String v = a.value == null ? "" : ex.assignment(a.value);
				if( !ShellContext.validReference(v)) {
					// (as bash: the rest of the line is not run)
					sc.error("`"+v+"': not a valid identifier");
					sc.console.setLastExitCode(1);
					throw new AbandonLine(a.line);
				}
				sc.setVariable(a.name, v);
				return;
			}
		}
		if( a.index == null && a.array == null ) {
			// a nameref to an element (declare -n r='x[2]'; r=v): that element
			String target = sc.resolveName(a.name);
			int b = target.indexOf('[');
			if( b > 0 && target.endsWith("]") && !target.equals(a.name)) {
				if( sc.selfReference(a.name)) {
					// a function's local -n a=a[0]
					sc.error("`"+target+"': not a valid identifier");
					return;
				}
				if( target.substring(0, b).equals(a.name)) {
					// a=foo with a -> b -> a[1]: a is an array again (bash's)
					sc.error("warning: "+a.name+": removing nameref attribute");
					sc.unSetVariable(a.name, false);
				}
				Ast.Assignment element = new Ast.Assignment();
				element.name = target.substring(0, b);
				element.index = target.substring(b+1, target.length()-1);
				element.append = a.append;
				element.value = a.value;
				element.line = a.line;
				element.start = a.start;
				element.end = a.end;
				assign(element, sc, ex, false);
				return;
			}
		}
		Object v = value(a, sc, ex, local, false);
		if( v == null ) {
			return;
		}
		if( local ) {
			sc.setLocalVariable(a.name, v);
		} else {
			sc.setVariable(a.name, v);
		}
	}

	/**
	 * The new value of a's variable, or null if a set an element itself (a[i]=v). assoc: an
	 * array literal is associative (declare -A).
	 */
	Object value(Ast.Assignment a, ShellContext sc, Expander ex, boolean local, boolean assoc) {
		Object old = local ? null : sc.getVariable(a.name);
		if( a.array != null ) {
			if( a.index != null ) {
				throw new ExpansionError(a.name+"["+a.index+"]: cannot assign list to array member");
			}
			return array(a, sc, ex, old, assoc || old instanceof Map<?,?>);
		}
		String v = a.value == null ? "" : ex.assignment(a.value);
		if( a.index != null ) {
			element(a, sc, ex, old, v);
			return null;
		}
		if( old instanceof Map<?,?> m ) {
			// m=v, m+=v of an associative array: its element "0"
			sc.setVariable(a.name, "0", elementValue(m.get("0"), v, a.append, sc.console.isInteger(sc.readonlyName(a.name)), sc));
			return null;
		}
		if( old instanceof FshList list && !local ) {
			// x=v, x+=v of an array: its element 0
			FshList copy = copy(list);
			copy.set(0, sc.cased(a.name, elementValue(list.get(0), v, a.append, sc.console.isInteger(sc.readonlyName(a.name)), sc)));
			return copy;
		}
		if( sc.console.isInteger(sc.readonlyName(a.name))) {
			Number n = arithmeticValue(v, sc);
			if( a.append ) {
				Object before = ShellContext.firstElement(old);
				n = arithmeticValue((before == null ? "0" : before)+"+("+n+")", sc);
			}
			return String.valueOf(n);
		}
		if( a.append ) {
			Object before = ShellContext.firstElement(old);
			v = (before == null ? "" : before.toString())+v;
		}
		if( old instanceof FshList list && !local ) {
			// x=v of an array sets its element 0
			FshList copy = copy(list);
			copy.set(0, v);
			return copy;
		}
		return v;
	}

	private static Number arithmeticValue(String text, ShellContext sc) {
		try {
			return Arithmetic.evaluate(text, sc);
		} catch (Arithmetic.ArithmeticError e) {
			throw new ExpansionError(e.getMessage());
		} catch (ShellContext.ReadonlyException e) {
			throw new ExpansionError(e.getMessage());
		}
	}

	private static FshList copy(FshList list) {
		FshList ret = new FshList();
		for(int i : list.getIndexes()) {
			ret.set(i, list.get(i));
		}
		return ret;
	}

	/** a[i]=v (i arithmetic, negative from the end) or m[key]=v */
	void element(Ast.Assignment a, ShellContext sc, Expander ex, Object old, String v) {
		if( a.index.isBlank() || !(old instanceof Map<?,?>) && (a.index.equals("@") || a.index.equals("*"))) {
			throw new ExpansionError(a.name+"["+a.index+"]: bad array subscript");
		}
		Word sub = Parser.fragment(a.index, Parser.Fragment.WORD);
		Object key;
		if( old instanceof Map<?,?> ) {
			String k = ex.string(sub);
			if( k.isEmpty()) {
				throw new ExpansionError(a.name+"["+a.index+"]: bad array subscript");
			}
			key = k;
		} else {
			long idx = ex.arithmetic(sub).longValue();
			if( idx < 0 ) {
				long top = old instanceof FshList f && !f.isEmpty() ? f.getIndexes().get(f.size()-1)+1 : old instanceof List<?> l ? l.size() : 0;
				idx += top;
				if( idx < 0 ) {
					throw new ExpansionError(a.name+"["+a.index+"]: bad array subscript");
				}
			}
			key = (int) idx;
		}
		if( a.append ) {
			Object before = old instanceof Map<?,?> m ? m.get(key) : old instanceof List<?> l && key instanceof Integer i ? (l instanceof FshList f ? f.get(i) : i < l.size() ? l.get(i) : null) : null;
			v = sc.console.isInteger(sc.readonlyName(a.name)) ? (before == null ? "0" : before)+"+("+v+")" : (before == null ? "" : before.toString())+v;
		}
		if( sc.console.isInteger(sc.readonlyName(a.name))) {
			v = String.valueOf(arithmeticValue(v, sc));
		}
		v = String.valueOf(sc.cased(a.name, v));
		if( old != null && !(old instanceof List<?>) && !(old instanceof Map<?,?>) && key instanceof Integer ) {
			// x=1; x[1]=2: x becomes an array with 1 at 0
			FshList list = new FshList();
			list.set(0, old);
			list.set((Integer) key, v);
			sc.setVariable(a.name, list);
			return;
		}
		sc.setVariable(a.name, key, v);
	}

	/** name=(words): [i]=v sets element i, other words are added after it */
	private Object array(Ast.Assignment a, ShellContext sc, Expander ex, Object old, boolean assoc) {
		if( assoc ) {
			Map<String,Object> map = new TreeMap<>();
			if( a.append && old instanceof Map<?,?> m ) {
				for(Map.Entry<?,?> e : m.entrySet()) {
					map.put(String.valueOf(e.getKey()), e.getValue());
				}
			}
			boolean integer = sc.console.isInteger(sc.readonlyName(a.name));
			if( !a.array.isEmpty() && keyValue(a.array.get(0)) == null ) {
				// m=(k1 v1 k2 v2): keys and values in turn (a key with no value gets ""), bash 5.1's
				List<String> words = new ArrayList<>();
				for(Word w : a.array) {
					words.addAll(ex.expand(w));
				}
				for (int i = 0; i < words.size(); i += 2) {
					String k = words.get(i);
					String v = i+1 < words.size() ? words.get(i+1) : "";
					if( k.isEmpty()) {
						error(sc, a.name+": bad array subscript");
						continue;
					}
					map.put(k, sc.cased(a.name, elementValue(map.get(k), v, false, integer, sc)));
				}
				return map;
			}
			for(Word w : a.array) {
				boolean [] plus = new boolean[1];
				Word [] kv = keyValue(w, plus);
				if( kv == null ) {
					error(sc, a.name+": "+w.raw+": must use subscript when assigning associative array");
					continue;
				}
				String k = ex.string(kv[0]);
				map.put(k, sc.cased(a.name, elementValue(map.get(k), ex.assignment(kv[1]), plus[0], integer, sc)));
			}
			return map;
		}
		FshList list = new FshList();
		int next = 0;
		if( a.append ) {
			if( old instanceof FshList f ) {
				list = copy(f);
				next = f.isEmpty() ? 0 : f.getIndexes().get(f.size()-1)+1;
			} else if( old instanceof List<?> l ) {
				for(Object o : l) {
					list.set(next++, o);
				}
			} else if( old != null ) {
				list.set(next++, old);
			}
		}
		boolean integer = sc.console.isInteger(sc.readonlyName(a.name));
		for(Word w : a.array) {
			boolean [] plus = new boolean[1];
			Word [] kv = keyValue(w, plus);
			if( kv != null ) {
				String k = kv[0].raw == null ? "" : kv[0].raw;
				String item = w.raw == null ? k : w.raw;
				if( k.isBlank()) {
					// x=([]=v): said, and the rest is not assigned
					error(sc, item+": bad array subscript");
					break;
				}
				if( k.equals("@") || k.equals("*")) {
					error(sc, item+": cannot assign to non-numeric index");
					continue;
				}
				int idx = (int) ex.arithmetic(kv[0]).longValue();
				if( idx < 0 ) {
					// from the end of what is there so far
					idx += list.isEmpty() ? 0 : list.getIndexes().get(list.size()-1)+1;
					if( idx < 0 ) {
						error(sc, item+": bad array subscript");
						continue;
					}
				}
				list.set(idx, sc.cased(a.name, elementValue(list.get(idx), ex.assignment(kv[1]), plus[0], integer, sc)));
				next = idx+1;
			} else {
				for(String s : ex.expand(w)) {
					list.set(next++, sc.cased(a.name, elementValue(null, s, false, integer, sc)));
				}
			}
		}
		return list;
	}

	/** an array literal's value: [k]+=v adds to (or appends to) what is there; -i evaluates it */
	private static Object elementValue(Object before, String v, boolean append, boolean integer, ShellContext sc) {
		if( integer ) {
			return String.valueOf(arithmeticValue(append && before != null ? before+"+("+v+")" : v, sc));
		}
		return append && before != null ? before+v : v;
	}

	/** [key]=value in an array literal: the key and value words, or null if w is not one */
	static Word [] keyValue(Word w) {
		return keyValue(w, new boolean[1]);
	}

	/** keyValue, and append[0] says whether it was [key]+=value */
	static Word [] keyValue(Word w, boolean [] append) {
		if( w.parts.isEmpty() || !(w.parts.get(0) instanceof Word.Literal l) || !l.text().startsWith("[")) {
			return null;
		}
		if( w.raw != null && w.raw.startsWith("[")) {
			// as written: a quoted key may have [ and ] in it (["a]b"]=v)
			String r = w.raw;
			int depth = 0;
			int close = -1;
			for (int i = 0; i < r.length() && close < 0; i++) {
				char c = r.charAt(i);
				if( c == '\\' ) {
					i++;
				} else if( c == '\'' ) {
					int end = r.indexOf('\'', i+1);
					i = end < 0 ? r.length() : end;
				} else if( c == '"' ) {
					for(i++; i < r.length() && r.charAt(i) != '"'; i++) {
						if( r.charAt(i) == '\\' ) {
							i++;
						}
					}
				} else if( c == '[' ) {
					depth++;
				} else if( c == ']' && --depth == 0 ) {
					close = i;
				}
			}
			if( close < 0 ) {
				return null;
			}
			int valueAt;
			if( r.startsWith("+=", close+1)) {
				append[0] = true;
				valueAt = close+3;
			} else if( r.startsWith("=", close+1)) {
				valueAt = close+2;
			} else {
				return null;
			}
			Word key = Parser.fragment(r.substring(1, close), Parser.Fragment.WORD);
			key.raw = r.substring(1, close);
			Word value = Parser.fragment(r.substring(valueAt), Parser.Fragment.WORD);
			value.raw = r.substring(valueAt);
			value.line = w.line;
			return new Word[] {key, value};
		}
		String t = l.text();
		int depth = 0;
		int close = -1;
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if( c == '[' ) {
				depth++;
			} else if( c == ']' && --depth == 0 ) {
				close = i;
				break;
			}
		}
		if( close >= 0 && t.startsWith("+=", close+1)) {
			append[0] = true;
			close++;
		} else if( close < 0 || close+1 >= t.length() || t.charAt(close+1) != '=' ) {
			return null;
		}
		int keyEnd = append[0] ? close-1 : close;
		Word key = Parser.fragment(t.substring(1, keyEnd), Parser.Fragment.WORD);
		key.raw = t.substring(1, keyEnd);
		Word value = new Word();
		value.line = w.line;
		value.start = w.start+close+2;
		value.end = w.end;
		value.raw = w.raw.length() > close+2 ? w.raw.substring(close+2) : "";
		if( close+2 < t.length()) {
			value.parts.add(new Word.Literal(t.substring(close+2)));
		}
		value.parts.addAll(w.parts.subList(1, w.parts.size()));
		return new Word[] {key, value};
	}

	// ------------------------------------------------------------------ substitutions

	/** >(cmd): cmd runs in a subshell, reading what was written to the file */
	static void outputSubstitution(String code, String file, ShellContext primary) {
		ShellContext ctx = primary.subShell();
		Console.Snapshot saved = null;
		try (InputStream in = new java.io.FileInputStream(file)) {
			ctx.stdin = in;
			saved = primary.console.snapshot();
			run(ctx, code);
		} catch (ExitException e) {
		} catch (Exception e) {
			primary.stderr.println(message(e));
		} finally {
			if( saved != null ) {
				try {
					primary.console.restore(saved);
				} catch (IOException e) {
				}
			}
			ctx.stdout.flush();
			new java.io.File(file).delete();
		}
	}

	/** what expansion needs from here: $( ), ${ ; }, <( ) and >( ) */
	private final class Host implements Expander.Host {
		private final ShellContext sc;

		Host(ShellContext sc) {
			this.sc = sc;
		}

		@Override
		public String commandOutput(Ast.Sequence body, String text, boolean backquote) {
			Executor ex = Executor.this;
			Ast.Sequence seq = body;
			if( seq == null ) {
				String code = backquote ? backtickCode(text) : text;
				try {
					// (its lines are counted from the command's, as bash counts them)
					seq = Parser.parse(code, Math.max(1, sc.currentLine()));
				} catch (SyntaxError e) {
					error(sc, e.getMessage());
					sc.console.substitutionDone(2);
					return "";
				}
				ex = new Executor(seq.source);
			}
			String file = ex.readFileForm(seq, sc);
			if( file != null ) {
				return file;
			}
			return ex.capture(seq, sc);
		}

		@Override
		public String functionOutput(Ast.Sequence body, String text, boolean reply) {
			int status = 0;
			if( reply ) {
				// ${| list; }: the output goes where it goes; the value is $REPLY
				Object before = sc.getVariable("REPLY");
				sc.setVariable("REPLY", null);
				sc.enterNofork();
				try {
					// (REPLY is local to it, as in bash)
					sc.setLocalVariable("REPLY", null);
					status = noforkList(body);
					Object v = sc.getVariable("REPLY");
					return v == null ? "" : v.toString();
				} catch (IOException e) {
					status = 1;
					return "";
				} finally {
					sc.exitFunction(null);
					sc.setVariable("REPLY", before);
					sc.console.substitutionDone(status);
				}
			}
			PrintStream out = sc.stdout;
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			sc.stdout = new PrintStream(bao, true);
			sc.enterNofork();
			try {
				status = noforkList(body);
			} catch (IOException e) {
				status = 1;
			} finally {
				sc.exitFunction(null);
				sc.stdout.flush();
				sc.stdout = out;
				sc.console.substitutionDone(status);
			}
			return bao.toString();
		}

		/** the commands of ${ list; }: return ends them */
		private int noforkList(Ast.Sequence body) throws IOException {
			int loops = sc.loopDepth;
			sc.loopDepth = 0;
			// as $( ): set -e is off in it, unless posix mode or shopt -s inherit_errexit
			boolean errexit = sc.console.isOptionEnabled(Option.ExitImediately);
			boolean off = errexit && !sc.console.isOptionEnabled(Option.Posix)
					&& !Boolean.TRUE.equals(sc.console.getShellOptions().get("inherit_errexit"));
			if( off ) {
				sc.console.setOption(Option.ExitImediately, false);
			}
			try {
				return list(body, sc);
			} catch (us.bringardner.fsh.signal.ReturnException e) {
				return e.exitCode;
			} finally {
				sc.loopDepth = loops;
				if( off ) {
					sc.console.setOption(Option.ExitImediately, true);
				}
			}
		}

		@Override
		public String processSubstitution(char direction, Ast.Sequence body, String text) {
			try {
				Ast.Sequence seq = body;
				Executor ex = Executor.this;
				if( seq == null ) {
					seq = Parser.parse(text);
					ex = new Executor(seq.source);
				}
				return ProcessSubstitutions.start(direction, seq, ex, text, sc);
			} catch (IOException | SyntaxError e) {
				throw new ExpansionError("process substitution: "+e.getMessage());
			}
		}

		@Override
		public void warning(String message) {
			error(sc, message);
		}
	}

	/**
	 * The command in `...`: as in bash, \` \\ and \$ there stand for the character (so
	 * `echo \`date\`` nests); another backslash is kept.
	 */
	static String backtickCode(String code) {
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < code.length(); i++) {
			char c = code.charAt(i);
			if( c == '\\' && i+1 < code.length() && "`\\$".indexOf(code.charAt(i+1)) >= 0 ) {
				ret.append(code.charAt(++i));
			} else if( c == '\\' && i+1 < code.length() && code.charAt(i+1) == '\n' ) {
				// backslash-newline is removed (even in '...')
				i++;
			} else {
				ret.append(c);
			}
		}
		return ret.toString();
	}

	/** $(< file): the file's text, without running anything; null if seq is not that */
	private String readFileForm(Ast.Sequence seq, ShellContext sc) {
		if( seq.items.size() != 1 || seq.items.get(0).background ) {
			return null;
		}
		Ast.AndOr ao = seq.items.get(0).command;
		if( ao.pipelines.size() != 1 || ao.pipelines.get(0).commands.size() != 1 || ao.pipelines.get(0).negated ) {
			return null;
		}
		if( !(ao.pipelines.get(0).commands.get(0) instanceof Ast.SimpleCommand s) || !s.words.isEmpty() || !s.assignments.isEmpty()
				|| s.redirects.size() != 1 || !s.redirects.get(0).op.equals("<") || s.redirects.get(0).fd != null ) {
			return null;
		}
		int status = 0;
		String path = "";
		try {
			// (expanded as a redirect's word: $(< *.txt) of one file is that file)
			try {
				path = Redirects.target(s.redirects.get(0), expander(sc));
			} catch (IOException e) {
				error(sc, e.getMessage());
				status = 1;
				return "";
			}
			try (InputStream in = sc.getFileSource(path).getInputStream()) {
				return new String(in.readAllBytes());
			}
		} catch (IOException e) {
			error(sc, path+": No such file or directory");
			status = 1;
			return "";
		} finally {
			sc.console.substitutionDone(status);
		}
	}

	/**
	 * Run seq in a subshell and return its standard output. As in bash, set -e is off there
	 * (unless shopt -s inherit_errexit), and $? after it is its status.
	 */
	String capture(Ast.Sequence seq, ShellContext primary) {
		ShellContext ctx = primary.subShell();
		ctx.errTrapBlocked++;
		ctx.substitutionLevel++;
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ctx.stdout = new PrintStream(bao, true);
		Console.Snapshot saved = null;
		int status = 0;
		try {
			saved = primary.console.snapshot();
			if( !Glob.option(primary, "inherit_errexit")) {
				primary.console.setOption(Option.ExitImediately, false);
			}
			status = list(seq, ctx);
		} catch (ExitException e) {
			status = e.exitCode;
		} catch (FshException e) {
			status = 1;
		} catch (Exception e) {
			status = 1;
			primary.stderr.println(message(e));
		} finally {
			if( saved != null ) {
				try {
					primary.console.restore(saved);
				} catch (IOException e) {
					primary.stderr.println(e.getMessage());
				}
			}
			ctx.stdout.flush();
			primary.console.substitutionDone(status);
		}
		return bao.toString();
	}
}
