package us.bringardner.fsh.commands;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * touch: sets files' access and modification times (now, or a time given), creating files that
 * don't exist.
 */
public class Touch extends ShellCommand{

	static String name = "touch";
	static String help = "touch [-acm] [-r FILE | -t TIME | -d DATE | -A OFFSET] FILE ...\n"
			+ "\tSet each file's access and modification times to now, or to the time given; a file that doesn't\n"
			+ "\texist is created, empty, unless -c is given.\n"
			+ "\t-a          change only the access time\n"
			+ "\t-m          change only the modification time\n"
			+ "\t-c          don't create files that don't exist\n"
			+ "\t-r FILE     use FILE's times\n"
			+ "\t-t TIME     [[CC]YY]MMDDhhmm[.SS], local time\n"
			+ "\t-d DATE     YYYY-MM-DD[Thh:mm[:SS[.frac]]], local time; or UTC with a trailing Z, or\n"
			+ "\t            another zone with +hh:mm or -hh:mm\n"
			+ "\t-A OFFSET   move the existing times by [-][[hh]mm]SS";

	/** -t: [[CC]YY]MMDDhhmm[.SS] */
	private static final Pattern T_TIME = Pattern.compile("(\\d{8}|\\d{10}|\\d{12})(?:\\.(\\d\\d))?");
	/** -d: YYYY-MM-DD, then optionally a time and a zone */
	private static final Pattern D_DATE = Pattern.compile(
			"(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ](\\d{1,2}):(\\d{2})(?::(\\d{2})(?:[.,](\\d{1,9}))?)?)?\\s*(Z|z|[+-]\\d{2}:?\\d{2})?");
	/** the time part of a -d date given as a second word (2020-07-23 06:30:10Z) */
	private static final Pattern D_TIME = Pattern.compile("\\d{1,2}:\\d{2}(?::\\d{2}(?:[.,]\\d{1,9})?)?\\s*(Z|z|[+-]\\d{2}:?\\d{2})?");
	/** -A: [-][[hh]mm]SS */
	private static final Pattern A_OFFSET = Pattern.compile("(-)?(\\d{2}|\\d{4}|\\d{6})");

	/** A mistake in the arguments: reported as "touch: ..." with status 1. */
	static class BadArgument extends Exception {
		private static final long serialVersionUID = 1L;
		BadArgument(String message) {
			super(message);
		}
	}

	public Touch() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean access = false, modification = false, create = true;
		String ref = null, tValue = null, dValue = null, aValue = null;
		List<String> paths = new ArrayList<>();

		List<String> words = new ArrayList<>();
		for(int i = 0; i < args.length; i++) {
			words.add(""+args[i].getValue(ctx));
		}
		try {
			boolean options = true;
			for(int idx = 0; idx < words.size(); idx++) {
				String w = words.get(idx);
				if( !options || !w.startsWith("-") || w.length() == 1 ) {
					paths.add(w);
					continue;
				}
				if( w.equals("--")) {
					options = false;
					continue;
				}
				for(int c = 1; c < w.length(); c++) {
					char o = w.charAt(c);
					switch (o) {
					case 'a': access = true; break;
					case 'm': modification = true; break;
					case 'c': create = false; break;
					// -h: the link itself, which isn't created either
					case 'h': create = false; break;
					case 'r': case 't': case 'd': case 'A': {
						// the value: the rest of this word (-t202001010000), or the next word
						String value;
						if( c+1 < w.length()) {
							value = w.substring(c+1);
						} else if( idx+1 < words.size()) {
							value = words.get(++idx);
						} else {
							throw new BadArgument("option requires an argument -- "+o);
						}
						if( o == 'd' && D_DATE.matcher(value).matches() && !value.matches(".*[T ].*")
								&& idx+2 < words.size() && D_TIME.matcher(words.get(idx+1)).matches()) {
							// the date and the time as two words (-d 2020-07-23 06:30:10), with a file after them
							value = value+" "+words.get(++idx);
						}
						switch (o) {
						case 'r': ref = value; break;
						case 't': tValue = value; break;
						case 'd': dValue = value; break;
						default: aValue = value; break;
						}
						c = w.length();
					} break;
					default:
						throw new UnsupportedOption("Unknown argument "+o);
					}
				}
			}
			if( paths.isEmpty()) {
				throw new BadArgument("missing file operand");
			}
			int sources = (ref != null ? 1 : 0)+(tValue != null ? 1 : 0)+(dValue != null ? 1 : 0)+(aValue != null ? 1 : 0);
			if( sources > 1 ) {
				throw new BadArgument("only one of -r, -t, -d and -A can be given");
			}
			if( !access && !modification ) {
				access = modification = true;
			}

			// the times to set (null for -A, which moves each file's own times)
			long[] times = null;
			long offset = 0;
			if( ref != null ) {
				List<FileSource> found = getFiles(ctx, ref);
				if( found.size() != 1 || !found.get(0).exists()) {
					throw new BadArgument(ref+": No such file or directory");
				}
				FileSource r = found.get(0);
				times = new long[] {r.lastAccessTime(), r.lastModified()};
			} else if( tValue != null ) {
				long t = parseT(tValue);
				times = new long[] {t, t};
			} else if( dValue != null ) {
				long t = parseD(dValue);
				times = new long[] {t, t};
			} else if( aValue != null ) {
				offset = parseA(aValue);
				// -A moves existing times; a file that doesn't exist has none to move
				create = false;
			} else {
				long now = System.currentTimeMillis();
				times = new long[] {now, now};
			}

			int ret = 0;
			for(String path : paths) {
				for(FileSource file : getFiles(ctx, path)) {
					try {
						touch(file, times, offset, access, modification, create);
					} catch (IOException e) {
						ctx.stderr.println(name+": "+path+": "+e.getMessage());
						ret = 1;
					}
				}
			}
			return ret;
		} catch (BadArgument e) {
			ctx.stderr.println(name+": "+e.getMessage());
			return 1;
		}
	}

	private static void touch(FileSource file, long[] times, long offset, boolean access, boolean modification,
			boolean create) throws IOException {
		if( !file.exists()) {
			if( !create ) {
				return;
			}
			file.createNewFile();
			if( !file.exists()) {
				throw new IOException("can't create it");
			}
		}
		long a = times != null ? times[0] : file.lastAccessTime()+offset;
		long m = times != null ? times[1] : file.lastModified()+offset;
		if( access ) {
			file.setLastAccessTime(a);
		}
		if( modification ) {
			file.setLastModifiedTime(m);
		}
	}

	/** -t [[CC]YY]MMDDhhmm[.SS], local time; the current year when there is none */
	static long parseT(String value) throws BadArgument {
		Matcher m = T_TIME.matcher(value);
		if( !m.matches()) {
			throw new BadArgument("invalid date format '"+value+"' (expected [[CC]YY]MMDDhhmm[.SS])");
		}
		String d = m.group(1);
		int year;
		if( d.length() == 12 ) {
			year = Integer.parseInt(d.substring(0, 4));
			d = d.substring(4);
		} else if( d.length() == 10 ) {
			int yy = Integer.parseInt(d.substring(0, 2));
			// as POSIX says: 69-99 are 1969-1999, 00-68 are 2000-2068
			year = yy >= 69 ? 1900+yy : 2000+yy;
			d = d.substring(2);
		} else {
			year = LocalDate.now().getYear();
		}
		// (a leap second, 60, is taken as 59)
		int ss = m.group(2) == null ? 0 : Math.min(59, Integer.parseInt(m.group(2)));
		try {
			LocalDateTime t = LocalDateTime.of(year, Integer.parseInt(d.substring(0, 2)), Integer.parseInt(d.substring(2, 4)),
					Integer.parseInt(d.substring(4, 6)), Integer.parseInt(d.substring(6, 8)), ss);
			return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		} catch (DateTimeException e) {
			throw new BadArgument("invalid date '"+value+"': "+e.getMessage());
		}
	}

	/** -d YYYY-MM-DD[Thh:mm[:SS[.frac]]][Z|+hh:mm], local time unless a zone is given */
	static long parseD(String value) throws BadArgument {
		Matcher m = D_DATE.matcher(value.trim());
		if( !m.matches()) {
			throw new BadArgument("invalid date format '"+value+"' (expected YYYY-MM-DDThh:mm:SS)");
		}
		try {
			int nanos = 0;
			if( m.group(7) != null ) {
				nanos = Integer.parseInt((m.group(7)+"000000000").substring(0, 9));
			}
			LocalDateTime t = LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
					Integer.parseInt(m.group(3)), m.group(4) == null ? 0 : Integer.parseInt(m.group(4)),
					m.group(5) == null ? 0 : Integer.parseInt(m.group(5)),
					m.group(6) == null ? 0 : Integer.parseInt(m.group(6)), nanos);
			String zone = m.group(8);
			ZoneId z;
			if( zone == null ) {
				z = ZoneId.systemDefault();
			} else if( zone.equalsIgnoreCase("Z")) {
				z = ZoneOffset.UTC;
			} else {
				String hhmm = zone.replace(":", "");
				int sign = hhmm.charAt(0) == '-' ? -1 : 1;
				z = ZoneOffset.ofHoursMinutes(sign*Integer.parseInt(hhmm.substring(1, 3)), sign*Integer.parseInt(hhmm.substring(3)));
			}
			return t.atZone(z).toInstant().toEpochMilli();
		} catch (DateTimeException e) {
			throw new BadArgument("invalid date '"+value+"': "+e.getMessage());
		}
	}

	/** -A [-][[hh]mm]SS, in milliseconds */
	static long parseA(String value) throws BadArgument {
		Matcher m = A_OFFSET.matcher(value);
		if( !m.matches()) {
			throw new BadArgument("invalid time adjustment '"+value+"' (expected [-][[hh]mm]SS)");
		}
		String d = m.group(2);
		long secs = 0;
		for(int i = 0; i < d.length(); i += 2) {
			secs = secs*60+Integer.parseInt(d.substring(i, i+2));
		}
		return (m.group(1) != null ? -secs : secs)*1000L;
	}
}
