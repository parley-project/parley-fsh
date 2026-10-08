package us.bringardner.fsh;

import java.util.ArrayList;
import java.util.List;

/**
 * The command line fsh is started with, read as bash reads its own:
 *
 * <pre>
 * fsh [options] [file [arguments]]
 * fsh [options] -c command [name [arguments]]
 * fsh [options] [-s] [arguments]
 * </pre>
 *
 * The options are set's (-e, -x, -o pipefail, +u ...) and -c, -s, -i, -l (--login), --norc,
 * --noprofile, --rcfile file (--init-file), --help and --version. The first word that is not an
 * option (or the word after - or --) ends them.
 */
public final class Invocation {

	/** a word fsh does not know (fsh: -z: invalid option) */
	public static final class Bad extends Exception {
		private static final long serialVersionUID = 1L;

		Bad(String message) {
			super(message);
		}
	}

	public static final String USAGE = "Usage:\tfsh [long option] [option] ...\n"
			+ "\tfsh [long option] [option] script-file ...\n"
			+ "Long options:\n"
			+ "\t--help\n"
			+ "\t--init-file\n"
			+ "\t--login\n"
			+ "\t--noediting\n"
			+ "\t--noprofile\n"
			+ "\t--norc\n"
			+ "\t--posix\n"
			+ "\t--rcfile\n"
			+ "\t--restricted\n"
			+ "\t--verbose\n"
			+ "\t--version\n"
			+ "Shell options:\n"
			+ "\t-ilrsD or -c command or -O shopt_option\t\t(invocation only)\n"
			+ "\t-abefhkmnptuvxBCEHPT or -o option";

	/** -c: the commands to run */
	public String command;
	/** the script to run (the first argument, without -c and -s) */
	public String file;
	/** -s: read commands from standard input (the arguments are the positional parameters) */
	public boolean stdin;
	/** -i */
	public boolean interactive;
	/** -l, --login */
	public boolean login;
	public boolean norc;
	public boolean noprofile;
	/** --rcfile, --init-file */
	public String rcfile;
	public boolean help;
	public boolean version;
	/** set's options, in order: {letter or name, "-" or "+"} */
	public final List<String[]> options = new ArrayList<>();
	/** what follows the options and the command or file: $0 (for -c) and the arguments */
	public final List<String> args = new ArrayList<>();

	private Invocation() {
	}

	public static Invocation parse(String ... words) throws Bad {
		Invocation ret = new Invocation();
		int i = 0;
		boolean wantCommand = false;
		for (; i < words.length; i++) {
			String w = words[i];
			if( w.equals("-") || w.equals("--")) {
				i++;
				break;
			}
			if( w.startsWith("--") ) {
				switch (w) {
				case "--login" -> ret.login = true;
				case "--norc" -> ret.norc = true;
				case "--noprofile" -> ret.noprofile = true;
				case "--help" -> ret.help = true;
				case "--version" -> ret.version = true;
				case "--rcfile", "--init-file" -> {
					if( i+1 >= words.length ) {
						throw new Bad(w+": option requires an argument");
					}
					ret.rcfile = words[++i];
				}
				// bash's that change nothing here
				case "--noediting", "--posix", "--restricted", "--verbose", "--debugger", "--dump-strings", "--pretty-print" -> {
					if( w.equals("--verbose")) {
						ret.options.add(new String[] {"v", "-"});
					}
				}
				default -> throw new Bad(w+": invalid option");
				}
				continue;
			}
			if( !(w.startsWith("-") || w.startsWith("+")) || w.length() == 1 ) {
				break;
			}
			String on = w.substring(0, 1);
			for (int k = 1; k < w.length(); k++) {
				char c = w.charAt(k);
				if( on.equals("-") && "cilsrD".indexOf(c) >= 0 ) {
					switch (c) {
					case 'c' -> wantCommand = true;
					case 'i' -> ret.interactive = true;
					case 'l' -> ret.login = true;
					case 's' -> ret.stdin = true;
					default -> {
						// -r (restricted), -D: not in fsh
					}
					}
				} else if( c == 'o' || c == 'O' ) {
					if( i+1 >= words.length ) {
						throw new Bad("-"+c+": option requires an argument");
					}
					String name = words[++i];
					if( c == 'o' ) {
						ret.options.add(new String[] {name, on});
					} else {
						ret.options.add(new String[] {"shopt "+name, on});
					}
				} else {
					ret.options.add(new String[] {String.valueOf(c), on});
				}
			}
		}
		if( wantCommand ) {
			if( i >= words.length ) {
				throw new Bad("-c: option requires an argument");
			}
			ret.command = words[i++];
		} else if( !ret.stdin && i < words.length ) {
			ret.file = words[i++];
		}
		for (; i < words.length; i++) {
			ret.args.add(words[i]);
		}
		return ret;
	}
}
