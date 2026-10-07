package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentPartContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignmentContext;
import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.shell.FsshList;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Arithmetic;
import us.bringardner.shell.antlr.Expression;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl;
import us.bringardner.shell.antlr.Statement;
import us.bringardner.shell.antlr.signal.ExitException;

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
		// the status is that of the last $( ) in the values (x=$(false) is 1), or 0 (a value may
		// read $?, so it is not reset first)
		long before = ctx.console.substitutionCount();
		for(AssignmentContext assignment : actx.assignment()) {
			name = assignment.id1.getText();
			if( ctx.console.isReadonly(name)) {
				// as in bash: an error, which ends a script
				ctx.stderr.println(name+": readonly variable");
				if( !ctx.console.isInteractive ) {
					throw new ExitException(ctx, 1);
				}
				return 1;
			}
			Object val = valueOf(assignment, ctx);
			boolean append = assignment.op != null && assignment.op.getType() == FileSourceShParser.PLUS_EQ;
			ParserRuleContext index = assignment.associative_index() != null ? assignment.associative_index() : assignment.array_index();
			if( index != null ) {
				setElement(ctx, name, index, val, append);
				continue;
			}
			val = combine(ctx, name, ctx.getVariable(name), val, append);
			if( assignment.LOCAL()!=null) {
				ctx.setLocalVariable(name, val);
			} else {
				ctx.setVariable(name, val);
			}
		}
		ret = ctx.console.substitutionCount() != before ? ctx.console.getLastExitCode() : 0;
		return ret;
	}

	/**
	 * The new value: x=v, x+=v (text appended, or a number added for declare -i), a+=(v w) (appended
	 * to the array).
	 */
	private static Object combine(ShellContext ctx, String name, Object old, Object val, boolean append) {
		if( val instanceof List<?> ) {
			if( !append ) {
				return val;
			}
			FsshList list = new FsshList();
			if( old instanceof FsshList ) {
				// keep the indexes (a[5]=z; a+=(w) puts w at 6)
				for(int idx : ((FsshList) old).getIndexes()) {
					list.set(idx, ((FsshList) old).get(idx));
				}
			} else if( old instanceof List<?> ) {
				list.addAll((List<?>) old);
			} else if( old != null ) {
				list.add(old);
			}
			list.addAll((List<?>) val);
			return list;
		}
		if( ctx.console.isInteger(name)) {
			Number n = Arithmetic.evaluate(""+val, ctx);
			return append ? Arithmetic.evaluate(""+(old == null ? 0 : old)+"+("+n+")", ctx) : n;
		}
		return append ? (old == null ? "" : ""+old)+val : val;
	}

	/**
	 * a[i]=v (i is arithmetic) or m[key]=v for an associative array.
	 */
	private static void setElement(ShellContext ctx, String name, ParserRuleContext index, Object val, boolean append) {
		String raw = index.getText();
		raw = raw.substring(1, raw.length()-1);
		Object cur = ctx.getVariable(name);
		Object key;
		if( cur instanceof java.util.Map<?,?> ) {
			key = keyText(raw, ctx);
		} else {
			int idx = Arithmetic.expandAndEvaluate(raw, ctx).intValue();
			if( idx < 0 && cur instanceof FsshList ) {
				List<Integer> indexes = ((FsshList) cur).getIndexes();
				idx += indexes.isEmpty() ? 0 : indexes.get(indexes.size()-1)+1;
			}
			key = idx;
		}
		if( append ) {
			Object old = cur instanceof List<?> && key instanceof Integer ? ((List<?>) cur).get((Integer) key)
					: cur instanceof java.util.Map<?,?> ? ((java.util.Map<?,?>) cur).get(key) : null;
			val = (old == null ? "" : ""+old)+val;
		}
		ctx.setVariable(name, key, val);
	}

	/** an associative array key as written: quotes removed, $x expanded */
	private static String keyText(String raw, ShellContext ctx) {
		if( raw.length() >= 2 && (raw.startsWith("\"") && raw.endsWith("\"") || raw.startsWith("'") && raw.endsWith("'"))) {
			String inner = raw.substring(1, raw.length()-1);
			return raw.startsWith("'") ? inner : FileSourceShPreProcessorVisitorImpl.processString(inner, ctx,
					FileSourceShPreProcessorVisitorImpl.Quoting.DOUBLE_QUOTED);
		}
		return FileSourceShPreProcessorVisitorImpl.processString(raw, ctx);
	}

	/**
	 * The value of one assignment: a=(...) is a list, a= is empty, and a value with one part keeps its type.
	 */
	public static Object valueOf(AssignmentContext actx, ShellContext ctx) throws IOException {

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
			case FileSourceShParser.TILDE: {
				// x=~ is the home directory
				Object home = ctx.getVariable("HOME");
				return home == null ? text : home;
			}
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

	@Override
	protected boolean errexitApplies() {
		return true;
	}
}
