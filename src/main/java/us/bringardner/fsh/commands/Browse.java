package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.spi.BrowseRequest;
import us.bringardner.fsh.spi.BrowserProvider;
import us.bringardner.fsh.spi.ShellConnections;
import us.bringardner.parley.files.FileSource;

/**
 * browse: open a file browser: fsh's own terminal browser ({@link TerminalBrowser}), or one a plugin
 * provides (us.bringardner.fsh.spi.BrowserProvider): the one of the highest priority that can run
 * here (a window when there is a display, else a terminal one), or the one --with names.
 */
public class Browse extends ShellCommand {
	static final String NAME = "browse";
	static final String USAGE = "browse [-1] [-c] [--with NAME] [DIR [DIR2]]  or  browse --list";
	static final String HELP = USAGE+"\n"
			+ "\tBrowse the shell's file tree (connections included) with DIR (default .) on the left and DIR2\n"
			+ "\t(default $HOME) on the right, in fsh's own full-screen terminal browser, or in one a plugin\n"
			+ "\tprovides (a window when there is a display).\n"
			+ "\t-1           start with one pane\n"
			+ "\t-c           when it ends, cd to the folder it was showing\n"
			+ "\t--with NAME  use this browser\n"
			+ "\t--list       list the browsers: their names, which can run here, and which is used";

	public Browse() {
		super(NAME, HELP);
	}

	/** the browser built into fsh */
	static final BrowserProvider BUILTIN = new TerminalBrowser();

	/**
	 * the browsers: fsh's own, then those the application and its plugins provide, the highest
	 * priority first (fsh's own before a plugin's of the same priority)
	 */
	static List<BrowserProvider> providers(ShellContext ctx) {
		List<BrowserProvider> ret = new ArrayList<>();
		ret.add(BUILTIN);
		for(BrowserProvider b : us.bringardner.fsh.Console.pluginProviders(BrowserProvider.class,
				e -> ctx.error(NAME+": a browser cannot be loaded: "+e))) {
			if( !b.getClass().equals(BUILTIN.getClass())) {
				ret.add(b);
			}
		}
		// (a stable sort: fsh's own stays first among equals)
		ret.sort(Comparator.comparingInt(BrowserProvider::priority).reversed());
		return ret;
	}

	/** the shell's connections, for the browser (connect and disconnect, as the commands do) */
	static ShellConnections connections(ShellContext ctx) {
		return new ShellConnections() {
			@Override
			public java.util.Map<String, FileSource> list() {
				try {
					return ctx.console.connections();
				} catch (IOException e) {
					return java.util.Collections.emptyMap();
				}
			}

			@Override
			public boolean connect(String path, FileSource top) throws IOException {
				return ctx.console.connectAt(top, path);
			}

			@Override
			public void disconnect(String path) throws IOException {
				ctx.console.disconnect(path);
			}

			@Override
			public FileSource file(String path) throws IOException {
				return ctx.console.createFileSource(path);
			}
		};
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		boolean onePane = false, cd = false, list = false;
		String with = null;
		List<String> words = new ArrayList<>();
		for(int idx = 0; idx < args.length; idx++) {
			String a = ""+args[idx].getValue(ctx);
			if( !words.isEmpty() || !a.startsWith("-") || a.equals("-")) {
				words.add(a);
			} else if( a.equals("--")) {
				for(idx++; idx < args.length; idx++) {
					words.add(""+args[idx].getValue(ctx));
				}
			} else if( a.equals("--list")) {
				list = true;
			} else if( a.equals("--with")) {
				if( ++idx >= args.length ) {
					ctx.error(NAME+": --with: needs a browser's name");
					return 2;
				}
				with = ""+args[idx].getValue(ctx);
			} else if( a.startsWith("--with=")) {
				with = a.substring(7);
			} else if( a.length() > 1 && a.charAt(1) != '-' && a.substring(1).chars().allMatch(c -> c == '1' || c == 'c')) {
				onePane |= a.indexOf('1') > 0;
				cd |= a.indexOf('c') > 0;
			} else {
				ctx.error(NAME+": "+a+": invalid option");
				ctx.error(NAME+": usage: "+USAGE);
				return 2;
			}
		}
		if( cd && ctx.console.restricted ) {
			ctx.error(NAME+": -c: restricted");
			return 1;
		}
		if( words.size() > 2 ) {
			ctx.error(NAME+": usage: "+USAGE);
			return 2;
		}
		List<BrowserProvider> all = providers(ctx);
		BrowserProvider use = null;
		for(BrowserProvider b : all) {
			if( with != null ? b.name().equals(with) : b.available(ctx)) {
				use = b;
				break;
			}
		}
		if( list ) {
			for(BrowserProvider b : all) {
				ctx.stdout.println((b == use ? "* " : "  ")+b.name()+" - "+b.description()
						+(b.available(ctx) ? "" : " (cannot run here)"));
			}
			return 0;
		}
		if( use == null ) {
			if( with != null ) {
				ctx.error(NAME+": "+with+": no such browser (browse --list lists them)");
			} else {
				ctx.error(NAME+": no browser can run here (browse --list lists them)");
			}
			return 1;
		}
		if( with != null && !use.available(ctx)) {
			ctx.error(NAME+": "+with+": cannot run here");
			return 1;
		}
		FileSource left = words.size() > 0 ? ctx.console.createFileSource(words.get(0)) : ctx.console.getCurrentDirectory();
		Object home = ctx.getVariable("HOME");
		FileSource right = words.size() > 1 ? ctx.console.createFileSource(words.get(1))
				: home != null && !home.toString().isEmpty() ? ctx.console.createFileSource(home.toString()) : left;
		for(FileSource f : new FileSource[] {left, right}) {
			if( !f.isDirectory()) {
				ctx.error(NAME+": "+words.get(f == left ? 0 : 1)+": Not a directory");
				return 1;
			}
		}
		ctx.stdout.flush();
		ctx.stderr.flush();
		FileSource end = use.browse(ctx, new BrowseRequest(left, right, onePane, connections(ctx)));
		if( cd && end != null ) {
			FileSource dir = ctx.console.createFileSource(end.getAbsolutePath());
			if( dir.isDirectory() && dir.getFileSourceFactory().isSameFileSystem(end.getFileSourceFactory())) {
				ctx.console.setCurrentDirectory(dir);
			} else {
				ctx.error(NAME+": "+end.getAbsolutePath()+" isn't in the shell's file tree (connect it first)");
				return 1;
			}
		}
		return 0;
	}
}
