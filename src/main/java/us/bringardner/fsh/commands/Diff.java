package us.bringardner.fsh.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/**
 * diff, modeled on the GNU diff: shows the lines that differ between two files, in the normal
 * or unified format, or between two directories. Exit status 0 same, 1 different, 2 trouble.
 */
public class Diff extends ShellCommand{
	static String name = "diff";
	static String help = "diff [OPTION]... FILE1 FILE2\n"
			+ "\tCompare two files line by line, or the files of two directories.\n"
			+ "\t-u, -U NUM, --unified, --context=NUM   unified format with NUM lines of context (default 3)\n"
			+ "\t-q, --brief                   only say whether the files differ\n"
			+ "\t-s, --report-identical-files  say when the files are the same\n"
			+ "\t-i, --ignore-case             ignore case differences\n"
			+ "\t-w, --ignore-all-space        ignore all white space\n"
			+ "\t-b, --ignore-space-change     ignore changes in the amount of white space\n"
			+ "\t-r, --recursive               compare the sub directories too\n"
			+ "\t-N, --new-file                treat a file that is missing as empty\n"
			+ "\tWithout -u the output is in the normal format (3,4c3,4 / < old / --- / > new).";

	public Diff() {
		super(name, help);
	}

	private static class Opts {
		boolean unified, brief, same, fold, allSpace, space, recursive, newFile;
		int context = 3;
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// -U takes a number; a bare --unified keeps 3
		Args a = Args.parse(ctx, args, "uqsiwbrN", "U", false, "unified:u", "brief:q", "report-identical-files:s", "ignore-case:i",
				"ignore-all-space:w", "ignore-space-change:b", "recursive:r", "new-file:N", "context=U");
		long ctxLines = a.number('U', 3);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.size() != 2 ) {
			ctx.stderr.println("diff: "+(a.operands.size() < 2 ? "missing operand after '"+(a.operands.isEmpty() ? "diff" : a.operands.get(0))+"'" : "extra operand '"+a.operands.get(2)+"'"));
			return 2;
		}
		Opts o = new Opts();
		o.unified = a.has('u') || a.has('U');
		o.context = (int) ctxLines;
		o.brief = a.has('q');
		o.same = a.has('s');
		o.fold = a.has('i');
		o.allSpace = a.has('w');
		o.space = a.has('b');
		o.recursive = a.has('r');
		o.newFile = a.has('N');

		String p1 = a.operands.get(0), p2 = a.operands.get(1);
		StringBuilder out = new StringBuilder();
		int status;
		try {
			FileSource f1 = p1.equals("-") ? null : ctx.console.createFileSource(p1);
			FileSource f2 = p2.equals("-") ? null : ctx.console.createFileSource(p2);
			if( f1 != null && f2 != null && f1.isDirectory() && f2.isDirectory()) {
				status = dirs(ctx, o, f1, f2, p1, p2, out);
			} else if( f1 != null && f1.isDirectory() || f2 != null && f2.isDirectory()) {
				// a file against a directory: the file of that name in the directory
				FileSource dir = f1 != null && f1.isDirectory() ? f1 : f2;
				FileSource file = dir == f1 ? f2 : f1;
				if( file == null ) {
					ctx.stderr.println("diff: cannot compare standard input to a directory");
					return 2;
				}
				FileSource inDir = ctx.console.createFileSource(dir.getAbsolutePath()+"/"+file.getName());
				status = dir == f1 ? files(ctx, o, inDir, p1+"/"+file.getName(), f2, p2, out) : files(ctx, o, f1, p1, inDir, p2+"/"+file.getName(), out);
			} else {
				status = files(ctx, o, f1, p1, f2, p2, out);
			}
		} catch (IOException e) {
			ctx.stderr.println("diff: "+e.getMessage());
			return 2;
		}
		ctx.stdout.print(out);
		ctx.stdout.flush();
		return status;
	}

	private int dirs(ShellContext ctx, Opts o, FileSource d1, FileSource d2, String p1, String p2, StringBuilder out) throws IOException {
		TreeSet<String> names = new TreeSet<>();
		FileSource [] k1 = d1.listFiles(), k2 = d2.listFiles();
		Map<String, FileSource> m1 = new HashMap<>(), m2 = new HashMap<>();
		for (FileSource f : k1 == null ? new FileSource[0] : k1) {
			m1.put(f.getName(), f);
			names.add(f.getName());
		}
		for (FileSource f : k2 == null ? new FileSource[0] : k2) {
			m2.put(f.getName(), f);
			names.add(f.getName());
		}
		int status = 0;
		for (String n : names) {
			FileSource x = m1.get(n), y = m2.get(n);
			String q1 = p1+"/"+n, q2 = p2+"/"+n;
			if( x == null || y == null ) {
				if( !o.newFile || (x != null ? x.isDirectory() : y.isDirectory())) {
					out.append("Only in ").append(x == null ? p2 : p1).append(": ").append(n).append('\n');
					status = Math.max(status, 1);
					continue;
				}
			}
			boolean dir = (x != null && x.isDirectory()) || (y != null && y.isDirectory());
			if( dir ) {
				if( x != null && y != null && x.isDirectory() && y.isDirectory()) {
					if( o.recursive ) {
						status = Math.max(status, dirs(ctx, o, x, y, q1, q2, out));
					} else {
						out.append("Common subdirectories: ").append(q1).append(" and ").append(q2).append('\n');
					}
				} else {
					out.append("File ").append(q1).append(" is a ").append(x != null && x.isDirectory() ? "directory" : "regular file")
						.append(" while file ").append(q2).append(" is a ").append(y != null && y.isDirectory() ? "directory" : "regular file").append('\n');
					status = Math.max(status, 1);
				}
				continue;
			}
			StringBuilder one = new StringBuilder();
			int s = files(ctx, o, x, q1, y, q2, one);
			if( s == 1 && !o.brief && !one.toString().startsWith("Binary") ) {
				out.append("diff ").append(o.recursive ? "-r " : "").append(q1).append(' ').append(q2).append('\n');
			}
			out.append(one);
			status = Math.max(status, s);
		}
		return status;
	}

	private static byte [] read(ShellContext ctx, FileSource f, String path, boolean newFile) throws IOException {
		if( f == null ) {
			return ctx.stdin.readAllBytes();
		}
		if( !f.exists()) {
			if( newFile ) {
				return new byte[0];
			}
			throw new IOException(path+": No such file or directory");
		}
		try (java.io.InputStream in = f.getInputStream()) {
			return in.readAllBytes();
		}
	}

	private static List<String> split(byte [] data) {
		List<String> lines = new ArrayList<>();
		String text = new String(data, StandardCharsets.UTF_8);
		int pos = 0;
		while( pos < text.length()) {
			int nl = text.indexOf('\n', pos);
			if( nl < 0 ) {
				lines.add(text.substring(pos));
				break;
			}
			lines.add(text.substring(pos, nl));
			pos = nl+1;
		}
		return lines;
	}

	private static String normalize(String line, Opts o) {
		String s = line;
		if( o.allSpace ) {
			s = s.replaceAll("[ \t\r\f\u000b]+", "");
		} else if( o.space ) {
			s = s.replaceAll("[ \t\r\f\u000b]+", " ").replaceAll(" $", "");
		}
		return o.fold ? s.toLowerCase() : s;
	}

	private int files(ShellContext ctx, Opts o, FileSource f1, String p1, FileSource f2, String p2, StringBuilder out) throws IOException {
		byte [] d1 = read(ctx, f1, p1, o.newFile), d2 = read(ctx, f2, p2, o.newFile);
		boolean binary = false;
		for (byte [] d : new byte[][] {d1, d2}) {
			for (int i = 0; i < Math.min(d.length, 8000); i++) {
				if( d[i] == 0 ) {
					binary = true;
				}
			}
		}
		List<String> a = split(d1), b = split(d2);
		int [] ia = new int[a.size()], ib = new int[b.size()];
		Map<String, Integer> ids = new HashMap<>();
		for (int i = 0; i < ia.length; i++) {
			ia[i] = ids.computeIfAbsent(normalize(a.get(i), o), k -> ids.size());
		}
		for (int i = 0; i < ib.length; i++) {
			ib[i] = ids.computeIfAbsent(normalize(b.get(i), o), k -> ids.size());
		}
		boolean same = Arrays.equals(ia, ib);
		if( same ) {
			if( o.same ) {
				out.append("Files ").append(p1).append(" and ").append(p2).append(" are identical\n");
			}
			return 0;
		}
		if( binary ) {
			out.append("Binary files ").append(p1).append(" and ").append(p2).append(" differ\n");
			return 1;
		}
		if( o.brief ) {
			out.append("Files ").append(p1).append(" and ").append(p2).append(" differ\n");
			return 1;
		}

		boolean [] del = new boolean[ia.length], ins = new boolean[ib.length];
		diff(ia, ib, del, ins);

		if( o.unified ) {
			out.append("--- ").append(p1).append('\t').append(stamp(f1)).append('\n');
			out.append("+++ ").append(p2).append('\t').append(stamp(f2)).append('\n');
			unified(o.context, a, b, del, ins, out);
		} else {
			normal(a, b, del, ins, out);
		}
		return 1;
	}

	private static String stamp(FileSource f) {
		try {
			long t = f == null ? System.currentTimeMillis() : f.lastModified();
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS000000 Z").format(new Date(t));
		} catch (IOException e) {
			return "";
		}
	}

	/** Myers' O(ND) difference: marks the lines of a that are deleted and the lines of b that are inserted */
	static void diff(int [] a, int [] b, boolean [] del, boolean [] ins) {
		int start = 0;
		while( start < a.length && start < b.length && a[start] == b[start]) {
			start++;
		}
		int endA = a.length, endB = b.length;
		while( endA > start && endB > start && a[endA-1] == b[endB-1]) {
			endA--;
			endB--;
		}
		int n = endA-start, m = endB-start;
		if( n == 0 ) {
			for (int j = start; j < endB; j++) {
				ins[j] = true;
			}
			return;
		}
		if( m == 0 ) {
			for (int i = start; i < endA; i++) {
				del[i] = true;
			}
			return;
		}
		int max = n+m;
		int off = max+1;
		int [] v = new int[2*max+3];
		List<int[]> trace = new ArrayList<>();
		for (int d = 0; d <= max; d++) {
			trace.add(Arrays.copyOfRange(v, off-d-1, off+d+2));
			for (int k = -d; k <= d; k += 2) {
				int x;
				if( k == -d || (k != d && v[off+k-1] < v[off+k+1])) {
					x = v[off+k+1];
				} else {
					x = v[off+k-1]+1;
				}
				int y = x-k;
				while( x < n && y < m && a[start+x] == b[start+y]) {
					x++;
					y++;
				}
				v[off+k] = x;
				if( x >= n && y >= m ) {
					backtrack(trace, n, m, start, del, ins);
					return;
				}
			}
		}
	}

	private static void backtrack(List<int[]> trace, int n, int m, int start, boolean [] del, boolean [] ins) {
		int x = n, y = m;
		for (int d = trace.size()-1; d >= 0; d--) {
			int [] v = trace.get(d);
			int k = x-y;
			// v holds k from -d-1: index k+d+1
			int prevK;
			if( k == -d || (k != d && v[k-1+d+1] < v[k+1+d+1])) {
				prevK = k+1;
			} else {
				prevK = k-1;
			}
			int prevX = v[prevK+d+1];
			int prevY = prevX-prevK;
			while( x > prevX && y > prevY ) {
				x--;
				y--;
			}
			if( d > 0 ) {
				if( x == prevX ) {
					ins[start+prevY] = true;
				} else {
					del[start+prevX] = true;
				}
			}
			x = prevX;
			y = prevY;
		}
	}

	private static String range(int first, int count) {
		return count == 1 ? Integer.toString(first) : first+","+(first+count-1);
	}

	private static void normal(List<String> a, List<String> b, boolean [] del, boolean [] ins, StringBuilder out) {
		int i = 0, j = 0;
		while( i < del.length || j < ins.length) {
			if( i < del.length && !del[i] && j < ins.length && !ins[j] ) {
				i++;
				j++;
				continue;
			}
			int i0 = i, j0 = j;
			while( i < del.length && del[i]) {
				i++;
			}
			while( j < ins.length && ins[j]) {
				j++;
			}
			if( i == i0 && j == j0 ) {
				break;
			}
			int da = i-i0, db = j-j0;
			if( da == 0 ) {
				out.append(i0).append('a').append(range(j0+1, db)).append('\n');
			} else if( db == 0 ) {
				out.append(range(i0+1, da)).append('d').append(j0).append('\n');
			} else {
				out.append(range(i0+1, da)).append('c').append(range(j0+1, db)).append('\n');
			}
			for (int k = i0; k < i; k++) {
				out.append("< ").append(a.get(k)).append('\n');
			}
			if( da > 0 && db > 0 ) {
				out.append("---\n");
			}
			for (int k = j0; k < j; k++) {
				out.append("> ").append(b.get(k)).append('\n');
			}
		}
	}

	private static String uRange(int start, int count) {
		if( count == 1 ) {
			return Integer.toString(start+1);
		}
		return (count == 0 ? start : start+1)+","+count;
	}

	private static void unified(int ctxLines, List<String> a, List<String> b, boolean [] del, boolean [] ins, StringBuilder out) {
		// the changed blocks: [i0,i1) of a and [j0,j1) of b
		List<int[]> blocks = new ArrayList<>();
		int i = 0, j = 0;
		while( i < del.length || j < ins.length) {
			if( i < del.length && !del[i] && j < ins.length && !ins[j] ) {
				i++;
				j++;
				continue;
			}
			int i0 = i, j0 = j;
			while( i < del.length && del[i]) {
				i++;
			}
			while( j < ins.length && ins[j]) {
				j++;
			}
			if( i == i0 && j == j0 ) {
				break;
			}
			blocks.add(new int[] {i0, i, j0, j});
		}
		int idx = 0;
		while( idx < blocks.size()) {
			int last = idx;
			while( last+1 < blocks.size() && blocks.get(last+1)[0]-blocks.get(last)[1] <= 2*ctxLines ) {
				last++;
			}
			int [] first = blocks.get(idx), end = blocks.get(last);
			int hs = Math.max(0, first[0]-ctxLines);
			int he = Math.min(del.length, end[1]+ctxLines);
			int hsB = first[2]-(first[0]-hs);
			int heB = end[3]+(he-end[1]);
			out.append("@@ -").append(uRange(hs, he-hs)).append(" +").append(uRange(hsB, heB-hsB)).append(" @@\n");
			int x = hs, y = hsB;
			while( x < he || y < heB ) {
				if( x < he && del[x] ) {
					out.append('-').append(a.get(x++)).append('\n');
				} else if( y < heB && ins[y] ) {
					out.append('+').append(b.get(y++)).append('\n');
				} else {
					out.append(' ').append(a.get(x)).append('\n');
					x++;
					y++;
				}
			}
			idx = last+1;
		}
	}
}
