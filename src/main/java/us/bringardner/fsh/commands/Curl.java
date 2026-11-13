package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** curl, modeled on the curl command: transfers a URL (http, https and file). There is no progress meter. */
public class Curl extends ShellCommand{
	static String name = "curl";
	static String help = "curl [OPTIONS] URL...\n"
			+ "\tTransfer the URL (http, https or file) to standard output.\n"
			+ "\t-o, --output FILE      write to FILE\n"
			+ "\t-O, --remote-name      write to a file named like the end of the URL\n"
			+ "\t-s, --silent           no error messages either unless -S\n"
			+ "\t-S, --show-error       with -s, still show errors\n"
			+ "\t-L, --location         follow redirects\n"
			+ "\t-f, --fail             no output and exit status 22 for an HTTP error (400 and up)\n"
			+ "\t-X, --request METHOD   the method (GET, POST, PUT, DELETE ...)\n"
			+ "\t-H, --header \"K: V\"    add a request header (repeatable)\n"
			+ "\t-d, --data DATA        POST DATA (@file reads it from a file; repeated -d are joined with &)\n"
			+ "\t    --data-binary DATA like -d, with the newlines of a file kept\n"
			+ "\t-T, --upload-file FILE PUT FILE\n"
			+ "\t-I, --head             fetch the headers only\n"
			+ "\t-i, --include          show the response headers with the output\n"
			+ "\t-v, --verbose          show the request and response headers on standard error\n"
			+ "\t-u, --user USER:PASS   basic authentication\n"
			+ "\t-A, --user-agent STR   the User-Agent header\n"
			+ "\t-m, --max-time SEC     give up after SEC seconds\n"
			+ "\t    --connect-timeout SEC   give up connecting after SEC seconds";

	public Curl() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "sSLfIivOkG", "oXHdTuAmeb", false, "output=o", "remote-name:O", "silent:s", "show-error:S", "location:L", "fail:f",
				"request=X", "header=H", "data=d", "data-ascii=d", "data-binary=D", "upload-file=T", "head:I", "include:i", "verbose:v", "user=u",
				"user-agent=A", "max-time=m", "connect-timeout=c", "insecure:k");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.has('k')) {
			ctx.stderr.println("curl: -k/--insecure is not supported");
			return 2;
		}
		if( a.operands.isEmpty()) {
			ctx.stderr.println("curl: try 'curl --help' for more information");
			return 2;
		}
		boolean silent = a.has('s') && !a.has('S');
		Http.Request base = new Http.Request();
		base.follow = a.has('L');
		base.user = a.value('u');
		long maxTime = a.number('m', 0), connect = a.number('c', 0);
		if( a.error != null ) {
			ctx.stderr.println("curl: option -m: expected a proper numerical parameter");
			return 2;
		}
		base.timeoutMs = (int) (maxTime*1000);
		base.connectTimeoutMs = (int) (connect*1000);
		for (String h : a.values('H')) {
			int colon = h.indexOf(':');
			if( colon > 0 ) {
				base.headers.add(new String[] {h.substring(0, colon).trim(), h.substring(colon+1).trim()});
			}
		}
		if( a.value('A') != null ) {
			base.headers.add(new String[] {"User-Agent", a.value('A')});
		}
		List<String> data = a.values('d');
		List<String> binary = a.values('D');
		if( !data.isEmpty() || !binary.isEmpty()) {
			StringBuilder body = new StringBuilder();
			for (String d : data) {
				if( body.length() > 0 ) {
					body.append('&');
				}
				body.append(d.startsWith("@") ? new String(TextFiles.readAll(ctx, name, d.substring(1)) == null ? new byte[0] : TextFiles.readAll(ctx, name, d.substring(1)), StandardCharsets.UTF_8).replaceAll("[\r\n]", "") : d);
			}
			for (String d : binary) {
				if( body.length() > 0 ) {
					body.append('&');
				}
				body.append(d.startsWith("@") ? new String(TextFiles.readAll(ctx, name, d.substring(1)), StandardCharsets.UTF_8) : d);
			}
			base.body = body.toString().getBytes(StandardCharsets.UTF_8);
			base.method = "POST";
			boolean hasType = false;
			for (String [] h : base.headers) {
				hasType |= h[0].equalsIgnoreCase("Content-Type");
			}
			if( !hasType ) {
				base.headers.add(new String[] {"Content-Type", "application/x-www-form-urlencoded"});
			}
		}
		if( a.value('T') != null ) {
			byte [] up = TextFiles.readAll(ctx, name, a.value('T'));
			if( up == null ) {
				return 26;
			}
			base.body = up;
			base.method = "PUT";
		}
		if( a.has('I')) {
			base.method = "HEAD";
		}
		if( a.value('X') != null ) {
			base.method = a.value('X').toUpperCase();
		}

		int ret = 0;
		for (String url : a.operands) {
			Http.Request r = new Http.Request();
			r.url = url;
			r.method = base.method;
			r.headers = base.headers;
			r.body = base.body;
			r.follow = base.follow;
			r.user = base.user;
			r.timeoutMs = base.timeoutMs;
			r.connectTimeoutMs = base.connectTimeoutMs;
			try {
				if( a.has('v')) {
					ctx.stderr.println("> "+r.method+" "+Http.normalize(url));
					for (String [] h : r.headers) {
						ctx.stderr.println("> "+h[0]+": "+h[1]);
					}
				}
				Http.Response resp = Http.execute(r);
				if( a.has('v') || a.has('i') || a.has('I')) {
					StringBuilder hdr = new StringBuilder();
					for (Map.Entry<String, List<String>> e : resp.headers.entrySet()) {
						for (String v : e.getValue()) {
							hdr.append(e.getKey() == null ? v : e.getKey()+": "+v).append("\r\n");
						}
					}
					hdr.append("\r\n");
					if( a.has('v')) {
						ctx.stderr.print(hdr.toString().replaceAll("(?m)^", "< ").replaceAll("< $", ""));
					} else {
						ctx.stdout.print(hdr);
					}
				}
				if( a.has('f') && resp.code >= 400 ) {
					if( !silent ) {
						ctx.stderr.println("curl: (22) The requested URL returned error: "+resp.code);
					}
					ret = 22;
					continue;
				}
				String outName = a.value('o');
				if( a.has('O')) {
					String path = new java.net.URL(Http.normalize(url)).getPath();
					outName = path.endsWith("/") || path.isEmpty() ? "index.html" : path.substring(path.lastIndexOf('/')+1);
				}
				try (InputStream in = resp.body) {
					if( a.has('I')) {
						continue;
					}
					if( outName != null && !outName.equals("-")) {
						FileSource f = ctx.console.createFileSource(outName);
						try (OutputStream os = f.getOutputStream()) {
							in.transferTo(os);
						}
					} else {
						in.transferTo(ctx.stdout);
					}
				}
			} catch (IOException e) {
				int status = Http.curlStatus(e);
				if( !silent ) {
					ctx.stderr.println("curl: ("+status+") "+(e instanceof java.net.UnknownHostException ? "Could not resolve host: "+e.getMessage() : e.getMessage() == null ? e.toString() : e.getMessage()));
				}
				ret = status;
			}
		}
		ctx.stdout.flush();
		return ret;
	}
}
