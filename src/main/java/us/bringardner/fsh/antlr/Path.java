package us.bringardner.fsh.antlr;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.parser.FileSourceShParser.PathContext;
import us.bringardner.parley.files.FileSource;
import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;

public class Path {
	ParserRuleContext context;
	
	public Path(ParserRuleContext ctx) {
		context = ctx;		
	}
	
	public Object getPath(ShellContext sc) throws IOException {
		Object ret = context.getText();
		if (context instanceof PathContext	) {
			
			ret = FileSourceShPreProcessorVisitorImpl.processString(""+ret, sc);
			if( ShellCommand.hasWildcard(""+ret)) {
				List<FileSource> list = ShellCommand.getFiles(sc, ""+ret);
				ret = list;
			}
			
		} else {
			// probably an ID
			ret = context.getText();
		}
		
		return ret;
	}
	
	public String toString() {
		return context.getText();
	}

}
