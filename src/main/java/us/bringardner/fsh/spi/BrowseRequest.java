package us.bringardner.fsh.spi;

import us.bringardner.parley.files.FileSource;

/** What browse asks a {@link BrowserProvider} to show. */
public final class BrowseRequest {

	/** the folder on the left (or the only one) */
	public final FileSource left;
	/** the folder on the right */
	public final FileSource right;
	/** start with one pane (browse -1) */
	public final boolean onePane;

	public BrowseRequest(FileSource left, FileSource right, boolean onePane) {
		this.left = left;
		this.right = right;
		this.onePane = onePane;
	}
}
