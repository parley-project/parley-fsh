package us.bringardner.fsh.exec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellFunction;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.signal.ReturnException;
import us.bringardner.fsh.syntax.Ast;

/**
 * A function defined by the new executor: its body and the script it was read from.
 */
final class AstFunction implements ShellFunction {

	private final Ast.FunctionDef def;
	private final Executor executor;
	private boolean exported;
	private final String file;

	AstFunction(Ast.FunctionDef def, Executor executor, String file) {
		this.def = def;
		this.executor = executor;
		this.file = file;
	}

	@Override
	public String sourceFile() {
		return file;
	}

	@Override
	public String getName() {
		return def.name;
	}

	@Override
	public boolean isExported() {
		return exported;
	}

	@Override
	public void setExported(boolean exported) {
		this.exported = exported;
	}

	@Override
	public int invoke(Argument[] args, ShellContext sc) throws IOException {
		Object [] values = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			values[i] = args[i].getValue(sc);
		}
		int ret = 0;
		int loops = sc.loopDepth;
		// (not run by a builtin, for what the variables say)
		String builtin = sc.builtin;
		sc.builtin = null;
		sc.enterFunction(values, this);
		// break and continue do not reach the caller's loops; the DEBUG trap does not run in
		// it (unless set -T)
		sc.loopDepth = 0;
		sc.debugBlocked++;
		if( sc.console.isOptionEnabled(us.bringardner.fsh.Console.Option.FuncTrace) || sc.debugTrapHere()) {
			// with set -T (or declare -ft) the DEBUG trap runs as the function starts, as in bash,
			// on the line its body starts on
			if( def.body.line > 0 ) {
				sc.line = def.body.line;
			}
			sc.console.runTrap(us.bringardner.fsh.Console.ConsoleMetaSignal.Debug, sc);
		}
		try {
			ret = executor.command(def.body, sc);
		} catch (ReturnException e) {
			ret = e.exitCode;
		} finally {
			sc.loopDepth = loops;
			sc.builtin = builtin;
			sc.debugBlocked--;
			try {
				sc.console.setLastExitCode(ret);
				// (the RETURN trap's $LINENO is the line the function starts on, as bash's)
				if( def.body.line > 0 ) {
					sc.line = def.body.line;
				}
				sc.functionReturning();
			} finally {
				sc.exitFunction(this);
			}
		}
		return ret;
	}

	/**
	 * As bash prints it:
	 * <pre>
	 * f () 
	 * { 
	 *     echo hi;
	 *     local x=1
	 * }
	 * </pre>
	 */
	@Override
	public String declaration() {
		return CommandPrinter.function(executor, getName(), def.body);
	}

	@Override
	public String exportedBody() {
		return CommandPrinter.exported(executor, def.body);
	}
}
