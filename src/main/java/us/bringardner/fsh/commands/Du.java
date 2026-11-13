package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * du, modeled on the GNU du. The sizes are the sizes of the contents (the apparent size), rounded up to
 * the unit, not the blocks the file system uses.
 */
public class Du extends ShellCommand{
	static String name = "du";
	static String help = "du [-abcdhksx] [FILE ...]\n"
			+ "\tShow the space used by each FILE and, for a directory, by each directory under it.\n"
			+ "\tThe size is that of the contents, in 1K units, rounded up.\n"
			+ "\t-a, --all              show the files too\n"
			+ "\t-b, --bytes            sizes in bytes\n"
			+ "\t-c, --total            add a total\n"
			+ "\t-d, --max-depth=N      show only the directories N levels down (0 is -s)\n"
			+ "\t-h, --human-readable   sizes like 1.5K 23M\n"
			+ "\t-k                     sizes in 1K units (the default)\n"
			+ "\t-s, --summarize        only a total for each FILE\n"
			+ "\tWith no FILE it is the current directory.";

	public Du() {
		super(name, help);
	}

	private static class Opts {
		boolean all, bytes, human;
		long maxDepth = Long.MAX_VALUE;
		StringBuilder out = new StringBuilder();
		int status;
	}

	static String human(long bytes) {
		if( bytes < 1024 ) {
			return Long.toString(bytes);
		}
		String units = "KMGTPE";
		double v = bytes;
		int u = -1;
		while( v >= 1024 && u < units.length()-1 ) {
			v /= 1024;
			u++;
		}
		// (as GNU: rounded up, with one decimal below 10)
		double r = Math.ceil(v*10)/10;
		return (v < 10 ? String.format(java.util.Locale.ROOT, "%.1f", r) : Long.toString((long) Math.ceil(v)))+units.charAt(u);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "abchksx", "d", false, "all:a", "bytes:b", "total:c", "human-readable:h", "summarize:s", "max-depth=d");
		long depth = a.number('d', Long.MAX_VALUE);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		Opts o = new Opts();
		o.all = a.has('a');
		o.bytes = a.has('b');
		o.human = a.has('h');
		o.maxDepth = a.has('s') ? 0 : depth;
		List<String> paths = new ArrayList<>(a.operands);
		if( paths.isEmpty()) {
			paths.add(".");
		}
		long total = 0;
		for (String p : paths) {
			FileSource f = ctx.console.createFileSource(p);
			if( !f.exists()) {
				ctx.stderr.println("du: cannot access '"+p+"': No such file or directory");
				o.status = 1;
				continue;
			}
			total += walk(f, p, 0, o);
		}
		if( a.has('c')) {
			line(o, total, "total");
		}
		ctx.stdout.print(o.out);
		ctx.stdout.flush();
		return o.status;
	}

	private static void line(Opts o, long bytes, String label) {
		String size = o.human ? human(bytes) : Long.toString(o.bytes ? bytes : (bytes+1023)/1024);
		o.out.append(size).append('\t').append(label).append('\n');
	}

	private long walk(FileSource f, String label, int depth, Opts o) throws IOException {
		if( !f.isDirectory()) {
			long size = f.length();
			if( o.all && depth <= o.maxDepth ) {
				line(o, size, label);
			} else if( depth == 0 ) {
				line(o, size, label);
			}
			return size;
		}
		long total = 0;
		FileSource [] kids = f.listFiles();
		if( kids != null ) {
			java.util.Arrays.sort(kids, (x, y) -> x.getName().compareTo(y.getName()));
			for (FileSource k : kids) {
				total += walk(k, label.endsWith("/") ? label+k.getName() : label+"/"+k.getName(), depth+1, o);
			}
		}
		if( depth <= o.maxDepth ) {
			line(o, total, label);
		}
		return total;
	}
}
