package us.bringardner.shell.commands;

import java.io.IOException;
import java.util.Map;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.statement.AssignStatement;

public class Export extends ShellCommand{
	enum Arguments {f,n,p};

	static String name = "export";
	static String help = "print the current directory\n"
			+ "\t export [-fn] [-p] [name[=value]]\n"
			+ "\n"
			+ "Mark each name to be passed to child processes in the environment. "
			+ "	If the -f option is supplied, the names refer to shell functions; otherwise the names refer to shell variables. "
			+ "	The -n option means to no longer mark each name for export. "
			+ "If no names are supplied, or if the -p option is given, a list of names of all exported variables is displayed. "
			+ "The -p option displays output in a form that may be reused as input. If a variable name is followed by =value, "
			+ "the value of the variable is set to value.\n"
			+ "\n"
			+ "The return status is zero unless an invalid option is supplied, one of the names is not a valid shell variable name, "
			+ "or -f is supplied with a name that is not a shell function.";

	public Export() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		int ret = 0;


		ShellArgument sa = parserArgs(ctx, Arguments.class);

		if(sa.options.contains(Arguments.f)) {
			// function
			if(sa.options.contains(Arguments.p) || sa.paths.size()==0) {
				// display functions
				/*
				Map<String, FunctionDefStatement> map = ctx.getFunctions();
				for(String name : map.keySet()) {
					// linux bash does nothing
					//ctx.stdout.println("function "+name);
				}
				*/
			} else if(sa.options.contains(Arguments.n)) {
				for(String name : sa.paths) {
					ctx.removeFunction(name);
				}
			}
		} else {
			if(sa.options.contains(Arguments.p) || sa.paths.size()==0) {
				boolean print = sa.options.contains(Arguments.p);
				// display variables
				Map<String, Object> env = ctx.getEnvironmentVariables();
				for(String name : env.keySet()) {
					if( print ) {
						ctx.stdout.print(ctx.console.isInteractive?"declare -x ":"export ");
					} 
					ctx.stdout.println(""+name+"="+env.get(name));
				}
				
			} else if(sa.options.contains(Arguments.n)) {
				// unset variables
				for(String name : sa.paths) {
					Object val = ctx.getEvironmentVariable(name);
					if(val !=null ) {
						ctx.setEnvironmentVariable(name, null);
						ctx.setVariable(name, val);
					}
				}
			} else {
				// with no args all we can do is set a value for export
				for (int idx = 0; idx < args.length; idx++) {
					String val = args[idx].getValue(ctx).toString();
					if( val.indexOf('=')>0) {
						// assign statement should be in antlr args
						Argument arg =  this.args[idx];
						ArgumentContext actx = arg.getContext();
						if(actx.assignStatement()!=null) {
							AssignStatement as = new AssignStatement(actx.assignStatement());
							if((ret= as.process(ctx)) !=0) {
								return ret;
							};
							String name = as.getName();
							Object value = ctx.getVariable(name);
							ctx.unSetVariable(name);
							ctx.setEnvironmentVariable(name, value);					
						}
					}
				}
			}

		}

		return ret;
	}

}
