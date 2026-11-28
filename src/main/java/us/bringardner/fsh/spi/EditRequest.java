package us.bringardner.fsh.spi;

import us.bringardner.parley.files.FileSource;

/** What edit asks an {@link EditorProvider} to edit. */
public class EditRequest {

	/** the file as the shell resolved it, on whatever file system it is: it may not exist yet */
	public final FileSource file;
	/** the name as it was typed, for messages */
	public final String name;

	public EditRequest(FileSource file, String name) {
		this.file = file;
		this.name = name;
	}
}
