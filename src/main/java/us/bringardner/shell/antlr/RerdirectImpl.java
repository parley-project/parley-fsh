package us.bringardner.shell.antlr;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.RedirectContext;
import us.bringardner.filesource.sh.FileSourceShParser.Redirect_oneContext;

/**
 * The redirects of a command or group, in the order they are written (they are applied in that
 * order: > out 2>&1 sends both to out, 2>&1 > out only stdout).
 */
public class RerdirectImpl {
	public final List<Redirect_oneContext> redirects = new ArrayList<>();

	/**
	 * Not bash: the last argument, when written right before the first redirect with no space
	 * ($fid>&- or $fid<> file), is its file descriptor if its value is a number.
	 */
	public ArgumentContext fdWord;

	/**
	 * @return the redirects anywhere among these children (not inside nested statements), or null if there are none
	 */
	public static RerdirectImpl find(List<ParseTree> kids) {
		RerdirectImpl ret = new RerdirectImpl();
		if( kids != null ) {
			for (int idx = 0; idx < kids.size(); idx++) {
				ParseTree kid = kids.get(idx);
				if( kid instanceof RedirectContext ) {
					List<Redirect_oneContext> list = ((RedirectContext) kid).redirect_one();
					if( ret.redirects.isEmpty() && idx > 0 && kids.get(idx-1) instanceof ArgumentContext
							&& !list.isEmpty() && list.get(0).fd == null ) {
						ret.fdWord = (ArgumentContext) kids.get(idx-1);
					}
					ret.redirects.addAll(list);
				}
			}
		}
		return ret.redirects.isEmpty() ? null : ret;
	}
}
