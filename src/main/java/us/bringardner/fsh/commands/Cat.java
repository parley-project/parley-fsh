package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * cat, modeled on the GNU cat: copies files (or standard input) to standard output, optionally
 * numbering lines, squeezing blank lines and showing the characters that don't print.
 */
public class Cat extends ShellCommand{
	static String name = "cat";
	static String help = "cat [-AbeEnstTuv] [FILE ...]\n"
			+ "\tConcatenate FILEs to standard output. With no FILE, or when FILE is -, read standard input.\n"
			+ "\t-A, --show-all            same as -vET\n"
			+ "\t-b, --number-nonblank     number the lines that aren't empty (overrides -n)\n"
			+ "\t-e                        same as -vE\n"
			+ "\t-E, --show-ends           show $ at the end of each line\n"
			+ "\t-n, --number              number all the lines\n"
			+ "\t-s, --squeeze-blank       replace repeated empty lines with one\n"
			+ "\t-t                        same as -vT\n"
			+ "\t-T, --show-tabs           show tabs as ^I\n"
			+ "\t-u                        ignored\n"
			+ "\t-v, --show-nonprinting    show control characters as ^X and others as M-X (but not newline or tab)\n"
			+ "\tLine numbers continue from one FILE to the next.";

	private static final String USAGE = "usage: cat [-AbeEnstTuv] [file ...]";

	/** the state of the output, kept from one file to the next */
	private static class Format {
		boolean number, nonBlank, squeeze, ends, tabs, nonprinting;
		long line;
		boolean atStart = true;
		boolean lastBlank;

		boolean plain() {
			return !number && !nonBlank && !squeeze && !ends && !tabs && !nonprinting;
		}
	}

	public Cat() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Format fmt = new Format();
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
				case "--show-all": fmt.nonprinting = fmt.ends = fmt.tabs = true; break;
				case "--number-nonblank": fmt.number = fmt.nonBlank = true; break;
				case "--show-ends": fmt.ends = true; break;
				case "--number": fmt.number = true; break;
				case "--squeeze-blank": fmt.squeeze = true; break;
				case "--show-tabs": fmt.tabs = true; break;
				case "--show-nonprinting": fmt.nonprinting = true; break;
				default:
					ctx.stderr.println("cat: unrecognized option '"+arg+"'");
					ctx.stderr.println(USAGE);
					return 1;
				}
			} else {
				for (char c : arg.substring(1).toCharArray()) {
					switch (c) {
					case 'A': fmt.nonprinting = fmt.ends = fmt.tabs = true; break;
					case 'b': fmt.number = fmt.nonBlank = true; break;
					case 'e': fmt.nonprinting = fmt.ends = true; break;
					case 'E': fmt.ends = true; break;
					case 'n': fmt.number = true; break;
					case 's': fmt.squeeze = true; break;
					case 't': fmt.nonprinting = fmt.tabs = true; break;
					case 'T': fmt.tabs = true; break;
					case 'u': break;
					case 'v': fmt.nonprinting = true; break;
					default:
						ctx.stderr.println("cat: invalid option -- '"+c+"'");
						ctx.stderr.println(USAGE);
						return 1;
					}
				}
			}
		}

		int ret = 0;
		if( files.isEmpty()) {
			copy(ctx, ctx.stdin, fmt);
		}
		for (String path : files) {
			if( path.equals("-")) {
				copy(ctx, ctx.stdin, fmt);
				continue;
			}
			for (FileSource file : getFiles(ctx, path)) {
				if( !file.exists()) {
					ctx.stderr.println(name+": "+path+": No such file or directory");
					ret = 1;
					continue;
				}
				if( file.isDirectory()) {
					ctx.stderr.println(name+": "+path+": Is a directory");
					ret = 1;
					continue;
				}
				try (InputStream in = file.getInputStream()) {
					copy(ctx, in, fmt);
				} catch (IOException e) {
					ctx.stderr.println(name+": "+path+": "+e.getMessage());
					ret = 1;
				}
			}
		}
		ctx.stdout.flush();
		return ret;
	}

	private void copy(ShellContext ctx, InputStream stream, Format fmt) throws IOException {
		if( fmt.plain()) {
			byte [] buf = new byte[8192];
			int n;
			while( (n = stream.read(buf)) >= 0 ) {
				ctx.stdout.write(buf, 0, n);
			}
			ctx.stdout.flush();
			return;
		}

		InputStream in = new BufferedInputStream(stream);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int c;
		while( (c = in.read()) >= 0 ) {
			if( fmt.atStart ) {
				boolean blank = c == '\n';
				if( blank && fmt.squeeze && fmt.lastBlank ) {
					continue;
				}
				fmt.lastBlank = blank;
				if( fmt.nonBlank ? !blank : fmt.number ) {
					out.write(String.format("%6d\t", ++fmt.line).getBytes());
				}
				fmt.atStart = false;
			}
			if( c == '\n' ) {
				if( fmt.ends ) {
					out.write('$');
				}
				out.write('\n');
				fmt.atStart = true;
			} else if( c == '\t' ) {
				if( fmt.tabs ) {
					out.write('^');
					out.write('I');
				} else {
					out.write('\t');
				}
			} else if( fmt.nonprinting ) {
				int b = c;
				if( b >= 128 ) {
					out.write('M');
					out.write('-');
					b -= 128;
				}
				if( b < 32 ) {
					out.write('^');
					out.write(b+64);
				} else if( b == 127 ) {
					out.write('^');
					out.write('?');
				} else {
					out.write(b);
				}
			} else {
				out.write(c);
			}
			if( out.size() >= 8192 ) {
				out.writeTo(ctx.stdout);
				out.reset();
			}
		}
		out.writeTo(ctx.stdout);
		ctx.stdout.flush();
	}
}
