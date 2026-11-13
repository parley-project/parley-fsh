package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

/** join, modeled on the GNU join: joins the lines of two files that have a common field. Both must be sorted on it. */
public class Join extends ShellCommand{
	static String name = "join";
	static String help = "join [OPTION]... FILE1 FILE2\n"
			+ "\tFor each pair of lines with identical join fields, write a line with the join field, the rest of\n"
			+ "\tthe line of FILE1 and the rest of the line of FILE2. Both files must be sorted on the field.\n"
			+ "\t-1 FIELD, -2 FIELD   join on this field of FILE1 / FILE2 (fields count from 1)\n"
			+ "\t-j FIELD             same as -1 FIELD -2 FIELD\n"
			+ "\t-t CHAR              the field separator, in and out (default: runs of blanks in, one space out)\n"
			+ "\t-a FILENUM           also print the lines of FILE1 or FILE2 that can't be paired\n"
			+ "\t-v FILENUM           print only the lines of FILE1 or FILE2 that can't be paired\n"
			+ "\t-e STRING            put STRING in the place of the missing fields\n"
			+ "\t-o LIST              the output fields, like 0,1.2,2.1 (0 is the join field)\n"
			+ "\t-i                   ignore case when comparing the join fields";

	public Join() {
		super(name, help);
	}

	private static class Row {
		String [] fields;
		String key;
	}

	private static List<Row> rows(List<String> lines, int field, String sep, boolean fold) {
		List<Row> ret = new ArrayList<>();
		for (String line : lines) {
			Row r = new Row();
			if( sep == null ) {
				String t = line.replaceAll("^[ \t]+", "");
				r.fields = t.isEmpty() ? new String[] {""} : t.split("[ \t]+");
			} else {
				r.fields = line.split(java.util.regex.Pattern.quote(sep), -1);
			}
			String k = field-1 < r.fields.length ? r.fields[field-1] : "";
			r.key = fold ? k.toLowerCase() : k;
			ret.add(r);
		}
		return ret;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "i", "12jtaveo", false);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.size() != 2 ) {
			ctx.stderr.println("join: "+(a.operands.size() < 2 ? "missing operand" : "extra operand '"+a.operands.get(2)+"'"));
			return 1;
		}
		long f1 = a.number('1', a.number('j', 1)), f2 = a.number('2', a.number('j', 1));
		if( a.error != null || f1 < 1 || f2 < 1 ) {
			ctx.stderr.println("join: invalid field number");
			return 1;
		}
		String sep = a.value('t');
		if( sep != null && sep.length() != 1 ) {
			ctx.stderr.println("join: multi-character tab '"+sep+"'");
			return 1;
		}
		String empty = a.value('e') == null ? "" : a.value('e');
		boolean fold = a.has('i');
		boolean print1 = false, print2 = false, only = a.has('v');
		for (String v : a.values(only ? 'v' : 'a')) {
			if( v.equals("1")) {
				print1 = true;
			} else if( v.equals("2")) {
				print2 = true;
			} else {
				ctx.stderr.println("join: invalid file number: '"+v+"'");
				return 1;
			}
		}
		String [] outFields = a.value('o') == null || a.value('o').equals("auto") ? null : a.value('o').split("[, ]+");

		int [] st = {0};
		List<String> l1 = TextFiles.readLines(ctx, name, a.operands.subList(0, 1), st);
		int s1 = st[0];
		List<String> l2 = TextFiles.readLines(ctx, name, a.operands.subList(1, 2), st);
		if( s1 != 0 || st[0] != 0 ) {
			return 1;
		}
		List<Row> r1 = rows(l1, (int) f1, sep, fold), r2 = rows(l2, (int) f2, sep, fold);
		String out = sep == null ? " " : sep;
		StringBuilder sb = new StringBuilder();
		int i = 0, j = 0;
		while( i < r1.size() || j < r2.size()) {
			int cmp = i >= r1.size() ? 1 : j >= r2.size() ? -1 : r1.get(i).key.compareTo(r2.get(j).key);
			if( cmp < 0 ) {
				if( print1 ) {
					sb.append(unpaired(r1.get(i), (int) f1, 1, outFields, out, empty)).append('\n');
				}
				i++;
			} else if( cmp > 0 ) {
				if( print2 ) {
					sb.append(unpaired(r2.get(j), (int) f2, 2, outFields, out, empty)).append('\n');
				}
				j++;
			} else {
				int ei = i, ej = j;
				String key = r1.get(i).key;
				while( ei < r1.size() && r1.get(ei).key.equals(key)) {
					ei++;
				}
				while( ej < r2.size() && r2.get(ej).key.equals(key)) {
					ej++;
				}
				if( !only ) {
					for (int x = i; x < ei; x++) {
						for (int y = j; y < ej; y++) {
							sb.append(paired(r1.get(x), r2.get(y), (int) f1, (int) f2, outFields, out, empty)).append('\n');
						}
					}
				}
				i = ei;
				j = ej;
			}
		}
		ctx.stdout.print(sb);
		ctx.stdout.flush();
		return 0;
	}

	private static String field(Row r, int n, String empty) {
		return n-1 < r.fields.length && n >= 1 && !r.fields[n-1].isEmpty() ? r.fields[n-1] : empty;
	}

	private static String paired(Row x, Row y, int f1, int f2, String [] spec, String sep, String empty) {
		List<String> parts = new ArrayList<>();
		if( spec == null ) {
			parts.add(field(x, f1, empty));
			rest(x, f1, parts);
			rest(y, f2, parts);
		} else {
			for (String s : spec) {
				parts.add(pick(s, x, y, f1, f2, empty));
			}
		}
		return String.join(sep, parts);
	}

	private static String unpaired(Row r, int f, int which, String [] spec, String sep, String empty) {
		List<String> parts = new ArrayList<>();
		if( spec == null ) {
			parts.add(field(r, f, empty));
			rest(r, f, parts);
		} else {
			for (String s : spec) {
				parts.add(pick(s, which == 1 ? r : null, which == 2 ? r : null, f, f, empty));
			}
		}
		return String.join(sep, parts);
	}

	private static void rest(Row r, int f, List<String> parts) {
		for (int k = 0; k < r.fields.length; k++) {
			if( k != f-1 ) {
				parts.add(r.fields[k]);
			}
		}
	}

	private static String pick(String spec, Row x, Row y, int f1, int f2, String empty) {
		if( spec.equals("0")) {
			return field(x != null ? x : y, x != null ? f1 : f2, empty);
		}
		String [] p = spec.split("\\.");
		try {
			int file = Integer.parseInt(p[0]), n = Integer.parseInt(p[1]);
			Row r = file == 1 ? x : y;
			return r == null ? empty : field(r, n, empty);
		} catch (RuntimeException e) {
			return empty;
		}
	}
}
