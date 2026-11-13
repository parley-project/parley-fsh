package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What curl and wget share: an HTTP(S) or file: request that follows redirects when asked. */
final class Http {

	private Http() {
	}

	static class Request {
		String url;
		String method = "GET";
		List<String []> headers = new ArrayList<>();
		byte [] body;
		boolean follow;
		int maxRedirects = 20;
		int connectTimeoutMs, timeoutMs;
		String user;
	}

	static class Response {
		/** the URL that answered, after the redirects */
		String url;
		int code;
		String message = "";
		/** the status line and the headers, as they came (the keys keep their case; the status line has a null key) */
		Map<String, List<String>> headers = new LinkedHashMap<>();
		InputStream body;
		/** how many redirects were followed */
		int redirects;
	}

	static String normalize(String url) {
		return url.matches("(?i)^[a-z][a-z0-9+.-]*:.*") ? url : "http://"+url;
	}

	static Response execute(Request r) throws IOException {
		String url = normalize(r.url);
		String method = r.method;
		byte [] body = r.body;
		Response resp = new Response();
		for (int hops = 0; ; hops++) {
			URL u = new URL(url);
			URLConnection raw = u.openConnection();
			if( r.connectTimeoutMs > 0 ) {
				raw.setConnectTimeout(r.connectTimeoutMs);
			}
			if( r.timeoutMs > 0 ) {
				raw.setReadTimeout(r.timeoutMs);
			}
			if( !(raw instanceof HttpURLConnection)) {
				// file: and the like
				resp.url = url;
				resp.code = 200;
				resp.body = raw.getInputStream();
				return resp;
			}
			HttpURLConnection c = (HttpURLConnection) raw;
			c.setInstanceFollowRedirects(false);
			c.setRequestMethod(method);
			boolean hasUa = false;
			for (String [] h : r.headers) {
				c.setRequestProperty(h[0], h[1]);
				if( h[0].equalsIgnoreCase("User-Agent")) {
					hasUa = true;
				}
			}
			if( !hasUa ) {
				c.setRequestProperty("User-Agent", "fsh");
			}
			if( r.user != null ) {
				c.setRequestProperty("Authorization", "Basic "+Base64.getEncoder().encodeToString(r.user.getBytes(StandardCharsets.UTF_8)));
			}
			if( body != null ) {
				c.setDoOutput(true);
				c.setFixedLengthStreamingMode(body.length);
				try (OutputStream os = c.getOutputStream()) {
					os.write(body);
				}
			}
			int code = c.getResponseCode();
			String location = c.getHeaderField("Location");
			if( r.follow && location != null && (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && hops < r.maxRedirects ) {
				url = new URL(u, location).toString();
				if( code == 303 || ((code == 301 || code == 302) && !method.equals("HEAD"))) {
					method = method.equals("HEAD") ? "HEAD" : "GET";
					body = null;
				}
				resp.redirects++;
				c.disconnect();
				continue;
			}
			resp.url = url;
			resp.code = code;
			resp.message = c.getResponseMessage() == null ? "" : c.getResponseMessage();
			for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
				resp.headers.put(e.getKey(), e.getValue());
			}
			resp.body = code >= 400 ? c.getErrorStream() : c.getInputStream();
			if( resp.body == null ) {
				resp.body = new java.io.ByteArrayInputStream(new byte[0]);
			}
			return resp;
		}
	}

	/** the exit status curl gives for an exception */
	static int curlStatus(IOException e) {
		if( e instanceof java.net.UnknownHostException ) {
			return 6;
		}
		if( e instanceof java.net.ConnectException ) {
			return 7;
		}
		if( e instanceof java.net.SocketTimeoutException ) {
			return 28;
		}
		if( e instanceof java.net.MalformedURLException ) {
			return e.getMessage() != null && e.getMessage().startsWith("unknown protocol") ? 1 : 3;
		}
		if( e instanceof javax.net.ssl.SSLException ) {
			return 60;
		}
		return 56;
	}
}
