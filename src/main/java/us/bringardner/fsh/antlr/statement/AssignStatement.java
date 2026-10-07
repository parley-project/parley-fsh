package us.bringardner.fsh.antlr.statement;

import java.io.IOException;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.parser.FileSourceShParser.ArgumentContext;
import us.bringardner.fsh.parser.FileSourceShParser.ArgumentPartContext;
import us.bringardner.fsh.parser.FileSourceShParser.AssignStatementContext;
import us.bringardner.fsh.parser.FileSourceShParser.AssignmentContext;
import us.bringardner.fsh.parser.FileSourceShParser;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.Glob;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Argument;
import us.bringardner.fsh.antlr.Arithmetic;
import us.bringardner.fsh.antlr.Expression;
import us.bringardner.fsh.antlr.FileSourceShPreProcessorVisitorImpl;
import us.bringardner.fsh.antlr.Statement;
import us.bringardner.fsh.antlr.signal.ExitException;

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
		// an assignment alone leaves $_ empty, as in bash
		ctx.console.lastArgument = "";
		AssignStatementContext actx = (AssignStatementContext) getContext();
		// local a=1 b=2: local applies to every name (the grammar puts it on the first)
		boolean local = !actx.assignment().isEmpty() && actx.assignment(0).LOCAL() != null;
		if( !ctx.isInFunction()) {
			for(AssignmentContext a : actx.assignment()) {
				if( a.LOCAL() != null ) {
					ctx.stderr.println("local: can only be used in a function");
					return 1;
				}
			}
		}
		// the status is that of the last $( ) in the values (x=$(false) is 1), or 0 (a value may
		// read $?, so it is not reset first)
		long before = ctx.console.substitutionCount();
		// local is a command: all its words are expanded before any is assigned (local w=1
		// p=$w gives p the old w); a=1 b=$a assigns as it goes
		java.util.List<Object> localValues = null;
		if( local ) {
			localValues = new java.util.ArrayList<>();
			for(AssignmentContext assignment : actx.assignment()) {
				localValues.add(valueOf(assignment, ctx));
			}
		}
		int position = 0;
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
			Object val = localValues != null ? localValues.get(position++) : valueOf(assignment, ctx);
			boolean append = assignment.op != null && assignment.op.getType() == FileSourceShParser.PLUS_EQ;
			ParserRuleContext index = assignment.associative_index() != null ? assignment.associative_index() : assignment.array_index();
			if( index != null ) {
				setElement(ctx, name, index, val, append);
				continue;
			}
			val = combine(ctx, name, ctx.getVariable(name), val, append);
			if( local ) {
				ctx.setLocalVariable(name, val);
			} else {
				ctx.setVariable(name, val);
			}
		}
		ret = ctx.console.substitutionCount() != before ? ctx.console.getLastExitCode() : 0;
		for(AssignmentContext a : actx.assignment()) {
			if( a.LOCAL() != null ) {
				// local x=$(false) is 0: local's own status hides the substitution's (as in bash)
				ret = 0;
			}
		}
		return ret;
	}

	/**
	 * The new value: x=v, x+=v (text appended, or a number added for declare -i), a+=(v w) (appended
	 * to the array).
	 */
	private static Object combine(ShellContext ctx, String name, Object old, Object val, boolean append) {
		if( val instanceof java.util.Map<?,?> map && append && old instanceof java.util.Map<?,?> before ) {
			// m+=([k]=v): added to (or replacing in) the existing keys
			java.util.Map<Object,Object> ret = new java.util.TreeMap<>(before);
			ret.putAll(map);
			return ret;
		}
		if( val instanceof List<?> ) {
			if( !append ) {
				return val;
			}
			FshList list = new FshList();
			if( old instanceof FshList ) {
				// keep the indexes (a[5]=z; a+=(w) puts w at 6)
				for(int idx : ((FshList) old).getIndexes()) {
					list.set(idx, ((FshList) old).get(idx));
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
			if( idx < 0 && cur instanceof FshList ) {
				List<Integer> indexes = ((FshList) cur).getIndexes();
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
	private static final java.util.regex.Pattern INDEXED = java.util.regex.Pattern.compile("\\[[^\\]]+\\]=.*", java.util.regex.Pattern.DOTALL);

	public static Object valueOf(AssignmentContext actx, ShellContext ctx) throws IOException {

		Object val = null;
		
		if( actx.arrayInitializer()!=null) {
			// each item is a word, expanded like a command's ("${a[@]}", $(cmd), *.c); [i]=v sets
			// element i
			if( ctx.getVariable(actx.id1.getText()) instanceof java.util.Map<?,?> ) {
				// m=([k]=v ...) of an associative array (declare -A m): keys, not indexes
				java.util.Map<String,Object> map = new java.util.TreeMap<>();
				for(ArgumentContext ac : actx.arrayInitializer().argument_list().argument()) {
					String text = ""+new Argument(ac).getValue(ctx);
					int close = text.indexOf("]=");
					if( text.startsWith("[") && close > 0 ) {
						map.put(text.substring(1, close), text.substring(close+2));
					} else {
						ctx.stderr.println(actx.id1.getText()+": "+text+": must use subscript when assigning associative array");
					}
				}
				return map;
			}
			FshList list = new FshList();
			for(ArgumentContext ac : actx.arrayInitializer().argument_list().argument()) {
				java.util.regex.Matcher m = INDEXED.matcher(ac.getText());
				if( m.matches()) {
					String text = ""+new Argument(ac).getValue(ctx);
					int close = text.indexOf("]=");
					int idx = Arithmetic.expandAndEvaluate(text.substring(1, close), ctx).intValue();
					list.set(idx, text.substring(close+2));
					continue;
				}
				for(String w : Glob.expandWord(ac, ctx)) {
					list.add(w);
				}
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
				// several parts make text, as in bash; in an assignment a ~ after : is $HOME too
				// (PATH=a:~/bin)
				StringBuilder text = new StringBuilder();
				for (int idx = 0; idx < parts.size(); idx++) {
					String home = Argument.tilde(parts, idx, ctx);
					if( home == null && idx > 0 && isTilde(parts.get(idx)) && afterColon(parts.get(idx-1)) && beforeSlashOrColon(parts, idx+1)) {
						home = Argument.tildeValue(parts.get(idx).literal.getText(), ctx);
					}
					text.append(home != null ? home : Argument.getValue(parts.get(idx), ctx));
				}
				val = text.toString();
			}
		}

		return val;
	}

	private static boolean isTilde(ArgumentPartContext part) {
		return part.literal != null && part.literal.getType() == FileSourceShParser.TILDE;
	}

	private static boolean afterColon(ArgumentPartContext part) {
		return part.literal != null && part.literal.getText().endsWith(":");
	}

	private static boolean beforeSlashOrColon(List<ArgumentPartContext> parts, int idx) {
		if( idx >= parts.size()) {
			return true;
		}
		ArgumentPartContext next = parts.get(idx);
		return next.literal != null && (next.literal.getText().startsWith("/") || next.literal.getText().startsWith(":"));
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
				// x=~ is the home directory (~+ $PWD, ~- $OLDPWD)
				String home = Argument.tildeValue(text, ctx);
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
