package us.bringardner.fsh;

import java.io.File;

/**
 * What native support this shell has, and what is limited without it (README: Native support).
 *
 * There are three separate pieces, and each can be missing: the keyboard, terminal and process
 * calls (the JNI library, or JNA when it is not there); starting programs with posix_spawn (one
 * of them again); and fshexec, the helper that runs between fork and exec. {@link #notice()} is
 * the one line an interactive shell prints when something is limited; {@link #report()} is
 * {@code fsh --native-status}.
 */
public final class NativeStatus {

	private NativeStatus() {
	}

	/** set FSH_NATIVE_NOTICE=0 (or -Dparley.fsh.native.notice=false) for no notice */
	private static boolean wanted() {
		String env = System.getenv("FSH_NATIVE_NOTICE");
		return !"0".equals(env) && !"false".equals(env) && !"false".equals(System.getProperty("parley.fsh.native.notice"));
	}

	private static boolean noticed;

	/**
	 * The one line to print at the start of an interactive shell, or null: nothing is limited, it
	 * was said already, or it was asked for not to be.
	 */
	public static synchronized String notice() {
		if( noticed || !wanted() ) {
			return null;
		}
		noticed = true;
		return notice(NativeKeyboard.isAvailible(), File.separatorChar == '/', NativeKeyboard.helper() != null);
	}

	/**
	 * @param provider the keyboard, terminal and process calls are done (by the JNI library or JNA)
	 * @param posix a Unix: it has fshexec to have
	 * @param helper fshexec was found
	 */
	static String notice(boolean provider, boolean posix, boolean helper) {
		if( !provider ) {
			return "fsh: no native support (fsh --native-status says why): line editing and job control are limited";
		}
		if( posix && !helper ) {
			return "fsh: fshexec not found (fsh --native-status says more): programs share the shell's process group"
					+ " and a few bash details are approximate";
		}
		return null;
	}

	private static String mark(boolean ok) {
		return ok ? "yes" : "NO";
	}

	/** the JNI library's file in java.library.path, or null */
	private static String jniLibrary() {
		String name = System.mapLibraryName("nativekeyboard");
		for(String dir : System.getProperty("java.library.path", "").split(File.pathSeparator)) {
			File f = new File(dir.isEmpty() ? "." : dir, name);
			if( f.isFile() ) {
				return f.getAbsolutePath();
			}
		}
		return null;
	}

	/** what {@code fsh --native-status} prints */
	public static String report() {
		boolean posix = File.separatorChar == '/';
		boolean available = NativeKeyboard.isAvailible();
		boolean jna = NativeKeyboard.usesJna();
		boolean jni = NativeKeyboard.isJniAvailible();
		boolean spawn = NativeKeyboard.canSpawn();
		String helper = NativeKeyboard.helper();
		StringBuilder b = new StringBuilder();
		b.append("fsh native support\n");
		b.append("  platform         : ").append(System.getProperty("os.name")).append(' ')
				.append(System.getProperty("os.arch")).append(NativeKeyboard.NATIVE_IMAGE ? " (native image)" : "").append('\n');
		b.append("  mode             : parley.fsh.native=").append(System.getProperty("parley.fsh.native", "auto")).append('\n');

		// the keyboard, the terminal and the process calls
		b.append("  keyboard, tty    : ");
		if( jna ) {
			String version = "";
			try {
				version = " "+com.sun.jna.Native.VERSION;
			} catch (Throwable e) {
			}
			b.append("JNA").append(version).append('\n');
		} else if( jni ) {
			String lib = jniLibrary();
			b.append("JNI library").append(lib == null ? "" : " ("+lib+")").append('\n');
		} else {
			b.append("none\n");
		}
		if( !available ) {
			if( NativeKeyboard.jniProblem() != null ) {
				b.append("                     JNI library: ").append(NativeKeyboard.jniProblem()).append('\n');
				b.append("                     (java.library.path: ").append(System.getProperty("java.library.path", "")).append(")\n");
			}
			if( NativeKeyboard.jnaProblem() != null ) {
				b.append("                     JNA: ").append(NativeKeyboard.jnaProblem()).append('\n');
			} else if( NativeKeyboard.NATIVE_IMAGE ) {
				b.append("                     JNA: not used in a native image unless -Dparley.fsh.native=jna\n");
			}
		}

		// starting programs
		b.append("  starting programs: ");
		if( !posix ) {
			b.append("Java's ProcessBuilder (Windows)\n");
		} else if( spawn ) {
			b.append(jna ? "posix_spawn through JNA\n" : "posix_spawn in the JNI library\n");
		} else {
			b.append("Java's ProcessBuilder\n");
			if( NativeKeyboard.spawnProblem() != null ) {
				b.append("                     ").append(NativeKeyboard.spawnProblem()).append('\n');
			}
		}

		// the helper
		b.append("  fshexec          : ");
		if( !posix ) {
			b.append("not used on Windows\n");
		} else if( helper != null ) {
			b.append(helper).append('\n');
		} else {
			b.append("not found\n");
			b.append("                     (looked in java.library.path: ").append(System.getProperty("java.library.path", ""))
					.append(", and parley.fsh.exec)\n");
		}

		// what that means
		boolean limited = !available || (posix && helper == null);
		if( !limited ) {
			b.append("  limited          : nothing\n");
			return b.toString();
		}
		b.append("  limited          :\n");
		if( !available ) {
			b.append("    - no raw keyboard: each typed line is echoed twice, arrow keys act only after Enter\n");
			b.append("    - Ctrl-C may not interrupt a builtin; no Ctrl-Z; no terminal width\n");
			b.append("    - programs are started by Java, in the shell's process group\n");
		}
		if( posix && helper == null ) {
			if( available ) {
				b.append("    - programs share the shell's process group (no terminal handoff to a job)\n");
			}
			b.append("    - no exec -a for programs, no shared stdin position with a program\n");
			b.append("    - no stand-in process for subshells ($BASHPID, signals to a subshell)\n");
			b.append("    - no user and group name completion, no enable -f, signals ignored at start are not inherited\n");
		}
		return b.toString();
	}
}
