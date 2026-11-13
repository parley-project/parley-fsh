package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** zip, modeled on the Info-ZIP zip: puts files in a zip archive. */
public class Zip extends ShellCommand{
	static String name = "zip";
	static String help = "zip [-rjq] [-0..9] ZIPFILE FILE...\n"
			+ "\tAdd the FILEs to the archive ZIPFILE (.zip is added to the name if it has no extension),\n"
			+ "\tcreating it if it doesn't exist. A file already in the archive is replaced.\n"
			+ "\t-r   go into the directories\n"
			+ "\t-j   store just the file name, not the path\n"
			+ "\t-0   store without compressing\n"
			+ "\t-q   quiet: don't list what is added";

	public Zip() {
		super(name, help);
	}

	private static class Item {
		String name;
		byte [] data;
		boolean dir;
		long time;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "rjq0123456789", "", false, "recurse-paths:r", "junk-paths:j", "quiet:q");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.size() < 2 ) {
			ctx.stderr.println("zip error: Nothing to do! (usage: zip [-rjq] zipfile file ...)");
			return 15;
		}
		String zipName = a.operands.get(0);
		if( !zipName.contains(".") || zipName.endsWith("/")) {
			zipName += ".zip";
		}
		FileSource zipFile = ctx.console.createFileSource(zipName);
		Map<String, Item> items = new LinkedHashMap<>();
		if( zipFile.exists()) {
			try (ZipInputStream in = new ZipInputStream(zipFile.getInputStream())) {
				ZipEntry e;
				while( (e = in.getNextEntry()) != null ) {
					Item it = new Item();
					it.name = e.getName();
					it.dir = e.isDirectory();
					it.time = e.getTime();
					it.data = it.dir ? new byte[0] : in.readAllBytes();
					items.put(it.name, it);
				}
			}
		}
		int ret = 0;
		List<String> added = new ArrayList<>();
		boolean recurse = a.has('r'), junk = a.has('j');
		for (String p : a.operands.subList(1, a.operands.size())) {
			for (FileSource f : getFiles(ctx, p)) {
				if( !f.exists()) {
					ctx.stderr.println("\tzip warning: name not matched: "+p);
					ret = 12;
					continue;
				}
				// (no drive letter and no leading slash in a name in the archive)
				String entry = p.replaceAll("^[A-Za-z]:", "").replaceAll("^(\\./|/)+", "");
				if( !f.getName().equals(p) && getFiles(ctx, p).size() > 1 ) {
					entry = (p.contains("/") ? p.substring(0, p.lastIndexOf('/')+1) : "")+f.getName();
				}
				add(f, entry, recurse, junk, items, added);
			}
		}
		boolean store = a.has('0');
		try (OutputStream os = zipFile.getOutputStream(); ZipOutputStream zos = new ZipOutputStream(os)) {
			if( store ) {
				zos.setMethod(ZipOutputStream.STORED);
			}
			for (Item it : items.values()) {
				ZipEntry e = new ZipEntry(it.name);
				if( it.time > 0 ) {
					e.setTime(it.time);
				}
				if( store || it.dir ) {
					java.util.zip.CRC32 crc = new java.util.zip.CRC32();
					crc.update(it.data);
					e.setMethod(ZipEntry.STORED);
					e.setSize(it.data.length);
					e.setCompressedSize(it.data.length);
					e.setCrc(crc.getValue());
				}
				zos.putNextEntry(e);
				zos.write(it.data);
				zos.closeEntry();
				if( !a.has('q') && added.contains(it.name)) {
					String how = store || it.dir || it.data.length == 0 ? "stored 0%" : "deflated "+Math.max(0, Math.round((1.0-(double) e.getCompressedSize()/it.data.length)*100))+"%";
					ctx.stdout.println("  adding: "+it.name+" ("+how+")");
				}
			}
		}
		ctx.stdout.flush();
		return ret;
	}

	private void add(FileSource f, String entry, boolean recurse, boolean junk, Map<String, Item> items, List<String> added) throws IOException {
		if( f.isDirectory()) {
			if( !junk ) {
				Item d = new Item();
				d.name = entry.endsWith("/") ? entry : entry+"/";
				d.dir = true;
				d.time = f.lastModified();
				d.data = new byte[0];
				items.put(d.name, d);
				added.add(d.name);
			}
			if( recurse ) {
				FileSource [] kids = f.listFiles();
				if( kids != null ) {
					java.util.Arrays.sort(kids, (x, y) -> x.getName().compareTo(y.getName()));
					for (FileSource k : kids) {
						add(k, junk ? k.getName() : (entry.endsWith("/") ? entry : entry+"/")+k.getName(), true, junk, items, added);
					}
				}
			}
			return;
		}
		Item it = new Item();
		it.name = junk ? f.getName() : entry;
		it.time = f.lastModified();
		try (InputStream in = f.getInputStream()) {
			it.data = in.readAllBytes();
		}
		items.put(it.name, it);
		added.add(it.name);
	}
}
