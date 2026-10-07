package us.bringardner.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import us.bringardner.io.filesource.FileSource;

/**
 * Pathname expansion, as bash does it to the words of a command: * matches any text, ? one character,
 * and [abc], [a-z], [!abc] or [^abc] one of a set, within one path segment. A name starting with . is
 * matched only by a pattern that starts with . (and never . or ..).
 * <p>
 * The matches are written as the pattern was (src/*.java gives src/A.java, /tmp/* absolute paths)
 * and sorted. A pattern ending with / matches directories only.
 */
public class Glob {

	private Glob() {
	}

	/** true if text has *, ? or [ (a word that is a pattern) */
	public static boolean isPattern(String text) {
		return text.indexOf('*') >= 0 || text.indexOf('?') >= 0 || text.indexOf('[') >= 0;
	}

	/**
	 * @return the paths that match pattern, sorted; empty if none do
	 */
	public static List<String> expand(String pattern, ShellContext ctx) throws IOException {
		List<String> ret = new ArrayList<>();
		if( !isPattern(pattern)) {
			return ret;
		}
		boolean dirsOnly = pattern.endsWith("/");
		String [] segments = pattern.split("/");
		// the paths matched so far, as written
		List<String> paths = new ArrayList<>();
		paths.add(pattern.startsWith("/") ? "/" : "");
		for (int idx = 0; idx < segments.length; idx++) {
			String segment = segments[idx];
			if( segment.isEmpty()) {
				continue;
			}
			boolean last = idx == segments.length-1;
			List<String> next = new ArrayList<>();
			for(String path : paths) {
				if( !isPattern(segment)) {
					String child = join(path, unescape(segment));
					FileSource file = ctx.console.createFileSource(child);
					if( last ? file.exists() : file.isDirectory()) {
						next.add(child);
					}
					continue;
				}
				FileSource dir = ctx.console.createFileSource(path.isEmpty() ? "." : path);
				FileSource [] kids = dir.isDirectory() ? dir.listFiles() : null;
				if( kids == null ) {
					continue;
				}
				Pattern rx = toRegex(segment);
				boolean hidden = segment.startsWith(".");
				for(FileSource kid : kids) {
					String name = kid.getName();
					if( name.equals(".") || name.equals("..") || (name.startsWith(".") && !hidden)) {
						continue;
					}
					if( rx.matcher(name).matches() && (!(last ? dirsOnly : true) || kid.isDirectory())) {
						next.add(join(path, name));
					}
				}
			}
			paths = next;
			if( paths.isEmpty()) {
				return ret;
			}
		}
		for(String path : paths) {
			ret.add(dirsOnly ? path+"/" : path);
		}
		Collections.sort(ret);
		return ret;
	}

	private static String join(String path, String name) {
		if( path.isEmpty()) {
			return name;
		}
		return path.endsWith("/") ? path+name : path+"/"+name;
	}

	private static String unescape(String text) {
		return text.replaceAll("\\\\(.)", "$1");
	}

	/**
	 * A segment of a pattern as a regular expression.
	 */
	public static Pattern toRegex(String glob) {
		StringBuilder rx = new StringBuilder();
		int n = glob.length();
		for (int idx = 0; idx < n; idx++) {
			char c = glob.charAt(idx);
			switch (c) {
			case '*':
				rx.append(".*");
				break;
			case '?':
				rx.append('.');
				break;
			case '\\':
				if( idx+1 < n ) {
					rx.append(Pattern.quote(""+glob.charAt(++idx)));
				} else {
					rx.append("\\\\");
				}
				break;
			case '[': {
				int end = classEnd(glob, idx);
				if( end < 0 ) {
					rx.append("\\[");
					break;
				}
				String body = glob.substring(idx+1, end);
				rx.append('[');
				int start = 0;
				if( body.startsWith("!") || body.startsWith("^")) {
					rx.append('^');
					start = 1;
				}
				for (int j = start; j < body.length(); j++) {
					char b = body.charAt(j);
					if( b == '-' && j > start && j < body.length()-1 ) {
						rx.append('-');
					} else if( "\\[]^&".indexOf(b) >= 0 ) {
						rx.append('\\').append(b);
					} else {
						rx.append(b);
					}
				}
				rx.append(']');
				idx = end;
				break;
			}
			default:
				rx.append(Pattern.quote(""+c));
			}
		}
		return Pattern.compile(rx.toString(), Pattern.DOTALL);
	}

	/** the index of the ] that closes the [ at start (a ] right after [ or [! is part of the set), or -1 */
	private static int classEnd(String glob, int start) {
		int idx = start+1;
		if( idx < glob.length() && (glob.charAt(idx) == '!' || glob.charAt(idx) == '^')) {
			idx++;
		}
		if( idx < glob.length() && glob.charAt(idx) == ']' ) {
			idx++;
		}
		while( idx < glob.length() && glob.charAt(idx) != ']' ) {
			idx++;
		}
		return idx < glob.length() ? idx : -1;
	}
}
