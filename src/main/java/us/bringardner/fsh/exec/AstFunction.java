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

	AstFunction(Ast.FunctionDef def, Executor executor) {
		this.def = def;
		this.executor = executor;
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
		sc.enterFunction(values, this);
		// break and continue do not reach the caller's loops
		sc.loopDepth = 0;
		try {
			ret = executor.command(def.body, sc);
		} catch (ReturnException e) {
			ret = e.exitCode;
		} finally {
			sc.loopDepth = loops;
			try {
				sc.console.setLastExitCode(ret);
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
		List<String> lines = new ArrayList<>();
		Ast.Command body = def.body;
		if( body instanceof Ast.BraceGroup g ) {
			for(Ast.Item item : g.body.items) {
				String t = executor.text(item.command).trim();
				while( t.endsWith(";")) {
					t = t.substring(0, t.length()-1).trim();
				}
				if( item.background ) {
					t += " &";
				}
				if( !t.isEmpty()) {
					lines.add(t);
				}
			}
		} else {
			lines.add(executor.text(body).trim());
		}
		StringBuilder ret = new StringBuilder(getName()+" () \n{ \n");
		for (int i = 0; i < lines.size(); i++) {
			ret.append("    ").append(lines.get(i).replace("\n", "\n    ")).append(i < lines.size()-1 ? ";" : "").append('\n');
		}
		ret.append('}');
		if( body instanceof Ast.BraceGroup && !body.redirects.isEmpty()) {
			Ast.Redirect first = body.redirects.get(0);
			Ast.Redirect last = body.redirects.get(body.redirects.size()-1);
			ret.append(' ').append(executor.text(first.start, last.end).trim());
		}
		return ret.toString();
	}
}
