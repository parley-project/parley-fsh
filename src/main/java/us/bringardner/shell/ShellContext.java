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

import us.bringardner.filesource.sh.FileSourceShParser.StringContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.shell.Console.Option;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl;
import us.bringardner.shell.antlr.Statement;
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
	private AtomicReference<Exception> exeption = new AtomicReference<>();

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


	public String expandString(StringContext context) {
		if( context.SQ_STRING() != null ) {
			String tmp = context.SQ_STRING().getText();
			return tmp.substring(1, tmp.length()-1);				
		} else if( context.DQ_STRING() !=null) {

			String tmp = context.DQ_STRING().getText();
			String ret = FileSourceShPreProcessorVisitorImpl.processString(tmp.substring(1,tmp.length()-1), this);
			return ret;
		} else if( context.ESC()!=null) {
			String tmp = context.ESC().getText().substring(1);
			return tmp;
		}
		throw new RuntimeException("No valid string for "+context.getText());
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

	public Object getVariable(VariableContext ctx) {
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

		Object ret = getVariable(name);

		if( ctx.associative_index()!=null) {
			// associative arrays should be in a parameter ${s[s]} so this should not happen.
			throw new RuntimeException("Handle associative array");
		}

		if( ctx.array_index()!=null) {
			Expression expr = new Expression(ctx.array_index().expression());
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
		console.setVariable(name, value);
	}

	public boolean unSetVariable(String name) {
		if( !functionStack.isEmpty()) {
			FunctionInvocation inv = functionStack.peek();
			Object tmp = inv.local.get(name);
			if( tmp !=null) {
				inv.local.remove(name);
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

	public ShellContext subShell() {
		ShellContext ret = new ShellContext(console);
		ret.stdout = stdout;
		ret.stdin = stdin;
		ret.stderr = stderr;
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

	public boolean removeFunction(String name) {
		return console.removeFunction(name);		
	}

	public void setPause(boolean b) {
		pause.set(b);
	}

	public boolean isPaused() {
		return pause.get();
	}

	public Exception getException() {
		return exeption.get();
	}

	public void setExecption(Exception e) {
		exeption.set(e);
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

		while(pause.get()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
		if(exeption.get() != null) {
			throw new IOException(exeption.get());
		}
	}

	public void exitStatement(int ret,Statement stmt) throws IOException {
		statementStack.pop();		
		console.debugContext.after(stmt.getContext(), this);
		if(exeption.get() != null) {
			throw new IOException(exeption.get());
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


			int ret = sub.executeUsingAntlr(code);

			return ret;				
		}
	}

}
