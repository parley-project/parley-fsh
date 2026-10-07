package us.bringardner.shell;


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

import us.bringardner.filesource.sh.FileSourceShParser.ArgVariableContext;
import us.bringardner.filesource.sh.FileSourceShParser.Array_indexContext;
import us.bringardner.filesource.sh.FileSourceShParser.Associative_indexContext;
import us.bringardner.filesource.sh.FileSourceShParser.StringContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.Console.Option;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl.Quoting;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.FsshException;
import us.bringardner.shell.antlr.statement.CommandStatement;
import us.bringardner.shell.antlr.statement.FunctionDefStatement;

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
	private Stack<Statement> statementStack = new Stack<>();
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
		if( stdin == Console.System_in) {
			stdin = new NativeKeyboard();
		}

	}

	public void addFunction(FunctionDefStatement function) {
		console.addFunction(function);
	}

	public FunctionDefStatement getFunction(String name) {
		return console.getFunction(name);
	}

	public Map<String, FunctionDefStatement> getFunctions() {
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


	public String expandString(StringContext context)  {
		if( context.SQ_STRING() != null ) {
			String tmp = context.SQ_STRING().getText();
			return tmp.substring(1, tmp.length()-1);				
		} else if( context.DQ_STRING() !=null) {

			String tmp = context.DQ_STRING().getText();
			String ret = FileSourceShPreProcessorVisitorImpl.processString(tmp.substring(1,tmp.length()-1), this, Quoting.DOUBLE_QUOTED);
			return ret;
		} else if( context.ESC()!=null) {
			String tmp = context.ESC().getText().substring(1);
			return tmp;
		} else if( context.ANSI_STRING()!=null) {
			String tmp = context.ANSI_STRING().getText();
			return ansiC(tmp.substring(2, tmp.length()-1));
		}
		throw new RuntimeException("No valid string for "+context.getText());
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
				if( idx+1 < n ) {
					ret.append((char)(text.charAt(++idx) & 0x1f));
				} else {
					ret.append("\\c");
				}
				break;
			case 'x': case 'u': case 'U': {
				int max = e == 'x' ? 2 : e == 'u' ? 4 : 8;
				int end = idx+1;
				while( end < n && end-idx-1 < max && Character.digit(text.charAt(end), 16) >= 0 ) {
					end++;
				}
				if( end == idx+1 ) {
					ret.append('\\').append(e);
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
					ret.append((char) Integer.parseInt(text.substring(idx, end), 8));
					idx = end-1;
				} else {
					ret.append('\\').append(e);
				}
			}
		}
		return ret.toString();
	}




	/**
	 * Some of these should be in console but other in context.
	 * 
	 * @param name
	 * @return
	 */
	private Object getSpecialParameter(String name) {
		List<Object> positionalParameters = console.positionalParameters;
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
			Object tmp = getValue(Console.IFS);
			if( tmp !=null) {
				String tmp2 = tmp.toString();
				if( tmp2.length()>0) {
					seperator = tmp2.charAt(0);
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
		case '-':StringBuilder bufx = new StringBuilder();
		for(Option flag : console.options) {
			bufx.append(flag.label);
		}

		ret = bufx.toString();
		break;
		/*
$
($$) Expands to the process ID of the shell. In a subshell, it expands to the process ID of the invoking shell, not the subshell.
		 */
		case '$':ret = 0;
		break;
		/*
!
($!) Expands to the process ID of the job most recently placed into the background, whether executed as an asynchronous command or 
		using the bg builtin (see Job Control Builtins).
		 */
		case '!':ret = console.getLastPid();break;
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

	public Object getVariable(VariableContext ctx)  {
		String name = ctx.getText();

		if( ctx.idOnly !=null ) {
			String tmp = ctx.idOnly.getText();
			Object val = getValue(tmp);
			if( val == null) {
				return ctx.idOnly.getText();
			} 
		} else 

			if( ctx.VARIABLE() !=null) {
				name = ctx.VARIABLE().getText();
			} else if( ctx.ID()!=null) {
				name = ctx.ID().getText();
			} 

		return index(getVariable(name), ctx.associative_index(), ctx.array_index());
	}

	/**
	 * The text a variable expands to: an unset variable (null) is empty, as in bash, or an error
	 * after set -u. Without this, unset variables printed as "null".
	 * 
	 * @param name the variable as written, for the error message ($x, ${x} ...)
	 */
	public String expand(Object value, String name) {
		if( value == null ) {
			if( console != null && console.isOptionEnabled(Option.NullParameterIsError)) {
				throw new RuntimeException(name.replaceAll("^\\$\\{?|\\}$", "")+": unbound variable");
			}
			return "";
		}
		return value.toString();
	}

	/**
	 * A variable that is part of a word ($name, $1, $? ... with an optional index).
	 */
	public Object getVariable(ArgVariableContext ctx)  {
		return index(getVariable(ctx.VARIABLE().getText()), ctx.associative_index(), ctx.array_index());
	}

	private Object index(Object ret, Associative_indexContext associativeIndex, Array_indexContext arrayIndex) {
		if( associativeIndex!=null) {
			// associative arrays should be in a parameter ${s[s]} so this should not happen.
			throw new RuntimeException("Handle associative array");
		}

		if( arrayIndex!=null) {
			Expression expr = new Expression(arrayIndex.expression());
			Object idx = expr.evaluate(this);
			if (idx instanceof Number) {
				int ii = ((Number) idx).intValue();
				if (ret instanceof List) {
					ret = ((List<?>) ret).get(ii);					
				}
			} else {

				if (ret instanceof Map) {
					ret = ((Map<?, ?>) ret).get(idx);					
				}
			}			
		}



		return ret;
	}

	public void setVariable(VariableContext variable,Object value) {
		String name = variable.getText();
		if( variable.ID()!=null ) {
			name = variable.ID().getText();
		}

		setVariable(name, value);				
	}

	@SuppressWarnings("unchecked")
	public void setVariable(String name,Object index, Object value) {
		Object val = console.getVariable(name);

		if( val == null) {
			if (index instanceof Integer) {
				Integer idx = (Integer) index;
				List<Object> list = new FsshList();
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
		console.setVariable(name, val);
	}

	public void setVariable(String name, Object value) {
		if( !functionStack.isEmpty() && functionStack.peek().local.containsKey(name)) {
			// name=value sets the function's local variable
			functionStack.peek().local.put(name, value);
		} else {
			console.setVariable(name, value);
		}
	}

	/** a function's local variable after unset: it stays unset (the global is not seen) until the function returns */
	private static final Object UNSET_LOCAL = new Object();

	public boolean unSetVariable(String name) {
		if( !functionStack.isEmpty()) {
			FunctionInvocation inv = functionStack.peek();
			if( inv.local.containsKey(name)) {
				inv.local.put(name, UNSET_LOCAL);
				return true;
			}
		}

		Map<Object, Object> map = commandStack.peek();

		@SuppressWarnings("unchecked")
		Map<String,Object> l = (Map<String, Object>) map.get(LOCAL_VARIABLES);		
		Object val = l.get(name);
		if( val != null ) {
			l.remove(name);
			return true;
		}

		val = console.variables.remove(name);
		if( val !=null) {
			return true;
		}

		if( console.removeEnvironmentVariables(name) != null) {
			return true;
		}

		return false;
	}

	public Object getVariable(String name) {
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

		if( !functionStack.isEmpty() && functionStack.peek().local.get(name) == UNSET_LOCAL ) {
			return null;
		}
		Object ret = getLocalVariable(name);
		if( ret == null ) {
			ret = console.getVariable(name);
			if( ret == null) {
				ret = getEvironmentVariable(name);
			}
		}
		return ret;
	}

	private Object getPositionalVariable(int pos) {
		if( !functionStack.isEmpty()) {
			FunctionInvocation inv = functionStack.peek();
			if(pos>0 && pos < inv.args.size()) {
				Object val = inv.args.get(pos);
				return val;
			} else {
				return null;
			}
		} else {
			int sz = console.positionalParameters.size();
			if( pos < sz) {
				return console.positionalParameters.get(pos);
			}
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	public Object getLocalVariable(String name) {
		if( !functionStack.isEmpty()) {
			FunctionInvocation inv = functionStack.peek();
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
		ret.stdout = stdout;
		ret.stdin = stdin;
		ret.stderr = stderr;
		for(FunctionInvocation inv : functionStack) {
			ret.functionStack.push(inv.copy());
		}
		if( !commandStack.isEmpty()) {
			Map<String,Object> l = (Map<String, Object>) commandStack.peek().get(LOCAL_VARIABLES);
			if( l != null ) {
				((Map<String, Object>) ret.commandStack.peek().get(LOCAL_VARIABLES)).putAll(l);
			}
		}
		return ret;
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
		FunctionDefStatement function;
		Map<String,Object> local = new TreeMap<>();

		public FunctionInvocation(Object[] args2, FunctionDefStatement function) throws IOException {
			this.function = function;
			this.args.add(function.getName());
			this.args.addAll(Arrays.asList(args2));

		}

		private FunctionInvocation(FunctionInvocation other) {
			function = other.function;
			args.addAll(other.args);
			local.putAll(other.local);
		}

		FunctionInvocation copy() {
			return new FunctionInvocation(this);
		}

	}
	Stack<FunctionInvocation> functionStack = new Stack<>();

	public void enterFunction(Object[] args, FunctionDefStatement function) throws IOException {
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

		functionStack.push(new FunctionInvocation(args,function));		
	}

	public void exitFunction(FunctionDefStatement functionDefStatement) {
		functionStack.pop();		
	}

	public boolean isInFunction() {
		return !functionStack.isEmpty();
	}

	public boolean removeFunction(String name) {
		return console.removeFunction(name);		
	}

	private final Object pauseLock = new Object();

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
			while(pause.get() && exeption.get() == null) {
				try {
					pauseLock.wait();
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
			while(exeption.get() == null && !pause.get()) {
				long left = end-System.currentTimeMillis();
				if( left <= 0 ) {
					break;
				}
				try {
					pauseLock.wait(left);
				} catch (InterruptedException e) {
					// stop requests arrive through setExecption
				}
			}
		}
	}

	public boolean isPaused() {
		return pause.get();
	}

	public RuntimeException getException() {
		return exeption.get();
	}

	public void setExecption(Exception e) {
		setExecption0(e);
		synchronized (pauseLock) {
			pauseLock.notifyAll();
		}
	}

	private void setExecption0(Exception e) {
		if (e instanceof FsshException) {
			FsshException rte = (FsshException) e;
			exeption.set(rte);
		} else {
			if (e instanceof RuntimeException) {
				exeption.set((RuntimeException) e);
			} else {
				exeption.set(new RuntimeException(e));
			}
		}
	}

	public void enterStatement(Statement stmt) throws IOException {
		statementStack.push(stmt);
		if( console!=null ) {
			console.debugContext.before(stmt.getContext(), this);
			if(console.debugContext.isBreakpoint(stmt.getLine(), this)				 
					|| console.debugContext.getCurrentState()==DebugContext.RunState.StepOver
					|| console.debugContext.getCurrentState()==DebugContext.RunState.StepInto
					) {
				console.debugContext.setCurrentState(DebugContext.RunState.AtBreakpoint);
				do {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}					
				} while(console.debugContext.getCurrentState() == DebugContext.RunState.AtBreakpoint);					
			}	


			if( console.isOptionEnabled(Option.PrintCommandTrace)) {
				if (stmt instanceof CommandStatement) {
					CommandStatement cmd = (CommandStatement) stmt;
					String ps4 = ""+console.getVariable(Console.VARIABLE_PS4);
					stdout.print(ps4);
					stdout.print(cmd.getName());
					for(Argument a : cmd.getArgs()) {
						stdout.print(" "+a.getValue(this));
					}
					stdout.println();
				}				
			}
		}

		waitWhilePaused();
		if(exeption.get() != null) {
			throw exeption.get();
		}
	}

	public void exitStatement(int ret,Statement stmt)  {
		statementStack.pop();		
		console.debugContext.after(stmt.getContext(), this);
		if(exeption.get() != null) {
			throw exeption.get();
		}
	}

	public Statement getLastStatement() {
		return statementStack.peek();
	}

	public List<Statement> getStatementStack(int max) {
		List<Statement> ret = new ArrayList<>();
		int sz = statementStack.size();
		int idx = sz-1;
		while( idx < sz && ret.size()< max) {
			ret.add(statementStack.get(idx--));
		}

		return ret;
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
		Object zero = console.positionalParameters.get(0);
		ret.put("$0", zero);

		if( functionStack.size()>0) {
			FunctionInvocation inv = functionStack.peek();
			for(int idx=0,sz=inv.args.size(); idx<sz; idx++ ) {
				Object val = inv.args.get(idx) ;
				ret.put("$"+idx, val);
			}
		} else {
			List<Object> list = console.positionalParameters;
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
		List<Object> all = functionStack.isEmpty() ? console.positionalParameters : functionStack.peek().args;
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
		if( functionStack.isEmpty()) {
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
		ret.addAll(console.positionalParameters);

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

			FsshList tmp = new FsshList();
			if( args !=null) {
				for (int idx = 0; idx < args.length; idx++) {
					String val = ""+ args[idx].getValue(this);
					tmp.add(val);
				}
			}
			sub.setPositionalParameters(true, tmp);
			sub.setDebugContext(console.getDebugContext());

			int ret = sub.executeUsingAntlr(code);

			return ret;				
		}
	}

}
