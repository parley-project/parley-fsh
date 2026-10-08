package us.bringardner.fsh.antlr.statement;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellContext.LoopControl;
import us.bringardner.fsh.antlr.Statement;
import us.bringardner.fsh.antlr.signal.FshException;

public abstract class LoopStatement extends Statement{

	public static class LoopControlException extends FshException {
		private static final long serialVersionUID = 1L;
		public  LoopControlException(LoopControl type, int i) {
			howFar = i;
			this.type = type;
		}

		public int howFar=0;
		public ShellContext.LoopControl type;

	}

	public LoopStatement(ParserRuleContext context) {
		super(context);

	}



}
