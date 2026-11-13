package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** unzip, modeled on the Info-ZIP unzip: lists and extracts zip archives. */
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
			out.append("Archive:  ").append(zipName).append('\n');
		}
		try (ZipInputStream in = new ZipInputStream(zip.getInputStream())) {
			ZipEntry e;
			while( (e = in.getNextEntry()) != null ) {
				String entry = e.getName();
				if( !wanted.isEmpty() && !matches(wanted, entry)) {
					continue;
				}
				if( list ) {
					byte [] data = e.isDirectory() ? new byte[0] : in.readAllBytes();
					out.append(String.format("%9d  %s   %s%n", data.length, new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(e.getTime())), entry));
					totalSize += data.length;
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
						in.transferTo(ctx.stdout);
					}
					continue;
				}
				FileSource target = ctx.console.createFileSource(dir+clean);
				if( e.isDirectory()) {
					target.mkdirs();
					if( !quiet ) {
						out.append("   creating: ").append(dir).append(clean).append('\n');
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
				try (OutputStream os = target.getOutputStream()) {
					in.transferTo(os);
				}
				if( e.getTime() > 0 ) {
					target.setLastModifiedTime(e.getTime());
				}
				if( !quiet ) {
					out.append(target.exists() && overwrite ? "  inflating: " : "  inflating: ").append(dir).append(clean).append('\n');
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
