package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.Map;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellFunction;

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


		ShellArgument sa = parseArgs(ctx, Arguments.class);

		if(sa.options.contains(Arguments.f)) {
			// function
			if(sa.options.contains(Arguments.p) || sa.paths.size()==0) {
				// display functions
				/*
				Map<String, ShellFunction> map = ctx.getFunctions();
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
				// as bash prints them: declare -x NAME="value", in name order
				Map<String, Object> env = new java.util.TreeMap<>(ctx.getEnvironmentVariables());
				for(String name : env.keySet()) {
					Object v = env.get(name);
					ctx.stdout.println("declare -x "+name+(v == null ? "" : "=\""+(""+v).replaceAll("([\"\\\\$`])", "\\\\$1")+"\""));
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
					int eq = val.indexOf('=');
					if( eq > 0) {
						// name=value (the value is already expanded)
						String name = val.substring(0, eq);
						ctx.unSetVariable(name);
						ctx.setEnvironmentVariable(name, val.substring(eq+1));					
					} else if( val.matches("[a-zA-Z_][a-zA-Z_0-9]*")) {
						// export name: the variable's value goes to the environment
						Object v = ctx.getVariable(val);
						if( v != null && ctx.getEvironmentVariable(val) == null ) {
							ctx.unSetVariable(val);
							ctx.setEnvironmentVariable(val, ""+ShellContext.firstElement(v));
						}
					}
				}
			}

		}

		return ret;
	}

}
