package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.Console;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * stat of a link is GNU stat's: the link itself (its own size, type, permissions and times, and
 * "'name' -> 'text'" for %N), also when it points at a directory or at nothing; with -L the file it leads
 * to. The expected values are what GNU coreutils 9.4 printed, and for a link's own permissions and times what
 * java.nio says without following it.
 */
public class TestStatLinks extends AbstractConsoleTest {

	private static final long FILE_TIME = 946_702_800L;   // 2000-01-01
	private static final long LINK_TIME = 915_166_800L;   // 1999-01-01
	private static final String FORMAT = "'%s|%F|%a|%A|%Y|%N'";

	@TempDir
	static Path scratch;

	private static File dir;

	private static String octal(Set<PosixFilePermission> ps) {
		int perms = 0;
		for (PosixFilePermission pp : ps) {
			perms |= 0400 >> pp.ordinal();
		}
		return Integer.toOctalString(perms);
	}

	private static String rwx(Set<PosixFilePermission> ps, char type) {
		StringBuilder sb = new StringBuilder().append(type);
		String rwx = "rwxrwxrwx";
		for (int i = 0; i < 9; i++) {
			sb.append(ps.contains(PosixFilePermission.values()[i]) ? rwx.charAt(i) : '-');
		}
		return sb.toString();
	}

	@BeforeAll
	static void setUp() throws IOException {
		FileSourceFactory.setDefaultFactory(FileSourceFactory.fileProxyFactory);
		dir = scratch.toFile().getCanonicalFile();
		Path f = dir.toPath().resolve("f");
		Files.write(f, "hello\n".getBytes());
		Files.setLastModifiedTime(f, FileTime.fromMillis(FILE_TIME*1000));
		Files.createDirectory(dir.toPath().resolve("d"));
		Path lf = Files.createSymbolicLink(dir.toPath().resolve("lf"), Path.of("f"));
		Files.createSymbolicLink(dir.toPath().resolve("lf2"), Path.of("lf"));
		Files.createSymbolicLink(dir.toPath().resolve("ld"), Path.of("d"));
		Files.createSymbolicLink(dir.toPath().resolve("dang"), Path.of("nothing"));
		try {
			FileTime t = FileTime.fromMillis(LINK_TIME*1000);
			Files.getFileAttributeView(lf, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setTimes(t, t, null);
		} catch (IOException e) {
			// a file system that can't set a link's own times (macOS on some volumes)
			Assumptions.abort("can't set the times of a link here: "+e.getMessage());
		}
		System.setProperty("user.home", dir.getAbsolutePath());
		console = new Console();
	}

	private static ExecuteResult stat(String options, String name) throws IOException {
		return executeCommand("cd '"+dir.getPath()+"'\nstat "+options+" -c "+FORMAT+" "+name, "");
	}

	private static Set<PosixFilePermission> ownPermissions(String name) throws IOException {
		return Files.getPosixFilePermissions(dir.toPath().resolve(name), LinkOption.NOFOLLOW_LINKS);
	}

	@Test
	void aLinkToAFileIsShownAsItself() throws IOException {
		Set<PosixFilePermission> own = ownPermissions("lf");
		ExecuteResult r = stat("", "lf");
		assertEquals("", r.getStdErr());
		assertEquals("1|symbolic link|"+octal(own)+"|"+rwx(own, 'l')+"|"+LINK_TIME+"|'lf' -> 'f'\n", r.getStdOut());
	}

	@Test
	void aLinkToALinkShowsItsOwnText() throws IOException {
		ExecuteResult r = stat("", "lf2");
		assertEquals("2", r.getStdOut().split("\\|")[0].substring(0), "size is the length of 'lf'");
		assertEquals(true, r.getStdOut().endsWith("|'lf2' -> 'lf'\n"), r.getStdOut());
	}

	@Test
	void aLinkToADirectoryIsAlsoALink() throws IOException {
		ExecuteResult r = stat("", "ld");
		assertEquals("", r.getStdErr());
		assertEquals(true, r.getStdOut().startsWith("1|symbolic link|"), r.getStdOut());
		assertEquals(true, r.getStdOut().endsWith("|'ld' -> 'd'\n"), r.getStdOut());
	}

	@Test
	void aLinkToNothingIsShown() throws IOException {
		ExecuteResult r = stat("", "dang");
		assertEquals("", r.getStdErr());
		assertEquals(0, r.exitCode);
		assertEquals(true, r.getStdOut().startsWith("7|symbolic link|"), r.getStdOut());
		assertEquals(true, r.getStdOut().endsWith("|'dang' -> 'nothing'\n"), r.getStdOut());
	}

	@Test
	void aFileIsShownAsAFile() throws IOException {
		Set<PosixFilePermission> own = ownPermissions("f");
		ExecuteResult r = stat("", "f");
		assertEquals("6|regular file|"+octal(own)+"|"+rwx(own, '-')+"|"+FILE_TIME+"|'f'\n", r.getStdOut());
	}

	@Test
	void dereferenceShowsTheFileALinkLeadsTo() throws IOException {
		Set<PosixFilePermission> target = ownPermissions("f");
		for(String name : new String[] {"lf", "lf2"}) {
			ExecuteResult r = stat("-L", name);
			assertEquals("", r.getStdErr());
			assertEquals("6|regular file|"+octal(target)+"|"+rwx(target, '-')+"|"+FILE_TIME+"|'"+name+"'\n", r.getStdOut(), name);
		}
		assertEquals(true, stat("-L", "ld").getStdOut().contains("|directory|"));
	}

	@Test
	void dereferenceOfALinkToNothingFails() throws IOException {
		ExecuteResult r = stat("-L", "dang");
		assertEquals(1, r.exitCode);
		assertEquals("stat: cannot statx 'dang': No such file or directory\n", r.getStdErr());
	}
}
