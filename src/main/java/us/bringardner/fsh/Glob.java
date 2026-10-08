package us.bringardner.fsh;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
		// an escaped / is a / (it separates directories either way)
		// (not after a pattern in the same directory name: bash's tm[p]\/a matches nothing)
		StringBuilder plain = new StringBuilder();
		boolean patternHere = false;
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if( c == '\\' && i+1 < pattern.length()) {
				char next = pattern.charAt(++i);
				if( next != '/' || patternHere ) {
					plain.append(c);
				}
				plain.append(next);
				if( next == '/' ) {
					patternHere = false;
				}
			} else {
				plain.append(c);
				patternHere = c == '/' ? false : patternHere || c == '*' || c == '?' || c == '[';
			}
		}
		pattern = plain.toString();
		boolean dirsOnly = pattern.endsWith("/");
		String [] segments = pattern.split("/");
		// the paths matched so far, as written
		List<String> paths = new ArrayList<>();
		paths.add(pattern.startsWith("/") ? "/" : "");
		// a segment before this was a pattern (a/** has a/ itself, **/a/** has a)
		boolean globbed = false;
		for (int idx = 0; idx < segments.length; idx++) {
			String segment = segments[idx];
			if( segment.isEmpty()) {
				continue;
			}
			boolean last = idx == segments.length-1;
			if( segment.equals("**") && option(ctx, "globstar") && !last && segments[idx+1].equals("**")) {
				// **/** is **
				globbed = true;
				continue;
			}
			List<String> next = new ArrayList<>();
			for(String path : paths) {
				if( !isPattern(segment)) {
					String child = join(path, unescape(segment));
					FileSource file = ctx.console.createFileSource(child);
					if( !path.isEmpty() && ctx.console.createFileSource(path) instanceof us.bringardner.parley.files.fileproxy.FileProxy proxy
							&& !java.nio.file.Files.isExecutable(proxy.getTarget().toPath())) {
						// nothing in a directory that cannot be searched (readable/. as well)
						continue;
					}
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
					} else if( !path.isEmpty()) {
						// (no directories: a/** has a/ itself)
						next.add(dirsOnly || globbed || path.endsWith("/") ? path : path+"/");
					}
					below(ctx, dir, path, last && !dirsOnly, last, next);
					continue;
				}
				FileSource [] kids = dir.isDirectory() ? dir.listFiles() : null;
				if( kids == null ) {
					continue;
				}
				GlobPattern rx = GlobPattern.compile(segment, option(ctx, "nocaseglob"));
				// a name with a leading dot only where the pattern has a . for it (@(.a|b) too), or
				// with dotglob; . and .. (when globskipdots is off) only so, as in bash
				boolean dotglob = dotglob(ctx);
				List<String> names = new ArrayList<>();
				if( !option(ctx, "globskipdots")) {
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
					if( kid == null ) {
						continue;
					}
					boolean period = name.equals(".") || name.equals("..") || name.startsWith(".") && !dotglob;
					if( rx.matches(name, period) && (!(last ? dirsOnly : true) || kid.isDirectory())) {
						next.add(join(path, name));
					}
				}
			}
			paths = next;
			globbed |= isPattern(segment);
			if( paths.isEmpty()) {
				return ret;
			}
		}
		for(String path : paths) {
			ret.add(dirsOnly ? path+"/" : path);
		}
		ignore(ret, ctx);
		sort(ret, ctx);
		return ret;
	}

	/** dotglob, or a GLOBIGNORE (which has names with a leading dot match, as bash's) */
	private static boolean dotglob(ShellContext ctx) {
		return option(ctx, "dotglob") || !globIgnore(ctx).isEmpty();
	}

	/** the patterns of $GLOBIGNORE */
	private static List<String> globIgnore(ShellContext ctx) {
		Object v = ctx.getVariable("GLOBIGNORE");
		List<String> ret = new ArrayList<>();
		if( v != null ) {
			// split at the colons that are not in a [...] ([[:alnum:]] is one pattern)
			String text = v.toString();
			int start = 0;
			for (int i = 0; i <= text.length(); i++) {
				if( i < text.length() && text.charAt(i) == '\\' ) {
					i++;
				} else if( i < text.length() && text.charAt(i) == '[' ) {
					int end = ShellCommand.bracketEnd(text, i);
					if( end > 0 ) {
						i = end;
					}
				} else if( i == text.length() || text.charAt(i) == ':' ) {
					if( i > start ) {
						ret.add(text.substring(start, i));
					}
					start = i+1;
				}
			}
		}
		return ret;
	}

	/** GLOBIGNORE: the matches that match one of its patterns go, and . and .. too */
	private static void ignore(List<String> paths, ShellContext ctx) {
		List<String> patterns = globIgnore(ctx);
		if( patterns.isEmpty()) {
			return;
		}
		List<GlobPattern> compiled = new ArrayList<>();
		for(String p : patterns) {
			compiled.add(GlobPattern.compile(p, option(ctx, "nocaseglob")));
		}
		paths.removeIf(path -> {
			String last = path.replaceAll("/+$", "").replaceAll("^.*/", "");
			if( last.equals(".") || last.equals("..")) {
				return true;
			}
			for(GlobPattern g : compiled) {
				if( g.matches(path)) {
					return true;
				}
			}
			return false;
		});
	}

	/**
	 * The matches in $GLOBSORT's order: [+|-]name, size, blocks, mtime, atime, ctime, numeric or
	 * nosort (- reverses; ties go by name). Anything else (or unset) is by name.
	 */
	private static void sort(List<String> paths, ShellContext ctx) {
		Object v = ctx.getVariable("GLOBSORT");
		String spec = v == null ? "" : v.toString().trim();
		boolean reverse = spec.startsWith("-");
		if( spec.startsWith("-") || spec.startsWith("+")) {
			spec = spec.substring(1);
		}
		if( spec.equals("nosort")) {
			return;
		}
		java.util.Comparator<String> byName = Comparator.naturalOrder();
		java.util.Comparator<String> order;
		switch (spec) {
		case "size", "blocks", "mtime", "atime", "ctime" -> {
			final String key = spec;
			java.util.Map<String,Long> keys = new java.util.HashMap<>();
			for(String p : paths) {
				keys.put(p, attribute(ctx, p, key));
			}
			order = Comparator.<String,Long>comparing(keys::get).thenComparing(byName);
		}
		case "numeric" -> order = Comparator.<String,java.math.BigInteger>comparing(p -> {
			String n = p.replaceAll("^.*/", "");
			return n.matches("[0-9]+") ? new java.math.BigInteger(n) : java.math.BigInteger.valueOf(-1);
		}).thenComparing(byName);
		case "name" -> order = byName;
		default -> {
			// (not a sort key: by name, never reversed)
			order = byName;
			reverse = false;
		}
		}
		paths.sort(reverse ? order.reversed() : order);
	}

	/** a file's size, blocks or time (nanoseconds), for GLOBSORT */
	private static long attribute(ShellContext ctx, String path, String key) {
		try {
			FileSource f = ctx.console.createFileSource(path);
			if( !(f instanceof us.bringardner.parley.files.fileproxy.FileProxy proxy)) {
				return 0;
			}
			java.nio.file.Path p = proxy.getTarget().toPath();
			java.nio.file.attribute.BasicFileAttributes a = java.nio.file.Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
			return switch (key) {
			case "size" -> a.size();
			case "blocks" -> (a.size()+511)/512;
			case "mtime" -> a.lastModifiedTime().to(java.util.concurrent.TimeUnit.NANOSECONDS);
			case "atime" -> a.lastAccessTime().to(java.util.concurrent.TimeUnit.NANOSECONDS);
			default -> {
				Object c = java.nio.file.Files.getAttribute(p, "unix:ctime");
				yield c instanceof java.nio.file.attribute.FileTime t ? t.to(java.util.concurrent.TimeUnit.NANOSECONDS) : 0;
			}
			};
		} catch (Exception e) {
			return 0;
		}
	}

	/** shopt name is set */
	public static boolean option(ShellContext ctx, String name) {
		return Boolean.TRUE.equals(ctx.console.getShellOptions().get(name));
	}

	/** every directory below dir (and, if files, every file too); hidden ones only with dotglob */
	private static void below(ShellContext ctx, FileSource dir, String path, boolean files, boolean last, List<String> out) throws IOException {
		FileSource [] kids = dir.isDirectory() ? dir.listFiles() : null;
		if( kids == null ) {
			return;
		}
		for(FileSource kid : kids) {
			String name = kid.getName();
			if( name.startsWith(".") && !dotglob(ctx)) {
				continue;
			}
			String child = join(path, name);
			if( kid.isDirectory()) {
				// a link to a directory: listed by a last **, never gone into (as bash's)
				boolean link = kid instanceof us.bringardner.parley.files.fileproxy.FileProxy proxy
						&& java.nio.file.Files.isSymbolicLink(proxy.getTarget().toPath());
				if( !link || last ) {
					out.add(child);
				}
				if( !link ) {
					below(ctx, kid, child, files, last, out);
				}
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
