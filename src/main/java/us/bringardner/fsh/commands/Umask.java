package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/**
 * umask [-p] [-S] [mode]: the file creation mask (octal, or symbolic as u=rwx,g=rx,o=), worked
 * out as bash's umask does. It applies to the programs fsh runs (see ProcessSettings).
 */
public class Umask extends ShellCommand{
	static String name = "umask";
	static String help = "umask [-p] [-S] [mode]\n"
			+ "	Print the file creation mask (-S: as u=rwx,g=rx,o=rx), or set it to mode (octal or\n"
			+ "	symbolic). Programs started afterwards create files with it."
			;

	public Umask() {
		super(name, help);
	}

	/** the mask now: the last one set, or the one the shell started with */
	static int current(ShellContext ctx) {
		if( ctx.console.umask == null ) {
			Object[] r = ProcessSettings.sh(ctx, "umask");
			try {
				ctx.console.umask = Integer.parseInt(((String) r[1]).trim(), 8);
			} catch (NumberFormatException e) {
				ctx.console.umask = 022;
			}
		}
		return ctx.console.umask;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean symbolic = false, p = false;
		int idx = 0;
		for(; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( a.equals("--")) {
				idx++;
				break;
			}
			if( !a.startsWith("-") || a.length() == 1 ) {
				break;
			}
			for(char c : a.substring(1).toCharArray()) {
				if( c == 'S' ) {
					symbolic = true;
				} else if( c == 'p' ) {
					p = true;
				} else {
					ctx.error("umask: -"+c+": invalid option");
					ctx.stderr.println("umask: usage: umask [-p] [-S] [mode]");
					return 2;
				}
			}
		}
		if( idx >= args.length ) {
			int mask = current(ctx);
			if( p ) {
				ctx.stdout.print("umask"+(symbolic ? " -S" : "")+" ");
			}
			ctx.stdout.println(symbolic ? symbolic(mask) : String.format("%04o", mask));
			return 0;
		}
		String mode = ""+args[idx].getValue(ctx);
		int mask;
		if( !mode.isEmpty() && Character.isDigit(mode.charAt(0))) {
			if( !mode.matches("[0-7]+") || Integer.parseInt(mode, 8) > 0777 ) {
				ctx.error("umask: "+mode+": octal number out of range");
				return 1;
			}
			mask = Integer.parseInt(mode, 8);
		} else {
			int bits = parse(ctx, mode, ~current(ctx) & 0777);
			if( bits < 0 ) {
				return 1;
			}
			mask = ~bits & 0777;
		}
		ctx.console.umask = mask;
		// what programs get (the last umask only)
		List<String> settings = ProcessSettings.settings(ctx);
		settings.removeIf(s -> s.startsWith("umask "));
		settings.add(String.format("umask %04o", mask));
		if( symbolic ) {
			ctx.stdout.println(symbolic(mask));
		}
		return 0;
	}

	/** u=rwx,g=rx,o=rx: what the mask lets files have */
	static String symbolic(int mask) {
		StringBuilder ret = new StringBuilder();
		String [] who = {"u", "g", "o"};
		for (int i = 0; i < 3; i++) {
			int shift = 6-3*i;
			ret.append(i > 0 ? "," : "").append(who[i]).append('=');
			if( (mask >> shift & 4) == 0 ) {
				ret.append('r');
			}
			if( (mask >> shift & 2) == 0 ) {
				ret.append('w');
			}
			if( (mask >> shift & 1) == 0 ) {
				ret.append('x');
			}
		}
		return ret.toString();
	}

	private static final int RUGO = 0444, WUGO = 0222, XUGO = 0111;

	/** bash's parse_symbolic_mode: the permission bits after mode, from initial; -1 if it is wrong */
	static int parse(ShellContext ctx, String mode, int initial) {
		int bits = initial;
		int s = 0;
		int n = mode.length();
		while( true ) {
			int who = 0;
			while( s < n && "agou".indexOf(mode.charAt(s)) >= 0 ) {
				switch (mode.charAt(s++)) {
				case 'u': who |= 0700; break;
				case 'g': who |= 0070; break;
				case 'o': who |= 0007; break;
				default: who |= 0777;
				}
			}
			if( who == 0 ) {
				who = 0777;
			}
			while( true ) {
				char op = s < n ? mode.charAt(s++) : '\0';
				if( op != '+' && op != '-' && op != '=' ) {
					ctx.error("umask: `"+op+"': invalid symbolic mode operator");
					return -1;
				}
				int perm = 0;
				while( s < n && "rwxXstugo".indexOf(mode.charAt(s)) >= 0 ) {
					switch (mode.charAt(s)) {
					case 'u': perm = copy(initial, 6); break;
					case 'g': perm = copy(initial, 3); break;
					case 'o': perm = copy(initial, 0); break;
					case 'r': perm |= RUGO; break;
					case 'w': perm |= WUGO; break;
					case 'X':
						if( (initial & XUGO) != 0 ) {
							perm |= XUGO;
						}
						break;
					case 'x': perm |= XUGO; break;
					default:
						// s and t: not in a mask
						break;
					}
					s++;
				}
				perm &= who;
				switch (op) {
				case '+': bits |= perm; break;
				case '-': bits &= ~perm; break;
				default:
					bits &= ~who;
					bits |= perm;
				}
				if( s < n && "+-=".indexOf(mode.charAt(s)) >= 0 ) {
					continue;
				}
				break;
			}
			if( s >= n ) {
				return bits;
			}
			if( mode.charAt(s) == ',' ) {
				s++;
				continue;
			}
			ctx.error("umask: `"+mode.charAt(s)+"': invalid symbolic mode character");
			return -1;
		}
	}

	/** the r w x of one class (at shift) for all three */
	private static int copy(int bits, int shift) {
		int b = bits >> shift & 7;
		return ((b & 4) != 0 ? RUGO : 0) | ((b & 2) != 0 ? WUGO : 0) | ((b & 1) != 0 ? XUGO : 0);
	}
}
