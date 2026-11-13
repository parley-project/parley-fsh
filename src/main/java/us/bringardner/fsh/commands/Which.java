package us.bringardner.fsh.commands;

import java.io.File;
import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** which, modeled on the GNU which: finds a program on the PATH. */
public class Which extends ShellCommand{
	static String name = "which";
	static String help = "which [-a] COMMAND...\n"
			+ "\tPrint the full path of each COMMAND, the file that would run: the first in the directories of\n"
			+ "\tthe PATH (on Windows with the extensions of PATHEXT). Exit status 1 if one isn't found.\n"
			+ "\t-a, --all   print every match, not only the first";

	public Which() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "a", "", false, "all:a");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		Object pathVar = ctx.getEnvironmentVariables().get("PATH");
		if( pathVar == null ) {
			pathVar = ctx.getEnvironmentVariables().get("Path");
		}
		String path = pathVar == null ? System.getenv("PATH") : pathVar.toString();
		if( path == null ) {
			path = "";
		}
		boolean windows = File.separatorChar == '\\';
		String [] exts = new String[] {""};
		if( windows ) {
			String pe = System.getenv("PATHEXT");
			String [] list = (pe == null ? ".COM;.EXE;.BAT;.CMD" : pe).split(";");
			exts = new String[list.length+1];
			exts[0] = "";
			System.arraycopy(list, 0, exts, 1, list.length);
		}
		int ret = 0;
		StringBuilder out = new StringBuilder();
		for (String cmd : a.operands) {
			boolean found = false;
			if( cmd.contains("/") || cmd.contains("\\")) {
				File f = new File(cmd);
				if( f.isFile() && (windows || f.canExecute())) {
					out.append(cmd).append('\n');
					found = true;
				}
			} else {
				search:
				for (String dir : path.split(File.pathSeparator)) {
					if( dir.isEmpty()) {
						continue;
					}
					for (String ext : exts) {
						File f = new File(dir, cmd+ext);
						// (on Windows a name that already has an extension needs none added; "" is tried first)
						if( f.isFile() && (windows ? !ext.isEmpty() || hasExt(cmd, exts) : f.canExecute())) {
							out.append(f.getPath().replace('\\', '/')).append('\n');
							found = true;
							if( !a.has('a')) {
								break search;
							}
							break;
						}
					}
				}
			}
			if( !found ) {
				ret = 1;
			}
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return ret;
	}

	private static boolean hasExt(String cmd, String [] exts) {
		String upper = cmd.toUpperCase();
		for (String e : exts) {
			if( !e.isEmpty() && upper.endsWith(e.toUpperCase())) {
				return true;
			}
		}
		return false;
	}
}
