package us.bringardner.fsh.spi;

import java.io.IOException;
import java.util.List;

import us.bringardner.fsh.ShellContext;

/**
 * An editor fsh's edit command can use, in place of running $VISUAL or $EDITOR on a temporary copy
 * of the file. It works on the file where it is (a {@link us.bringardner.parley.files.FileSource}
 * on any connected file system), so it can lock it, check at the moment of saving that it hasn't
 * changed, and save it safely. A plugin jar lists its provider in
 * META-INF/services/us.bringardner.fsh.spi.EditorProvider and has Parley-Plugin-Api: 1 in its
 * manifest; fsh finds it in its plugin folders as it starts.
 *
 * edit --with NAME uses the provider of that name; otherwise it uses the first one that
 * {@link #available} here, in the order of the plugin folders (the system folder before the user's).
 * A provider has no priority: which plugin an installation prefers is the administrator's to
 * decide by what is installed where, and a person's by an alias (alias edit='edit --with NAME').
 * If none can run, edit does what it always did: $VISUAL, else $EDITOR, else vi.
 */
public interface EditorProvider {

	/** a short name for edit --with, such as scribe */
	String name();

	/** one line for edit --list */
	String description();

	/**
	 * It can run here, for this command: an editor on the terminal needs fsh's input and output to
	 * be that terminal (see {@link BrowserProvider#onTerminal}), so it is not available in a script
	 * or a pipe.
	 */
	boolean available(ShellContext ctx);

	/**
	 * Edit the file until the person is done. The provider saves it itself: fsh writes nothing.
	 * It says what it has to say on ctx's standard error.
	 *
	 * @return the command's status: 0 when it was saved or there was nothing to save, 1 when it was not
	 * @throws IOException if the file can't be read or written; edit shows the message and does not
	 * fall back to another editor
	 */
	int edit(ShellContext ctx, EditRequest request) throws IOException;

	/**
	 * Edit the files edit was given (edit a b c), in order. An editor that has an argument list, as vi
	 * has, overrides this to keep them in one session (:n goes to the next); the default edits them one
	 * after another, each in its own session, and when one can't be read or written says so and goes on
	 * with the rest.
	 *
	 * @return the highest status of any of them
	 */
	default int edit(ShellContext ctx, List<EditRequest> requests) throws IOException {
		int status = 0;
		for(EditRequest request : requests) {
			try {
				status = Math.max(status, edit(ctx, request));
			} catch (IOException e) {
				ctx.error("edit: "+request.name+": "+e.getMessage());
				status = Math.max(status, 1);
			}
		}
		return status;
	}
}
