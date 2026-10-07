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
	@Override
	protected int execute(ShellContext sc) throws IOException {
		DeclareAssociativeArrayStatementContext ctx = (DeclareAssociativeArrayStatementContext) getContext();
		String opts = ctx.DECLARE_A().getText();
		opts = opts.substring(opts.indexOf('-')+1);
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
