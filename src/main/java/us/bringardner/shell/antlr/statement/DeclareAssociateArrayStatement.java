package us.bringardner.shell.antlr.statement;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.antlr.v4.runtime.ParserRuleContext;

import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssociativeArrayElementContext;
import us.bringardner.filesource.sh.FileSourceShParser.DeclareAssociativeArrayStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.DeclareItemContext;
import us.bringardner.shell.FsshList;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.Argument;
import us.bringardner.shell.antlr.Arithmetic;
import us.bringardner.shell.antlr.Statement;

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
		} else if( val instanceof FsshList ) {
			flags += "a";
			value.append('(');
			FsshList list = (FsshList) val;
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
		String opts = ctx.DECLARE_A().getText();
		opts = opts.substring(opts.indexOf('-')+1);
		if( opts.indexOf('p') >= 0 ) {
			// declare -p name ...: as declarations the shell can read back
			int ret = 0;
			for(DeclareItemContext item : ctx.declareItem()) {
				String name = item.id1.getText();
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
		for(DeclareItemContext item : ctx.declareItem()) {
			String name = item.id1.getText();
			if( opts.indexOf('i') >= 0 ) {
				sc.console.setInteger(name, true);
			}
			Object val = null;
			if( item.associativeArrayInitializer() != null ) {
				Map<String,Object> map = new TreeMap<>();
				for( AssociativeArrayElementContext e : item.associativeArrayInitializer().associativeArrayElement()) {
					map.put(""+new Argument(e.key).getValue(sc), new Argument(e.value).getValue(sc));
				}
				val = map;
			} else if( item.arrayInitializer() != null ) {
				FsshList list = new FsshList();
				for(ArgumentContext ac : item.arrayInitializer().argument_list().argument()) {
					list.add(new Argument(ac).getValue(sc));
				}
				val = list;
			} else if( item.value != null ) {
				val = new Argument(item.value).getValue(sc);
				if( sc.console.isInteger(name)) {
					val = Arithmetic.evaluate(""+val, sc);
				}
			} else if( item.EQ() != null ) {
				val = "";
			}
			Object old = sc.getVariable(name);
			if( val == null ) {
				// declare -A m, declare -a a: an empty array (an existing one stays)
				if( opts.indexOf('A') >= 0 && !(old instanceof Map<?,?>)) {
					val = new TreeMap<String,Object>();
				} else if( opts.indexOf('a') >= 0 && !(old instanceof List<?>)) {
					val = new FsshList();
				}
			}
			if( val != null ) {
				sc.setVariable(name, val);
			}
			if( opts.indexOf('x') >= 0 ) {
				Object v = sc.getVariable(name);
				sc.setEnvironmentVariable(name, v == null ? "" : ""+v);
			}
		}
		return 0;
	}


}
