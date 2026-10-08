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
public final class Declarations {

	private final Executor executor;
	private final ShellContext sc;
	private final Expander ex;

	Declarations(Executor executor, ShellContext sc, Expander ex) {
		this.executor = executor;
		this.sc = sc;
		this.ex = ex;
	}

	private void error(String command, String message) {
		sc.error(command+": "+message);
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
			return functions(command, items, o.indexOf('F') >= 0, o.indexOf('p') >= 0, o.indexOf('x') >= 0);
		}
		if( o.indexOf('p') >= 0 || (items.isEmpty() && !remove)) {
			return print(command, items, remove ? "" : o);
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
				java.util.regex.Matcher sub = SUBSCRIPTED.matcher(name);
				if( sub.matches() && !command.equals("readonly") && !remove ) {
					if( eq < 0 ) {
						// declare -a b[256]: the array b (the subscript is ignored)
						name = sub.group(1);
						if( !(sc.getVariable(name) instanceof List<?>) && !(sc.getVariable(name) instanceof Map<?,?>) && o.indexOf('A') < 0 ) {
							Object was = sc.getVariable(name);
							FshList list = new FshList();
							if( was != null ) {
								list.set(0, was);
							} else {
								sc.console.declaredUnset.add(name);
							}
							if( local ) {
								sc.setLocalVariable(name, list);
							} else {
								sc.setVariable(name, list);
							}
						}
						if( o.indexOf('r') >= 0 ) {
							sc.console.setReadonly(name);
						}
						continue;
					}
					// declare "a[7 + 8]=v": one element
					Ast.Assignment element = new Ast.Assignment();
					element.name = sub.group(1);
					element.index = sub.group(2);
					element.append = sub.group(3) != null;
					if( sc.console.isReadonly(element.name)) {
						error(command, element.name+": readonly variable");
						status = 1;
						continue;
					}
					executor.element(element, sc, ex, sc.getVariable(element.name), s.substring(eq+1));
					continue;
				}
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
			if( remove && (o.indexOf('a') >= 0 || o.indexOf('A') >= 0)) {
				// declare +a: not for an array
				Object cur = sc.getVariable(name);
				if( sc.console.isReadonly(name)) {
					error(command, name+": readonly variable");
					status = 1;
					continue;
				}
				if( cur instanceof List<?> || cur instanceof Map<?,?> ) {
					error(command, name+": cannot destroy array variables in this way");
					status = 1;
					continue;
				}
			}
			if( (assignment != null || text != null) && sc.console.isReadonly(name) && !remove ) {
				boolean hasA = o.indexOf('a') >= 0 || o.indexOf('A') >= 0;
				if( (command.equals("readonly") || command.equals("export")) && (!hasA || assignment != null && assignment.array != null)) {
					// as bash says it: readonly -a x=(1) in function f is "f: x: ...", readonly x=1
					// and readonly 'x=(1)' just "x: ...", readonly -a 'x=(1)' "readonly: x: ..."
					Object function = sc.isInFunction() ? ShellContext.firstElement(sc.getVariable("FUNCNAME")) : null;
					if( assignment != null && assignment.array != null && hasA ) {
						error(function != null ? function.toString() : command, name+": readonly variable");
					} else {
						sc.error(name+": readonly variable");
					}
				} else {
					error(command, name+": readonly variable");
				}
				status = 1;
				continue;
			}
			if( o.indexOf('i') >= 0 ) {
				sc.console.setInteger(name, !remove);
			}
			if( o.indexOf('l') >= 0 || o.indexOf('u') >= 0 || o.indexOf('c') >= 0 ) {
				// -l -u -c (capitalized): the last one given
				int l = o.lastIndexOf('l'), u = o.lastIndexOf('u'), c = o.lastIndexOf('c');
				char which = l > u && l > c ? 'l' : u > c ? 'u' : 'c';
				sc.setCaseAttribute(name, remove ? null : which, local);
			}
			if( o.indexOf('n') >= 0 && !remove ) {
				// a reference: the variable named by the value
				String target = text != null ? text : assignment != null && assignment.value != null ? ex.assignment(assignment.value) : "";
				sc.setNameRef(name, target, local);
				continue;
			}
			boolean arrays = !remove && (o.indexOf('a') >= 0 || o.indexOf('A') >= 0);
			if( arrays && (text != null || assignment != null && assignment.array == null && !assignment.append && assignment.value != null)) {
				// declare -a x='(1 2)': the value is read as x=(1 2) (as bash still does)
				String t = assignment == null ? text : values.get(n) != null ? (String) values.get(n) : ex.assignment(assignment.value);
				Ast.Assignment c = t.startsWith("(") && t.endsWith(")") ? compound(name, t) : null;
				if( c != null ) {
					assignment = c;
					values.set(n, null);
					text = null;
				} else if( assignment != null ) {
					if( assignment.index != null ) {
						executor.element(assignment, sc, ex, sc.getVariable(name), t);
						continue;
					}
					values.set(n, t);
				}
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
				if( !local && sc.getVariable(name) instanceof FshList f ) {
					// readonly 'a=(3)' of an array: its element 0
					FshList list = copyOf(f);
					list.set(0, val);
					val = list;
				}
			}
			Object old = local ? null : sc.getVariable(name);
			if( arrays && val instanceof String v && o.indexOf('A') < 0 && !(old instanceof Map<?,?>)) {
				// declare -a x=v: element 0
				FshList list = old instanceof FshList f ? copyOf(f) : new FshList();
				list.set(0, v);
				val = list;
			}
			if( val == null && !remove ) {
				// declare -A m, declare -a a: an empty array (an existing one stays)
				if( o.indexOf('A') >= 0 && !(old instanceof Map<?,?>)) {
					val = new TreeMap<String,Object>();
				} else if( o.indexOf('a') >= 0 && !(old instanceof List<?>)) {
					// declare -a x: an existing value becomes its element 0
					FshList list = new FshList();
					if( old != null && !(old instanceof Map<?,?>)) {
						list.set(0, old);
					}
					val = list;
				}
				if( old == null && !local ) {
					// declare x, declare -a a: declared, with no value yet (declare -p shows it so)
					sc.console.declaredUnset.add(name);
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

	/** name=text as an array assignment, if text is ( words ) */
	private static Ast.Assignment compound(String name, String text) {
		try {
			Ast.Sequence seq = us.bringardner.fsh.syntax.Parser.parse(name+"="+text);
			if( seq.items.size() == 1 && seq.items.get(0).command.pipelines.size() == 1
					&& seq.items.get(0).command.pipelines.get(0).commands.size() == 1
					&& seq.items.get(0).command.pipelines.get(0).commands.get(0) instanceof Ast.SimpleCommand c
					&& c.words.isEmpty() && c.assignments.size() == 1 && c.assignments.get(0).array != null ) {
				return c.assignments.get(0);
			}
		} catch (us.bringardner.fsh.syntax.SyntaxError e) {
		}
		return null;
	}

	private static FshList copyOf(FshList list) {
		FshList ret = new FshList();
		for(int i : list.getIndexes()) {
			ret.set(i, list.get(i));
		}
		return ret;
	}

	/** declare -f [name ...]: functions as code; -F: their names */
	private int functions(String command, List<Object> items, boolean names, boolean p, boolean exported) {
		List<String> wanted = new ArrayList<>();
		for(Object item : items) {
			wanted.add(item instanceof Ast.Assignment a ? a.name : String.valueOf(item));
		}
		if( wanted.isEmpty()) {
			for(String n : new TreeSet<>(sc.console.getFunctions().keySet())) {
				// (declare -xf: the exported ones)
				if( !exported || sc.console.getFunctions().get(n).isExported()) {
					wanted.add(n);
				}
			}
		}
		int ret = 0;
		for(String name : wanted) {
			ShellFunction f = sc.console.getFunctions().get(name);
			if( f == null ) {
				if( p ) {
					// (declare -fp says so; declare -f does not)
					error(command, name+": not found");
				}
				ret = 1;
			} else if( names ) {
				sc.stdout.println(items.isEmpty() ? "declare -f "+name : name);
			} else {
				sc.stdout.println(f.declaration());
			}
		}
		return ret;
	}

	/** name[subscript], name[subscript]= or name[subscript]+= (group 3) */
	private static final java.util.regex.Pattern SUBSCRIPTED = java.util.regex.Pattern.compile("([A-Za-z_][A-Za-z_0-9]*)\\[(.*)\\](\\+)?", java.util.regex.Pattern.DOTALL);

	/** declare -p [name ...]: as declarations the shell can read back; with no names, those with the attributes in o (-a -r ..) */
	private int print(String command, List<Object> items, String o) {
		List<String> names = new ArrayList<>();
		for(Object item : items) {
			names.add(item instanceof Ast.Assignment a ? a.name : String.valueOf(item));
		}
		if( names.isEmpty()) {
			TreeSet<String> all = new TreeSet<>(sc.getVariables().keySet());
			all.addAll(sc.console.declaredUnset);
			String wanted = o.replaceAll("[^aAiluc rxn]", "").replace(" ", "");
			for(String n : all) {
				if( Executor.isName(n) && (sc.getVariable(n) != null || sc.console.declaredUnset.contains(n))) {
					String flags = flags(n, sc.getVariable(n));
					boolean has = true;
					for(char c : wanted.toCharArray()) {
						has &= flags.indexOf(c) >= 0;
					}
					if( has ) {
						names.add(n);
					}
				}
			}
		}
		int ret = 0;
		for(String name : names) {
			Object val = sc.getVariable(name);
			if( command.equals("readonly") && items.isEmpty() && sc.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix)) {
				// set -o posix: readonly -a a=(..), readonly x="1"
				String d = declaration(name, val).substring("declare -".length());
				String flags = d.substring(0, d.indexOf(' ')).replace("r", "");
				sc.stdout.println("readonly "+(flags.isEmpty() ? "" : "-"+flags+" ")+d.substring(d.indexOf(' ')+1));
			} else if( val == null && sc.console.declaredUnset.contains(name)) {
				sc.stdout.println(declaration(name, val));
			} else if( val == null && !(sc.rawVariable(name) instanceof ShellContext.NameRef)) {
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
		StringBuilder value = new StringBuilder();
		if( sc.rawVariable(name) instanceof ShellContext.NameRef ) {
			return "declare -n "+name+"=\""+sc.resolveName(name)+"\"";
		}
		String flags = flags(name, val);
		if( sc.console.declaredUnset.contains(name) && (val == null || val instanceof Map<?,?> m0 && m0.isEmpty() || val instanceof List<?> l0 && l0.isEmpty())) {
			// declared, never given a value
			return "declare -"+(flags.isEmpty() ? "-" : flags)+" "+name;
		}
		value.append(val instanceof Map<?,?> || val instanceof FshList ? arrayText(val) : quote(val));
		return "declare -"+(flags.isEmpty() ? "-" : flags)+" "+name+"="+value;
	}

	/** an array as declare -p shows it: ([0]="a" [1]="b"), ([k]="v" ) */
	public static String arrayText(Object val) {
		StringBuilder value = new StringBuilder("(");
		if( val instanceof Map<?,?> m ) {
			for(Map.Entry<?,?> e : m.entrySet()) {
				value.append('[').append(key(String.valueOf(e.getKey()))).append("]=").append(quote(e.getValue())).append(' ');
			}
		} else if( val instanceof FshList list ) {
			boolean first = true;
			for(int idx : list.getIndexes()) {
				if( !first ) {
					value.append(' ');
				}
				first = false;
				value.append('[').append(idx).append("]=").append(quote(list.get(idx)));
			}
		}
		return value.append(')').toString();
	}

	/** the attributes declare -p shows: a A i l u c r x */
	private String flags(String name, Object val) {
		String flags = val instanceof Map<?,?> ? "A" : val instanceof List<?> ? "a" : "";
		if( sc.console.isInteger(name)) {
			flags += "i";
		}
		// (bash's order: a A i n r x c l u)
		if( sc.console.isReadonly(name)) {
			flags += "r";
		}
		if( sc.getEvironmentVariable(name) != null ) {
			flags += "x";
		}
		Character c = sc.caseAttribute(name);
		if( c != null ) {
			flags += c;
		}
		return flags;
	}

	/** an associative array's key: "quoted" if the shell would read it differently (as bash's) */
	static String key(String k) {
		if( k.chars().anyMatch(Character::isISOControl)) {
			// $'..'
			return quote(k);
		}
		boolean quote = k.startsWith("~") || k.startsWith("#");
		for (int i = 0; i < k.length() && !quote; i++) {
			quote = " \t\n'\"\\|&;()<>!{}*[?]^$`".indexOf(k.charAt(i)) >= 0;
		}
		if( !quote ) {
			return k;
		}
		StringBuilder ret = new StringBuilder("\"");
		for(char c : k.toCharArray()) {
			if( "$`\"\\".indexOf(c) >= 0 ) {
				ret.append('\\');
			}
			ret.append(c);
		}
		return ret.append('"').toString();
	}

	/** "v", or $'v' when v has a character that does not print (as bash's ansic_quote) */
	public static String quote(Object v) {
		String s = String.valueOf(v);
		boolean ansi = false;
		for (int i = 0; i < s.length() && !ansi; i++) {
			char c = s.charAt(i);
			ansi = Character.isISOControl(c);
		}
		if( ansi ) {
			StringBuilder ret = new StringBuilder("$'");
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				switch (c) {
				case 033 -> ret.append("\\E");
				case 007 -> ret.append("\\a");
				case 013 -> ret.append("\\v");
				case '\b' -> ret.append("\\b");
				case '\f' -> ret.append("\\f");
				case '\n' -> ret.append("\\n");
				case '\r' -> ret.append("\\r");
				case '\t' -> ret.append("\\t");
				case '\\', '\'' -> ret.append('\\').append(c);
				default -> {
					if( Character.isISOControl(c)) {
						int b = c;
						ret.append('\\').append((b >> 6) & 7).append((b >> 3) & 7).append(b & 7);
					} else {
						ret.append(c);
					}
				}
				}
			}
			return ret.append('\'').toString();
		}
		return "\""+String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`")+"\"";
	}
}
