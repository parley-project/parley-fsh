package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.archive.FileSourceSources;
import us.bringardner.parley.files.archive.format.SeekableSource;
import us.bringardner.parley.files.archive.zip.ZipArchiveEntry;
import us.bringardner.parley.files.archive.zip.ZipReader;

/** unzip, modeled on the Info-ZIP unzip: lists and extracts zip archives (parley-files-archive's ZipReader, with random access). */
public class Unzip extends ShellCommand{
	static String name = "unzip";
	static String help = "unzip [-lonpq] [-d DIR] ZIPFILE [FILE ...]\n"
			+ "\tExtract the files of the archive ZIPFILE (the ones that match the FILEs, if any are given).\n"
			+ "\t-l   list the files, don't extract\n"
			+ "\t-p   extract to standard output\n"
			+ "\t-d DIR   extract into DIR (it is created)\n"
			+ "\t-o   overwrite files that exist\n"
			+ "\t-n   never overwrite files (the default, as there is nobody to ask)\n"
			+ "\t-q   quiet";

	public Unzip() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "lonpq", "d", false);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.isEmpty()) {
			ctx.stderr.println("UnZip: missing zipfile");
			return 10;
		}
		String zipName = a.operands.get(0);
		FileSource zip = ctx.console.createFileSource(zipName);
		if( !zip.exists() && !zipName.contains(".")) {
			zip = ctx.console.createFileSource(zipName+".zip");
		}
		if( !zip.exists()) {
			ctx.stderr.println("unzip:  cannot find or open "+zipName+", "+zipName+".zip.");
			return 9;
		}
		List<String> wanted = a.operands.subList(1, a.operands.size());
		String dir = a.value('d') == null ? "" : (a.value('d').endsWith("/") ? a.value('d') : a.value('d')+"/");
		boolean list = a.has('l'), quiet = a.has('q'), toStdout = a.has('p'), overwrite = a.has('o');
		int ret = 0;
		StringBuilder out = new StringBuilder();
		long totalSize = 0;
		int count = 0;
		if( list ) {
			out.append("Archive:  ").append(zipName).append('\n');
			out.append("  Length      Date    Time    Name\n---------  ---------- -----   ----\n");
		} else if( !quiet && !toStdout ) {
			// (extracting, each line is written as it happens, as Info-ZIP's unzip does: a program
			// reading the output, such as the viewer's progress, sees each file as it is done)
			ctx.stdout.println("Archive:  "+zipName);
			ctx.stdout.flush();
		}
		// random access: the index is read from the end, and only the entries wanted are read
		try (SeekableSource src = FileSourceSources.open(zip); ZipReader in = ZipReader.open(src)) {
			for (ZipArchiveEntry e : in.getEntries()) {
				String entry = e.getRawName()+(e.isDirectory() ? "/" : "");
				if( !wanted.isEmpty() && !matches(wanted, entry)) {
					continue;
				}
				if( list ) {
					long size = e.isDirectory() ? 0 : e.getSize();
					out.append(String.format("%9d  %s   %s%n", size, new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(e.getLastModified())), entry));
					totalSize += size;
					count++;
					continue;
				}
				// no entry may leave the target directory
				String clean = entry.replace('\\', '/');
				if( clean.startsWith("/") || clean.matches("^[A-Za-z]:.*") || clean.equals("..") || clean.startsWith("../") || clean.contains("/../") || clean.endsWith("/..")) {
					ctx.stderr.println("warning:  skipped \"../\" path component(s) in "+entry);
					ret = 1;
					continue;
				}
				if( toStdout ) {
					if( !e.isDirectory()) {
						try (InputStream data = in.openStream(e, src)) {
							data.transferTo(ctx.stdout);
						}
					}
					continue;
				}
				FileSource target = ctx.console.createFileSource(dir+clean);
				if( e.isDirectory()) {
					target.mkdirs();
					if( !quiet ) {
						ctx.stdout.println("   creating: "+dir+clean);
						ctx.stdout.flush();
					}
					continue;
				}
				if( target.exists() && !overwrite ) {
					ctx.stderr.println("unzip: "+dir+clean+" exists (use -o to overwrite); skipped");
					ret = 1;
					continue;
				}
				FileSource parent = target.getParentFile();
				if( parent != null && !parent.exists()) {
					parent.mkdirs();
				}
				try (InputStream data = in.openStream(e, src); OutputStream os = target.getOutputStream()) {
					data.transferTo(os);
				}
				if( e.getLastModified() > 0 ) {
					target.setLastModifiedTime(e.getLastModified());
				}
				if( !quiet ) {
					ctx.stdout.println("  inflating: "+dir+clean);
					ctx.stdout.flush();
				}
			}
		}
		if( list ) {
			out.append("---------                     -------\n");
			out.append(String.format("%9d                     %d file%s%n", totalSize, count, count == 1 ? "" : "s"));
		}
		ctx.stdout.print(out.toString().replace("\r\n", "\n"));
		ctx.stdout.flush();
		return ret;
	}

	private static boolean matches(List<String> patterns, String entry) {
		for (String p : patterns) {
			StringBuilder re = new StringBuilder();
			for (char c : p.toCharArray()) {
				if( c == '*' ) {
					re.append(".*");
				} else if( c == '?' ) {
					re.append('.');
				} else {
					re.append(java.util.regex.Pattern.quote(String.valueOf(c)));
				}
			}
			if( entry.matches(re.toString())) {
				return true;
			}
		}
		return false;
	}
}
