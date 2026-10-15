package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** touch's times: now for an existing file, -t and -d parsed exactly, mistakes reported. */
public class TestTouchTimes extends AbstractConsoleTest {

	@TempDir
	Path dir;

	@BeforeAll
	public static void beforeAll() throws IOException {
		setup("TouchTestFiles");
	}

	private long mtime(String name) throws IOException {
		return Files.getLastModifiedTime(dir.resolve(name)).toMillis();
	}

	private static long local(int y, int mo, int d, int h, int mi, int s) {
		return LocalDateTime.of(y, mo, d, h, mi, s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private ExecuteResult touch(String args) throws IOException {
		return executeCommand("cd "+dir+"; touch "+args, "");
	}

	@Test
	public void anExistingFileGetsTheTimeNow() throws IOException {
		Path f = Files.createFile(dir.resolve("old"));
		Files.setLastModifiedTime(f, FileTime.fromMillis(local(2020, 1, 1, 0, 0, 0)));
		long before = System.currentTimeMillis()-2000;
		ExecuteResult res = touch("old");
		assertEquals(0, res.exitCode, res.getStdErr());
		assertTrue(mtime("old") >= before, "touch with no time option sets now");
	}

	@Test
	public void tIsLocalTimeOnA24HourClock() throws IOException {
		assertEquals(0, touch("-t 202001021230 a").exitCode);
		assertEquals(local(2020, 1, 2, 12, 30, 0), mtime("a"), "hour 12 is noon");
		assertEquals(0, touch("-t 2001021730.45 b").exitCode);
		assertEquals(local(2020, 1, 2, 17, 30, 45), mtime("b"));
		assertEquals(0, touch("-t 7001021730 c").exitCode);
		assertEquals(local(1970, 1, 2, 17, 30, 0), mtime("c"), "69-99 are 19xx");
		assertEquals(0, touch("-t 01021730 d").exitCode);
		assertEquals(local(LocalDate.now().getYear(), 1, 2, 17, 30, 0), mtime("d"), "no year is this year");
	}

	@Test
	public void dTakesOneDateAndEveryFileAfterIt() throws IOException {
		assertEquals(0, touch("-d 2021-03-04 a b").exitCode);
		assertEquals(local(2021, 3, 4, 0, 0, 0), mtime("a"));
		assertEquals(local(2021, 3, 4, 0, 0, 0), mtime("b"), "the files after the date are all touched");
		assertEquals(0, touch("-d 2021-03-04T05:06:07Z c").exitCode);
		assertEquals(ZonedDateTime.of(2021, 3, 4, 5, 6, 7, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(), mtime("c"));
		assertEquals(0, touch("-d '2021-03-04 05:06:07.5' d").exitCode);
		assertEquals(local(2021, 3, 4, 5, 6, 7)+500, mtime("d"));
		assertEquals(0, touch("-d 2021-03-04 05:06:07 e").exitCode);
		assertEquals(local(2021, 3, 4, 5, 6, 7), mtime("e"), "date and time as two words");
		assertEquals(0, touch("-d 2021-03-04 -m f").exitCode, "an option after the date is an option");
		assertEquals(local(2021, 3, 4, 0, 0, 0), mtime("f"));
	}

	@Test
	public void mistakesAreReportedNotHandedToTheSystemTouch() throws IOException {
		ExecuteResult res = touch("-t 202013010000 x");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().startsWith("touch: invalid date"), res.getStdErr());
		res = touch("-d junk x");
		assertEquals(1, res.exitCode);
		assertTrue(res.getStdErr().startsWith("touch: invalid date format"), res.getStdErr());
		res = touch("-t");
		assertEquals(1, res.exitCode);
		assertEquals("touch: option requires an argument -- t", res.getStdErr().trim());
		res = touch("");
		assertEquals(1, res.exitCode);
		assertEquals("touch: missing file operand", res.getStdErr().trim());
		res = touch("-t 202001010000 -d 2020-01-01 x");
		assertEquals(1, res.exitCode);
		assertFalse(Files.exists(dir.resolve("x")));
	}

	@Test
	public void doubleDashEndsTheOptions() throws IOException {
		assertEquals(0, touch("-- -name").exitCode);
		assertTrue(Files.exists(dir.resolve("-name")));
	}
}
