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

	/**
	 * The words a word becomes: braces, splitting and pathname expansion (as for a command's
	 * arguments, and array values a=(*.c)).
	 */
	public static List<String> expandWord(us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext word, ShellContext ctx) throws IOException {
		List<us.bringardner.shell.antlr.Argument> fields = us.bringardner.shell.antlr.Argument.expandWord(word, ctx, true);
		if( fields == null ) {
			fields = List.of(new us.bringardner.shell.antlr.Argument(word));
		}
		List<String> ret = new ArrayList<>();
		for(us.bringardner.shell.antlr.Argument a : fields) {
			String value = ""+a.getValue(ctx);
			List<String> matches = a.hasUnquotedWildcard() ? expand(value, ctx) : List.of();
			if( matches.isEmpty()) {
				ret.add(value);
			} else {
				ret.addAll(matches);
			}
		}
		return ret;
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
		return Pattern.compile(ShellCommand.prepWildCards(glob, true), Pattern.DOTALL);
	}

}
