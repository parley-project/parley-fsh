package us.bringardner.shell.antlr.statement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl;
import us.bringardner.shell.antlr.FileSourceShVisitorImpl;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.ExitException;

public class CommandSubstitutionStatement extends Statement{
	
	public CommandSubstitutionStatement(ParserRuleContext context) {
		super(context);		
	}
	
	private String stdout ;
	private int exitCode ;
	private Exception error;
	
	
	public String getStdout() {
		return stdout;
	}


	public int getExitCode() {
		return exitCode;
	}


	public Exception getError() {
		return error;
	}


	@Override
	protected int execute(ShellContext primary) throws IOException {
		exitCode = 0;
		
		// the text between $( and ) as written: joining the tokens with spaces split words
		// (ls *.txt ran as ls * . txt). The last token may be )) when it also closes a nested $( ).
		String code = "";
		int start = context.getStart().getStopIndex()+1;
		int stop = context.getStop().getStopIndex()-1;
		if( stop >= start ) {
			code = context.getStart().getInputStream().getText(Interval.of(start, stop)).trim();
		}
		exitCode =execute(code,primary);
			
		return exitCode;
	}


	/** $(< file): the file's text, without running anything */
	private static final java.util.regex.Pattern READ_FILE = java.util.regex.Pattern.compile("<\\s*([^<>|&;\\s]+|\"[^\"]*\"|'[^']*')");

	private int readFile(String word, ShellContext primary) {
		String path = FileSourceShPreProcessorVisitorImpl.processString(word, primary);
		if( path.length() > 1 && (path.startsWith("\"") && path.endsWith("\"") || path.startsWith("'") && path.endsWith("'"))) {
			path = path.substring(1, path.length()-1);
		}
		try (java.io.InputStream in = primary.getFileSource(path).getInputStream()) {
			stdout = new String(in.readAllBytes());
			exitCode = 0;
		} catch (IOException e) {
			primary.stderr.println(path+": No such file or directory");
			stdout = "";
			exitCode = 1;
		}
		while(stdout.endsWith("\n")) {
			stdout = stdout.substring(0, stdout.length()-1);
		}
		primary.console.substitutionDone(exitCode);
		return exitCode;
	}

	/**
	 * Run code in a subshell and capture its standard output. As in bash, every command runs (a
	 * failure does not stop the rest, exit does), the status is that of the last one, and
	 * standard error is not captured: it goes where the caller's goes.
	 */
	public int execute(String code, ShellContext primary) {
		java.util.regex.Matcher read = READ_FILE.matcher(code.trim());
		if( read.matches()) {
			return readFile(read.group(1), primary);
		}
		ShellContext ctx = primary.subShell();
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		
		ctx.stdout = new PrintStream(bao);
		// a subshell: x=$(cd /; y=1) changes neither the directory nor y
		Console.Snapshot saved = null;
	
		try {
			saved = primary.console.snapshot();
			List<Statement> stmts = FileSourceShVisitorImpl.parse(code);
			for(Statement s : stmts) {
				exitCode = s.process(ctx);
			}
		} catch (ExitException e) {
			exitCode = e.exitCode;
		} catch (Exception e) {
			error = e;
			exitCode = 1;
			String msg = e.getMessage();
			primary.stderr.println(msg != null ? msg : e.toString());
		} finally {
			if( saved != null ) {
				try {
					primary.console.restore(saved);
				} catch (IOException e) {
					primary.stderr.println(e.getMessage());
				}
			}
			stdout = new String(bao.toByteArray());
			//Bash performs command substitution by executing command in a subshell environment and replacing the command substitution with the standard output of the command, 
			//with any trailing newlines deleted
			while(stdout.endsWith("\n")) {
				stdout = stdout.substring(0, stdout.length()-1);
			}
			// $? after x=$(cmd) is the status of cmd
			primary.console.substitutionDone(exitCode);
		}
	
		return exitCode;
	}
	/**
	 * <(cmd): run cmd in a subshell, put its output in a temporary file, and return the file's name
	 * (bash uses a pipe; a file reads the same). The file is deleted when the shell exits.
	 */
	public static String processSubstitution(String token, ShellContext primary) {
		String code = token.substring(2, token.length()-1);
		ShellContext ctx = primary.subShell();
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ctx.stdout = new PrintStream(bao);
		Console.Snapshot saved = null;
		try {
			saved = primary.console.snapshot();
			for(Statement s : FileSourceShVisitorImpl.parse(code)) {
				s.process(ctx);
			}
		} catch (ExitException e) {
		} catch (Exception e) {
			String msg = e.getMessage();
			primary.stderr.println(msg != null ? msg : e.toString());
		} finally {
			if( saved != null ) {
				try {
					primary.console.restore(saved);
				} catch (IOException e) {
					primary.stderr.println(e.getMessage());
				}
			}
		}
		try {
			java.io.File file = java.io.File.createTempFile("bjlshell-", ".fifo");
			file.deleteOnExit();
			ctx.stdout.flush();
			java.nio.file.Files.write(file.toPath(), bao.toByteArray());
			return file.getAbsolutePath();
		} catch (IOException e) {
			throw new RuntimeException("process substitution: "+e.getMessage(), e);
		}
	}
}
