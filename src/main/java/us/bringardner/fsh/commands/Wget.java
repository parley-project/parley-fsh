package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** wget, modeled on the wget command: downloads URLs (http, https and file), following redirects. */
public class Wget extends ShellCommand{
	static String name = "wget";
	static String help = "wget [OPTION]... URL...\n"
			+ "\tDownload each URL to a file named like the end of the URL (index.html for a directory).\n"
			+ "\t-O, --output-document=FILE   write to FILE instead (- is standard output)\n"
			+ "\t-q, --quiet                  say nothing\n"
			+ "\t-nv, --no-verbose            only errors and what is saved (the default here)\n"
			+ "\t--header=\"K: V\"              add a request header\n"
			+ "\t-U, --user-agent=STR         the User-Agent header\n"
			+ "\t-T, --timeout=SECONDS        give up after SECONDS\n"
			+ "\t--user=NAME --password=PASS  basic authentication\n"
			+ "\t--post-data=STRING           POST STRING\n"
			+ "\tExit status 0, 1 for an error, 4 for a network failure, 8 for a server error response.";

	public Wget() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		// -nv is two letters: take it out first
		java.util.List<String> words = new java.util.ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String w = ""+args[i].getValue(ctx);
			words.add(w.equals("-nv") ? "-v" : w);
		}
		Args a = Args.parseWords(words, "qv", "OUT", null, "output-document=O", "quiet:q", "no-verbose:v", "header=H", "user-agent=U", "timeout=T",
				"user=1", "password=2", "post-data=3");
		long timeout = a.number('T', 0);
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.isEmpty()) {
			ctx.stderr.println("wget: missing URL");
			ctx.stderr.println("Usage: wget [OPTION]... [URL]...");
			return 1;
		}
		boolean quiet = a.has('q');
		int ret = 0;
		for (String url : a.operands) {
			Http.Request r = new Http.Request();
			r.url = url;
			r.follow = true;
			r.timeoutMs = (int) (timeout*1000);
			r.connectTimeoutMs = r.timeoutMs;
			for (String h : a.values('H')) {
				int colon = h.indexOf(':');
				if( colon > 0 ) {
					r.headers.add(new String[] {h.substring(0, colon).trim(), h.substring(colon+1).trim()});
				}
			}
			if( a.value('U') != null ) {
				r.headers.add(new String[] {"User-Agent", a.value('U')});
			}
			if( a.value('1') != null ) {
				r.user = a.value('1')+":"+(a.value('2') == null ? "" : a.value('2'));
			}
			if( a.value('3') != null ) {
				r.method = "POST";
				r.body = a.value('3').getBytes(java.nio.charset.StandardCharsets.UTF_8);
				r.headers.add(new String[] {"Content-Type", "application/x-www-form-urlencoded"});
			}
			try {
				Http.Response resp = Http.execute(r);
				if( resp.code >= 400 ) {
					ctx.stderr.println("wget: "+Http.normalize(url)+": ERROR "+resp.code+": "+resp.message+".");
					ret = 8;
					continue;
				}
				String outName = a.value('O');
				if( outName == null ) {
					String path = new java.net.URL(resp.url).getPath();
					outName = path.endsWith("/") || path.isEmpty() ? "index.html" : path.substring(path.lastIndexOf('/')+1);
				}
				try (InputStream in = resp.body) {
					if( outName.equals("-")) {
						in.transferTo(ctx.stdout);
					} else {
						FileSource f = ctx.console.createFileSource(outName);
						long n;
						try (OutputStream os = f.getOutputStream()) {
							n = in.transferTo(os);
						}
						if( !quiet ) {
							ctx.stderr.println("'"+outName+"' saved ["+n+"]");
						}
					}
				}
			} catch (IOException e) {
				ctx.stderr.println("wget: "+Http.normalize(url)+": "+(e.getMessage() == null ? e.toString() : e.getMessage()));
				ret = e instanceof java.net.MalformedURLException ? 1 : 4;
			}
		}
		ctx.stdout.flush();
		return ret;
	}
}
