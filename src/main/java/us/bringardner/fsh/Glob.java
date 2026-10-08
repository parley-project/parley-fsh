package us.bringardner.fsh;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import us.bringardner.parley.files.FileSource;

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
	 * shopt -s failglob: a pattern that matches nothing is an error. As in bash it ends a script
	 * (or the subshell it is in) with status 1; at a prompt only the command fails.
	 */
	public static void failglob(String pattern, ShellContext ctx) {
		if( option(ctx, "failglob")) {
			ctx.stderr.println("no match: "+pattern);
			if( ctx.console.isInteractive ) {
				throw new RuntimeException("no match: "+pattern);
			}
			throw new us.bringardner.fsh.signal.ExitException(ctx, 1);
		}
	}

	/** true if text has *, ? or [ (a word that is a pattern) */
	/** the pattern starts with a dot: .*, or an extglob group with an alternative that does */
	static boolean startsWithDot(String segment) {
		if( segment.startsWith(".") || segment.startsWith("\\.")) {
			return true;
		}
		if( segment.length() > 2 && "?*+@!".indexOf(segment.charAt(0)) >= 0 && segment.charAt(1) == '(' ) {
			int depth = 0;
			boolean altStart = true;
			for (int i = 1; i < segment.length(); i++) {
				char c = segment.charAt(i);
				if( c == '(' ) {
					depth++;
					altStart = depth == 1;
					continue;
				}
				if( c == ')' && --depth == 0 ) {
					break;
				}
				if( depth == 1 && altStart && c == '.' ) {
					return true;
				}
				altStart = depth == 1 && c == '|';
			}
		}
		return false;
	}

	public static boolean isPattern(String text) {
		return text.indexOf('*') >= 0 || text.indexOf('?') >= 0 || text.indexOf('[') >= 0
				|| text.contains("@(") || text.contains("!(") || text.contains("+(");
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
				if( segment.equals("**") && option(ctx, "globstar")) {
					// ** matches this directory and every one below it (and, last, every file)
					if( !last ) {
						next.add(path);
					}
					below(ctx, dir, path, last, next);
					continue;
				}
				FileSource [] kids = dir.isDirectory() ? dir.listFiles() : null;
				if( kids == null ) {
					continue;
				}
				GlobPattern rx = GlobPattern.compile(segment, option(ctx, "nocaseglob"));
				// a name with a leading dot only for a pattern with one (in @(.a|b) too), or dotglob;
				// . and .. too when globskipdots is off, as in bash
				boolean explicitDot = startsWithDot(segment);
				boolean hidden = explicitDot || option(ctx, "dotglob");
				List<String> names = new ArrayList<>();
				if( explicitDot && !option(ctx, "globskipdots")) {
					names.add(".");
					names.add("..");
				}
				for(FileSource kid : kids) {
					names.add(kid.getName());
				}
				for(String name : names) {
					FileSource kid = name.equals(".") ? dir : name.equals("..") ? dir.getParentFile() : null;
					if( kid == null ) {
						for(FileSource k : kids) {
							if( k.getName().equals(name)) {
								kid = k;
								break;
							}
						}
					}
					if( kid == null || (name.startsWith(".") && !hidden)) {
						continue;
					}
					if( (name.equals(".") || name.equals("..")) && !explicitDot ) {
						continue;
					}
					if( rx.matches(name) && (!(last ? dirsOnly : true) || kid.isDirectory())) {
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

	/** shopt name is set */
	public static boolean option(ShellContext ctx, String name) {
		return Boolean.TRUE.equals(ctx.console.getShellOptions().get(name));
	}

	/** every directory below dir (and, if files, every file too); hidden ones only with dotglob */
	private static void below(ShellContext ctx, FileSource dir, String path, boolean files, List<String> out) throws IOException {
		FileSource [] kids = dir.isDirectory() ? dir.listFiles() : null;
		if( kids == null ) {
			return;
		}
		for(FileSource kid : kids) {
			String name = kid.getName();
			if( name.startsWith(".") && !option(ctx, "dotglob")) {
				continue;
			}
			String child = join(path, name);
			if( kid.isDirectory()) {
				out.add(child);
				below(ctx, kid, child, files, out);
			} else if( files ) {
				out.add(child);
			}
		}
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
