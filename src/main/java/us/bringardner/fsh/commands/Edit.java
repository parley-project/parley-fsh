/**
 * <PRE>
 *
 * Copyright Tony Bringardner 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *	@author Tony Bringardner
 */
package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.spi.EditRequest;
import us.bringardner.fsh.spi.EditorProvider;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.viewer.tui.EditSession;
import us.bringardner.parley.files.viewer.tui.FileOps;

/**
 * edit: any file, on any connected file system. An editor a plugin provides
 * ({@link EditorProvider}) edits it where it is; without one, $VISUAL / $EDITOR edits a copy.
 */
public class Edit extends ShellCommand {

	static final String NAME = "edit";
	static final String USAGE = "edit [--with NAME] FILE...  or  edit --list";
	static final String HELP = USAGE+"\n"
			+ "\tEdit each FILE, one after another (a pattern such as *.xml names every file it matches), with an editor a plugin provides, which works on the file where it is, or, when\n"
			+ "\tnone can run here (a script, a pipe, no plugin), with $VISUAL, else $EDITOR, else vi. A\n"
			+ "\tfile on a connected remote file system (connect) is then copied to a temporary local file\n"
			+ "\tfor the editor and copied back if it was saved; if it was changed on the server meanwhile,\n"
			+ "\tedit asks before replacing it. A file that doesn't exist is created when it's saved. If the\n"
			+ "\tedited copy can't be copied back (the editor failed, the copy failed, or you chose not to\n"
			+ "\treplace the file), edit keeps it and says where.\n"
			+ "\t--with NAME  use this editor plugin (an alias makes it the default: alias edit='edit --with NAME')\n"
			+ "\t--list       list the editor plugins, which can run here, and which is used";

	public Edit() {
		super(NAME, HELP);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		String[] all = FshArgs.words(args, ctx);
		String with = null;
		boolean list = false;
		int first = 0;
		while( first < all.length && all[first].startsWith("-") && !all[first].equals("-")) {
			String a = all[first];
			if( a.equals("--")) {
				first++;
				break;
			} else if( a.equals("--list")) {
				list = true;
			} else if( a.equals("--with")) {
				if( ++first >= all.length ) {
					ctx.error(NAME+": --with: needs an editor's name");
					return 2;
				}
				with = all[first];
			} else if( a.startsWith("--with=")) {
				with = a.substring(7);
			} else {
				ctx.error(NAME+": "+a+": invalid option");
				ctx.error(NAME+": usage: "+USAGE);
				return 2;
			}
			first++;
		}
		String[] words = java.util.Arrays.copyOfRange(all, first, all.length);
		List<EditorProvider> editors = providers(ctx);
		EditorProvider use = choose(ctx, editors, with);
		if( list ) {
			if( words.length > 0 ) {
				ctx.error(NAME+": usage: "+USAGE);
				return 2;
			}
			for(EditorProvider e : editors) {
				ctx.stdout.println((e == use ? "* " : "  ")+e.name()+" - "+e.description()
						+(e.available(ctx) ? "" : " (cannot run here)"));
			}
			if( editors.isEmpty()) {
				ctx.stdout.println("  (no editor plugin: edit uses $VISUAL, else $EDITOR, else vi)");
			}
			return 0;
		}
		if( words.length == 0 ) {
			ctx.error(NAME+": usage: "+USAGE);
			return 2;
		}
		if( with != null ) {
			if( use == null ) {
				ctx.error(NAME+": "+with+": no such editor (edit --list lists them)");
				return 1;
			}
			if( !use.available(ctx)) {
				ctx.error(NAME+": "+with+": cannot run here");
				return 1;
			}
		}
		// each name is resolved first: a pattern names every file it matches
		int status = 0;
		List<String> names = new ArrayList<>();
		List<FileSource> files = new ArrayList<>();
		for(String word : words) {
			List<FileSource> found = getFiles(ctx, word);
			if( found.size() == 1 ) {
				names.add(word);
				files.add(found.get(0));
			} else {
				ctx.error(NAME+": "+word+": names "+found.size()+" files");
				status = 2;
			}
		}
		if( use != null && files.size() > 1 ) {
			// (an editor with an argument list, vi's :n, gets them all at once)
			List<EditRequest> requests = new ArrayList<>();
			for(int i = 0; i < files.size(); i++) {
				requests.add(new EditRequest(files.get(i), names.get(i)));
			}
			ctx.stdout.flush();
			ctx.stderr.flush();
			try {
				return Math.max(status, use.edit(ctx, requests));
			} catch (IOException e) {
				ctx.error(NAME+": "+e.getMessage());
				return Math.max(status, 1);
			} catch (RuntimeException | LinkageError e) {
				ctx.error(NAME+": "+use.name()+": "+e);
				return Math.max(status, 1);
			}
		}
		// each file is edited in turn, as vi a b does; one that fails is reported and the rest are still edited
		for(int i = 0; i < files.size(); i++) {
			status = Math.max(status, editOne(ctx, use, files.get(i), names.get(i)));
		}
		return status;
	}

	/** edit one file, with the editor plugin if there is one, else $VISUAL / $EDITOR */
	private int editOne(ShellContext ctx, EditorProvider use, FileSource file, String name) throws IOException {
		if( use != null ) {
			ctx.stdout.flush();
			ctx.stderr.flush();
			try {
				// (the editor saves the file itself, and there is no falling back to another editor
				// once it has started: what it did is shown, and the file is as it left it)
				return use.edit(ctx, new EditRequest(file, name));
			} catch (IOException e) {
				ctx.error(NAME+": "+name+": "+e.getMessage());
				return 1;
			} catch (RuntimeException | LinkageError e) {
				ctx.error(NAME+": "+use.name()+": "+e);
				return 1;
			}
		}
		// (a file under a connection reports the connected file system, so a remote one is staged)
		FileSource real = file;
		try (EditSession session = EditSession.open(real)) {
			StringBuilder cmd = new StringBuilder();
			for(String part : EditSession.editorCommand(session.getLocalFile())) {
				if( cmd.length() > 0 ) {
					cmd.append(' ');
				}
				cmd.append(FshTerminal.quote(part));
			}
			// fsh starts the editor, so it gets the terminal as any program would
			int code = ctx.console.executeScript(ctx, cmd.toString());
			if( !session.isRemote()) {
				if( code != 0 ) {
					ctx.error(NAME+": the editor exited with "+code);
				}
				return code;
			}
			if( !session.isChanged()) {
				if( code != 0 ) {
					ctx.error(NAME+": the editor exited with "+code+"; "+name+" was not copied back");
					return code;
				}
				ctx.stderr.println(NAME+": "+name+": not changed");
				return 0;
			}
			if( code != 0 ) {
				// what was typed is not thrown away because the editor said it failed
				ctx.error(NAME+": the editor exited with "+code+"; "+name+" was not copied back, your edited copy is kept in "+session.keepCopy());
				return code;
			}
			boolean changedThere = session.isRemoteChanged();
			if( changedThere ) {
				String answer = promptAndGetReponse(ctx.stderr, ctx.stdin,
						NAME+": "+name+" was changed on the server while you edited it. Replace it? [y/N] ");
				if( answer == null || !answer.trim().toLowerCase().startsWith("y")) {
					ctx.stderr.println(NAME+": your edit was not saved, your edited copy is kept in "+session.keepCopy());
					return 1;
				}
			}
			try {
				session.writeBack(changedThere);
			} catch (IOException e) {
				ctx.error(NAME+": "+name+": "+e.getMessage()+"; your edited copy is kept in "+session.keepCopy());
				return 1;
			}
			ctx.stderr.println(NAME+": saved "+name+" to "+FileOps.describe(real.getFileSourceFactory()));
			return 0;
		} catch (IOException e) {
			ctx.error(NAME+": "+name+": "+e.getMessage());
			return 1;
		}
	}

	/** the editors the plugins provide, in the order of the plugin folders (the system folder first) */
	static List<EditorProvider> providers(ShellContext ctx) {
		return us.bringardner.fsh.Console.pluginProviders(EditorProvider.class,
				e -> ctx.error(NAME+": an editor cannot be loaded: "+e));
	}

	/** the one --with names (null if none has the name), else the first that can run here (null if none) */
	static EditorProvider choose(ShellContext ctx, List<EditorProvider> editors, String with) {
		for(EditorProvider e : editors) {
			if( with != null ? e.name().equals(with) : e.available(ctx)) {
				return e;
			}
		}
		return null;
	}
}
