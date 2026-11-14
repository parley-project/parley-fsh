package us.bringardner.fsh.commands;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** date, modeled on the GNU date: shows the date and time (setting the clock is not supported). */
public class Date extends ShellCommand{
	static String name = "date";
	static String help = "date [-u] [-d STRING] [-r FILE] [-I[FMT]] [-R] [+FORMAT]\n"
			+ "\tPrint the current date and time in FORMAT (default: like Fri Nov 13 16:43:21 PST 2026).\n"
			+ "\t-d, --date=STRING   show STRING instead of now: @SECONDS, 2026-11-13, \"2026-11-13 16:43:21\", now, today,\n"
			+ "\t                    yesterday, tomorrow, \"3 days ago\", \"2 weeks\", \"+1 month\"\n"
			+ "\t-r, --reference=FILE   show the last modification time of FILE\n"
			+ "\t-u, --utc           use UTC\n"
			+ "\t/T                  as Windows's date /T: Fri 11/13/2026\n"
			+ "\t-I[date|hours|minutes|seconds]   ISO 8601 output\n"
			+ "\t-R, --rfc-email     RFC 5322 output\n"
			+ "\tFORMAT: %Y %m %d %H %M %S %y %b %B %a %A %j %e %k %l %I %p %Z %z %s %N %F %T %D %R %c %x %X %u %w %U %V %G %n %t %%";

	private static final Pattern RELATIVE = Pattern.compile("([-+]?\\d+)\\s*(second|sec|minute|min|hour|day|week|fortnight|month|year)s?(\\s+ago)?");

	public Date() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "uR", "dr", "\\+.*|-I.*|--iso-8601.*", "utc:u", "universal:u", "rfc-email:R", "date=d", "reference=r");
		// -I takes an optional attached value, so it is handled here
		String iso = null;
		for (String op : new java.util.ArrayList<>(a.operands)) {
			if( op.startsWith("-I")) {
				iso = op.length() == 2 ? "date" : op.substring(2);
				a.operands.remove(op);
			} else if( op.startsWith("--iso-8601")) {
				iso = op.contains("=") ? op.substring(op.indexOf('=')+1) : "date";
				a.operands.remove(op);
			}
		}
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		ZoneId zone = a.has('u') ? ZoneOffset.UTC : ZoneId.systemDefault();
		ZonedDateTime when = ZonedDateTime.now(zone);
		if( a.value('r') != null ) {
			FileSource f = ctx.console.createFileSource(a.value('r'));
			if( !f.exists()) {
				ctx.stderr.println("date: "+a.value('r')+": No such file or directory");
				return 1;
			}
			when = Instant.ofEpochMilli(f.lastModified()).atZone(zone);
		} else if( a.value('d') != null ) {
			when = parse(a.value('d'), when);
			if( when == null ) {
				ctx.stderr.println("date: invalid date '"+a.value('d')+"'");
				return 1;
			}
		}
		String format = null;
		for (String op : a.operands) {
			if( op.startsWith("+")) {
				format = op.substring(1);
			} else if( op.equalsIgnoreCase("/t")) {
				// Windows's date /T: the day and the date, like Fri 11/13/2026
				format = "%a %m/%d/%Y";
			} else {
				ctx.stderr.println("date: invalid date '"+op+"' (setting the date is not supported)");
				return 1;
			}
		}
		if( format == null ) {
			if( iso != null ) {
				switch (iso) {
				case "hours": format = "%FT%H%:z"; break;
				case "minutes": format = "%FT%H:%M%:z"; break;
				case "seconds": format = "%FT%T%:z"; break;
				case "ns": format = "%FT%T,%N%:z"; break;
				default: format = "%F";
				}
			} else if( a.has('R')) {
				format = "%a, %d %b %Y %T %z";
			} else {
				format = "%a %b %e %H:%M:%S %Z %Y";
			}
		}
		ctx.stdout.println(format(when, format));
		ctx.stdout.flush();
		return 0;
	}

	static ZonedDateTime parse(String s, ZonedDateTime now) {
		String t = s.trim();
		String lower = t.toLowerCase(Locale.ROOT);
		ZoneId zone = now.getZone();
		if( t.startsWith("@")) {
			try {
				return Instant.ofEpochSecond(Long.parseLong(t.substring(1))).atZone(zone);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		switch (lower) {
		case "now": return now;
		case "today": return now.truncatedTo(ChronoUnit.DAYS);
		case "yesterday": return now.minusDays(1);
		case "tomorrow": return now.plusDays(1);
		case "midnight": return now.truncatedTo(ChronoUnit.DAYS);
		}
		Matcher m = RELATIVE.matcher(lower);
		if( m.matches()) {
			long n = Long.parseLong(m.group(1).replace("+", ""));
			if( m.group(3) != null ) {
				n = -n;
			}
			switch (m.group(2)) {
			case "second": case "sec": return now.plusSeconds(n);
			case "minute": case "min": return now.plusMinutes(n);
			case "hour": return now.plusHours(n);
			case "day": return now.plusDays(n);
			case "week": return now.plusWeeks(n);
			case "fortnight": return now.plusWeeks(2*n);
			case "month": return now.plusMonths(n);
			default: return now.plusYears(n);
			}
		}
		try {
			String u = t.replace('T', ' ');
			if( u.matches("\\d{4}-\\d{2}-\\d{2}")) {
				return LocalDate.parse(u).atStartOfDay(zone);
			}
			if( u.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(:\\d{2})?")) {
				String [] p = u.split(" ");
				return LocalDateTime.of(LocalDate.parse(p[0]), LocalTime.parse(p[1])).atZone(zone);
			}
			if( u.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(:\\d{2})?Z")) {
				String [] p = u.substring(0, u.length()-1).split(" ");
				return LocalDateTime.of(LocalDate.parse(p[0]), LocalTime.parse(p[1])).atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
			}
		} catch (RuntimeException e) {
			return null;
		}
		return null;
	}

	static String format(ZonedDateTime t, String f) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < f.length(); i++) {
			char c = f.charAt(i);
			if( c != '%' || i+1 >= f.length()) {
				sb.append(c);
				continue;
			}
			char d = f.charAt(++i);
			// flags: - no padding, _ space padding, 0 zero padding, ^ upper case, : for %:z
			boolean noPad = false, upper = false;
			char padChar = 0;
			boolean colon = false;
			while( (d == '-' || d == '_' || d == '0' || d == '^' || d == ':') && i+1 < f.length()) {
				if( d == '-' ) {
					noPad = true;
				} else if( d == '_' || d == '0' ) {
					padChar = d == '_' ? ' ' : '0';
				} else if( d == '^' ) {
					upper = true;
				} else {
					colon = true;
				}
				d = f.charAt(++i);
			}
			String s;
			switch (d) {
			case 'Y': s = Integer.toString(t.getYear()); break;
			case 'C': s = pad(t.getYear()/100, 2, '0'); break;
			case 'y': s = pad(t.getYear()%100, 2, '0'); break;
			case 'm': s = pad(t.getMonthValue(), 2, '0'); break;
			case 'd': s = pad(t.getDayOfMonth(), 2, '0'); break;
			case 'e': s = pad(t.getDayOfMonth(), 2, ' '); break;
			case 'H': s = pad(t.getHour(), 2, '0'); break;
			case 'k': s = pad(t.getHour(), 2, ' '); break;
			case 'I': s = pad(t.getHour()%12 == 0 ? 12 : t.getHour()%12, 2, '0'); break;
			case 'l': s = pad(t.getHour()%12 == 0 ? 12 : t.getHour()%12, 2, ' '); break;
			case 'M': s = pad(t.getMinute(), 2, '0'); break;
			case 'S': s = pad(t.getSecond(), 2, '0'); break;
			case 'N': s = pad(t.getNano(), 9, '0'); break;
			case 'p': s = t.getHour() < 12 ? "AM" : "PM"; break;
			case 'P': s = t.getHour() < 12 ? "am" : "pm"; break;
			case 'a': s = t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH); break;
			case 'A': s = t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH); break;
			case 'b': case 'h': s = t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH); break;
			case 'B': s = t.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH); break;
			case 'j': s = pad(t.getDayOfYear(), 3, '0'); break;
			case 'u': s = Integer.toString(t.getDayOfWeek().getValue()); break;
			case 'w': s = Integer.toString(t.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : t.getDayOfWeek().getValue()); break;
			case 'U': s = pad((t.getDayOfYear()+6-(t.getDayOfWeek().getValue()%7))/7, 2, '0'); break;
			case 'V': s = pad(t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), 2, '0'); break;
			case 'G': s = Integer.toString(t.get(IsoFields.WEEK_BASED_YEAR)); break;
			// (zzz is EST or EDT as the date is; the zone's own short name would be ET)
			case 'Z': s = t.getOffset().getTotalSeconds() == 0 && t.getZone() instanceof ZoneOffset ? "UTC" : java.time.format.DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH).format(t); break;
			case 'z': {
				int secs = t.getOffset().getTotalSeconds();
				String sign = secs < 0 ? "-" : "+";
				secs = Math.abs(secs);
				s = sign+pad(secs/3600, 2, '0')+(colon ? ":" : "")+pad(secs%3600/60, 2, '0');
				break;
			}
			case 's': s = Long.toString(t.toEpochSecond()); break;
			case 'F': s = format(t, "%Y-%m-%d"); break;
			case 'T': s = format(t, "%H:%M:%S"); break;
			case 'R': s = format(t, "%H:%M"); break;
			case 'D': case 'x': s = format(t, "%m/%d/%y"); break;
			case 'X': s = format(t, "%H:%M:%S"); break;
			case 'r': s = format(t, "%I:%M:%S %p"); break;
			case 'c': s = format(t, "%a %b %e %H:%M:%S %Y"); break;
			case 'n': s = "\n"; break;
			case 't': s = "\t"; break;
			case '%': s = "%"; break;
			default: s = "%"+d;
			}
			if( noPad ) {
				s = s.replaceFirst("^[0 ]+(?=.)", "");
			} else if( padChar != 0 && s.length() > 0 && (s.charAt(0) == '0' || s.charAt(0) == ' ')) {
				s = s.replaceFirst("^[0 ]+(?=.)", "");
				s = pad2(s, padChar, "dmHIMSyeklj".indexOf(d) >= 0 ? 2 : 1);
			}
			sb.append(upper ? s.toUpperCase() : s);
		}
		return sb.toString();
	}

	private static String pad(long v, int width, char c) {
		StringBuilder s = new StringBuilder(Long.toString(v));
		while( s.length() < width ) {
			s.insert(0, c);
		}
		return s.toString();
	}

	private static String pad2(String s, char c, int width) {
		StringBuilder b = new StringBuilder(s);
		while( b.length() < width ) {
			b.insert(0, c);
		}
		return b.toString();
	}
}
