package us.bringardner.fsh;


import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.signal.FshException;
import us.bringardner.fsh.exec.Programs;

public class ShellContext {

	public enum LoopControl {Break,Continue}


	private static final String LOCAL_VARIABLES = "Local - Variables";

	public PrintStream stdout = System.out;
	public PrintStream stderr = System.err;
	public InputStream stdin = System.in;

	public Console console;	
	public Integer exitCode; 
	private Stack<Map<Object,Object>> commandStack = new Stack<>();
	private List<String> activeAlias = new ArrayList<>();
	/** the commands the executor is running, innermost last (for a debugger) */
	private final Stack<us.bringardner.fsh.syntax.Ast.Node> nodeStack = new Stack<>();
	private AtomicBoolean pause = new AtomicBoolean();
	private AtomicReference<RuntimeException> exeption = new AtomicReference<>();

	public ShellContext() {
		enterCommand();
	}

	public ShellContext(Console console) {
		this();
		this.console = console;
		stderr = console.getStdErr();
		stdout = console.getStdOut();
		stdin = console.getStdIn();
		if( Console.isKeyboard(stdin)) {
			stdin = new NativeKeyboard();
		}

	}

	public void addFunction(ShellFunction function) {
		console.addFunction(function);
	}

	public ShellFunction getFunction(String name) {
		return console.getFunction(name);
	}

	public Map<String, ShellFunction> getFunctions() {
		return console.getFunctions();
	}


	public void enterCommand() {
		Map<Object,Object> map = new HashMap<>();
		Map<String,Object> l = new HashMap<>();
		map.put(LOCAL_VARIABLES, l);
		commandStack.push(map);
	}

	public void exitCommand() {
		commandStack.pop();
	}

	public void setValue(Object key,Object value) {
		if( !commandStack.isEmpty()) {
			commandStack.peek().put(key, value);
		}
	}

	public Object getValue(Object key) {

		Object ret = null;
		if( !commandStack.isEmpty()) {
			Map<Object, Object> map = commandStack.peek();
			ret = map.get(key);
			if( ret == null ) {
				ret = console.getVariable(key.toString());
			}
		}

		return ret;
	}

	public Object getValue(Object key,Object def) {
		Object ret = commandStack.peek().get(key);
		if( ret == null ) {
			ret = def;
		}
		return ret;
	}



	/** a double-quoted string as written ("..."), without the $ of $"..." */
	public static String dq(String token) {
		return token.startsWith("$") ? token.substring(1) : token;
	}

	/**
	 * The escapes of $'...', as bash reads them (each after a backslash): a b e E f n r t v, a backslash,
	 * quotes and ?, nnn (octal), xHH, uHHHH, UHHHHHHHH and cX (control-X). Anything else keeps its backslash.
	 */
	public static String ansiC(String text) {
		StringBuilder ret = new StringBuilder();
		int n = text.length();
		for (int idx = 0; idx < n; idx++) {
			char c = text.charAt(idx);
			if( c != '\\' || idx+1 >= n ) {
				ret.append(c);
				continue;
			}
			char e = text.charAt(++idx);
			switch (e) {
			case 'a': ret.append('\u0007'); break;
			case 'b': ret.append('\b'); break;
			case 'e':
			case 'E': ret.append('\u001b'); break;
			case 'f': ret.append('\f'); break;
			case 'n': ret.append('\n'); break;
			case 'r': ret.append('\r'); break;
			case 't': ret.append('\t'); break;
			case 'v': ret.append('\u000b'); break;
			case '\\': case '\'': case '"': case '?': ret.append(e); break;
			case 'c':
				if( idx+1 < n && text.charAt(idx+1) == '\\' && idx+2 < n && text.charAt(idx+2) == '\\' ) {
					// \c\\ is control-backslash
					idx++;
				}
				if( idx+1 < n ) {
					// as bash's TOCTRL: \c? is DEL
					char x = text.charAt(++idx);
					ret.append(x == '?' ? (char) 0x7f : (char)(Character.toUpperCase(x) & 0x1f));
				} else {
					ret.append("\\c");
				}
				break;
			case 'x': case 'u': case 'U': {
				if( e == 'x' && idx+1 < n && text.charAt(idx+1) == '{' ) {
					// \x{HHH}: all the hex digits, a byte (bash's), the } if it is there
					int end = idx+2;
					while( end < n && Character.digit(text.charAt(end), 16) >= 0 ) {
						end++;
					}
					int value = end == idx+2 ? 0 : (int) (Long.parseLong(text.substring(idx+2, Math.min(end, idx+2+15)), 16) & 0xff);
					if( end < n && text.charAt(end) == '}' ) {
						end++;
					}
					idx = end-1;
					if( value == 0 ) {
						// a NUL ends the string, as in bash
						return ByteText.finish(ret);
					}
					ret.append(ByteText.mark(value));
					break;
				}
				int max = e == 'x' ? 2 : e == 'u' ? 4 : 8;
				int end = idx+1;
				while( end < n && end-idx-1 < max && Character.digit(text.charAt(end), 16) >= 0 ) {
					end++;
				}
				if( end == idx+1 ) {
					ret.append('\\').append(e);
				} else if( e == 'x' ) {
					// a byte (of UTF-8 text with the ones next to it); a NUL ends the string
					int value = Integer.parseInt(text.substring(idx+1, end), 16);
					if( value == 0 ) {
						return ByteText.finish(ret);
					}
					ret.append(ByteText.mark(value));
					idx = end-1;
				} else {
					ret.appendCodePoint(Integer.parseInt(text.substring(idx+1, end), 16));
					idx = end-1;
				}
				break;
			}
			default:
				if( e >= '0' && e <= '7' ) {
					int end = idx;
					while( end < n && end-idx < 3 && text.charAt(end) >= '0' && text.charAt(end) <= '7' ) {
						end++;
					}
					int value = Integer.parseInt(text.substring(idx, end), 8) & 0xff;
					if( value == 0 ) {
						// a NUL ends the string, as in bash
						return ByteText.finish(ret);
					}
					ret.append(ByteText.mark(value));
					idx = end-1;
				} else {
					ret.append('\\').append(e);
				}
			}
		}
		return ByteText.finish(ret);
	}




	/**
	 * Some of these should be in console but other in context.
	 * 
	 * @param name
	 * @return
	 */
	private Object getSpecialParameter(String name) {
		List<Object> positionalParameters = topPositional();
		if( !functionStack.isEmpty()) {
			positionalParameters = functionStack.peek().args;
		}
		Object ret = null;
		char op = name.charAt(1);

		char seperator = ' ';
		switch(op) {


		/*
		 *
($*) Expands to the positional parameters, starting from one. When the expansion is not within double quotes, 
		each positional parameter expands to a separate word. In contexts where it is performed, those words are 
		subject to further word splitting and filename expansion. When the expansion occurs within double quotes, 
		it expands to a single word with the value of each parameter separated by the first character of the IFS special variable. 
		That is, "$*" is equivalent to "$1c$2c…", where c is the first character of the value of the IFS variable. If IFS is unset, 
		the parameters are separated by spaces. If IFS is null, the parameters are joined without intervening separators.
		 */
		case '*':
			// joined by the first character of IFS (a local IFS too); an empty IFS joins with nothing
			Object tmp = getVariable(Console.IFS);
			if( tmp !=null) {
				String tmp2 = tmp.toString();
				if( tmp2.length()>0) {
					seperator = tmp2.charAt(0);
				} else {
					StringBuilder joined = new StringBuilder();
					for(int idx=1, sz= positionalParameters.size(); idx < sz; idx++) {
						joined.append(positionalParameters.get(idx));
					}
					return joined.toString();
				}
			}
			// fall through
			/*
@
($@) Expands to the positional parameters, starting from one. In contexts where word splitting is performed,
 		this expands each positional parameter to a separate word; if not within double quotes, these words are subject to word splitting. 
 		In contexts where word splitting is not performed, this expands to a single word with each positional parameter separated by a space. 
 		When the expansion occurs within double quotes, and word splitting is performed, each parameter expands to a separate word. 
 		That is, "$@" is equivalent to "$1" "$2" …. If the double-quoted expansion occurs within a word, the expansion of the first parameter 
 		is joined with the beginning part of the original word, and the expansion of the last parameter is joined with the last part of the original word. 
 		When there are no positional parameters, "$@" and $@ expand to nothing (i.e., they are removed).
			 */
		case '@':
			StringBuilder buf = new StringBuilder();
			for(int idx=1, sz= positionalParameters.size(); idx < sz;idx++) {
				if( !buf.isEmpty()) {
					buf.append(seperator);
				}
				buf.append(positionalParameters.get(idx));
			}
			ret = buf.toString();
			break;
			/*
#
($#) Expands to the number of positional parameters in decimal.
			 */
		case '#': ret = positionalParameters.size()-1;
		break;
		/*
?
($?) Expands to the exit status of the most recently executed foreground pipeline.
		 */
		case '?':ret = console.getLastExitCode(); 
		break;
		/*
-
($-, a hyphen.) Expands to the current option flags as specified upon invocation, by the set builtin command, 
		or those set by the shell itself (such as the -i option).
		 */
		case '-':ret = console.optionFlags();
		break;
		/*
$
($$) Expands to the process ID of the shell. In a subshell, it expands to the process ID of the invoking shell, not the subshell.
		 */
		// the shell's process id (the JVM's); $BASHPID is the same
		case '$':ret = ProcessHandle.current().pid();
		break;
		/*
!
($!) Expands to the process ID of the job most recently placed into the background, whether executed as an asynchronous command or 
		using the bg builtin (see Job Control Builtins).
		 */
		case '!':ret = console.getLastPid() == 0 ? null : console.getLastPid();break;
		/*
0
($0) Expands to the name of the shell or shell script. This is set at shell initialization. 
		If Bash is invoked with a file of commands (see Shell Scripts), $0 is set to the name of that file. 
		If Bash is started with the -c option (see Invoking Bash), then $0 is set to the first argument after the string to be executed, 
		if one is present. Otherwise, it is set to the filename used to invoke Bash, as given by argument zero.

		file of cmd name is in arg[0]
		 */


		}
		return ret;
	}


	/**
	 * The text a variable expands to: an unset variable (null) is empty, as in bash, or an error
	 * after set -u. Without this, unset variables printed as "null".
	 * 
	 * @param name the variable as written, for the error message ($x, ${x} ...)
	 */
	/**
	 * set -u and an unset variable: an error, which ends a script (as in bash).
	 */
	public void unbound(String name) {
		String msg = name+": unbound variable";
		if( console.isInteractive ) {
			throw new RuntimeException(msg);
		}
		stderr.println(msg);
		throw new us.bringardner.fsh.signal.ExitException(this, 1);
	}

	public String expand(Object value, String name) {
		if( value == null ) {
			if( console != null && console.isOptionEnabled(Option.NullParameterIsError)) {
				unbound(name.replaceAll("^\\$\\{?|\\}$", ""));
			}
			return "";
		}
		return value.toString();
	}


	/** an array or map copied (so changing one does not change the other); anything else as it is */
	public static Object copyValue(Object val) {
		if( val instanceof FshList list ) {
			FshList ret = new FshList();
			for(int idx : list.getIndexes()) {
				ret.set(idx, list.get(idx));
			}
			return ret;
		} else if( val instanceof List<?> list ) {
			return new ArrayList<Object>(list);
		} else if( val instanceof TreeMap<?,?> map ) {
			return new TreeMap<Object,Object>(map);
		} else if( val instanceof Map<?,?> map ) {
			return new java.util.LinkedHashMap<Object,Object>(map);
		}
		return val;
	}

	/** an array's element 0 (a map's "0"); anything else as it is */
	public static Object firstElement(Object val) {
		if( val instanceof List<?> ) {
			return ((List<?>) val).isEmpty() ? null : ((List<?>) val).get(0);
		} else if( val instanceof Map<?,?> ) {
			return ((Map<?,?>) val).get("0");
		}
		return val;
	}



	/**
	 * A pipe stage's own variables (null: the shell's are used): its assignments go here, so they
	 * do not reach the shell, as in bash where each stage is a subshell. UNSET marks an unset one.
	 */
	private Map<String,Object> isolated;

	/**
	 * How many conditions are being evaluated (an if or while test, the left side of && or ||, a
	 * command after !): set -e does not end the script for a command that fails there.
	 */
	public int conditionDepth;
	private static final Object UNSET = new Object();

	/** a variable outside any function: the stage's own, or the shell's */
	private Object globalVariable(String name) {
		if( isolated != null && isolated.containsKey(name)) {
			Object v = isolated.get(name);
			return v == UNSET ? null : v;
		}
		return console.getVariable(name);
	}

	private void setGlobalVariable(String name, Object value) {
		if( isolated != null ) {
			isolated.put(name, value == null ? UNSET : value);
		} else {
			console.setVariable(name, value);
		}
	}

	@SuppressWarnings("unchecked")
	public void setVariable(String name,Object index, Object value) {
		name = resolveName(name);
		console.declaredUnset.remove(name);
		Object val = globalVariable(name);
		if( isolated != null && !isolated.containsKey(name)) {
			// the shell's array: change a copy
			if( val instanceof FshList ) {
				FshList copy = new FshList();
				for(int idx : ((FshList) val).getIndexes()) {
					copy.set(idx, ((FshList) val).get(idx));
				}
				val = copy;
			} else if( val instanceof Map<?,?> ) {
				val = new TreeMap<>((Map<String,Object>) val);
			}
		}

		if( val == null) {
			if (index instanceof Integer) {
				Integer idx = (Integer) index;
				List<Object> list = new FshList();
				list.add(idx, value);
				val = list;
			} else {
				Map<String,Object> map = new TreeMap<>();
				map.put(""+index, value);
				val = map;
			}
		} else if (val instanceof List<?>) {
			Integer idx = (Integer) index;
			List<Object> list = (List<Object>)val;
			list.add(idx, value);
		} else if (val instanceof Map<?,?>) {
			Map<String,Object> map = (Map<String, Object>)val;
			map.put(""+index, value);
		}
		setGlobalVariable(name, val);
	}

	/** OPTIND=n: getopts starts at word n's first letter, as bash's */
	private void optindAssigned(Object value) {
		try {
			console.setGetoptsPosition(Integer.parseInt(String.valueOf(firstElement(value)).trim()), 0);
		} catch (NumberFormatException e) {
			console.setGetoptsPosition(1, 0);
		}
	}

	public void setVariable(String name, Object value) {
		if( !(value instanceof NameRef)) {
			name = resolveName(name);
			value = withCase(name, value);
		}
		if( console.isReadonly(name)) {
			throw new ReadonlyException(name);
		}
		if( name.equals("OPTIND")) {
			optindAssigned(value);
		}
		if( value != null && !(value instanceof List<?> l && l.isEmpty()) && !(value instanceof Map<?,?> m && m.isEmpty())) {
			console.declaredUnset.remove(name);
		}
		if( (name.equals("RANDOM") || name.equals("SECONDS")) && !console.unsetSpecials.contains(name) && value != null
				&& !(value instanceof List<?>) && !(value instanceof Map<?,?>)) {
			// RANDOM=n seeds it, SECONDS=n counts from n (neither keeps the value itself)
			long n;
			try {
				n = Long.parseLong(value.toString().trim());
			} catch (NumberFormatException e) {
				n = 0;
			}
			if( name.equals("RANDOM")) {
				console.seedRandom(n);
			} else {
				console.setSeconds(n);
			}
			return;
		}
		if( name.equals("PATH")) {
			// as bash: a new PATH forgets the remembered programs
			console.hashTable.clear();
		}
		if( name.equals("POSIXLY_CORRECT") && value != null ) {
			// as bash: setting it turns on posix mode
			console.setOption(Console.Option.Posix, true);
		}
		FunctionInvocation scope = localScope(name);
		if( scope != null ) {
			// name=value sets the local variable of the nearest function that has one (bash's
			// dynamic scope: a function sees, and sets, its caller's locals)
			scope.local.put(name, value);
		} else {
			setGlobalVariable(name, value);
			if( isolated == null && value != null && !(value instanceof List<?>) && !(value instanceof Map<?,?>) && !(value instanceof NameRef)
					&& console.getEvironmentVariables(name) != null ) {
				// an exported variable: its new value is exported
				console.setEnvironmentVariable(name, ""+value);
			}
			if( value != null && !(value instanceof List<?>) && !(value instanceof Map<?,?>) && !(value instanceof NameRef)
					&& console.isOptionEnabled(Console.Option.MarkAllForExport) && Character.isLetter(name.charAt(0)) ) {
				// set -a: every variable that is set is exported
				console.setEnvironmentVariable(name, ""+value);
			}
		}
	}

	/** a function's local variable after unset: it stays unset (the global is not seen) until the function returns */
	private static final Object UNSET_LOCAL = new Object();

	/** a nameref's value (declare -n): the name of the variable it stands for */
	public static final class NameRef {
		final String target;
		NameRef(String target) {
			this.target = target;
		}
	}

	/**
	 * declare -n name=target (local: the function's).
	 */
	public void setNameRef(String name, String target, boolean local) {
		NameRef ref = new NameRef(target);
		if( local && !functionStack.isEmpty()) {
			functionStack.peek().local.put(name, ref);
		} else if( local ) {
			setLocalVariable(name, ref);
		} else {
			setGlobalVariable(name, ref);
		}
	}

	/**
	 * local x with no value: the function's, unset until it is given one.
	 */
	public void declareLocal(String name) {
		if( !functionStack.isEmpty()) {
			functionStack.peek().local.put(name, UNSET_LOCAL);
		}
	}

	/** the variable a name stands for: itself, or what its nameref names (a few levels deep) */
	public String resolveName(String name) {
		for (int depth = 0; depth < 8; depth++) {
			Object raw = rawVariable(name);
			if( !(raw instanceof NameRef) || ((NameRef) raw).target.isEmpty()) {
				return name;
			}
			name = ((NameRef) raw).target;
		}
		return name;
	}

	/** a variable's own value (a NameRef stays one) */
	public Object rawVariable(String name) {
		FunctionInvocation scope = localScope(name);
		if( scope != null ) {
			Object v = scope.local.get(name);
			return v == UNSET_LOCAL ? null : v;
		}
		@SuppressWarnings("unchecked")
		Map<String,Object> l = commandStack.isEmpty() ? null : (Map<String, Object>) commandStack.peek().get(LOCAL_VARIABLES);
		if( l != null && l.containsKey(name)) {
			return l.get(name);
		}
		return globalVariable(name);
	}

	public boolean unSetVariable(String name) {
		return unSetVariable(name, true);
	}

	/** @param follow false for unset -n: a nameref itself, not the variable it names */
	public boolean unSetVariable(String name, boolean follow) {
		if( follow ) {
			name = resolveName(name);
		}
		if( console.isReadonly(name)) {
			throw new ReadonlyException(name);
		}
		FunctionInvocation scope = localScope(name);
		if( scope != null ) {
			scope.local.put(name, UNSET_LOCAL);
			return true;
		}

		Map<Object, Object> map = commandStack.peek();

		@SuppressWarnings("unchecked")
		Map<String,Object> l = (Map<String, Object>) map.get(LOCAL_VARIABLES);		
		Object val = l.get(name);
		if( val != null ) {
			l.remove(name);
			return true;
		}

		if( isolated != null ) {
			boolean was = globalVariable(name) != null;
			isolated.put(name, UNSET);
			return was;
		}
		val = console.variables.remove(name);
		if( val !=null) {
			// (an exported one is no longer in the environment either)
			console.removeEnvironmentVariables(name);
			return true;
		}

		if( console.removeEnvironmentVariables(name) != null) {
			return true;
		}

		return false;
	}

	public Object getVariable(String name) {
		Object ret = getVariable0(name);
		// a nameref stands for the variable it names
		for (int depth = 0; depth < 8 && ret instanceof NameRef; depth++) {
			String target = ((NameRef) ret).target;
			ret = target.isEmpty() ? null : getVariable0(target);
		}
		return ret;
	}

	private Object getVariable0(String name) {
		if( name.charAt(0)=='$') {
			char c = name.charAt(1);
			if( c=='_' || Character.isLetterOrDigit(c)) {
				name = name.substring(1);
				int pos = -1;
				try {
					pos = Integer.parseInt(name);
				} catch (Exception e) {
				}
				if( pos>=0 ) {
					return getPositionalVariable(pos);					
				}

			} else {
				return getSpecialParameter(name);
			}
		}

		FunctionInvocation scope = localScope(name);
		if( scope != null && scope.local.get(name) == UNSET_LOCAL ) {
			return null;
		}
		if( name.equals("FUNCNAME")) {
			// the running functions, innermost first, then main
			if( functionStack.isEmpty()) {
				return null;
			}
			FshList names = new FshList();
			for (int idx = functionStack.size()-1; idx >= 0; idx--) {
				if( functionStack.get(idx).function != null ) {
					names.add(functionStack.get(idx).function.getName());
				}
			}
			if( names.isEmpty()) {
				// (only ${ list; } frames)
				return null;
			}
			names.add("main");
			return names;
		}
		if( name.equals("BASH_LINENO")) {
			// the line each running function was called from, innermost first, then 0 (main; outside
			// a function that is all)
			FshList lines = new FshList();
			for (int idx = functionStack.size()-1; idx >= 0; idx--) {
				lines.add(String.valueOf(functionStack.get(idx).callLine));
			}
			lines.add("0");
			return lines;
		}
		if( name.equals("_")) {
			// the last argument of the last command (not the _ the JVM was started with)
			return console.lastArgument;
		}
		if( name.equals("BASHPID")) {
			return ProcessHandle.current().pid();
		}
		if( name.equals("RANDOM") && !console.unsetSpecials.contains(name)) {
			return console.random();
		}
		if( name.equals("SRANDOM") && !console.unsetSpecials.contains(name)) {
			return Integer.toUnsignedLong(SECURE.nextInt());
		}
		if( name.equals("SECONDS") && !console.unsetSpecials.contains(name)) {
			return console.seconds();
		}
		if( name.equals("BASH_SOURCE")) {
			// the files being sourced, innermost first, then the script
			FshList files = new FshList();
			for(java.util.Iterator<String> it = sourceFiles.descendingIterator(); it.hasNext(); ) {
				files.add(it.next());
			}
			files.add(""+getPositionalVariable(0));
			return files;
		}
		if( name.equals("LINENO")) {
			if( trapLine != null ) {
				// in a trap: the line of the command it ran for
				return trapLine;
			}
			return currentLine();
		}
		if( name.equals("BASH_COMMAND")) {
			// the command running (in a trap: the one the trap ran for)
			return currentCommand;
		}
		Object ret = getLocalVariable(name);
		if( ret == null ) {
			if( isolated != null && isolated.containsKey(name)) {
				Object v = isolated.get(name);
				return v == UNSET ? null : v;
			}
			ret = console.getVariable(name);
			if( ret == null) {
				ret = getEvironmentVariable(name);
			}
			if( ret == null ) {
				ret = dynamicVariable(name);
			}
		}
		return ret;
	}

	private static final java.security.SecureRandom SECURE = new java.security.SecureRandom();

	private static volatile String hostName;
	private static volatile Long userId;

	/** $PPID $UID $EUID $HOSTNAME $EPOCHSECONDS $EPOCHREALTIME, as bash sets them */
	private static Object dynamicVariable(String name) {
		switch (name) {
		case "PPID":
			return ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(0L);
		case "UID":
		case "EUID":
			if( userId == null ) {
				userId = findUserId();
			}
			return userId;
		case "HOSTNAME":
			if( hostName == null ) {
				hostName = findHostName();
			}
			return hostName;
		case "EPOCHSECONDS":
			return System.currentTimeMillis()/1000;
		case "EPOCHREALTIME": {
			java.time.Instant now = java.time.Instant.now();
			return String.format("%d.%06d", now.getEpochSecond(), now.getNano()/1000);
		}
		default:
			return null;
		}
	}

	private static Long findUserId() {
		try {
			// jdk.security.auth, where there is one
			Class<?> c = Class.forName("com.sun.security.auth.module.UnixSystem");
			return (Long) c.getMethod("getUid").invoke(c.getConstructor().newInstance());
		} catch (Throwable e) {
		}
		String id = run("id", "-u");
		try {
			return id == null ? 0L : Long.parseLong(id);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static String findHostName() {
		String ret = System.getenv("HOSTNAME");
		if( ret == null || ret.isEmpty()) {
			ret = run("hostname");
		}
		if( ret == null || ret.isEmpty()) {
			try {
				ret = java.net.InetAddress.getLocalHost().getHostName();
			} catch (Exception e) {
				ret = "localhost";
			}
		}
		return ret;
	}

	/** the first line a command prints, or null */
	private static String run(String ... cmd) {
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
				String line = r.readLine();
				p.waitFor();
				return line == null ? null : line.trim();
			}
		} catch (Exception e) {
			return null;
		}
	}

	private Object getPositionalVariable(int pos) {
		if( pos == 0 ) {
			// $0 is the script's in a function too, as in bash
			List<Object> top = topPositional();
			return top.isEmpty() ? "" : top.get(0);
		}
		if( !functionStack.isEmpty()) {
			FunctionInvocation inv = functionStack.peek();
			if(pos>0 && pos < inv.args.size()) {
				Object val = inv.args.get(pos);
				return val;
			} else {
				return null;
			}
		} else {
			int sz = topPositional().size();
			if( pos < sz) {
				return topPositional().get(pos);
			}
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	/** the innermost running function with a local variable name, or null */
	private FunctionInvocation localScope(String name) {
		for (int idx = functionStack.size()-1; idx >= 0; idx--) {
			if( functionStack.get(idx).local.containsKey(name)) {
				return functionStack.get(idx);
			}
		}
		return null;
	}

	/** an assignment to a readonly variable */
	public static class ReadonlyException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public ReadonlyException(String name) {
			super(name+": readonly variable");
		}
	}

	/** name is a local variable of the running function itself (not a caller's) */
	public boolean isOwnLocal(String name) {
		return !functionStack.isEmpty() && functionStack.peek().local.containsKey(name);
	}

	public Object getLocalVariable(String name) {
		FunctionInvocation inv = localScope(name);
		if( inv != null ) {
			Object tmp = inv.local.get(name);
			if( tmp == UNSET_LOCAL ) {
				return null;
			}
			if( tmp !=null) {
				return tmp;
			}
		}

		Map<Object, Object> map = commandStack.peek();

		Map<String,Object> l = (Map<String, Object>) map.get(LOCAL_VARIABLES);		
		Object ret = l.get(name);		
		return ret;
	}

	public void setLocalVariable(String name, Object val) {
		val = withCase(name, val);
		if( name.equals("OPTIND") && val != null ) {
			optindAssigned(val);
		}
		if( !functionStack.isEmpty()) {
			FunctionInvocation inv = functionStack.peek();
			inv.local.put(name, val);
		} else {
			@SuppressWarnings("unchecked")
			Map<String,Object> l = (Map<String, Object>) getValue(LOCAL_VARIABLES);
			l.put(name, val);
		}
	}

	public FileSource getFileSource(String path) throws IOException {
		return console.createFileSource(path);
	}

	/**
	 * A context for $( ), a pipe stage or a background job. It starts with copies of the running
	 * functions ($1, $@, local variables) and of the local variables (such as for loop variables),
	 * so changes made in it are not seen here, as in bash.
	 */
	@SuppressWarnings("unchecked")
	public ShellContext subShell() {
		ShellContext ret = new ShellContext(console);
		ret.line = line;
		ret.job = job;
		ret.loopDepth = loopDepth;
		ret.substitutionLevel = substitutionLevel;
		ret.debugBlocked = debugBlocked + (console.isOptionEnabled(Console.Option.FuncTrace) ? 0 : 1);
		ret.stdout = stdout;
		ret.stdin = stdin;
		ret.stderr = stderr;
		ret.errTrapBlocked = errTrapBlocked + (console.isOptionEnabled(Console.Option.ErrTrace) ? 0 : 1);
		// in a condition (if ( false; ... )) set -e stays off in the subshell too
		ret.conditionDepth = conditionDepth;
		for(FunctionInvocation inv : functionStack) {
			ret.functionStack.push(inv.copy());
		}
		if( !commandStack.isEmpty()) {
			Map<String,Object> l = (Map<String, Object>) commandStack.peek().get(LOCAL_VARIABLES);
			if( l != null ) {
				((Map<String, Object>) ret.commandStack.peek().get(LOCAL_VARIABLES)).putAll(l);
			}
		}
		// a subshell inside a pipe stage sees (a copy of) the stage's variables
		if( isolated != null ) {
			ret.isolated = new java.util.HashMap<>(isolated);
		}
		if( stagePositional != null ) {
			ret.stagePositional = new ArrayList<>(stagePositional);
		}
		return ret;
	}

	/**
	 * A subshell for a pipe stage: it runs at the same time as the shell, so it gets its own
	 * variables instead of a snapshot ( ... ) would restore.
	 */
	/** a pipe stage's own $0 $1 ... (set -- there), or null: the shell's */
	private List<Object> stagePositional;

	/** $0 $1 ... outside a function: the stage's own, or the shell's */
	private List<Object> topPositional() {
		return stagePositional != null ? stagePositional : console.positionalParameters;
	}

	/** this is (in) a pipe stage */
	public boolean isIsolated() {
		return isolated != null;
	}

	public ShellContext isolatedSubShell() {
		ShellContext ret = subShell();
		// the pipeline's status runs the ERR trap, not its stages
		ret.errTrapBlocked++;
		if( ret.isolated == null ) {
			ret.isolated = new java.util.HashMap<>();
		}
		return ret;
	}

	/**
	 * What is left to do when the running command is done (its <(cmd) and >(cmd)), in order; the
	 * executor runs what a command added once that command ends.
	 */
	public final List<Runnable> afterCommand = new ArrayList<>();

	/** how many sourced files are running (return ends the innermost) */
	public int sourceDepth;

	/** the files being sourced, outermost first ($BASH_SOURCE) */
	public final java.util.Deque<String> sourceFiles = new java.util.ArrayDeque<>();

	/**
	 * caller n: the call n frames up.
	 * @return {line, function (or main), file}, or null if there is no such frame
	 */
	public String [] callerFrame(int n) {
		int idx = functionStack.size()-1-n;
		if( idx < 0 ) {
			return null;
		}
		FunctionInvocation inv = functionStack.get(idx);
		String from = idx > 0 && functionStack.get(idx-1).function != null ? functionStack.get(idx-1).function.getName() : "main";
		String file = sourceFiles.isEmpty() ? ""+getPositionalVariable(0) : sourceFiles.peekLast();
		return new String[] {""+inv.callLine, from, file};
	}

	public Object getEvironmentVariable(String name) {		
		return console.getEvironmentVariables(name);
	}

	public void setEnvironmentVariable(String name,Object value) {
		console.setEnvironmentVariable(name, value);
	}

	public Map<String, Object> getEnvironmentVariables() {
		return console.getEnvironmentVariable();
	}

	class FunctionInvocation {
		List<Object> args = new ArrayList<>();;
		ShellFunction function;
		Map<String,Object> local = new TreeMap<>();

		int callLine;
		/** local -l / -u attributes of this function's variables */
		final Map<String,Character> caseAttributes = new java.util.HashMap<>();
		/** set -E is off: the ERR trap does not run in this function */
		boolean errBlocked;
		/** trap ... RETURN was set while this ran: it runs when this returns */
		boolean returnTrap;
		/** getopts' place (OPTIND, the letter in that word) when this was called */
		int [] getopts = {1, 0};

		/** ${ list; }: a frame for its locals and return, with the caller's parameters (function null) */
		FunctionInvocation(List<Object> callerArgs) {
			this.function = null;
			this.args.addAll(callerArgs);
		}

		public FunctionInvocation(Object[] args2, ShellFunction function) throws IOException {
			this.function = function;
			this.args.add(function.getName());
			this.args.addAll(Arrays.asList(args2));

		}

		private FunctionInvocation(FunctionInvocation other) {
			function = other.function;
			args.addAll(other.args);
			// a subshell's copy: its local arrays are its own
			for(Map.Entry<String,Object> e : other.local.entrySet()) {
				local.put(e.getKey(), copyValue(e.getValue()));
			}
		}

		FunctionInvocation copy() {
			return new FunctionInvocation(this);
		}

	}
	Stack<FunctionInvocation> functionStack = new Stack<>();

	public void enterFunction(Object[] args, ShellFunction function) throws IOException {
		Object tmp = getEvironmentVariable("FUNCNEST");
		if( tmp != null ) {
			try {
				int max = Integer.parseInt(tmp.toString());
				if( max >0 && max > functionStack.size()) {
					throw new RuntimeException("Max function deepth (FUNCNEST) exceeded. max="+max+" size="+functionStack.size());
					//line 4: f: maximum function nesting level exceeded (4)
				}
			} catch (Exception e) {
			}
		}

		FunctionInvocation inv = new FunctionInvocation(args,function);
		// the line it was called from (caller)
		inv.callLine = currentLine();
		inv.errBlocked = !console.isOptionEnabled(Console.Option.ErrTrace);
		if( inv.errBlocked ) {
			errTrapBlocked++;
		}
		inv.getopts = console.getoptsState();
		functionStack.push(inv);		
	}

	/** ${ list; } runs as a function does (local, return), with the caller's $1 ... */
	public void enterNofork() {
		FunctionInvocation inv = new FunctionInvocation(new ArrayList<>());
		// the caller's parameters themselves: shift and set -- in it change them
		inv.args = functionStack.isEmpty() ? topPositional() : functionStack.peek().args;
		inv.callLine = currentLine();
		inv.getopts = console.getoptsState();
		functionStack.push(inv);
	}

	public void exitFunction(ShellFunction functionDefStatement) {
		FunctionInvocation inv = functionStack.pop();
		if( inv.errBlocked ) {
			errTrapBlocked--;
		}
		if( inv.local.containsKey("OPTIND")) {
			// local OPTIND: getopts goes on where it was in the caller, as bash's
			console.setGetoptsPosition(inv.getopts[0], inv.getopts[1]);
		}
	}

	/**
	 * The ERR trap does not run inside functions, ( ), $( ) and pipe stages unless set -E is on
	 * (they do not inherit it): above 0 it is off.
	 */
	public int errTrapBlocked;

	/** l (declare -l), u (declare -u) or null: the case attribute of the variable name refers to */
	public Character caseAttribute(String name) {
		// the innermost function with the attribute or a local of that name decides
		for (int idx = functionStack.size()-1; idx >= 0; idx--) {
			FunctionInvocation inv = functionStack.get(idx);
			if( inv.caseAttributes.containsKey(name)) {
				return inv.caseAttributes.get(name);
			}
			if( inv.local.containsKey(name)) {
				return null;
			}
		}
		return console.getCaseAttribute(name);
	}

	/** declare -l/-u name, or +l/+u (attr null); local: the running function's variable */
	public void setCaseAttribute(String name, Character attr, boolean local) {
		if( local && !functionStack.isEmpty()) {
			if( attr == null ) {
				functionStack.peek().caseAttributes.remove(name);
			} else {
				functionStack.peek().caseAttributes.put(name, attr);
			}
		} else {
			console.setCaseAttribute(name, attr);
		}
	}

	/** a scalar value as the variable's case attribute makes it */
	private Object withCase(String name, Object value) {
		if( value == null || value instanceof List<?> || value instanceof Map<?,?> ) {
			return value;
		}
		Character attr = caseAttribute(name);
		if( attr == null ) {
			return value;
		}
		String v = value.toString();
		if( attr == 'c' ) {
			// capitalized: the first character upper case, the rest lower
			return v.isEmpty() ? v : v.substring(0, 1).toUpperCase()+v.substring(1).toLowerCase();
		}
		return attr == 'u' ? v.toUpperCase() : v.toLowerCase();
	}

	/** while a trap runs: the line of the command it ran for ($LINENO), or null */
	public Integer trapLine;
	/** the simple command running, as bash shows it in $BASH_COMMAND */
	public volatile String currentCommand = "";

	/** the line of the statement running (0 if none) */
	public int currentLine() {
		if( line > 0 ) {
			return line;
		}
		return nodeStack.isEmpty() ? 0 : nodeStack.peek().line;
	}

	/**
	 * What comes before an error message, as bash writes it: "script: line 3: " in a script
	 * (script is $0), "fsh: " in an interactive shell.
	 */
	public String errorPrefix() {
		if( console != null && console.isInteractive ) {
			return "fsh: ";
		}
		Object zero = getVariable("$0");
		return (zero == null || zero.toString().isEmpty() ? "fsh" : zero)+": line "+currentLine()+": ";
	}

	/** an error message, on standard error, after errorPrefix ("cd: x: No such file or directory") */
	public void error(String message) {
		stderr.println(errorPrefix()+message);
	}

	/** the line the new executor is running (0: none, the statement stack says) */
	public int line;

	/** how many loops the new executor is in (in this function): break and continue need one */
	public int loopDepth;

	/** how many $( ) deep this is: set -x repeats the first character of PS4 once more for each */
	public int substitutionLevel;

	/**
	 * The DEBUG trap does not run here when above 0: in a function, ( ) or $( ), unless set -T
	 * (functrace) is on, as in bash.
	 */
	public int debugBlocked;

	/** trap ... RETURN in a function: it runs when that function returns */
	public void returnTrapSet() {
		if( !functionStack.isEmpty()) {
			functionStack.peek().returnTrap = true;
		}
	}

	/** a function is returning: its RETURN trap runs (any function's, with set -T) */
	public void functionReturning() {
		if( !functionStack.isEmpty() && (functionStack.peek().returnTrap || console.isOptionEnabled(Console.Option.FuncTrace))) {
			console.runTrap(Console.ConsoleMetaSignal.Return, this);
		}
	}

	public boolean isInFunction() {
		return !functionStack.isEmpty();
	}

	public boolean removeFunction(String name) {
		return console.removeFunction(name);		
	}

	private final Object pauseLock = new Object();

	/**
	 * The job this runs in: when it is stopped (Ctrl-Z) or killed, so is what runs here (a
	 * subshell or pipe stage is in the job of the command that started it).
	 */
	public volatile us.bringardner.fsh.job.IJob job;

	public void setPause(boolean b) {
		pause.set(b);
		synchronized (pauseLock) {
			pauseLock.notifyAll();
		}
	}

	/**
	 * Wait while this context is paused (the job is suspended). Returns early when the context
	 * is stopped (see {@link #setExecption(Exception)}) so a suspended job can still be killed.
	 */
	public void waitWhilePaused() {
		synchronized (pauseLock) {
			while(isPaused() && getException() == null) {
				try {
					// the job's state is not signalled here
					pauseLock.wait(100);
				} catch (InterruptedException e) {
					// stop requests arrive through setExecption
				}
			}
		}
	}

	/**
	 * Sleep for up to millis, waking early when this context is stopped or paused.
	 */
	public void sleep(long millis) {
		long end = System.currentTimeMillis()+millis;
		synchronized (pauseLock) {
			while(getException() == null && !isPaused() && (console == null || !console.hasPendingSignal())) {
				long left = end-System.currentTimeMillis();
				if( left <= 0 ) {
					break;
				}
				try {
					pauseLock.wait(Math.min(left, 100));
				} catch (InterruptedException e) {
					// stop requests arrive through setExecption
				}
			}
		}
	}

	public boolean isPaused() {
		us.bringardner.fsh.job.IJob j = job;
		return pause.get() || (j != null && j.getState() == us.bringardner.fsh.job.JobState.Suspended);
	}

	public RuntimeException getException() {
		RuntimeException ret = exeption.get();
		us.bringardner.fsh.job.IJob j = job;
		if( ret == null && j != null ) {
			ret = j.getStopRequest();
		}
		return ret;
	}

	public void setExecption(Exception e) {
		setExecption0(e);
		synchronized (pauseLock) {
			pauseLock.notifyAll();
		}
	}

	private void setExecption0(Exception e) {
		if (e instanceof FshException) {
			FshException rte = (FshException) e;
			exeption.set(rte);
		} else {
			if (e instanceof RuntimeException) {
				exeption.set((RuntimeException) e);
			} else {
				exeption.set(new RuntimeException(e));
			}
		}
	}





	// only for debugging
	public Map<String,Object> getVariables() {
		Map<String,Object> ret = new TreeMap<>();
		ret.putAll(console.getEnvironmentVariable());
		ret.putAll(console.variables);
		Map<Object, Object> map = commandStack.peek();
		@SuppressWarnings("unchecked")
		Map<String,Object> local = (Map<String, Object>) map.get(LOCAL_VARIABLES);
		ret.putAll(local);
		Object zero = topPositional().get(0);
		ret.put("$0", zero);

		if( functionStack.size()>0) {
			FunctionInvocation inv = functionStack.peek();
			for(int idx=0,sz=inv.args.size(); idx<sz; idx++ ) {
				Object val = inv.args.get(idx) ;
				ret.put("$"+idx, val);
			}
		} else {
			List<Object> list = topPositional();
			for(int idx=0,sz=list.size(); idx<sz; idx++ ) {
				Object val = list.get(idx) ;
				ret.put("$"+idx, val);
			}
		}


		return ret;
	}

	/**
	 * @return $1, $2 ... of the running function, or of the script outside a function
	 */
	public List<Object> getPositionalParameterValues() {
		List<Object> all = functionStack.isEmpty() ? topPositional() : functionStack.peek().args;
		List<Object> ret = new ArrayList<>();
		if( all != null ) {
			for(int idx=1, sz=all.size(); idx < sz; idx++) {
				ret.add(all.get(idx));
			}
		}
		return ret;
	}

	/**
	 * Set $1, $2 ... of the running function, or of the script outside a function ($0 is kept).
	 */
	public void setPositionalParameterValues(List<Object> values) {
		if( functionStack.isEmpty() && isolated != null ) {
			// set -- in a pipe stage changes the stage's
			List<Object> list = new ArrayList<>();
			list.add(topPositional().isEmpty() ? "" : topPositional().get(0));
			list.addAll(values);
			stagePositional = list;
		} else if( functionStack.isEmpty()) {
			console.setPositionalParameters(false, values);
		} else {
			List<Object> args = functionStack.peek().args;
			Object zero = args.get(0);
			args.clear();
			args.add(zero);
			args.addAll(values);
		}
	}

	public List<Object>  getAllPositionalParameters() {
		List<Object> ret = new ArrayList<>();
		ret.addAll(topPositional());

		if( functionStack.size()>0) {
			FunctionInvocation inv = functionStack.peek();

			for(int idx=1,sz=inv.args.size(); idx<sz; idx++ ) {
				Object val = inv.args.get(idx) ;
				if( idx < ret.size()) {
					ret.add(idx, val);
				} else {
					ret.set(idx, val);
				}
			}
		}

		return ret;
	}

	/**
	 * Prevent recursive alias calls.
	 * @param name
	 * @return an alias assigned to name if, and only if, there is no active alias of that name
	 */
	public Object getAlias(String name) {
		Object ret = null;
		if( !activeAlias.contains(name)) {
			ret = console.alias.get(name);
		}

		return ret;
	}

	/**
	 * 
	 * @param name
	 */
	public void addActiveAlias(String name) {
		activeAlias.add(name);		
	}

	/**
	 * 
	 * @param name
	 */
	public void removeActiveAlias(String name) {
		activeAlias.remove(name);		
	}

	public int executeSubShell(FileSource file,Argument[] args) throws IOException {
		try (InputStream in = file.getInputStream()) {
			String code = new String(in.readAllBytes());
			Console sub = new Console();
			sub.setStdIn(stdin);
			sub.setStdErr(stderr);
			sub.setStdOut(stdout);

			FshList tmp = new FshList();
			if( args !=null) {
				for (int idx = 0; idx < args.length; idx++) {
					String val = ""+ args[idx].getValue(this);
					tmp.add(val);
				}
			}
			sub.setPositionalParameters(true, tmp);
			sub.setDebugContext(console.getDebugContext());
			// the exported variables and functions, as a program would get them
			for(Map.Entry<String,Object> e : getEnvironmentVariables().entrySet()) {
				sub.setEnvironmentVariable(e.getKey(), e.getValue());
			}
			for(ShellFunction f : console.getFunctions().values()) {
				if( f.isExported()) {
					sub.addFunction(f);
				}
			}

			int ret = sub.executeScript(code);

			return ret;				
		}
	}


	/**
	 * The executor starts a command (read from source): a debugger sees it (and stops at a breakpoint or a step);
	 * a stop or suspend of the job takes effect here.
	 */
	public void enterNode(us.bringardner.fsh.syntax.Ast.Node node, String source) {
		nodeStack.push(node);
		if( console != null ) {
			DebugContext debug = console.debugContext;
			debug.before(node, source, this);
			if( debug.isBreakpoint(new java.awt.Point(node.line, 0), this)
					|| debug.getCurrentState() == DebugContext.RunState.StepOver
					|| debug.getCurrentState() == DebugContext.RunState.StepInto ) {
				debug.setCurrentState(DebugContext.RunState.AtBreakpoint);
				while( debug.getCurrentState() == DebugContext.RunState.AtBreakpoint ) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
				}
			}
		}
		if( console != null && console.hasPendingSignal()) {
			console.runPendingTraps(this);
		}
		waitWhilePaused();
		if( getException() != null ) {
			throw getException();
		}
	}

	/** the executor is done with a command */
	public void exitNode(us.bringardner.fsh.syntax.Ast.Node node, String source) {
		if( !nodeStack.isEmpty()) {
			nodeStack.pop();
		}
		if( console != null ) {
			console.debugContext.after(node, source, this);
		}
	}

	/** the command running (null if none) */
	public us.bringardner.fsh.syntax.Ast.Node getLastNode() {
		return nodeStack.isEmpty() ? null : nodeStack.peek();
	}

	/** the commands running, innermost first, at most max */
	public List<us.bringardner.fsh.syntax.Ast.Node> getNodeStack(int max) {
		List<us.bringardner.fsh.syntax.Ast.Node> ret = new ArrayList<>();
		for (int i = nodeStack.size()-1; i >= 0 && ret.size() < max; i--) {
			ret.add(nodeStack.get(i));
		}
		return ret;
	}
}
