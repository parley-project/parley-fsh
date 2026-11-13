package us.bringardner.fsh.commands;

import java.io.IOException;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** cmp, modeled on the GNU cmp: compares two files byte by byte. Exit status 0 same, 1 different, 2 trouble. */
public class Cmp extends ShellCommand{
	static String name = "cmp";
	static String help = "cmp [-ls] [-n LIMIT] FILE1 FILE2\n"
			+ "\tCompare two files byte by byte (- is standard input) and say where the first difference is.\n"
			+ "\t-l, --verbose     print the byte number and the two values (in octal) of every difference\n"
			+ "\t-s, --quiet       print nothing, only set the exit status\n"
			+ "\t-n, --bytes=N     compare at most N bytes";

	public Cmp() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "ls", "n", false, "verbose:l", "quiet:s", "silent:s", "bytes=n");
		long limit = a.number('n', Long.MAX_VALUE);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.size() != 2 ) {
			ctx.stderr.println("cmp: "+(a.operands.size() < 2 ? "missing operand after '"+(a.operands.isEmpty() ? "cmp" : a.operands.get(0))+"'" : "extra operand '"+a.operands.get(2)+"'"));
			return 2;
		}
		String f1 = a.operands.get(0), f2 = a.operands.get(1);
		byte [] b1 = TextFiles.readAll(ctx, name, f1);
		byte [] b2 = b1 == null ? null : TextFiles.readAll(ctx, name, f2);
		if( b1 == null || b2 == null ) {
			return 2;
		}
		boolean verbose = a.has('l'), quiet = a.has('s');
		int n = (int) Math.min(limit, Math.max(b1.length, b2.length));
		int status = 0;
		long line = 1;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if( i >= b1.length || i >= b2.length ) {
				status = 1;
				if( !quiet ) {
					ctx.stderr.println("cmp: EOF on "+(i >= b1.length ? f1 : f2)+" after byte "+i+(verbose ? "" : ", line "+line));
				}
				break;
			}
			if( b1[i] != b2[i] ) {
				status = 1;
				if( quiet ) {
					break;
				}
				if( !verbose ) {
					sb.append(f1).append(' ').append(f2).append(" differ: byte ").append(i+1).append(", line ").append(line).append('\n');
					break;
				}
				sb.append(String.format("%d %3o %3o%n", i+1, b1[i] & 0xff, b2[i] & 0xff));
			}
			if( b1[i] == '\n' ) {
				line++;
			}
		}
		ctx.stdout.print(sb.toString().replace("\r\n", "\n"));
		ctx.stdout.flush();
		return status;
	}
}
