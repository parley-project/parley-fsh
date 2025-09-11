package us.bringardner.shell.antlr.statement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.FileSourceShVisitorImpl;
import us.bringardner.shell.antlr.Statement;

public class CommandSubstitutionStatement extends Statement{
	
	public CommandSubstitutionStatement(ParserRuleContext context) {
		super(context);		
	}
	
	private String stdout ;
	private String stderr ;
	private int exitCode ;
	private Exception error;
	
	
	public String getStdout() {
		return stdout;
	}


	public String getStderr() {
		return stderr;
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
		
		StringBuilder tmp = new StringBuilder();
		for(int idx=1;idx < context.getChildCount()-1; idx++) {
			if( !tmp.isEmpty()) {
				tmp.append(' ');
			}
			tmp.append(context.getChild(idx).getText().trim());
		}
		String code = tmp.toString();
		exitCode =execute(code,primary);
			
		return exitCode;
	}


	public int execute(String code, ShellContext primary) {
		ShellContext ctx = primary.subShell();
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();		
		
		ctx.stdout = new PrintStream(bao);
		ctx.stderr = new PrintStream(bae);
	
		try {
			List<Statement> stmts = FileSourceShVisitorImpl.parse(code);
			for(Statement s : stmts) {
				if((exitCode = s.process(ctx)) !=0) {
					break;
				}
			}
		} catch (Exception e) {
			error = e;
			exitCode = 1;
		} finally {
			stdout = new String(bao.toByteArray());
			//Bash performs command substitution by executing command in a subshell environment and replacing the command substitution with the standard output of the command, 
			//with any trailing newlines deleted
			while(stdout.endsWith("\n")) {
				stdout = stdout.substring(0, stdout.length()-1);
			}
			stderr = new String(bae.toByteArray());
			if( error !=null) {
				stderr += error.toString();
			}	
		}
	
		return exitCode;
	}
	
	
}
