package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.archive.FileSourceSources;
import us.bringardner.parley.files.archive.format.SeekableSource;
import us.bringardner.parley.files.archive.zip.ZipArchiveEntry;
import us.bringardner.parley.files.archive.zip.ZipReader;
import us.bringardner.parley.files.archive.zip.ZipWriter;

/** zip, modeled on the Info-ZIP zip: puts files in a zip archive (parley-files-archive's ZipWriter; entries already there are kept as their compressed bytes). */
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

	/** what goes in the archive under a name: an entry of the archive as it was, or a file */
	private static class Item {
		String name;
		boolean dir;
		/** an entry kept from the archive (copied as its compressed bytes), or null */
		ZipArchiveEntry old;
		/** a file to add, or null */
		FileSource file;
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
		int ret = 0;
		List<String> added = new ArrayList<>();
		boolean recurse = a.has('r'), junk = a.has('j');
		boolean store = a.has('0');
		// the archive as it is: kept open while its entries are copied into the new one
		SeekableSource oldSource = zipFile.exists() ? FileSourceSources.open(zipFile) : null;
		FileSource tmp = null;
		try {
			ZipReader old = oldSource == null ? null : ZipReader.open(oldSource);
			if( old != null ) {
				for (ZipArchiveEntry e : old.getEntries()) {
					Item it = new Item();
					it.dir = e.isDirectory();
					it.name = e.getRawName()+(it.dir ? "/" : "");
					it.old = e;
					items.put(it.name, it);
				}
			}
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
			// written beside the archive, then put in its place
			FileSource parent = zipFile.getParentFile();
			tmp = parent == null ? ctx.console.createFileSource("."+zipFile.getName()+"."+System.nanoTime()+".tmp")
					: parent.getChild("."+zipFile.getName()+"."+System.nanoTime()+".tmp");
			try (ZipWriter zos = new ZipWriter(tmp.getOutputStream())) {
				for (Item it : items.values()) {
					if( it.old != null ) {
						try (InputStream raw = old.openRawStream(it.old, oldSource)) {
							zos.copyEntry(it.old, raw);
						}
						continue;
					}
					ZipArchiveEntry.Builder b = ZipArchiveEntry.builder(it.name).directory(it.dir).lastModified(it.file.lastModified());
					long size = it.dir ? 0 : it.file.contentLength();
					if( !it.dir && store ) {
						// a stored entry's size and CRC-32 go before its data: read the file once for them
						CRC32 crc = new CRC32();
						try (InputStream in = it.file.getInputStream()) {
							byte [] buf = new byte[65536];
							size = 0;
							for (int n; (n = in.read(buf)) >= 0;) {
								crc.update(buf, 0, n);
								size += n;
							}
						}
						b.method(ZipArchiveEntry.STORED).sizeAndCrc(size, crc.getValue());
					}
					try (OutputStream os = zos.putEntry(b.build())) {
						if( !it.dir ) {
							try (InputStream in = it.file.getInputStream()) {
								in.transferTo(os);
							}
						}
					}
					if( !a.has('q') && added.contains(it.name)) {
						List<ZipArchiveEntry> done = zos.getWrittenEntries();
						ZipArchiveEntry w = done.get(done.size()-1);
						String how = w.getMethod() == ZipArchiveEntry.STORED || w.getSize() == 0 ? "stored 0%"
								: "deflated "+Math.max(0, Math.round((1.0-(double) w.getCompressedSize()/w.getSize())*100))+"%";
						ctx.stdout.println("  adding: "+it.name+" ("+how+")");
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			if( tmp != null ) {
				tmp.delete();
			}
			throw e;
		} finally {
			if( oldSource != null ) {
				oldSource.close();
			}
		}
		if( zipFile.exists() ? !tmp.renameTo(zipFile, true) : !tmp.renameTo(zipFile)) {
			tmp.delete();
			ctx.stderr.println("zip error: could not write "+zipName);
			return 15;
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
				d.file = f;
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
		it.file = f;
		items.put(it.name, it);
		added.add(it.name);
	}
}
