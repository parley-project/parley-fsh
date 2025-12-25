package us.bringardner.shell.antlr.statement;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.shell.ShellContext;
import us.bringardner.shell.ShellContext.LoopControl;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.FsshException;

public abstract class LoopStatement extends Statement{

	public static class LoopControlException extends FsshException {
		private static final long serialVersionUID = 1L;
		public  LoopControlException(LoopControl type, int i) {
			howFar = i;
			this.type = type;
		}

		public int howFar=0;
		ShellContext.LoopControl type;

	}

	public LoopStatement(ParserRuleContext context) {
		super(context);

	}



}
