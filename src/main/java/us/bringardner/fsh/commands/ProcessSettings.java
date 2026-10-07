package us.bringardner.fsh.commands;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellContext;

/**
 * umask and ulimit: a JVM cannot change its own, so the settings are kept as the commands
 * that made them (umask 027, ulimit -S -n 200) and replayed in /bin/sh: to show them, to
 * check a new one, and before each external command (see wrap).
 */
public class ProcessSettings {

	private ProcessSettings() {
	}

	/** the settings of ctx's shell, oldest first */
	public static List<String> settings(ShellContext ctx) {
		return ctx.console.processSettings;
	}

	/** the external command with the settings applied: sh -c '...; exec "$@"' sh cmd ... */
	public static List<String> wrap(ShellContext ctx, List<String> cmd) {
		List<String> s = settings(ctx);
		if( s.isEmpty() || isWindows()) {
			return cmd;
		}
		List<String> ret = new ArrayList<>();
		ret.add("/bin/sh");
		ret.add("-c");
		ret.add(String.join("; ", s)+"; exec \"$@\"");
		ret.add("sh");
		ret.addAll(cmd);
		return ret;
	}

	/** run script in /bin/sh after the settings: {exit status, output} */
	static Object[] sh(ShellContext ctx, String script) {
		List<String> all = new ArrayList<>(settings(ctx));
		all.add(script);
		try {
			Process p = new ProcessBuilder("/bin/sh", "-c", String.join("; ", all)).start();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ByteArrayOutputStream err = new ByteArrayOutputStream();
			try (InputStream in = p.getInputStream(); InputStream e = p.getErrorStream()) {
				in.transferTo(out);
				e.transferTo(err);
			}
			return new Object[] {p.waitFor(), out.toString(), err.toString()};
		} catch (Exception e) {
			return new Object[] {1, "", e.getMessage()+"\n"};
		}
	}

	static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().startsWith("win");
	}

	/** quote a word for sh */
	static String quote(String s) {
		return "'"+s.replace("'", "'\\''")+"'";
	}

	/** run a umask or ulimit command: query (prints) or set (kept if sh accepts it) */
	static int run(ShellContext ctx, String name, String[] words, boolean sets) {
		if( isWindows()) {
			ctx.stderr.println(name+": not supported on this system");
			return 1;
		}
		StringBuilder cmd = new StringBuilder(name);
		for(String w : words) {
			cmd.append(' ').append(quote(w));
		}
		Object[] r = sh(ctx, cmd.toString());
		ctx.stdout.print(r[1]);
		String err = ((String) r[2]).replaceFirst("^/bin/sh: (line \\d+: )?", "");
		if( !err.isEmpty()) {
			ctx.stderr.print(err);
		}
		int status = (Integer) r[0];
		if( status == 0 && sets ) {
			settings(ctx).add(cmd.toString());
		}
		return status;
	}
}
