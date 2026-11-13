package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** tee, modeled on the GNU tee: copies standard input to standard output and to files. */
public class Tee extends ShellCommand{
	static String name = "tee";
	static String help = "tee [-ai] [FILE ...]\n"
			+ "\tCopy standard input to standard output and to each FILE.\n"
			+ "\t-a, --append            append to the FILEs instead of overwriting them\n"
			+ "\t-i, --ignore-interrupts accepted and ignored";

	private static final String USAGE = "usage: tee [-ai] [file ...]";

	public Tee() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean append = false;
		List<String> files = new ArrayList<>();
		boolean optionsEnded = false;
		for (int idx = 0; idx < args.length; idx++) {
			String arg = ""+args[idx].getValue(ctx);
			if( optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
				files.add(arg);
			} else if( arg.equals("--")) {
				optionsEnded = true;
			} else if( arg.startsWith("--")) {
				switch (arg) {
				case "--append": append = true; break;
				case "--ignore-interrupts": break;
				default:
					ctx.stderr.println("tee: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 1;
				}
			} else {
				for (char ch : arg.substring(1).toCharArray()) {
					switch (ch) {
					case 'a': append = true; break;
					case 'i': case 'p': break;
					default:
						ctx.stderr.println("tee: invalid option -- '"+ch+"'");
						ctx.stderr.println(USAGE);
						return 1;
					}
				}
			}
		}

		int ret = 0;
		List<OutputStream> outs = new ArrayList<>();
		for (String path : files) {
			try {
				outs.add(ctx.console.createFileSource(path).getOutputStream(append));
			} catch (IOException e) {
				ctx.stderr.println("tee: "+path+": "+e.getMessage());
				ret = 1;
			}
		}
		try {
			byte [] buf = new byte[8192];
			int got;
			while( (got = ctx.stdin.read(buf)) >= 0 ) {
				ctx.stdout.write(buf, 0, got);
				ctx.stdout.flush();
				for (OutputStream os : outs) {
					os.write(buf, 0, got);
				}
			}
		} finally {
			for (OutputStream os : outs) {
				try {
					os.close();
				} catch (IOException e) {
					ret = 1;
				}
			}
		}
		ctx.stdout.flush();
		return ret;
	}
}
