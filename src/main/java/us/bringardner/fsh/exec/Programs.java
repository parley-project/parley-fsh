package us.bringardner.fsh.exec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Argument;
import us.bringardner.fsh.antlr.statement.CommandStatement;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * Programs: a command that is not a function or a builtin, found by its path or on $PATH.
 */
final class Programs {

	private Programs() {
	}

	/** a program file that exists but may not be run (status 126) */
	static final class NotExecutable extends IOException {
		private static final long serialVersionUID = 1L;

		NotExecutable(String name) {
			super(name+": Permission denied");
		}
	}

	/** run name with args; status 127 (and bash's message) if there is no such program */
	static int run(String name, List<String> args, ShellContext sc) throws IOException {
		FileSource exec = CommandStatement.which(name, sc);
		if( exec == null && !name.contains("/")) {
			// fsh runs a program in the current directory without ./ (bash does not)
			FileSource here = sc.getFileSource(name);
			if( here.exists() && here.isFile()) {
				exec = here;
			}
		}
		if( exec != null ) {
			if( !exec.canExecute()) {
				throw new NotExecutable(name);
			}
			if( isFshScript(exec)) {
				Argument [] all = new Argument[args.size()+1];
				all[0] = new Argument(exec.getName());
				for (int i = 0; i < args.size(); i++) {
					all[i+1] = new Argument(args.get(i));
				}
				return sc.executeSubShell(exec, all);
			}
			List<String> cmd = new ArrayList<>();
			cmd.add(exec.getAbsolutePath());
			cmd.addAll(args);
			return CommandStatement.execute(cmd, sc);
		}
		if( name.contains("/")) {
			sc.stderr.println(name+": No such file or directory");
			return 127;
		}

		List<String> cmd = new ArrayList<>();
		cmd.add(name);
		cmd.addAll(args);
		if( FileSourceFactory.isWindows()) {
			// cmd /r name ... (dir, type and the like are cmd's own)
			FileSource shell = CommandStatement.which("cmd", sc);
			cmd.add(0, "/r");
			cmd.add(0, shell != null ? shell.getAbsolutePath() : "cmd");
		}
		return CommandStatement.execute(cmd, sc);
	}

	/** #!fsh (or #!fssh, the name before): a script for this shell */
	private static boolean isFshScript(FileSource exec) throws IOException {
		byte [] data = exec.head(20);
		if( data.length < 2 || data[0] != '#' || data[1] != '!' ) {
			return false;
		}
		String first = new String(data).substring(2);
		int nl = first.indexOf('\n');
		if( nl < 0 ) {
			return false;
		}
		first = first.substring(0, nl).trim();
		return first.equals("fsh") || first.equals("fssh");
	}
}
