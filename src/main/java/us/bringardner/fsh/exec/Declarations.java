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

		if( (o.indexOf('f') >= 0 || o.indexOf('F') >= 0) && o.indexOf('t') >= 0 && !items.isEmpty()) {
			// declare -ft name: the trace attribute (+t takes it off)
			int ret = 0;
			for(Object item : items) {
				String n = String.valueOf(item instanceof Ast.Assignment a ? a.name : item);
				if( sc.getFunction(n) == null ) {
					error(command, n+": not found");
					ret = 1;
				} else if( remove ) {
					sc.console.tracedFunctions.remove(n);
				} else {
					sc.console.tracedFunctions.add(n);
				}
			}
			return ret;
		}
		boolean readonlyCommand = command.equals("readonly");
		for(char c : o.toCharArray()) {
			if( (readonlyCommand ? "aAfpgrn" : "aAfFgiIlnprtuxc").indexOf(c) < 0 ) {
				error(command, "-"+c+": invalid option");
				sc.stderr.println(readonlyCommand ? "readonly: usage: readonly [-aAf] [name[=value] ...] or readonly -p"
						: command.equals("local") ? "local: usage: local [option] name[=value] ..."
						: command+": usage: "+command+" [-aAfFgiIlnrtux] [name[=value] ...] or "+command+" -p [-aAfFilnrtux] [name ...]");
				return 2;
			}
		}
		if( readonlyCommand ) {
			// (readonly -n: as readonly)
			o = o.replace("n", "");
		}
		if( o.indexOf('f') >= 0 || o.indexOf('F') >= 0 ) {
			for(char c : o.toCharArray()) {
				if( "aAilnuIc".indexOf(c) >= 0 ) {
					// (functions have no such attribute)
					error(command, "-"+c+": invalid option");
					return 1;
				}
			}
			for(Object item : items) {
				if( item instanceof Ast.Assignment || String.valueOf(item).contains("=")) {
					error(command, "cannot use `-f' to make functions");
					return 1;
				}
			}
			if( (o.indexOf('r') >= 0 || o.indexOf('x') >= 0) && !items.isEmpty() && o.indexOf('p') < 0 ) {
				// declare -fr name, declare -fx name, +x: the attributes
				int ret = 0;
				for(Object item : items) {
					String n = String.valueOf(item);
					ShellFunction f = sc.getFunction(n);
					if( f == null ) {
						error(command, n+": not found");
						ret = 1;
						continue;
					}
					if( o.indexOf('r') >= 0 ) {
						if( remove ) {
							if( sc.console.isReadonlyFunction(n)) {
								error(command, n+": readonly function");
								ret = 1;
							}
						} else {
							sc.console.setReadonlyFunction(n);
						}
					}
					if( o.indexOf('x') >= 0 ) {
						f.setExported(!remove);
					}
				}
				return ret;
			}
			return functions(command, items, o.indexOf('F') >= 0, o.indexOf('p') >= 0, o.indexOf('x') >= 0, o.indexOf('r') >= 0 && !remove);
		}
		if( isLocal && !items.isEmpty() && o.equals("p")) {
			// local -p names: the function's own variables only
			int ret = 0;
			for(Object item : items) {
				String n = String.valueOf(item instanceof Ast.Assignment a ? a.name : item);
				if( sc.isOwnLocal(n)) {
					sc.stdout.println(declaration(n, sc.getVariable(n)));
				} else {
					error(command, n+": not found");
					ret = 1;
				}
			}
			return ret;
		}
		if( isLocal && items.isEmpty() && (o.isEmpty() || o.equals("p"))) {
			// local, local -p: the function's own variables (local - first)
			if( sc.hasLocalOptions()) {
				sc.stdout.println("local -");
			}
			for(String n : new TreeSet<>(sc.ownLocalNames())) {
				Object v = sc.getVariable(n);
				if( v != null || sc.rawVariable(n) instanceof ShellContext.NameRef ) {
					sc.stdout.println(declaration(n, v));
				}
			}
			return 0;
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
			// declare "x+=v": the v
			String appended = null;
			if( item instanceof Ast.Assignment a ) {
				assignment = a;
				name = a.name;
			} else {
				// declare "$name=$value"
				String s = String.valueOf(item);
				int eq = s.indexOf('=');
				name = eq < 0 ? s : s.substring(0, eq);
				java.util.regex.Matcher sub = SUBSCRIPTED.matcher(name);
				if( sub.matches() && !balanced(sub.group(2))) {
					// declare 'a[foo[bar]=v': the [ is not closed
					error(command, "`"+s+"': not a valid identifier");
					status = 1;
					continue;
				}
				if( sub.matches() && o.indexOf('n') >= 0 && !remove ) {
					error(command, name+": reference variable cannot be an array");
					status = 1;
					continue;
				}
				if( sub.matches() && !command.equals("readonly") && !remove ) {
					if( eq < 0 ) {
						// declare -a b[256]: the array b (the subscript is ignored)
						name = sub.group(1);
						if( o.indexOf('A') >= 0 && !(sc.getVariable(name) instanceof Map<?,?>)) {
							// declare -A m[200]: the associative array m
							sc.console.declaredUnset.add(name);
							if( local ) {
								sc.setLocalVariable(name, new TreeMap<String,Object>());
							} else {
								sc.setVariable(name, new TreeMap<String,Object>());
							}
						} else if( !(sc.getVariable(name) instanceof List<?>) && !(sc.getVariable(name) instanceof Map<?,?>) && o.indexOf('A') < 0 ) {
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
					if( sc.rawVariable(element.name) instanceof ShellContext.NameRef ) {
						// declare -a ref[1]=v: ref is an array again (bash's)
						sc.error("warning: "+element.name+": removing nameref attribute");
						sc.unSetVariable(element.name, false);
					}
					if( o.indexOf('A') >= 0 && !(sc.getVariable(element.name) instanceof Map<?,?>)) {
						// declare -A m["k"]=v: the associative array m, then its element
						if( local ) {
							sc.setLocalVariable(element.name, new TreeMap<String,Object>());
						} else {
							sc.setVariable(element.name, new TreeMap<String,Object>());
						}
					}
					executor.element(element, sc, ex, sc.getVariable(element.name), s.substring(eq+1));
					continue;
				}
				if( eq >= 0 ) {
					text = s.substring(eq+1);
					if( name.endsWith("+")) {
						name = name.substring(0, name.length()-1);
						appended = text;
						Object before = sc.getVariable(name);
						text = (before == null ? "" : before)+text;
					}
				}
			}
			if( name.equals("-") && isLocal && assignment == null && text == null ) {
				// local -: the set -o options are the function's (put back when it returns)
				sc.localOptions();
				continue;
			}
			if( !Executor.isName(name)) {
				error(command, "`"+(item instanceof String s ? s : name)+"': not a valid identifier");
				status = 1;
				if( command.equals("readonly") || command.equals("export")) {
					// (a special builtin: in posix mode a script ends)
					sc.specialBuiltinFailed(1);
				}
				continue;
			}
			if( (text != null || assignment != null) && o.indexOf('n') >= 0 && o.indexOf('i') >= 0 && !remove ) {
				// declare -in r=v: the value is a number, no name: nothing is made (bash's, unsaid)
				status = 1;
				continue;
			}
			if( text == null && assignment == null && o.indexOf('n') < 0 && !remove && !isLocal
					&& !(local && !sc.hasLocal(name)) && sc.rawVariable(name) instanceof ShellContext.NameRef
					&& sc.resolveName(name).indexOf('[') > 0 ) {
				// declare -A r of a nameref to an element (r=a[1]): nothing, as bash's
				continue;
			}
			if( local && text == null && assignment == null && sc.commandTemporaries != null
					&& isTemporary(sc, name) && sc.getVariable(name) instanceof String tv ) {
				// var=value declare var in a function: the local takes the temporary value, as bash's
				text = tv;
			}
			if( !local && sc.commandTemporaries != null ) {
				// x=1 declare -r x: the temporary x stays (and exported), as bash's; in a function it
				// stays as the function's local
				String declared = name;
				Object[] entry = null;
				for(Object[] t : sc.commandTemporaries) {
					if( declared.equals(t[0])) {
						entry = t;
					}
				}
				if( entry != null ) {
					sc.commandTemporaries.remove(entry);
					boolean outer = false;
					for(List<Object[]> l : sc.console.temporaryAssignments) {
						for(Object[] t : l) {
							outer |= l != sc.commandTemporaries && declared.equals(t[0]);
						}
					}
					if( sc.isInFunction() && !sc.hasLocal(name) && outer ) {
						// (a=7 f, with a=3 readonly a in f: the temporary a of the call has it)
						Object value = sc.getVariable(name);
						sc.setGlobal(name, entry[1]);
						sc.setEnvironmentVariable(name, entry[2]);
						sc.localAttributes(name);
						sc.setLocalVariable(name, value);
					}
				}
			}
			if( (assignment != null || text != null) && o.indexOf('n') < 0 && sc.rawVariable(name) instanceof ShellContext.NameRef r0
					&& r0.target().isEmpty() && sc.console.isReadonly(name)) {
				// typeset r=v of a readonly nameref with no value
				error(command, name+": readonly variable");
				status = 1;
				continue;
			}
			if( o.indexOf('n') < 0 && !remove && sc.rawVariable(name) instanceof ShellContext.NameRef empty && empty.target().isEmpty()
					&& !(local && sc.isInFunction() && !sc.isOwnLocal(name))) {
				if( o.indexOf('a') >= 0 || o.indexOf('A') >= 0 ) {
					// declare -a of a nameref with no value: an array
					sc.unSetVariable(name, false);
				} else if( assignment != null && assignment.index == null && assignment.array == null || text != null ) {
					// declare r=v of a nameref with no value: v is what it names (one that is no name:
					// said, and r is gone)
					String v = text != null ? text : assignment.value == null ? "" : ex.assignment(assignment.value);
					if( !ShellContext.validReference(v) && local && sc.isInFunction() && sc.isOwnLocal(name)) {
						// (a function's own: it stays, as bash's)
						error(command, "`"+v+"': invalid variable name for name reference");
						status = 1;
						continue;
					}
					if( !ShellContext.validReference(v)) {
						error(command, "`"+v+"': not a valid identifier");
						sc.unSetVariable(name, false);
						status = 1;
						continue;
					}
					if( o.indexOf('i') >= 0 ) {
						sc.console.setInteger(name, true);
					}
					sc.retarget(name, v);
					continue;
				}
			}
			if( o.indexOf('n') < 0 && !(assignment != null && assignment.index != null)) {
				// declare -a ref, declare ref=(..): the variable a nameref names (with -g, by the
				// global ones; not declare ref[1]=v)
				boolean global = o.indexOf('g') >= 0;
				Object raw = global ? sc.globalRaw(name) : sc.rawVariable(name);
				// (in a function, a nameref of its own: local ref=v of a global one makes a local ref)
				if( raw instanceof ShellContext.NameRef r && !r.target().isEmpty() && (global || !local || sc.isOwnLocal(name))) {
					String t = global ? sc.resolveGlobalName(name) : sc.resolveName(name);
					if( Executor.isName(t)) {
						name = t;
					} else if( t.startsWith(name+"[") && t.endsWith("]") && (assignment != null || text != null)) {
						// declare a=v with a -> b -> a[1]: a is an array again, and a[1] is v
						sc.error("warning: "+name+": removing nameref attribute");
						sc.unSetVariable(name, false);
						Ast.Assignment element = new Ast.Assignment();
						element.name = name;
						element.index = t.substring(name.length()+1, t.length()-1);
						String v = text != null ? text : assignment.value == null ? "" : ex.assignment(assignment.value);
						executor.element(element, sc, ex, sc.getVariable(name), v);
						continue;
					} else if( t.indexOf('[') > 0 && t.endsWith("]") && (assignment != null && assignment.array == null || text != null)
							&& o.indexOf('a') < 0 && o.indexOf('A') < 0 ) {
						// declare r=v, r+=v with r -> a[1]: that element (+= adds to it)
						Ast.Assignment element = new Ast.Assignment();
						element.name = t.substring(0, t.indexOf('['));
						element.index = t.substring(t.indexOf('[')+1, t.length()-1);
						element.append = text == null && assignment.append;
						String v = text != null ? text : assignment.value == null ? "" : ex.assignment(assignment.value);
						executor.element(element, sc, ex, sc.getVariable(element.name), v);
						continue;
					}
				}
			}
			if( remove && o.indexOf('r') >= 0 && sc.console.isReadonly(name) && sc.rawVariable(name) instanceof ShellContext.NameRef r1
					&& r1.target().isEmpty()) {
				// typeset +r of a readonly nameref that names nothing: nothing
				continue;
			}
			if( remove && o.indexOf('r') >= 0 && sc.console.isReadonly(name)) {
				// declare +r: readonly stays
				error(command, name+": readonly variable");
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
			if( local && sc.isInFunction() && !sc.isOwnLocal(name) && sc.readonlyFromCaller(name)) {
				// a caller's readonly local: this function may have its own
				sc.localAttributes(name);
				sc.console.clearReadonly(name);
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
					if( assignment != null && assignment.array != null ) {
						// (local x=(..) of a readonly x: the assignment says it too, as bash's)
						sc.error(name+": readonly variable");
					}
					error(command, name+": readonly variable");
				}
				status = 1;
				continue;
			}
			if( local && sc.isInFunction() && !sc.console.isReadonly(name)) {
				// (its readonly and integer attributes are the local's)
				if( !sc.isOwnLocal(name)) {
					sc.localAttributes(name);
					sc.console.setInteger(name, false);
				}
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
			if( o.indexOf('n') >= 0 && !remove && assignment != null && assignment.array != null && assignment.index == null ) {
				// declare -n r=(a b): said, and r=(a b) is an array, as bash's
				error(command, name+": reference variable cannot be an array");
				status = 1;
				executor.assign(assignment, sc, ex, local);
				continue;
			}
			if( o.indexOf('n') >= 0 && !remove ) {
				// a reference: the variable named by the value (with no value, the one it has now; a
				// function's local one is new)
				Object raw = local && sc.isInFunction() && !sc.isOwnLocal(name) ? null : sc.rawVariable(name);
				if( raw == null && assignment == null && text == null && sc.rawVariable(name) instanceof String visible ) {
					// (local -n r with no value: the value r has where it is called, r=/ f)
					raw = visible;
				}
				boolean append = assignment != null && assignment.append || appended != null;
				if( append && raw instanceof ShellContext.NameRef r ) {
					// ref+=x: x after the name it has (typeset -n ref=var ref+=[@])
					String more = appended != null ? appended : assignment.value == null ? "" : ex.assignment(assignment.value);
					String target = r.target()+more;
					if( target.equals(name)) {
						sc.error(name+": nameref variable self references not allowed");
						status = 1;
						continue;
					}
					sc.retarget(name, target);
					continue;
				}
				if( assignment != null && assignment.index != null ) {
					error(command, name+"["+assignment.index+"]: reference variable cannot be an array");
					status = 1;
					continue;
				}
				String named = text != null ? text : assignment != null && assignment.array == null && assignment.value != null ? ex.assignment(assignment.value) : null;
				if( (raw instanceof List<?> || raw instanceof Map<?,?>) && named != null && !named.isEmpty()
						&& !Executor.isName(named) && !SUBSCRIPTED.matcher(named).matches()) {
					// (the name is looked at first)
					error(command, "`"+named+"': invalid variable name for name reference");
					status = 1;
					continue;
				}
				if( raw instanceof List<?> || raw instanceof Map<?,?> ) {
					error(command, name+": reference variable cannot be an array");
					status = 1;
					continue;
				}
				String target = text != null ? text : assignment != null && assignment.value != null ? ex.assignment(assignment.value)
						: raw instanceof ShellContext.NameRef r ? r.target() : raw == null ? "" : raw instanceof List<?> || raw instanceof Map<?,?> ? null : String.valueOf(raw);
				if( target == null ) {
					error(command, name+": reference variable cannot be an array");
					status = 1;
					continue;
				}
				if( (target.equals(name) || target.startsWith(name+"[")) && local && sc.isInFunction()) {
					// a function's local -n v=v: allowed, and said
					error(command, "warning: "+name+": circular name reference");
					sc.error("warning: "+name+": circular name reference");
				}
				if( target.equals(name) && !(local && sc.isInFunction())) {
					error(command, name+": nameref variable self references not allowed");
					status = 1;
					continue;
				}
				if( target.isEmpty() && (assignment != null && assignment.value != null || text != null)) {
					// declare -n r=""
					error(command, "`': not a valid identifier");
					status = 1;
					continue;
				}
				if( (!target.isEmpty() || raw instanceof String) && !Executor.isName(target) && !SUBSCRIPTED.matcher(target).matches()) {
					error(command, "`"+target+"': invalid variable name for name reference");
					status = 1;
					continue;
				}
				if( sc.console.isReadonly(name)) {
					error(command, name+": readonly variable");
					status = 1;
					continue;
				}
				sc.setNameRef(name, target, local);
				if( o.indexOf('i') < 0 ) {
					// (a nameref is no integer: declare -i x; declare -n x=y)
					sc.console.setInteger(name, false);
				}
				if( o.indexOf('r') >= 0 ) {
					sc.console.setReadonly(name);
				}
				if( o.indexOf('x') >= 0 ) {
					// declare -nx ref=var: ref=var is in the environment
					sc.setEnvironmentVariable(name, target);
				}
				continue;
			}
			if( o.indexOf('n') >= 0 && remove && sc.rawVariable(name) instanceof ShellContext.NameRef ref && sc.console.isReadonly(name)) {
				// +n of a readonly nameref: only of one with no value yet (it stays readonly)
				if( !ref.target().isEmpty() || assignment != null || text != null ) {
					error(command, name+": readonly variable");
					status = 1;
					continue;
				}
				sc.console.clearReadonly(name);
				sc.unSetVariable(name, false);
				sc.console.declaredUnset.add(name);
				sc.console.setReadonly(name);
				continue;
			}
			if( o.indexOf('n') >= 0 && remove && sc.rawVariable(name) instanceof ShellContext.NameRef ref ) {
				// +n: a value goes to the variable it names; then it is a plain variable whose value
				// is that name
				if( assignment != null || text != null ) {
					String v = text != null ? text : assignment.value == null ? "" : ex.assignment(assignment.value);
					if( !ref.target().isEmpty()) {
						if( o.indexOf('i') >= 0 ) {
							// declare +n -i ref=7+4: what it names is an integer, 11
							sc.console.setInteger(ref.target(), true);
							v = String.valueOf(us.bringardner.fsh.expand.Arithmetic.evaluate(v, sc));
						}
						sc.setVariable(ref.target(), v);
					}
				}
				if( ref.target().isEmpty()) {
					// (naming nothing: declared, with no value)
					sc.unSetVariable(name, false);
					sc.console.declaredUnset.add(name);
				} else if( local ) {
					sc.setLocalVariable(name, ref.target());
				} else {
					sc.unSetVariable(name, false);
					sc.setVariable(name, ref.target());
				}
				continue;
			}
			if( local && sc.isInFunction() && !sc.isOwnLocal(name) && (o.indexOf('I') >= 0 || us.bringardner.fsh.Glob.option(sc, "localvar_inherit"))) {
				// shopt -s localvar_inherit: the local starts with the value (and attributes) it has
				Object inherited = sc.getVariable(name);
				if( inherited != null ) {
					sc.localAttributes(name);
					sc.setLocalVariable(name, copy(inherited));
				}
			}
			Object existing = local && !sc.isOwnLocal(name) ? null : sc.getVariable(name);
			// (declare a='(1 2)' of an array that is there: its words, as bash does)
			boolean arrays = !remove && (o.indexOf('a') >= 0 || o.indexOf('A') >= 0 || existing instanceof List<?> || existing instanceof Map<?,?>);
			if( arrays && (text != null || assignment != null && assignment.array == null && !assignment.append && assignment.value != null)) {
				// declare -a x='(1 2)': the value is read as x=(1 2) (as bash still does)
				String t = assignment == null ? text : values.get(n) != null ? (String) values.get(n) : ex.assignment(assignment.value);
				// (for an element only with -a or -A: declare a[1]='(x)' is the text)
				boolean parens = t.startsWith("(") && t.endsWith(")");
				boolean creating = o.indexOf('a') >= 0 || o.indexOf('A') >= 0;
				Ast.Assignment c = parens && (assignment == null || assignment.index == null || creating) ? compound(name, t) : null;
				if( parens && c == null && !creating && !(existing instanceof List<?> || existing instanceof Map<?,?>)) {
					sc.error("warning: "+name+"["+assignment.index+"]="+t+": quoted compound array assignment deprecated");
				}
				if( c != null ) {
					assignment = c;
					values.set(n, null);
					text = null;
				} else if( assignment != null ) {
					if( assignment.index != null ) {
						if( sc.rawVariable(name) instanceof ShellContext.NameRef ) {
							// declare -a ref[1]=v: ref is an array again (bash's)
							sc.error("warning: "+name+": removing nameref attribute");
							sc.unSetVariable(name, false);
						}
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
					// (through a nameref: what it names, an element's array's -i)
					boolean ref = o.indexOf('n') < 0 && sc.rawVariable(name) instanceof ShellContext.NameRef;
					boolean integer = sc.console.isInteger(ref ? sc.readonlyName(name) : name);
					if( assignment.append && (sc.isOwnLocal(name) || ref)) {
						// local x+=v of a value it started with (localvar_inherit)
						Object before = ShellContext.firstElement(sc.getVariable(name));
						v = integer ? String.valueOf(Executor.integerAppend(before, v, sc)) : (before == null ? "" : before)+v;
					}
					val = integer ? String.valueOf(us.bringardner.fsh.expand.Arithmetic.evaluate(v, sc)) : v;
				} else if( assignment.index != null ) {
					if( sc.rawVariable(name) instanceof ShellContext.NameRef ) {
						// declare -a ref[1]=v: ref is an array again (bash's)
						sc.error("warning: "+name+": removing nameref attribute");
						sc.unSetVariable(name, false);
					}
					if( !(existing instanceof List<?>) && !(existing instanceof Map<?,?>) && assignment.value != null && assignment.value.raw != null
							&& (assignment.value.raw.startsWith("'") || assignment.value.raw.startsWith("\"")) ) {
						String v = ex.assignment(assignment.value);
						if( v.startsWith("(") && v.endsWith(")")) {
							sc.error("warning: "+name+"["+assignment.index+"]="+v+": quoted compound array assignment deprecated");
						}
					}
					executor.assign(assignment, sc, ex, false);
					continue;
				} else {
					try {
						val = executor.value(assignment, sc, ex, local && !assignment.append, o.indexOf('A') >= 0 || existing instanceof Map<?,?>);
					} catch (us.bringardner.fsh.expand.ExpansionError e) {
						if( assignment.array != null && !local && existing == null && (o.indexOf('a') >= 0 || o.indexOf('A') >= 0)) {
							// declare -a x=(...) that fails: x is there, an empty array (bash's)
							sc.setVariable(name, o.indexOf('A') >= 0 ? new TreeMap<String,Object>() : new FshList());
						}
						throw e;
					}
				}
			} else if( text != null ) {
				val = sc.console.isInteger(name) ? String.valueOf(us.bringardner.fsh.expand.Arithmetic.evaluate(text, sc)) : text;
				if( !local && sc.getVariable(name) instanceof FshList f ) {
					// readonly 'a=(3)' of an array: its element 0
					FshList list = copyOf(f);
					list.set(0, inCase(o, name, (String) val, sc));
					val = list;
				}
			}
			// declare -g in a function: the global (readonly and export: a local if there is one)
			boolean toGlobal = o.indexOf('g') >= 0 && sc.isInFunction()
					&& !((command.equals("readonly") || command.equals("export")) && sc.hasLocal(name));
			Object old = local ? (sc.isOwnLocal(name) ? sc.getVariable(name) : null) : toGlobal ? sc.globalRaw(name) : sc.getVariable(name);
			if( o.indexOf('a') >= 0 && o.indexOf('A') < 0 && !remove && old instanceof Map<?,?> ) {
				// declare -a of an associative array
				error(command, name+": cannot convert associative to indexed array");
				status = 1;
				continue;
			}
			if( o.indexOf('A') >= 0 && !remove && old instanceof FshList ) {
				// declare -A of an indexed array (an assignment says so too)
				if( sc.isInFunction() && (assignment != null || text != null)) {
					sc.error(ShellContext.firstElement(sc.getVariable("FUNCNAME"))+": "+name+": cannot convert indexed to associative array");
				}
				error(command, name+": cannot convert indexed to associative array");
				status = 1;
				continue;
			}
			if( arrays && val instanceof String v && (o.indexOf('A') >= 0 || old instanceof Map<?,?>)) {
				// declare -A m=v: the element "0"
				Map<String,Object> map = new TreeMap<>();
				if( old instanceof Map<?,?> m ) {
					for(Map.Entry<?,?> e : m.entrySet()) {
						map.put(String.valueOf(e.getKey()), e.getValue());
					}
				}
				map.put("0", v);
				val = map;
			} else if( arrays && val instanceof String v && o.indexOf('A') < 0 && !(old instanceof Map<?,?>)) {
				// declare -a x=v: element 0
				FshList list = old instanceof FshList f ? copyOf(f) : new FshList();
				list.set(0, inCase(o, name, v, sc));
				val = list;
			}
			if( val == null && !remove ) {
				// declare -A m, declare -a a: an empty array (an existing one stays)
				if( o.indexOf('A') >= 0 && !(old instanceof Map<?,?>)) {
					// (a value it has is its element "0")
					TreeMap<String,Object> map = new TreeMap<>();
					if( old != null && !(old instanceof List<?>)) {
						map.put("0", old);
					}
					val = map;
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
			} else if( val == null && remove && !local && sc.getVariable(name) == null && sc.rawVariable(name) == null ) {
				// (declare +r x too)
				sc.console.declaredUnset.add(name);
			}
			if( val instanceof String v && sc.rawVariable(name) instanceof ShellContext.NameRef r && r.target().isEmpty()
					&& !ShellContext.validReference(v)) {
				// declare r=/ of a nameref with no value
				error(command, "`"+v+"': not a valid identifier");
				status = 1;
				continue;
			}
			if( local ) {
				if( val == null ) {
					if( !sc.isOwnLocal(name) && sc.isInFunction() && temporary(name)) {
						// local x of a variable the function (or this command) was given (x=1 f):
						// that value
						sc.setLocalVariable(name, sc.getVariable(name));
					} else if( !sc.isOwnLocal(name)) {
						sc.declareLocal(name);
					}
				} else {
					sc.setLocalVariable(name, val);
				}
			} else if( val != null && toGlobal ) {
				// declare -g in a function: the global, past any local of that name
				sc.setGlobalChecked(name, val);
			} else if( val != null ) {
				sc.setVariable(name, val);
			}
			if( o.indexOf('r') >= 0 && !remove ) {
				// (readonly ref: the variable it names; not an element)
				String target = sc.resolveName(name);
				if( target.indexOf('[') > 0 ) {
					error(command, "`"+target+"': not a valid identifier");
					status = 1;
					continue;
				}
				sc.console.setReadonly(target);
				if( local && sc.isInFunction()) {
					sc.readonlyHere(target);
				}
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

	/** a value to start a local with (an array: its own copy) */
	private static Object copy(Object v) {
		if( v instanceof FshList f ) {
			return copyOf(f);
		}
		if( v instanceof Map<?,?> m ) {
			Map<String,Object> ret = new TreeMap<>();
			for(Map.Entry<?,?> e : m.entrySet()) {
				ret.put(String.valueOf(e.getKey()), e.getValue());
			}
			return ret;
		}
		return v;
	}

	private static FshList copyOf(FshList list) {
		FshList ret = new FshList();
		for(int i : list.getIndexes()) {
			ret.set(i, list.get(i));
		}
		return ret;
	}

	/** declare -f [name ...]: functions as code; -F: their names */
	private int functions(String command, List<Object> items, boolean names, boolean p, boolean exported, boolean readonly) {
		List<String> wanted = new ArrayList<>();
		for(Object item : items) {
			wanted.add(item instanceof Ast.Assignment a ? a.name : String.valueOf(item));
		}
		if( wanted.isEmpty()) {
			for(String n : new TreeSet<>(sc.console.getFunctions().keySet())) {
				// (declare -xf: the exported ones)
				if( (!exported || sc.console.getFunctions().get(n).isExported()) && (!readonly || sc.console.isReadonlyFunction(n))) {
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
				// (with its attributes: declare -fr f, declare -fx f)
				String flags = "f"+(sc.console.isReadonlyFunction(name) ? "r" : "")+(f.isExported() ? "x" : "");
				sc.stdout.println(items.isEmpty() ? "declare -"+flags+" "+name : name);
			} else {
				sc.stdout.println(f.declaration());
			}
		}
		return ret;
	}

	/** a subscript's [ and ] pair up */
	private static boolean balanced(String subscript) {
		int depth = 0;
		for(char c : subscript.toCharArray()) {
			if( c == '[' ) {
				depth++;
			} else if( c == ']' && --depth < 0 ) {
				return false;
			}
		}
		return depth == 0;
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
			if( !sc.isInFunction() && !sc.console.unsetSpecials.contains("FUNCNAME")) {
				// (declared, with no value outside a function)
				all.add("FUNCNAME");
			}
			String wanted = o.replaceAll("[^aAiluc rxn]", "").replace(" ", "");
			for(String n : all) {
				if( Executor.isName(n) && (sc.getVariable(n) != null || sc.console.declaredUnset.contains(n) || n.equals("FUNCNAME") || sc.rawVariable(n) instanceof ShellContext.NameRef)) {
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
			if( name.equals("DIRSTACK") && sc.console.dirStack.isEmpty() && !sc.console.unsetSpecials.contains(name)) {
				// (as bash shows it before it is used)
				val = new FshList();
			}
			if( command.equals("readonly") && items.isEmpty() && sc.console.isOptionEnabled(us.bringardner.fsh.Console.Option.Posix)) {
				// set -o posix: readonly -a a=(..), readonly x="1"
				String d = declaration(name, val).substring("declare -".length());
				String flags = d.substring(0, d.indexOf(' ')).replace("r", "");
				sc.stdout.println("readonly "+(flags.isEmpty() ? "" : "-"+flags+" ")+d.substring(d.indexOf(' ')+1));
			} else if( val == null && (sc.console.declaredUnset.contains(name) || sc.isDeclaredLocal(name) || name.equals("FUNCNAME") && !sc.console.unsetSpecials.contains(name))) {
				sc.stdout.println(declaration(name, val));
			} else if( val == null && !(sc.rawVariable(name) instanceof ShellContext.NameRef)) {
				error(command.equals("readonly") ? "declare" : command, name+": not found");
				ret = 1;
			} else {
				sc.stdout.println(declaration(name, val));
			}
		}
		return ret;
	}

	/** name has a temporary value now (x=1 f, x=1 local x) */
	private boolean temporary(String name) {
		for(List<Object[]> l : sc.console.temporaryAssignments) {
			for(Object[] o : l) {
				if( name.equals(o[0]) && sc.getVariable(name) != null ) {
					return true;
				}
			}
		}
		return false;
	}

	/** declare -- x="1", declare -a a=([0]="x"), declare -A m=([k]="v" ) */
	private String declaration(String name, Object val) {
		StringBuilder value = new StringBuilder();
		if( sc.rawVariable(name) instanceof ShellContext.NameRef ) {
			// (what it names as given, not followed; nothing yet: just the name)
			String target = ((ShellContext.NameRef) sc.rawVariable(name)).target();
			return "declare -"+flags(name, val)+" "+name+(target.isEmpty() ? "" : "="+quote(target));
		}
		String flags = flags(name, val);
		boolean declaredOnly = sc.hasLocal(name) ? sc.isDeclaredLocal(name) : sc.console.declaredUnset.contains(name);
		if( (declaredOnly || val == null && name.equals("FUNCNAME")) && (val == null || val instanceof Map<?,?> m0 && m0.isEmpty() || val instanceof List<?> l0 && l0.isEmpty())) {
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
		String flags = val instanceof Map<?,?> ? "A" : val instanceof List<?> || val == null && name.equals("FUNCNAME") ? "a" : "";
		if( sc.console.isInteger(name)) {
			flags += "i";
		}
		if( sc.rawVariable(name) instanceof ShellContext.NameRef ) {
			// (its own attributes: i n r x)
			flags = (sc.console.isInteger(name) ? "i" : "")+"n";
			if( sc.console.isReadonly(name)) {
				flags += "r";
			}
			if( sc.getEvironmentVariable(name) != null ) {
				flags += "x";
			}
			return flags;
		}
		// (bash's order: a A i n r x c l u)
		if( sc.console.isReadonly(name)) {
			flags += "r";
		}
		if( sc.getEvironmentVariable(name) != null || val == null && sc.console.pendingExports.contains(name)) {
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
			quote = " \t\n'\"\\|&;()<>!{}*[?]^$`@".indexOf(k.charAt(i)) >= 0;
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

	/** name has a temporary assignment for the running command */
	private static boolean isTemporary(ShellContext sc, String name) {
		return sc.commandTemporaries.stream().anyMatch(t -> t[0].equals(name));
	}

	/** v in the case name is declared with (declare -l a=V), or has */
	private static String inCase(String o, String name, String v, ShellContext sc) {
		return o.indexOf('u') >= 0 ? v.toUpperCase() : o.indexOf('l') >= 0 ? v.toLowerCase()
				: o.indexOf('c') >= 0 && !v.isEmpty() ? v.substring(0, 1).toUpperCase()+v.substring(1).toLowerCase() : String.valueOf(sc.cased(name, v));
	}
}
