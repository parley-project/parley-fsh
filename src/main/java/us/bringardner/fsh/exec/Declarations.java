package us.bringardner.fsh.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellFunction;
import us.bringardner.fsh.expand.Expander;
import us.bringardner.fsh.syntax.Ast;

/**
 * declare, typeset and local: attributes (-a -A -i -l -u -n -r -x), values (name=value,
 * name=(words)), and -p -f -F to print. In a function they make local variables (declare -g:
 * global), as in bash.
 */
final class Declarations {

	private final Executor executor;
	private final ShellContext sc;
	private final Expander ex;

	Declarations(Executor executor, ShellContext sc, Expander ex) {
		this.executor = executor;
		this.sc = sc;
		this.ex = ex;
	}

	private void error(String command, String message) {
		sc.stderr.println(Executor.prefix(sc, sc.currentLine())+command+": "+message);
	}

	/** args: words (String) and name=value words (Ast.Assignment) */
	int declare(String command, List<Object> args) {
		boolean isLocal = command.equals("local");
		if( isLocal && !sc.isInFunction()) {
			error(command, "can only be used in a function");
			return 1;
		}
		StringBuilder opts = new StringBuilder();
		boolean remove = false;
		int i = 0;
		while( i < args.size() && args.get(i) instanceof String s && s.length() > 1 && (s.startsWith("-") || s.startsWith("+"))) {
			i++;
			if( s.equals("--")) {
				break;
			}
			remove |= s.startsWith("+");
			opts.append(s.substring(1));
		}
		String o = opts.toString();
		List<Object> items = args.subList(i, args.size());
		// in a function: local, unless -g
		boolean local = isLocal || (sc.isInFunction() && o.indexOf('g') < 0);

		if( o.indexOf('f') >= 0 || o.indexOf('F') >= 0 ) {
			return functions(items, o.indexOf('F') >= 0);
		}
		if( o.indexOf('p') >= 0 || (items.isEmpty() && !remove)) {
			return print(items);
		}

		// local w=1 p=$w: every value is expanded before any is set (p gets the old w)
		List<Object> values = new ArrayList<>();
		for(Object item : items) {
			values.add(item instanceof Ast.Assignment a && local && a.array == null && a.index == null
					? (a.value == null ? "" : ex.assignment(a.value)) : null);
		}
		int status = 0;
		for (int n = 0; n < items.size(); n++) {
			Object item = items.get(n);
			String name;
			Ast.Assignment assignment = null;
			String text = null;
			if( item instanceof Ast.Assignment a ) {
				assignment = a;
				name = a.name;
			} else {
				// declare "$name=$value"
				String s = String.valueOf(item);
				int eq = s.indexOf('=');
				name = eq < 0 ? s : s.substring(0, eq);
				if( eq >= 0 ) {
					text = s.substring(eq+1);
					if( name.endsWith("+")) {
						name = name.substring(0, name.length()-1);
						Object before = sc.getVariable(name);
						text = (before == null ? "" : before)+text;
					}
				}
			}
			if( !Executor.isName(name)) {
				error(command, "`"+(item instanceof String s ? s : name)+"': not a valid identifier");
				status = 1;
				continue;
			}
			if( (assignment != null || text != null) && sc.console.isReadonly(name) && !remove ) {
				error(command, name+": readonly variable");
				status = 1;
				continue;
			}
			if( o.indexOf('i') >= 0 ) {
				sc.console.setInteger(name, !remove);
			}
			if( o.indexOf('l') >= 0 || o.indexOf('u') >= 0 ) {
				sc.setCaseAttribute(name, remove ? null : o.lastIndexOf('l') > o.lastIndexOf('u') ? 'l' : 'u', local);
			}
			if( o.indexOf('n') >= 0 && !remove ) {
				// a reference: the variable named by the value
				String target = text != null ? text : assignment != null && assignment.value != null ? ex.assignment(assignment.value) : "";
				sc.setNameRef(name, target, local);
				continue;
			}
			Object val = null;
			if( assignment != null ) {
				if( values.get(n) != null ) {
					String v = (String) values.get(n);
					val = sc.console.isInteger(name) ? String.valueOf(us.bringardner.fsh.expand.Arithmetic.evaluate(v, sc)) : v;
				} else if( assignment.index != null ) {
					executor.assign(assignment, sc, ex, false);
					continue;
				} else {
					val = executor.value(assignment, sc, ex, local && !assignment.append, o.indexOf('A') >= 0);
				}
			} else if( text != null ) {
				val = sc.console.isInteger(name) ? String.valueOf(us.bringardner.fsh.expand.Arithmetic.evaluate(text, sc)) : text;
			}
			Object old = local ? null : sc.getVariable(name);
			if( val == null && !remove ) {
				// declare -A m, declare -a a: an empty array (an existing one stays)
				if( o.indexOf('A') >= 0 && !(old instanceof Map<?,?>)) {
					val = new TreeMap<String,Object>();
				} else if( o.indexOf('a') >= 0 && !(old instanceof List<?>)) {
					val = new FshList();
				}
			}
			if( local ) {
				if( val == null ) {
					if( !sc.isOwnLocal(name)) {
						sc.declareLocal(name);
					}
				} else {
					sc.setLocalVariable(name, val);
				}
			} else if( val != null ) {
				sc.setVariable(name, val);
			}
			if( o.indexOf('r') >= 0 && !remove ) {
				sc.console.setReadonly(name);
			}
			if( o.indexOf('x') >= 0 ) {
				Object v = sc.getVariable(name);
				sc.setEnvironmentVariable(name, remove ? null : v == null ? "" : String.valueOf(v));
			}
		}
		return status;
	}

	/** declare -f [name ...]: functions as code; -F: their names */
	private int functions(List<Object> items, boolean names) {
		List<String> wanted = new ArrayList<>();
		for(Object item : items) {
			wanted.add(item instanceof Ast.Assignment a ? a.name : String.valueOf(item));
		}
		if( wanted.isEmpty()) {
			wanted.addAll(new TreeSet<>(sc.console.getFunctions().keySet()));
		}
		int ret = 0;
		for(String name : wanted) {
			ShellFunction f = sc.console.getFunctions().get(name);
			if( f == null ) {
				ret = 1;
			} else if( names ) {
				sc.stdout.println(items.isEmpty() ? "declare -f "+name : name);
			} else {
				sc.stdout.println(f.declaration());
			}
		}
		return ret;
	}

	/** declare -p [name ...]: as declarations the shell can read back */
	private int print(List<Object> items) {
		List<String> names = new ArrayList<>();
		for(Object item : items) {
			names.add(item instanceof Ast.Assignment a ? a.name : String.valueOf(item));
		}
		if( names.isEmpty()) {
			for(String n : new TreeSet<>(sc.getVariables().keySet())) {
				if( Executor.isName(n)) {
					names.add(n);
				}
			}
		}
		int ret = 0;
		for(String name : names) {
			Object val = sc.getVariable(name);
			if( val == null && !(sc.rawVariable(name) instanceof ShellContext.NameRef)) {
				error("declare", name+": not found");
				ret = 1;
			} else {
				sc.stdout.println(declaration(name, val));
			}
		}
		return ret;
	}

	/** declare -- x="1", declare -a a=([0]="x"), declare -A m=([k]="v" ) */
	private String declaration(String name, Object val) {
		String flags = "";
		StringBuilder value = new StringBuilder();
		if( sc.rawVariable(name) instanceof ShellContext.NameRef ) {
			return "declare -n "+name+"=\""+sc.resolveName(name)+"\"";
		}
		if( val instanceof Map<?,?> m ) {
			flags += "A";
			value.append('(');
			for(Map.Entry<?,?> e : m.entrySet()) {
				value.append('[').append(e.getKey()).append("]=").append(quote(e.getValue())).append(' ');
			}
			value.append(')');
		} else if( val instanceof FshList list ) {
			flags += "a";
			value.append('(');
			boolean first = true;
			for(int idx : list.getIndexes()) {
				if( !first ) {
					value.append(' ');
				}
				first = false;
				value.append('[').append(idx).append("]=").append(quote(list.get(idx)));
			}
			value.append(')');
		} else {
			value.append(quote(val));
		}
		if( sc.console.isInteger(name)) {
			flags += "i";
		}
		Character c = sc.caseAttribute(name);
		if( c != null ) {
			flags += c;
		}
		if( sc.console.isReadonly(name)) {
			flags += "r";
		}
		if( sc.getEvironmentVariable(name) != null ) {
			flags += "x";
		}
		return "declare -"+(flags.isEmpty() ? "-" : flags)+" "+name+"="+value;
	}

	private static String quote(Object v) {
		return "\""+String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`")+"\"";
	}
}
