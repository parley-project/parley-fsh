package us.bringardner.fsh.antlr.statement;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.fsh.parser.FileSourceShParser.ArgumentContext;
import us.bringardner.fsh.parser.FileSourceShParser.AssociativeArrayElementContext;
import us.bringardner.fsh.parser.FileSourceShParser.DeclareAssociativeArrayStatementContext;
import us.bringardner.fsh.parser.FileSourceShParser.DeclareItemContext;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.Argument;
import us.bringardner.fsh.antlr.Arithmetic;
import us.bringardner.fsh.antlr.Statement;

public class DeclareAssociateArrayStatement extends Statement{

	public DeclareAssociateArrayStatement(ParserRuleContext context) {
		super(context);
	}

	/*


// New rule to support 'declare -A my_array' and 'declare -A my_array=([key1]=value1 [key2]=value2)'
declareAssociativeArrayStatement
    : DECLARE_A id1=ID (NL? EQ NL? associativeArrayInitializer)? NL? CMD_TERMINATOR?
    ;

associativeArrayInitializer
    : NL? LPAREN NL? (associativeArrayElement NL?) * RPAREN
    ;

associativeArrayElement
    :NL? LSQUARE key=argument RSQUARE EQ value=argument NL?
    ;    
	 */
	/** declare -- x="1", declare -a a=([0]="x"), declare -A m=([k]="v" ) */
	static String declaration(String name, Object val, ShellContext sc) {
		String flags = "";
		StringBuilder value = new StringBuilder();
		if( val instanceof Map<?,?> ) {
			flags += "A";
			value.append('(');
			for(Map.Entry<?,?> e : ((Map<?,?>) val).entrySet()) {
				value.append('[').append(e.getKey()).append("]=").append(quote(e.getValue())).append(' ');
			}
			value.append(')');
		} else if( val instanceof FshList ) {
			flags += "a";
			value.append('(');
			FshList list = (FshList) val;
			boolean first = true;
			for(int idx : list.getIndexes()) {
				if( !first ) {
					value.append(' ');
				}
				first = false;
				value.append('[').append(idx).append("]=").append(quote(list.get(idx)));
			}
			value.append(')');
		} else {
			value.append(quote(val));
		}
		if( sc.console.isInteger(name)) {
			flags += "i";
		}
		if( sc.console.isReadonly(name)) {
			flags += "r";
		}
		if( sc.getEvironmentVariable(name) != null ) {
			flags += "x";
		}
		return "declare -"+(flags.isEmpty() ? "-" : flags)+" "+name+"="+value;
	}

	private static String quote(Object v) {
		return "\""+(""+v).replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`")+"\"";
	}

	@Override
	protected int execute(ShellContext sc) throws IOException {
		DeclareAssociativeArrayStatementContext ctx = (DeclareAssociativeArrayStatementContext) getContext();
		// declare -opts, or local [-opts]: local makes the names the function's
		boolean local = ctx.LOCAL() != null;
		boolean remove = false;
		if( local && !sc.isInFunction()) {
			sc.stderr.println("local: can only be used in a function");
			return 1;
		}
		String opts = "";
		if( local ) {
			for(org.antlr.v4.runtime.Token t : ctx.localOpts) {
				opts += t.getText().substring(1);
			}
		} else {
			opts = ctx.DECLARE_A().getText();
			// declare +l x: take attributes away
			int sign = Math.max(opts.indexOf('-'), opts.indexOf('+'));
			remove = sign >= 0 && opts.charAt(sign) == '+';
			opts = sign < 0 ? "" : opts.substring(sign+1);
		}
		if( opts.indexOf('f') >= 0 || opts.indexOf('F') >= 0 ) {
			// declare -f [name ...]: functions as code; -F: their names
			boolean names = opts.indexOf('F') >= 0;
			java.util.List<String> wanted = new java.util.ArrayList<>();
			for(DeclareItemContext item : ctx.declareItem()) {
				wanted.add(itemName(item, sc));
			}
			if( wanted.isEmpty()) {
				wanted.addAll(new java.util.TreeSet<>(sc.console.getFunctions().keySet()));
			}
			int ret = 0;
			for(String name : wanted) {
				us.bringardner.fsh.antlr.statement.FunctionDefStatement f = sc.console.getFunctions().get(name);
				if( f == null ) {
					ret = 1;
				} else if( names ) {
					sc.stdout.println(ctx.declareItem().isEmpty() ? "declare -f "+name : name);
				} else {
					sc.stdout.println(f.declaration());
				}
			}
			return ret;
		}
		if( opts.indexOf('p') >= 0 ) {
			// declare -p name ...: as declarations the shell can read back
			int ret = 0;
			for(DeclareItemContext item : ctx.declareItem()) {
				String name = itemName(item, sc);
				Object val = sc.getVariable(name);
				if( val == null ) {
					sc.stderr.println("declare: "+name+": not found");
					ret = 1;
				} else {
					sc.stdout.println(declaration(name, val, sc));
				}
			}
			return ret;
		}
		int status = 0;
		for(DeclareItemContext item : ctx.declareItem()) {
			String name = itemName(item, sc);
			// declare "$name=$value": the value after the =
			String wordValue = null;
			if( item.word != null ) {
				String text = ""+new Argument(item.word).getValue(sc);
				int eq = text.indexOf('=');
				if( eq >= 0 ) {
					wordValue = text.substring(eq+1);
				}
			}
			if( !name.matches("[a-zA-Z_][a-zA-Z_0-9]*")) {
				sc.stderr.println((local ? "local" : "declare")+": `"+name+"': not a valid identifier");
				status = 1;
				continue;
			}
			if( opts.indexOf('i') >= 0 ) {
				sc.console.setInteger(name, !remove);
			}
			// -l -u: lower or upper case (set before the value, which they change)
			if( opts.indexOf('l') >= 0 || opts.indexOf('u') >= 0 ) {
				sc.setCaseAttribute(name, remove ? null : opts.lastIndexOf('l') > opts.lastIndexOf('u') ? 'l' : 'u', local);
			}
			if( remove ) {
				if( item.value == null && item.associativeArrayInitializer() == null && item.arrayInitializer() == null ) {
					continue;
				}
			}
			if( opts.indexOf('n') >= 0 ) {
				// a reference: the variable named by the value (declare -n ref=target, local -n r=$1)
				String target = item.value == null ? "" : ""+new Argument(item.value).getValue(sc);
				sc.setNameRef(name, target, local);
				continue;
			}
			Object val = null;
			if( item.associativeArrayInitializer() != null && opts.indexOf('A') < 0 && !(sc.getVariable(name) instanceof Map<?,?>)) {
				// declare -a a=() or a=([2]=x): an indexed array (the [key]= form, or empty, read
				// as an associative one)
				FshList list = new FshList();
				for( AssociativeArrayElementContext e : item.associativeArrayInitializer().associativeArrayElement()) {
					int index = Arithmetic.evaluate(key(e, sc), sc).intValue();
					list.set(index, e.value == null ? "" : new Argument(e.value).getValue(sc));
				}
				val = list;
			} else if( item.associativeArrayInitializer() != null ) {
				Map<String,Object> map = new TreeMap<>();
				for( AssociativeArrayElementContext e : item.associativeArrayInitializer().associativeArrayElement()) {
					map.put(key(e, sc), e.value == null ? "" : new Argument(e.value).getValue(sc));
				}
				val = map;
			} else if( item.arrayInitializer() != null ) {
				FshList list = new FshList();
				for(ArgumentContext ac : item.arrayInitializer().array_list().argument()) {
					for(String w : us.bringardner.fsh.Glob.expandWord(ac, sc)) {
						list.add(w);
					}
				}
				val = list;
			} else if( wordValue != null ) {
				val = wordValue;
				if( sc.console.isInteger(name)) {
					val = Arithmetic.evaluate(""+val, sc);
				}
			} else if( item.value != null ) {
				val = new Argument(item.value).getValue(sc);
				if( sc.console.isInteger(name)) {
					val = Arithmetic.evaluate(""+val, sc);
				}
			} else if( item.EQ() != null ) {
				val = "";
			}
			Object old = local ? null : sc.getVariable(name);
			if( val == null ) {
				// declare -A m, declare -a a: an empty array (an existing one stays)
				if( opts.indexOf('A') >= 0 && !(old instanceof Map<?,?>)) {
					val = new TreeMap<String,Object>();
				} else if( opts.indexOf('a') >= 0 && !(old instanceof List<?>)) {
					val = new FshList();
				}
			}
			if( local ) {
				// local x: the function's, and unset until it is given a value
				if( val == null ) {
					sc.declareLocal(name);
				} else {
					sc.setLocalVariable(name, val);
				}
			} else if( val != null ) {
				sc.setVariable(name, val);
			}
			if( opts.indexOf('r') >= 0 ) {
				sc.console.setReadonly(name);
			}
			if( opts.indexOf('x') >= 0 ) {
				Object v = sc.getVariable(name);
				sc.setEnvironmentVariable(name, v == null ? "" : ""+v);
			}
		}
		return status;
	}


	/** the key of [key]=value; [a b]=v keeps the space */
	static String key(AssociativeArrayElementContext e, ShellContext sc) {
		if( e.keyText == null ) {
			return ""+new Argument(e.key.get(0)).getValue(sc);
		}
		String text = e.keyText.getStart().getInputStream().getText(
				org.antlr.v4.runtime.misc.Interval.of(e.keyText.getStart().getStartIndex(), e.keyText.getStop().getStopIndex()));
		text = us.bringardner.fsh.antlr.FileSourceShPreProcessorVisitorImpl.processString(text, sc);
		// quote removal
		StringBuilder ret = new StringBuilder();
		char quote = 0;
		for (int idx = 0; idx < text.length(); idx++) {
			char c = text.charAt(idx);
			if( quote == 0 && (c == '"' || c == '\'')) {
				quote = c;
			} else if( c == quote ) {
				quote = 0;
			} else if( c == '\\' && quote != '\'' && idx+1 < text.length()) {
				ret.append(text.charAt(++idx));
			} else {
				ret.append(c);
			}
		}
		return ret.toString();
	}
	/** the name an item declares: x of x=1, or the expanded word's text before any = */
	private static String itemName(DeclareItemContext item, ShellContext sc) {
		if( item.id1 != null ) {
			return item.id1.getText();
		}
		String text = ""+new Argument(item.word).getValue(sc);
		int eq = text.indexOf('=');
		return eq < 0 ? text : text.substring(0, eq);
	}
}
