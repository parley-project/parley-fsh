package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import sun.misc.Signal;
import us.bringardner.shell.Console;
import us.bringardner.shell.Console.ConsoleMetaSignal;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import sun.misc.SignalHandler;

public class Trap extends ShellCommand implements SignalHandler {
	static String name = "trap";
	// physical 
	static String help = "trap [-lpP] [action] [sigspec ...]\n"
			+ "The action is a command that is read and executed when the shell receives any of the signals sigspec. If action is absent (and there is a single sigspec) or equal to ‘-’, each specified sigspec’s disposition is reset to the value it had when the shell was started. If action is the null string, then the signal specified by each sigspec is ignored by the shell and commands it invokes.\n"
			+ "\n"
			+ "If no arguments are supplied, trap prints the actions associated with each trapped signal as a set of trap commands that can be reused as shell input to restore the current signal dispositions.\n"
			+ "\n"
			+ "If action is not present and -p has been supplied, trap displays the trap commands associated with each sigspec, or, if no sigspecs are supplied, for all trapped signals, as a set of trap commands that can be reused as shell input to restore the current signal dispositions. The -P option behaves similarly, but displays only the actions associated with each sigspec argument. -P requires at least one sigspec argument. The -P or -p options may be used in a subshell environment (e.g., command substitution) and, as long as they are used before trap is used to change a signal’s handling, will display the state of its parent’s traps.\n"
			+ "\n"
			+ "The -l option prints a list of signal names and their corresponding numbers. Each sigspec is either a signal name or a signal number. Signal names are case insensitive and the SIG prefix is optional. If -l is supplied with no sigspec arguments, it prints a list of valid signal names.\n"
			+ "\n"
			+ "If a sigspec is 0 or EXIT, action is executed when the shell exits. If a sigspec is DEBUG, action is executed before every simple command, for command, case command, select command, (( arithmetic command, [[ conditional command, arithmetic for command, and before the first command executes in a shell function. Refer to the description of the extdebug shell option (see The Shopt Builtin) for details of its effect on the DEBUG trap. If a sigspec is RETURN, action is executed each time a shell function or a script executed with the . or source builtins finishes executing.\n"
			+ "\n"
			+ "If a sigspec is ERR, action is executed whenever a pipeline (which may consist of a single simple command), a list, or a compound command returns a non-zero exit status, subject to the following conditions. The ERR trap is not executed if the failed command is part of the command list immediately following an until or while reserved word, part of the test following the if or elif reserved words, part of a command executed in a && or || list except the command following the final && or ||, any command in a pipeline but the last, (subject to the state of the pipefail shell option), or if the command’s return status is being inverted using !. These are the same conditions obeyed by the errexit (-e) option.\n"
			+ "\n"
			+ "When the shell is not interactive, signals ignored upon entry to a non-interactive shell cannot be trapped or reset. Interactive shells permit trapping signals ignored on entry. Trapped signals that are not being ignored are reset to their original values in a subshell or subshell environment when one is created.\n"
			+ "\n"
			+ "The return status is zero unless a sigspec does not specify a valid signal; non-zero otherwise.";
	
	public Trap() {
		super(name, help);
	}
	
	private enum Args{l,p,P};
	
	
	private static final String [] COMMON_NAMES = {"HUP","INT","QUIT","ILL","TRAP","ABRT","FPE","KILL","BUS","SEGV","SYS","PIPE","ALRM","TERM","URG","STOP","TSTP","CONT","CHLD","TTIN","TTOU","IO","XCPU","XFSZ","VTALRM","PROF","WINCH","USR1","USR2"};
	private static transient Map<Integer,String> locals;
	public static Map<Integer, String> getLocalSignals() {
		if( locals == null ) {
			synchronized (Trap.class) {
				if( locals == null ) {
					Map<Integer,String> tmp = new TreeMap<>();
					for(String name : COMMON_NAMES) {
						try {
							Signal s = new Signal(name);
							tmp.put(s.getNumber(),s.getName());
						} catch (Exception e) {
						}
					}
					locals = tmp;
				}
			}
		}
		
		return locals;
	}

	
	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;
		
		ShellArgument ops = parserArgs(ctx, Args.class);
		if( ops.paths.size()>0) {
			String action = ops.paths.remove(0);
			for(String val : ops.paths) {
				val = val.toUpperCase();
				if( val.startsWith("SIG") ) {
					val = val.substring(3);
				}
				if(Character.isDigit(val.charAt(0))) {
					try {
						int n = Integer.parseInt(val);					
						val = getLocalSignals().get(n);	
					} catch (Exception e) {
						ret = 1;
						ctx.stderr.println(e);
					}
					
				}
				Console.ConsoleMetaSignal cs = Console.ConsoleMetaSignal.find(val);
				if( cs == ConsoleMetaSignal.UnKnown ) {
					try {
						Signal s = new Signal(val);
						ctx.console.registerHandler(s,action);
					} catch (Exception e) {
						ret = 1;
						ctx.stderr.println(e);
					}					
				} else {
					ctx.console.registerHandler(cs,action);
				}
			}
		}
		
		return ret;
	}

	@Override
	public void handle(Signal sig) {
		// TODO Auto-generated method stub
		
	}

}
