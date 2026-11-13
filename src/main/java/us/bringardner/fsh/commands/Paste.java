package us.bringardner.fsh.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** paste, modeled on the GNU paste: merges the lines of files side by side. */
public class Paste extends ShellCommand{
	static String name = "paste";
	static String help = "paste [-s] [-d LIST] [FILE ...]\n"
			+ "\tWrite the corresponding lines of the FILEs, separated by tabs (- is standard input).\n"
			+ "\t-d, --delimiters=LIST   use the characters of LIST in turn instead of tabs (\\n \\t \\\\ \\0 work)\n"
			+ "\t-s, --serial            paste the lines of one file at a time instead of in parallel";

	public Paste() {
		super(name, help);
	}

	static String delimiters(String list) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.length(); i++) {
			char c = list.charAt(i);
			if( c == '\\' && i+1 < list.length()) {
				char d = list.charAt(++i);
				sb.append(d == 'n' ? '\n' : d == 't' ? '\t' : d == '0' ? '\0' : d);
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/** reads lines from a stream; stdin can be shared by several - operands */
	private static class Source {
		final InputStream in;
		Source(InputStream in) {
			this.in = in;
		}
		String next() throws IOException {
			byte [] l = TextFiles.line(in);
			return l == null ? null : TextFiles.chomp(l);
		}
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "s", "d", false, "serial:s", "delimiters=d");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		String delims = a.value('d') == null ? "\t" : delimiters(a.value('d'));
		if( delims.isEmpty()) {
			delims = "\0";
		}
		List<String> files = a.operands.isEmpty() ? java.util.Collections.singletonList("-") : a.operands;
		List<Source> sources = new ArrayList<>();
		List<InputStream> toClose = new ArrayList<>();
		Source stdin = new Source(new BufferedInputStream(ctx.stdin));
		int ret = 0;
		try {
			for (String f : files) {
				if( f.equals("-")) {
					sources.add(stdin);
					continue;
				}
				FileSource fs = ctx.console.createFileSource(f);
				if( !fs.exists() || fs.isDirectory()) {
					ctx.stderr.println("paste: "+f+": "+(fs.exists() ? "Is a directory" : "No such file or directory"));
					return 1;
				}
				InputStream in = new BufferedInputStream(fs.getInputStream());
				toClose.add(in);
				sources.add(new Source(in));
			}
			StringBuilder out = new StringBuilder();
			if( a.has('s')) {
				for (Source s : sources) {
					String line;
					int i = 0;
					boolean any = false;
					while( (line = s.next()) != null ) {
						if( any ) {
							out.append(delims.charAt((i-1) % delims.length()));
						}
						out.append(line);
						any = true;
						i++;
					}
					out.append('\n');
				}
			} else {
				while( true ) {
					boolean any = false;
					StringBuilder row = new StringBuilder();
					for (int i = 0; i < sources.size(); i++) {
						String line = sources.get(i).next();
						if( line != null ) {
							any = true;
							row.append(line);
						}
						if( i < sources.size()-1 ) {
							row.append(delims.charAt(i % delims.length()));
						}
					}
					if( !any ) {
						break;
					}
					out.append(row).append('\n');
				}
			}
			ctx.stdout.print(out);
		} finally {
			for (InputStream in : toClose) {
				in.close();
			}
		}
		ctx.stdout.flush();
		return ret;
	}
}
