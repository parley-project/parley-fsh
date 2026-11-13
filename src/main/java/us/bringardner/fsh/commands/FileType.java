package us.bringardner.fsh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSource;

/** The file command (this class is FileType because a file is already something else): guesses what a file holds. */
public class FileType extends ShellCommand{
	static String name = "file";
	static String help = "file [-bi] FILE...\n"
			+ "\tGuess what kind of data each FILE holds, from its first bytes.\n"
			+ "\t-b, --brief        don't print the file name\n"
			+ "\t-i, --mime-type    print the MIME type instead (--mime-type is the same)";

	public FileType() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		Args a = Args.parse(ctx, args, "bi", "", false, "brief:b", "mime-type:i", "mime:i");
		if( a.error != null ) {
			return a.fail(ctx, name);
		}
		if( a.operands.isEmpty()) {
			ctx.stderr.println("Usage: file [-bi] file ...");
			return 1;
		}
		int ret = 0;
		for (String p : a.operands) {
			String [] d;
			if( p.equals("-")) {
				d = describe(ctx.stdin.readNBytes(4096), false);
			} else {
				FileSource f = ctx.console.createFileSource(p);
				if( !f.exists()) {
					d = new String[] {"cannot open `"+p+"' (No such file or directory)", "application/octet-stream"};
					ret = 1;
				} else if( f.isDirectory()) {
					d = new String[] {"directory", "inode/directory"};
				} else {
					try (InputStream in = f.getInputStream()) {
						d = describe(in.readNBytes(4096), f.length() > 4096);
					}
				}
			}
			String text = a.has('i') ? d[1] : d[0];
			ctx.stdout.println(a.has('b') ? text : p+": "+text);
		}
		ctx.stdout.flush();
		return ret;
	}

	private static boolean starts(byte [] b, int... magic) {
		if( b.length < magic.length ) {
			return false;
		}
		for (int i = 0; i < magic.length; i++) {
			if( (b[i] & 0xff) != magic[i] ) {
				return false;
			}
		}
		return true;
	}

	private static boolean startsText(byte [] b, String s) {
		return b.length >= s.length() && new String(b, 0, s.length(), StandardCharsets.ISO_8859_1).equals(s);
	}

	/** {description, mime type} */
	static String [] describe(byte [] b, boolean truncated) {
		if( b.length == 0 ) {
			return new String[] {"empty", "inode/x-empty"};
		}
		if( starts(b, 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a)) {
			return new String[] {"PNG image data", "image/png"};
		}
		if( starts(b, 0xff, 0xd8, 0xff)) {
			return new String[] {"JPEG image data", "image/jpeg"};
		}
		if( startsText(b, "GIF87a") || startsText(b, "GIF89a")) {
			return new String[] {"GIF image data, version "+new String(b, 3, 3, StandardCharsets.ISO_8859_1), "image/gif"};
		}
		if( startsText(b, "%PDF-")) {
			return new String[] {"PDF document", "application/pdf"};
		}
		if( starts(b, 'P', 'K', 3, 4)) {
			return new String[] {"Zip archive data", "application/zip"};
		}
		if( starts(b, 0x1f, 0x8b)) {
			return new String[] {"gzip compressed data", "application/gzip"};
		}
		if( startsText(b, "BZh")) {
			return new String[] {"bzip2 compressed data", "application/x-bzip2"};
		}
		if( starts(b, 0x7f, 'E', 'L', 'F')) {
			return new String[] {"ELF executable", "application/x-executable"};
		}
		if( starts(b, 'M', 'Z')) {
			return new String[] {"PE executable (MS-DOS or Windows)", "application/vnd.microsoft.portable-executable"};
		}
		if( starts(b, 0xca, 0xfe, 0xba, 0xbe)) {
			return new String[] {"compiled Java class data", "application/x-java-applet"};
		}
		if( starts(b, '7', 'z', 0xbc, 0xaf, 0x27, 0x1c)) {
			return new String[] {"7-zip archive data", "application/x-7z-compressed"};
		}
		if( b.length > 262 && new String(b, 257, 5, StandardCharsets.ISO_8859_1).equals("ustar")) {
			return new String[] {"POSIX tar archive", "application/x-tar"};
		}
		if( startsText(b, "RIFF") && b.length > 11 ) {
			String kind = new String(b, 8, 4, StandardCharsets.ISO_8859_1);
			return new String[] {"RIFF data, "+kind, kind.equals("WAVE") ? "audio/x-wav" : "application/octet-stream"};
		}
		if( startsText(b, "SQLite format 3")) {
			return new String[] {"SQLite 3.x database", "application/vnd.sqlite3"};
		}
		for (int i = 0; i < b.length; i++) {
			if( b[i] == 0 ) {
				return new String[] {"data", "application/octet-stream"};
			}
		}
		// text
		boolean ascii = true;
		for (byte x : b) {
			if( x < 0 ) {
				ascii = false;
				break;
			}
		}
		boolean utf8 = true;
		if( !ascii ) {
			try {
				// (a multi-byte character may be cut at the end of what was read)
				int len = b.length;
				if( truncated ) {
					while( len > 0 && (b[len-1] & 0xC0) == 0x80 ) {
						len--;
					}
					if( len > 0 && b[len-1] < 0 ) {
						len--;
					}
				}
				StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b, 0, len));
			} catch (CharacterCodingException e) {
				utf8 = false;
			}
		}
		String text = new String(b, utf8 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
		String head = text.trim();
		String kind, mime;
		if( head.startsWith("#!")) {
			String line = head.split("\n", 2)[0];
			String interp = line.substring(2).trim().replaceAll(".*/", "").replaceAll("^env ", "").split(" ")[0];
			kind = interp+" script";
			mime = "text/x-shellscript";
		} else if( head.startsWith("<?xml")) {
			kind = "XML document";
			mime = "text/xml";
		} else if( head.toLowerCase().startsWith("<!doctype html") || head.toLowerCase().startsWith("<html")) {
			kind = "HTML document";
			mime = "text/html";
		} else if( (head.startsWith("{") && head.endsWith("}")) || (head.startsWith("[") && head.endsWith("]")) && head.contains("\"")) {
			kind = "JSON data";
			mime = "application/json";
		} else {
			kind = ascii ? "ASCII text" : utf8 ? "Unicode text, UTF-8 text" : "ISO-8859 text";
			mime = "text/plain";
		}
		if( kind.contains("script") || kind.contains("document") || kind.contains("JSON")) {
			kind += ascii ? " text" : utf8 ? ", Unicode text, UTF-8 text" : "";
			kind = kind.replace("JSON data text", "JSON text data");
		}
		if( text.contains("\r\n")) {
			kind += ", with CRLF line terminators";
		}
		int longest = 0, cur = 0;
		for (int i = 0; i < text.length(); i++) {
			if( text.charAt(i) == '\n' ) {
				cur = 0;
			} else {
				longest = Math.max(longest, ++cur);
			}
		}
		if( longest > 300 ) {
			kind += ", with very long lines";
		}
		return new String[] {kind, mime+"; charset="+(ascii ? "us-ascii" : utf8 ? "utf-8" : "iso-8859-1")};
	}
}
