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
		Ast.Sequence seq;
		try {
			seq = Parser.parse(code);
		} catch (SyntaxError e) {
			syntaxError(sc, e, code, 1);
			return 2;
		}
		return new Executor(code).list(seq, sc);
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
		Ast.Sequence seq;
		try {
			seq = Parser.parse(code, firstLine);
		} catch (SyntaxError e) {
			syntaxError(sc, e, code, firstLine);
			return 2;
		}
		Executor ex = new Executor(code);
		int ret = 0;
		int abandoned = -1;
		for(Ast.Item item : seq.items) {
			if( item.command.line == abandoned ) {
				continue;
			}
			if( !sc.console.isInteractive && sc.console.isOptionEnabled(Option.History)) {
				// set -o history in a script: each command it runs is kept, as bash keeps them
				sc.console.rememberCommand(ex.text(item.command).trim()+(item.background ? " &" : ""));
			}
			try {
				ret = ex.item(item, sc);
			} catch (AbandonLine e) {
				// the rest of the line is not run (in a function too, as bash's)
				abandoned = item.command.line;
				ret = 1;
				sc.console.setLastExitCode(1);
			}
		}
		return ret;
	}

	/** an error after which bash does not run the rest of the line (x=1 of a readonly x) */
	static final class AbandonLine extends us.bringardner.fsh.signal.FshException {
		private static final long serialVersionUID = 1L;
		final int line;

		AbandonLine(int line) {
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
		String prefix = prefix(sc, e.line);
		if( sc.console.inCommandString && !sc.console.isInteractive ) {
			String zero = String.valueOf(sc.getVariable("$0"));
			if( prefix.startsWith(zero+": ")) {
				prefix = zero+": -c: "+prefix.substring(zero.length()+2);
			}
		}
		sc.stderr.println(prefix+e.getMessage());
		if( e.getMessage().startsWith("syntax error near unexpected token")) {
			String [] lines = code.split("\n", -1);
			int i = e.line-firstLine;
			if( i >= 0 && i < lines.length ) {
				sc.stderr.println(prefix+"`"+lines[i]+"'");
			}
		}
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
			return new Executor(code).list(seq, sc) == 0;
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
		sc.setVariable(k.name, fds);
		sc.setVariable(k.name+"_PID", String.valueOf(job.pid));
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
		} catch (AbandonLine e) {
			// (an error that ends the line ends the subshell, as bash's)
			ret = 1;
		} finally {
			// its EXIT trap runs as it ends
			sc.console.endSubshellTrap(trap, ret);
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
				sc.setVariable(f.variable, v);
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
			trace(sc, sc.stderr, "(( "+text.trim()+" ))");
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
				sc.setVariable(s.variable, choice);
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
		Pattern rx = Glob.toRegex(pattern);
		if( Glob.option(sc, "nocasematch")) {
			rx = Pattern.compile(rx.pattern(), rx.flags() | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		}
		return rx.matcher(text).matches();
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

	/** an invalid regular expression after =~: status 2 */
	private static final class BadRegex extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	private int cond(Ast.Cond c, ShellContext sc) {
		debugTrap(sc, text(c).trim());
		try {
			return test(c.expression, sc, expander(sc)) ? 0 : 1;
		} catch (BadRegex e) {
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
			traceTest(sc, u.op()+" "+v);
			return us.bringardner.fsh.commands.Test.unaryTest(u.op(), v, sc);
		}
		case Ast.CondBinary b -> {
			if( tracing(sc)) {
				traceTest(sc, ex.string(b.left())+" "+b.op()+" "+ex.string(b.right()));
			}
			return binary(b, sc, ex);
		}
		}
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
			throw new BadRegex();
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
	private static String posixClasses(String rx) {
		String [][] classes = {
				{"word", "\\w", "\\W"}, {"ascii", "\\p{ASCII}", "\\P{ASCII}"}, {"lower", "\\p{Lower}", "\\P{Lower}"},
				{"upper", "\\p{Upper}", "\\P{Upper}"}, {"alpha", "\\p{Alpha}", "\\P{Alpha}"}, {"digit", "\\p{Digit}", "\\P{Digit}"},
				{"alnum", "\\p{Alnum}", "\\P{Alnum}"}, {"punct", "\\p{Punct}", "\\P{Punct}"}, {"graph", "\\p{Graph}", "\\P{Graph}"},
				{"print", "\\p{Print}", "\\P{Print}"}, {"blank", "\\p{Blank}", "\\P{Blank}"}, {"cntrl", "\\p{Cntrl}", "\\P{Cntrl}"},
				{"xdigit", "\\p{XDigit}", "\\P{XDigit}"}, {"space", "\\s", "\\S"}};
		String ret = rx;
		for(String [] c : classes) {
			ret = ret.replace("[:^"+c[0]+":]", c[2]).replace("[:"+c[0]+":]", c[1]);
		}
		return ret;
	}

	// ------------------------------------------------------------------ functions

	private int define(Ast.FunctionDef f, ShellContext sc) {
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
				if( keyword && !declaration && !args.isEmpty() && w.raw.matches("[A-Za-z_][A-Za-z0-9_]*=(?s).*")) {
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
			return assignments(c, sc, ex, substitutions);
		}
		String name = (String) args.remove(0);
		List<Object[]> saved = null;
		if( !keywordAssignments.isEmpty()) {
			saved = new ArrayList<>();
			for(String [] a : keywordAssignments) {
				if( sc.console.isReadonly(a[0])) {
					error(sc, a[0]+": readonly variable");
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
					if( tracing(sc)) {
						trace(sc, sc.stderr, a.name+"="+assigned(v));
					}
					if( sc.console.isReadonly(a.name)) {
						// as bash: said, and the command runs without it
						error(sc, a.name+": readonly variable");
						continue;
					}
					saved.add(new Object[] {a.name, sc.console.getVariable(a.name), sc.getEvironmentVariable(a.name)});
					sc.setVariable(a.name, v);
					sc.setEnvironmentVariable(a.name, v);
				}
			} catch (ExpansionError e) {
				restore(saved, sc);
				return expansionError(sc, e);
			}
		}
		try {
			return run(name, args, c, sc, ex);
		} finally {
			if( saved != null ) {
				restore(saved, sc);
			}
		}
	}

	private static void restore(List<Object[]> saved, ShellContext sc) {
		for (int i = saved.size()-1; i >= 0; i--) {
			Object [] s = saved.get(i);
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
			for(Ast.Assignment a : c.assignments) {
				if( sc.console.isReadonly(a.name)) {
					error(sc, a.name+": readonly variable");
					sc.console.setLastExitCode(1);
					// as in bash, the rest of the line is not run
					throw new AbandonLine(c.line);
				}
				assign(a, sc, ex, false);
			}
			if( prefix != null && !expandingPs4.get()) {
				for(Ast.Assignment a : c.assignments) {
					Object v = sc.getVariable(a.name);
					String shown = a.array != null ? "("+(v instanceof List<?> l ? String.join(" ", strings(new ArrayList<>(l))) : "")+")"
							: assigned(String.valueOf(ShellContext.firstElement(v) == null ? "" : ShellContext.firstElement(v)));
					streams.err().println(prefix+a.name+(a.append ? "+=" : "=")+shown);
				}
			}
		} catch (ExpansionError e) {
			return expansionError(sc, e);
		} catch (IOException e) {
			error(sc, e.getMessage());
			return 1;
		} finally {
			streams.restore(sc);
			Redirects.close(opened);
		}
		return sc.console.substitutionCount() != substitutions ? sc.console.getLastExitCode() : 0;
	}

	/** run the command name with its arguments, its redirects applied around it */
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
			err.println(ps4(sc)+text);
		}
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
		if( sc.debugBlocked > 0 && !sc.console.isOptionEnabled(Option.FuncTrace)) {
			return;
		}
		if( sc.trapLine == null ) {
			sc.currentCommand = text;
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
		boolean plain = !s.isEmpty() && !s.startsWith("~") && !s.startsWith("#");
		for (int i = 0; plain && i < s.length(); i++) {
			plain = " \t\n'\"\\|&;()<>!{}*[?]$`".indexOf(s.charAt(i)) < 0;
		}
		return plain ? s : "'"+s.replace("'", "'\\''")+"'";
	}

	private int dispatch(String name, List<Object> args, ShellContext sc, Expander ex) throws IOException {
		if( name.equals(".")) {
			name = "source";
		}
		Object alias = sc.getAlias(name);
		if( alias != null && where(sc).equals(sc.console.aliasLines.get(name))) {
			// as in bash, not on the line that defined it (fsh, unlike bash, expands aliases in
			// scripts without shopt -s expand_aliases)
			alias = null;
		}
		if( alias != null ) {
			StringBuilder code = new StringBuilder(alias.toString());
			for(Object a : args) {
				code.append(' ').append(a instanceof Ast.Assignment as ? text(as) : quote(String.valueOf(a)));
			}
			sc.addActiveAlias(name);
			try {
				return run(sc, code.toString());
			} finally {
				sc.removeActiveAlias(name);
			}
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
			return code.isEmpty() ? 0 : run(sc, code);
		}
		case "declare":
		case "typeset":
		case "local":
			return new Declarations(this, sc, ex).declare(name, args);
		case "exec":
			if( args.isEmpty()) {
				// exec >file: the redirects stay
				return 0;
			}
			break;
		default:
		}
		ShellFunction f = sc.getFunction(name);
		if( f != null ) {
			return f.invoke(arguments(strings(args)), sc);
		}
		if( name.equals("export") || name.equals("readonly")) {
			// export x=1 a=(1 2): assigned here, then the builtin sees the names
			List<String> names = new ArrayList<>();
			for(Object a : args) {
				if( a instanceof Ast.Assignment as ) {
					if( sc.console.isReadonly(as.name)) {
						error(sc, as.name+": readonly variable");
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
			int ret = cmd.process(sc);
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
			error(sc, name+": only meaningful in a `for', `while', or `until' loop");
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

	static boolean isName(String s) {
		return s.matches("[A-Za-z_][A-Za-z0-9_]*");
	}

	// ------------------------------------------------------------------ assignments

	/**
	 * name=value, name+=value, name[i]=value, name=(words): an indexed array's subscript is
	 * arithmetic, an associative array's is text; declare -i makes a value arithmetic.
	 */
	void assign(Ast.Assignment a, ShellContext sc, Expander ex, boolean local) {
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
			return array(a, sc, ex, old, assoc || old instanceof Map<?,?>);
		}
		String v = a.value == null ? "" : ex.assignment(a.value);
		if( a.index != null ) {
			element(a, sc, ex, old, v);
			return null;
		}
		if( sc.console.isInteger(a.name)) {
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
	private void element(Ast.Assignment a, ShellContext sc, Expander ex, Object old, String v) {
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
		if( sc.console.isInteger(a.name)) {
			v = String.valueOf(arithmeticValue(v, sc));
		}
		if( a.append ) {
			Object before = old instanceof Map<?,?> m ? m.get(key) : old instanceof List<?> l && key instanceof Integer i ? (l instanceof FshList f ? f.get(i) : i < l.size() ? l.get(i) : null) : null;
			v = (before == null ? "" : before.toString())+v;
		}
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
			for(Word w : a.array) {
				Word [] kv = keyValue(w);
				if( kv == null ) {
					error(sc, a.name+": "+w.raw+": must use subscript when assigning associative array");
					continue;
				}
				map.put(ex.string(kv[0]), ex.assignment(kv[1]));
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
		for(Word w : a.array) {
			Word [] kv = keyValue(w);
			if( kv != null ) {
				int idx = (int) ex.arithmetic(kv[0]).longValue();
				list.set(idx, ex.assignment(kv[1]));
				next = idx+1;
			} else {
				for(String s : ex.expand(w)) {
					list.set(next++, s);
				}
			}
		}
		return list;
	}

	/** [key]=value in an array literal: the key and value words, or null if w is not one */
	static Word [] keyValue(Word w) {
		if( w.parts.isEmpty() || !(w.parts.get(0) instanceof Word.Literal l) || !l.text().startsWith("[")) {
			return null;
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
		if( close < 0 || close+1 >= t.length() || t.charAt(close+1) != '=' ) {
			return null;
		}
		Word key = Parser.fragment(t.substring(1, close), Parser.Fragment.WORD);
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
					seq = Parser.parse(code);
				} catch (SyntaxError e) {
					error(sc, e.getMessage());
					sc.console.substitutionDone(2);
					return "";
				}
				ex = new Executor(code);
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
				try {
					status = list(body, sc);
					Object v = sc.getVariable("REPLY");
					return v == null ? "" : v.toString();
				} catch (IOException e) {
					status = 1;
					return "";
				} finally {
					sc.setVariable("REPLY", before);
					sc.console.substitutionDone(status);
				}
			}
			PrintStream out = sc.stdout;
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			sc.stdout = new PrintStream(bao, true);
			try {
				status = list(body, sc);
			} catch (IOException e) {
				status = 1;
			} finally {
				sc.stdout.flush();
				sc.stdout = out;
				sc.console.substitutionDone(status);
			}
			return bao.toString();
		}

		@Override
		public String processSubstitution(char direction, Ast.Sequence body, String text) {
			try {
				Ast.Sequence seq = body;
				Executor ex = Executor.this;
				if( seq == null ) {
					seq = Parser.parse(text);
					ex = new Executor(text);
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
			path = expander(sc).string(s.redirects.get(0).target);
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
