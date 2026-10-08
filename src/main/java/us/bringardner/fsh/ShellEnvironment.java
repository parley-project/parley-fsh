package us.bringardner.fsh;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.fsh.Console.Option;
import us.bringardner.fsh.antlr.Variable;

public class ShellEnvironment {
	public PrintStream stdout = System.out;
	public PrintStream stderr = System.err;
	public InputStream stdin = System.in;
	
	//open files inherited by the shell at invocation, as modified by redirections supplied to the exec builtin
	public Object [] open_files= new Object[0];
	
	//the current working directory as set by cd, pushd, or popd, or inherited by the shell at invocation
	public Map<String,FileSourceFactory> factories = new TreeMap<>();
	public VirtualFileSourceFactory mountFactory;	
	//the file creation mode mask as set by umask or inherited from the shell’s parent
	public int umask = 022;
	//current traps set by trap
	public Map<Integer,ShellFunction> traps = new TreeMap<>();
	//shell parameters that are set by variable assignment or with set or inherited from the shell’s parent in the environment
	public Map<String,Variable> variables = new TreeMap<>();
	public List<Object> positionalParameters = new FshList();

	//shell functions defined during execution or inherited from the shell’s parent in the environment
	public Map<String,ShellFunction> functions = new TreeMap<>();
	//options enabled at invocation (either by default or with command-line arguments) or by set	
	//options enabled by shopt (see The Shopt Builtin)
	public List<Option> options = new ArrayList<>();
	//shell aliases defined with alias (see Aliases)
	public Map<String,ShellFunction> aliases = new TreeMap<>();
	//various process IDs, including those of background jobs (see Lists of Commands), the value of $$, and the value of $PPID
	public long pid = ProcessHandle.current().pid();


	public ShellEnvironment() {
		
	}
	
}
