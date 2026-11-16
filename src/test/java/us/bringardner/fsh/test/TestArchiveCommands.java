package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.Console;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * fsh's archive commands: tar, zip, unzip, gzip, gunzip and zcat, built on parley-files-archive.
 * They are built in on Windows only; shopt -s fshutils turns fsh's own on, so these run on every
 * system.
 */
public class TestArchiveCommands {

	static Console console;
	String lastErr;
	int lastStatus;

	@BeforeAll
	public static void beforeAll() throws IOException {
		FileSourceFactory.setDefaultFactory(FileSourceFactory.fileProxyFactory);
		console = new Console();
		console.executeScript("shopt -s fshutils");
	}

	@AfterAll
	public static void afterAll() {
		console = null;
	}

	byte [] runBytes(String command, byte [] stdin) throws IOException {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ByteArrayOutputStream bae = new ByteArrayOutputStream();
		console.setStdOut(new PrintStream(bao, true));
		console.setStdErr(new PrintStream(bae, true));
		console.setStdIn(new ByteArrayInputStream(stdin));
		lastStatus = console.executeScript(command);
		lastErr = new String(bae.toByteArray(), StandardCharsets.UTF_8).trim();
		return bao.toByteArray();
	}

	String run(String command, String stdin) throws IOException {
		return new String(runBytes(command, stdin.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	String run(String command) throws IOException {
		return run(command, "");
	}

	static String path(File f) {
		return f.getAbsolutePath().replace(File.separatorChar, '/');
	}

	static File write(File dir, String name, String text) throws IOException {
		File f = new File(dir, name);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
		return f;
	}

	static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}


	@Test
	public void testGzip(@TempDir File dir) throws IOException {
		File f = write(dir, "a.txt", "hello hello hello\n");
		String p = path(f);
		run("gzip "+p);
		assertEquals(0, lastStatus, lastErr);
		assertFalse(f.exists());
		assertTrue(new File(dir, "a.txt.gz").exists());
		assertEquals("hello hello hello\n", run("zcat "+p+".gz"));
		run("gunzip "+p+".gz");
		assertEquals("hello hello hello\n", read(f));
		assertFalse(new File(dir, "a.txt.gz").exists());
		run("gzip -k "+p);
		assertTrue(f.exists() && new File(dir, "a.txt.gz").exists());
		run("gzip "+p);
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("already exists"), lastErr);
		run("gzip -kf "+p);
		assertEquals(0, lastStatus);
		assertEquals("hello hello hello\n", run("gzip -c "+p+" | gunzip"));
		assertEquals("piped\n", run("gzip | gzip -d", "piped\n"));
		run("gunzip "+path(write(dir, "plain", "x")));
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("unknown suffix"), lastErr);
		assertEquals("hello hello hello\n", run("gzip -dc "+p+".gz"));
		run("gzip "+path(new File(dir, "nope")));
		assertEquals(1, lastStatus);
	}

	@Test
	public void testZipUnzip(@TempDir File dir) throws IOException {
		write(dir, "src/a.txt", "alpha\n");
		write(dir, "src/sub/b.txt", "beta beta beta beta\n");
		String zip = path(dir)+"/out.zip";
		String listing = run("zip -r "+zip+" "+path(dir)+"/src");
		assertEquals(0, lastStatus, lastErr);
		assertTrue(listing.contains("adding:"), listing);
		assertTrue(new File(zip).exists());
		String l = run("unzip -l "+zip);
		assertTrue(l.contains("a.txt") && l.contains("b.txt") && l.contains("4 files"), l);
		File out = new File(dir, "out");
		run("unzip -q -d "+path(out)+" "+zip);
		assertEquals(0, lastStatus, lastErr);
		String rel = path(dir).replaceFirst("^[A-Za-z]:", "").replaceFirst("^/", "");
		assertEquals("alpha\n", read(new File(out, rel+"/src/a.txt")));
		assertEquals("beta beta beta beta\n", read(new File(out, rel+"/src/sub/b.txt")));
		assertEquals("alpha\n", run("unzip -p "+zip+" '*a.txt'"));
		// not overwritten without -o
		run("unzip -q -d "+path(out)+" "+zip);
		assertEquals(1, lastStatus);
		assertTrue(lastErr.contains("exists"), lastErr);
		run("unzip -q -o -d "+path(out)+" "+zip);
		assertEquals(0, lastStatus);
		run("unzip "+path(dir)+"/nope.zip");
		assertEquals(9, lastStatus);
	}

	@Test
	public void testZipOptions(@TempDir File dir) throws IOException {
		write(dir, "x/y.txt", "yy\n");
		String zip = path(dir)+"/j.zip";
		run("zip -j -q "+zip+" "+path(dir)+"/x/y.txt");
		assertEquals(0, lastStatus, lastErr);
		assertTrue(run("unzip -l "+zip).contains("y.txt"));
		assertFalse(run("unzip -l "+zip).contains("/y.txt"));
		// adding to the archive keeps what is in it
		write(dir, "z.txt", "zz\n");
		run("zip -q "+zip+" "+path(dir)+"/z.txt");
		String l = run("unzip -l "+zip);
		assertTrue(l.contains("y.txt") && l.contains("z.txt"), l);
		run("zip");
		assertEquals(15, lastStatus);
		// stored (-0), and the entries already there are kept as they were
		write(dir, "s.txt", "stored stored stored\n");
		run("zip -0 -q "+zip+" "+path(dir)+"/s.txt");
		assertEquals(0, lastStatus, lastErr);
		assertEquals("stored stored stored\n", run("unzip -p "+zip+" '*s.txt'"));
		assertEquals("yy\n", run("unzip -p "+zip+" y.txt"));
	}

	@Test
	public void testTar(@TempDir File dir) throws IOException {
		write(dir, "pkg/a.txt", "alpha\n");
		write(dir, "pkg/sub/b.txt", "beta\n");
		String tar = path(dir)+"/pkg.tar";
		run("tar -cf "+tar+" -C "+path(dir)+" pkg");
		assertEquals(0, lastStatus, lastErr);
		assertEquals("pkg/\npkg/a.txt\npkg/sub/\npkg/sub/b.txt\n", run("tar -tf "+tar));
		assertEquals(0, new File(tar).length() % 512);
		File out = new File(dir, "out");
		out.mkdirs();
		run("tar -xf "+tar+" -C "+path(out));
		assertEquals(0, lastStatus, lastErr);
		assertEquals("alpha\n", read(new File(out, "pkg/a.txt")));
		assertEquals("beta\n", read(new File(out, "pkg/sub/b.txt")));
		assertEquals("pkg/a.txt\n", run("tar -tf "+tar+" pkg/a.txt"));
		assertEquals("alpha\n", run("tar -xOf "+tar+" pkg/a.txt"));
		File stripped = new File(dir, "stripped");
		stripped.mkdirs();
		run("tar -xf "+tar+" -C "+path(stripped)+" --strip-components=1");
		assertEquals("alpha\n", read(new File(stripped, "a.txt")));
		assertEquals("pkg/\npkg/a.txt\npkg/sub/\npkg/sub/b.txt\n", run("tar -cvf "+path(dir)+"/v.tar -C "+path(dir)+" pkg"));
	}

	@Test
	public void testTarGzipAndOldStyle(@TempDir File dir) throws IOException {
		write(dir, "pkg/a.txt", "alpha\n");
		String tgz = path(dir)+"/pkg.tgz";
		run("tar czf "+tgz+" -C "+path(dir)+" pkg");
		assertEquals(0, lastStatus, lastErr);
		byte [] head = new byte[2];
		try (java.io.InputStream in = Files.newInputStream(new File(tgz).toPath())) {
			in.read(head);
		}
		assertEquals(0x1f, head[0] & 0xff);
		assertEquals(0x8b, head[1] & 0xff);
		assertEquals("pkg/\npkg/a.txt\n", run("tar tzf "+tgz));
		assertEquals("pkg/\npkg/a.txt\n", run("tar -tf "+tgz));
		File out = new File(dir, "out");
		out.mkdirs();
		run("tar xzf "+tgz+" -C "+path(out));
		assertEquals("alpha\n", read(new File(out, "pkg/a.txt")));
		run("tar");
		assertEquals(2, lastStatus);
		run("tar -tf "+path(dir)+"/nope.tar");
		assertEquals(2, lastStatus);
	}

	@Test
	public void testTarLongNames(@TempDir File dir) throws IOException {
		String name = "a".repeat(60)+"/"+"b".repeat(60)+"/file.txt";
		write(dir, "root/"+name, "deep\n");
		String tar = path(dir)+"/long.tar";
		run("tar -cf "+tar+" -C "+path(dir)+" root");
		assertEquals(0, lastStatus, lastErr);
		assertTrue(run("tar -tf "+tar).contains("root/"+name+"\n"));
		File out = new File(dir, "out");
		out.mkdirs();
		run("tar -xf "+tar+" -C "+path(out));
		assertEquals("deep\n", read(new File(out, "root/"+name)));
		// a name that cannot be split goes in a pax extended header
		String single = "n".repeat(150);
		write(dir, "r2/"+single, "one\n");
		run("tar -cf "+path(dir)+"/l2.tar -C "+path(dir)+" r2");
		assertTrue(run("tar -tf "+path(dir)+"/l2.tar").contains("r2/"+single+"\n"));
	}

	@Test
	public void testTarLinksAreSkipped(@TempDir File dir) throws IOException {
		// a tar with a symbolic link and a hard link, written by parley-files-archive's TarWriter
		File tar = new File(dir, "links.tar");
		try (us.bringardner.parley.files.archive.tar.TarWriter w = new us.bringardner.parley.files.archive.tar.TarWriter(Files.newOutputStream(tar.toPath()))) {
			try (OutputStream o = w.putEntry(us.bringardner.parley.files.archive.tar.TarArchiveEntry.builder("a.txt").size(2).build())) {
				o.write("a\n".getBytes(StandardCharsets.UTF_8));
			}
			w.putEntry(us.bringardner.parley.files.archive.tar.TarArchiveEntry.builder("sym").symbolicLink("a.txt").build()).close();
			w.putEntry(us.bringardner.parley.files.archive.tar.TarArchiveEntry.builder("hard").hardLink("a.txt").build()).close();
		}
		File out = new File(dir, "out");
		out.mkdirs();
		run("tar -xf "+path(tar)+" -C "+path(out));
		assertEquals("a\n", read(new File(out, "a.txt")));
		assertTrue(lastErr.contains("sym: links are not supported"), lastErr);
		assertTrue(lastErr.contains("hard: links are not supported"), lastErr);
		assertFalse(new File(out, "sym").exists());
	}
}
