package us.bringardner.fsh.commands;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.tree.LocalFiles;

/** df, modeled on the GNU df: free space on the local file systems. */
public class Df extends ShellCommand{
	static String name = "df";
	static String help = "df [-hkT] [FILE ...]\n"
			+ "\tShow the space on the file system of each FILE (every local file system with no FILE).\n"
			+ "\t-h, --human-readable   sizes like 1.5G\n"
			+ "\t-k                     sizes in 1K blocks (the default)\n"
			+ "\t-T, --print-type       add the file system type";

	public Df() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "hkT", "", false, "human-readable:h", "print-type:T");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		List<Path> paths = new ArrayList<>();
		int ret = 0;
		if( a.operands.isEmpty()) {
			for (Path root : FileSystems.getDefault().getRootDirectories()) {
				paths.add(root);
			}
		}
		for (String p : a.operands) {
			FileSource f = ctx.console.createFileSource(p);
			if( !f.exists()) {
				ctx.stderr.println("df: "+p+": No such file or directory");
				ret = 1;
			} else if( !LocalFiles.isLocal(f)) {
				ctx.stderr.println("df: "+p+": not on a local file system");
				ret = 1;
			} else {
				paths.add(LocalFiles.of(f).toPath());
			}
		}
		boolean human = a.has('h'), type = a.has('T');
		StringBuilder out = new StringBuilder();
		out.append(String.format("%-20s", "Filesystem")).append(type ? String.format("%-8s", "Type") : "")
			.append(String.format("%12s%12s%12s %5s ", human ? "Size" : "1K-blocks", "Used", "Avail", "Use%")).append("Mounted on\n");
		for (Path p : paths) {
			try {
				FileStore s = java.nio.file.Files.getFileStore(p);
				long total = s.getTotalSpace(), free = s.getUsableSpace(), used = total-s.getUnallocatedSpace();
				String pct = total == 0 ? "-" : (long) Math.ceil(used*100.0/(used+free))+"%";
				String name = s.name().isEmpty() ? p.toString() : s.name();
				out.append(String.format("%-20s", name)).append(type ? String.format("%-8s", s.type()) : "")
					.append(String.format("%12s%12s%12s %5s ", size(total, human), size(used, human), size(free, human), pct))
					.append(p.toString().replace('\\', '/')).append('\n');
			} catch (IOException e) {
				ctx.stderr.println("df: "+p+": "+e.getMessage());
				ret = 1;
			}
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return ret;
	}

	private static String size(long bytes, boolean human) {
		return human ? Du.human(bytes) : Long.toString(bytes/1024);
	}
}
