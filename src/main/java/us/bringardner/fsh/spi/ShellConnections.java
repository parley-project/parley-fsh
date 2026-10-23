package us.bringardner.fsh.spi;

import java.io.IOException;
import java.util.Map;

import us.bringardner.parley.files.FileSource;

/**
 * The shell's connections (what connect and disconnect make and remove), for a plugin: a browser
 * connects a file system the person opens in it here, so it stays after the browser ends and the
 * shell can use it.
 */
public interface ShellConnections {

	/** the path each connection is at (/mem), and the top folder of the file system connected there */
	Map<String, FileSource> list();

	/**
	 * Connect top (the top folder of a connected file system) at path, as connect does.
	 * @return false if something is already connected at path
	 */
	boolean connect(String path, FileSource top) throws IOException;

	/** Disconnect what is at path, as disconnect does: its file system is closed if nothing else uses it. */
	void disconnect(String path) throws IOException;

	/** the file at path in the shell's file tree (/mem/x) */
	FileSource file(String path) throws IOException;
}
