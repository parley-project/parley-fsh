package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentPartContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignmentContext;
import us.bringardner.shell.FsshList;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.Statement;

public class AssignStatement extends Statement{

	String name ;
	public AssignStatement(ParserRuleContext context) {
		super(context);
	}

	public String getName() {
		return name;
	}

	/*


assignStatement
    : LOCAL? id1=ID EQ boolean
    | LOCAL? id1=ID EQ string
    | LOCAL? id1=ID EQ id2=ID
    | LOCAL? id1=ID EQ variable
    | LOCAL? id1=ID EQ expression
    | LOCAL? id1=ID EQ mathExpression
    | LOCAL? id1=ID EQ parameter
    
	 */
	@Override
	protected int execute(ShellContext ctx) throws IOException {
		int ret = 0;
		AssignStatementContext actx = (AssignStatementContext) getContext();
		AssignmentContext assignment = actx.assignment();
		name = assignment.id1.getText();
		Object val = getValue(ctx);
		
		
		
		if( assignment.LOCAL()!=null) {
			ctx.setLocalVariable(name, val);
		} else {
			ctx.setVariable(name, val);
		}
		return ret;
	}

	public Object getValue(ShellContext ctx) throws IOException {
		AssignStatementContext aactx = (AssignStatementContext) getContext();
		AssignmentContext actx = aactx.assignment();
		
		Object val = null;
		
		if( actx.arrayInitializer()!=null) {
			List<Object> list = new FsshList();
			for(ArgumentContext ac : actx.arrayInitializer().argument_list().argument()) {
				Argument arg = new Argument(ac);
				Object v = arg.getValue(ctx);
				list.add(v);
			}
			val = list;
		} else if( actx.value == null ) {
			// x=
			val = "";
		} else {
			List<ArgumentPartContext> parts = actx.value.argumentPart();
			if( parts.size() == 1 ) {
				val = typedValue(parts.get(0), ctx);
			} else {
				// several parts make text, as in bash
				val = new Argument(actx.value).getValue(ctx);
			}
		}

		return val;
	}

	/**
	 * A value with one part keeps its type, so x=1 is a number and y=$x is whatever x holds.
	 */
	private static Object typedValue(ArgumentPartContext part, ShellContext ctx) {
		if( part.literal != null ) {
			String text = part.literal.getText();
			switch (part.literal.getType()) {
			case FileSourceShParser.NUMBER:
				Number number = parseNumber(text);
				return number == null ? text : number;
			case FileSourceShParser.TRUE: return true;
			case FileSourceShParser.FALSE: return false;
			default: return text;
			}
		} else if( part.argVariable() != null ) {
			// y=$x keeps x's value (and type); y is empty if x is unset
			Object val = ctx.getVariable(part.argVariable());
			return val == null ? ctx.expand(null, part.argVariable().getText()) : val;
		}
		return Argument.getValue(part, ctx);
	}

	// the same types Expression uses
	private static Number parseNumber(String text) {
		try {
			return text.indexOf('.') >= 0 ? (Number)Double.parseDouble(text) : (Number)Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
