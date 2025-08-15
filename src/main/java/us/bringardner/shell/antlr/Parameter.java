package us.bringardner.shell.antlr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.Associative_indexContext;
import us.bringardner.filesource.sh.FileSourceShParser.Parameter1Context;
import us.bringardner.filesource.sh.FileSourceShParser.ParameterContext;
import us.bringardner.filesource.sh.FileSourceShParser.PbodyContext;
import us.bringardner.shell.ShellCommand;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.signal.ExitException;

public class Parameter {

	ParameterContext ctx1;
	public Parameter(ParameterContext parameter) {
		ctx1 = parameter;
	}


	/*
parameter:
    '${' NOT? ID array_index?  parameter_body '}'
    | '${' NOT? expression array_index?  parameter_body '}'
    | '${' NOT? (STAR|AT)  parameter_body '}'
    ;

parameter_body
    : pbody
    | '#' pattern_string SLASH replacement_string
    ;

${parameter:-word}
	 */
	public static final Pattern RANGE1 = Pattern.compile("(:(?<number1>[ \\-0-9]*))(:(?<number2>[\\-0-9]*))?(:(?<number3>[\\-0-9]*))?");
	public static final Pattern RANGE2 = Pattern.compile(""
					+ "(?<number1>[ \\-0-9]*)"
					+ ":(?<number2>[\\-0-9]*)"
					+ ":(?<number3>[\\-0-9]*)"
			+ ""
			);
	public static final Pattern NO_RANGE = Pattern.compile("(?<name>[a-zA-Z_]{1,}[a-zA-Z0-9_]{0,})(?<colon>[:])?(?<type>[-=?+])(?<val>.*)");

	public Object evaluate(ShellContext sc) {
		String fullText = ctx1.getText();
		fullText = fullText.substring(2,fullText.length()-1);
		fullText = FileSourceShPreProcessorVisitorImpl.processString(fullText, sc);
		
		Matcher m = NO_RANGE.matcher(fullText);
		if( m.matches()) {
			return evaluateNoRange(m,sc);
		}
		
		Parameter1Context ctx = FileSourceShVisitorImpl.parseParameter1(fullText);
		String bodyText = ctx.parameter_body().getText();
		boolean isRange = false;
		Object ret = null;
		String name = null;
		
		m = RANGE2.matcher(fullText);

		if( m.matches()) {
			String n1 = m.group("number1");
			name = "$"+n1;
			isRange = true;
		} else 
		
		if( ctx.ID()!=null) {
			name = ctx.ID().getText();
		} else if(ctx.TEXT()!=null) {
			name = ctx.TEXT().getText();
		} else if(ctx.AMP()!=null) {
			name = "@";
		} else if(ctx.STAR()!=null) {
			name = "*";		
		} else if( ctx.expression()!=null) {
			Expression e = new Expression(ctx.expression());
			name = ""+ e.evaluate(sc);
		} else if( bodyText.charAt(0)=='@' || bodyText.charAt(0)=='*') {
			name = ""+bodyText.charAt(0);
		} else {
			m = RANGE1.matcher(":"+bodyText);

			if( m.matches()) {
				bodyText = ":"+bodyText;
				String n1 = m.group("number1");
				name = "$"+n1;
				isRange = true;
			} else {
				throw new RuntimeException("Invalid parameter. no valis name in "+ctx.getText());
			}
		}
		if(ctx.NOT()!=null) {
			if( bodyText.equals("[*]") || bodyText.equals("[@]")) {
				ret = sc.getVariable(name);
				if( ret == null ) {
					return 0;
				} else {
					if (ret instanceof List<?>) {
						List<?> list = (List<?>) ret;
						StringBuilder buf = new StringBuilder();
						for(int idx=0,sz=list.size(); idx < sz; idx++ ) {
							if( !buf.isEmpty()) {
								buf.append(' ');
							}
							buf.append(""+idx);
						}
						return buf.toString();
					} else {
						throw new RuntimeException("Not a list ");
					}
				}
			}
			StringBuilder buf = new StringBuilder();
			for(String vname:sc.console.getVariables().keySet()) {
				if( vname.startsWith(name)) {
					if( !buf.isEmpty()) {
						buf.append(' ');
					}
					buf.append(vname);
				}
			}
			return buf.toString();
		}

		if( name.equals("@") || name.equals("*")) {
			ret = sc.console.getPositionalParameters();
		} else {
			ret = sc.getVariable(name);
		}
		
		if( ctx.parameter_index()!=null) {
			Object index = null;
			if( ctx.parameter_index().array_index()!=null) {
				if( ctx.parameter_index().array_index().expression()!=null) {
					Expression e = new Expression(ctx.parameter_index().array_index().expression());
					index = e.evaluate(sc);					
				}
			} else if( ctx.parameter_index().associative_index()!=null) {
				Associative_indexContext as = ctx.parameter_index().associative_index();
				if( as.ID()!=null) {
					index = as.ID().getText();
				} else {
					throw new RuntimeException("No ID in associative array");
				}
			} else if( ctx.parameter_index().TEXT()!=null) {
				index =  ctx.parameter_index().TEXT().getText();
			}

			if (ret instanceof List<?>) {	
				String tmp = index.toString();
				if( tmp.charAt(0)!='@') {

					List<?> list = (List<?>) ret;
					int idx = 0;
					try {
						idx = Integer.parseInt(""+index);
					} catch (Exception e) {
					}
					if( idx < list.size()) {
						ret = list.get(idx);
					}
				}
			} else if (ret instanceof Map<?,?>) {
				Map<?,?> map = (Map<?,?>) ret;
				ret = map.get(""+index);

			}

		}

		if( ctx.PIPE()!=null ) {
			if (ret instanceof List) {
				List<?> list = (List<?>) ret;
				return list.size();
			} else {
				String tmp = ret.toString();
				return tmp.length();
			}
		}



		
		String body = bodyText;
		
		StringBuilder buf = new StringBuilder();
		PbodyContext bc = ctx.parameter_body().pbody();
		if(!isRange && bc !=null ) {
			body = bc.getText();
			if( bc.children !=null) {
				for(ParseTree t : bc.children) {
					if(buf.length()>0) {
						buf.append(' ');
					}
					buf.append(t.getText());
				}


				if( body.startsWith("^") || body.startsWith(",")) {
					return patternChageCase(ret,bodyText,bc);
				}
				if( bc.children.size()>1) {
					TerminalNode tn = (TerminalNode) bc.children.get(0);
					int type = tn.getSymbol().getType();
					if(type == FileSourceShParser.PATH_START || type == FileSourceShParser.SLASH) {
						return patternSearchReplace(ret,bodyText,bc);
					} 
				}
				if( bc.children.size()>1) {
					TerminalNode tn = (TerminalNode) bc.children.get(0);
					int type = tn.getSymbol().getType();
					if( type == FileSourceShParser.PIPE || type == FileSourceShParser.OR) {
						return patternHashReplaceHead(ret,bodyText,bc);
					} else if( type == FileSourceShParser.PERC) {
						return patternHashReplaceTail(ret,bodyText,bc);
					} 
				}

				if( bc.children.size()>2) {
					TerminalNode tn = (TerminalNode) bc.children.get(2);
					int type = tn.getSymbol().getType();
					if( type == FileSourceShParser.NUMBER) {
						isRange = true;
					} 
				}
			}
		}
		
		if( body.startsWith(":") ) {
			if( ret !=null ) {
				m = RANGE1.matcher(body);

				if( m.matches()) {
					String n1 = m.group("number1");
					String n2 = m.group("number2");
					String n3 = m.group("number3");
					if( n3 !=null) {
						n1 = n2;
						n2 = n3;
					}
					
					//System.out.println("n1="+n1+" "+n2+" "+n3);
					int offset = Integer.parseInt(n1);


					if (ret instanceof String) {
						if( offset < 0 ) {
							offset = ((String) ret).length()+offset;
						}
						if( n2 == null ) {
							ret = ((String) ret).substring(offset);
						} else {
							int len = Integer.parseInt(n2);
							if( len < 0) {
								len = ((String) ret).length()+len-offset;
							}
							ret = ((String) ret).substring(offset,offset+len);
						}
					} else if (ret instanceof List<?>) {
						@SuppressWarnings("unchecked")
						List<Object> list = (List<Object>) ret;
						// this is to match bash result
						// it's also really stupid
						if( name.equals("*")) {
							String fileName = ""+sc.getVariable("$0");
							List<Object> tl = new ArrayList<>();
							tl.add(fileName);
							for(Object o : list) {
								tl.add(o);
							}
							list = tl;
						}
						if( offset < 0 ) {
							offset = list.size()+offset;
						}
						int len = list.size();
						if( n2 !=null) {
							len = Integer.parseInt(n2);
							if( len < 0) {
								throw new RuntimeException(""+len+": substring expression < 0");
							} else {
								len = offset+len;
							}
						}
						StringBuilder bufx = new StringBuilder();
						for(int idx=offset; idx < len; idx++) {
							if( !bufx.isEmpty()) {
								bufx.append(" ");
							}
							bufx.append(""+list.get(idx));
						}
						ret = bufx.toString();
					} else {
						throw new RuntimeException("Un excpeted type ="+ret.getClass());
					}

				} else {
					ret = "";
				}
			}
		} else {
			//throw new RuntimeException("Invalid parameter="+ctx.getText());
		}

		return ret;
	}

	private Object evaluateNoRange(Matcher m, ShellContext sc)  {
		String name = m.group("name");
		String type = m.group("type");
		String val = m.group("val");
		String colon = m.group("colon");
		Object ret = sc.getVariable(name);
		switch(type.charAt(0)) {
		case '=':
			/*
			${parameter:=word}
			If parameter is unset or null, the expansion of word is assigned to parameter. 
			The value of parameter is then substituted. Positional parameters and special parameters may not be assigned to in this way.
			 */
			if( ret == null) {
				ret = val;
				
				sc.setVariable(name, ret);
			}
			break;
		case '-':
			/*
			 * ${parameter:−word}
					If parameter is unset or null, the expansion of word is substituted. 
					Otherwise, the value of parameter is substituted.
					
				if the colon is included, the operator tests for both parameter’s existence and that its value is not null; 
				if the colon is omitted, the operator tests only for existence.
			 */
			if( colon == null && ret == null) {
				// TODO: requires revamp of variable management
				ret = val;
			} else if( ret == null ) {					
					ret = val;
				}
			break;
		case '+':
			/*
			${parameter:+word}
			If parameter is null or unset, nothing is substituted, otherwise the expansion of word is substituted.
			 */		
			if( ret != null ) {
				ret = val; 
			}

			break;
		case '?':
			/*
			If parameter is null or unset, the shell writes the expansion of word (or a message to that effect if word is not present) 
				to the standard error and, if it is not interactive, exits with a non-zero status. 
				An interactive shell does not exit, but does not execute the command associated with the expansion. Otherwise, 
				the value of parameter is substituted.
			 */
			if( ret == null) {
				
				if( sc.console.isInteractive) {
					throw new ExitException(sc, 1,val);
				}
			}
			break;
		default: throw new RuntimeException("Invalid parameter type = "+type);
		}

		return ret;
	}

	enum CaseType {Lower,Upper,FirstLower,FirstUpper}
	
	private Object patternChageCase(Object val, String bodyText, PbodyContext bc) {
		if (val instanceof List<?>) {
			List<?> list = (List<?>) val;
			StringBuilder buf = new StringBuilder();
			for(int idx=0,sz=list.size(); idx < sz; idx++ ) {
				Object o = list.get(idx);
				if( !buf.isEmpty()) {
					buf.append(' ');
				}
				Object tmp = patternChageCase(o, bodyText, bc);
				buf.append(""+tmp);
			}
			return buf.toString();
		}

		
		CaseType type = null;
		String pat = null;
		if( bodyText.startsWith("^^")) {
			type = CaseType.Upper;
			pat = bodyText.substring(2);	
		} else if( bodyText.startsWith("^")) {
			type = CaseType.FirstUpper;
			pat = bodyText.substring(1);	
		} else if( bodyText.startsWith(",,")) {
			type = CaseType.Lower;
			pat = bodyText.substring(2);	
		} else if( bodyText.startsWith(",")) {
			type = CaseType.FirstLower;
			pat = bodyText.substring(1);	
		}
		if( pat.trim().isEmpty()) {
			pat = "?";
		}
		
		String ret = ""+val;

		
		
		String preped = ShellCommand.prepWildCards(pat,false);

		Pattern rx = Pattern.compile(preped);
		Matcher m = rx.matcher(ret);
		
		
		while(m.find()) {
			String tmp = m.group();
			int start = m.start();
			int end = m.end();
			String left = ret.substring(0,start);
			String right = ret.substring(end);
			switch (type) {
			case Lower:
			case FirstLower:
				tmp = tmp.toLowerCase();
				break;
			case FirstUpper:
			case Upper: tmp = tmp.toUpperCase();
				break;
			default:
				throw new IllegalArgumentException("Unexpected value: " + type);
			}
			ret = left+tmp+right;
			if( type == CaseType.FirstLower || type == CaseType.FirstUpper) {
				break;
			}
		}

		
		return ret;
	}
	enum PatternType {One,All,Start,End};
	
	private Object patternSearchReplace(Object val, String bodyText, PbodyContext bc) {
		String ret = ""+val;
		
		
		PatternType type = PatternType.One;

		String tmp = bodyText.substring(1);
		int idx = tmp.lastIndexOf('/');
		if( idx > 0 ) {
			String replace = tmp.substring(idx+1);
			String target  = tmp.substring(0,idx);
			
			// # match start % match end
			if( target.charAt(0)== '/') {
				target = target.substring(1);
				type = PatternType.All;
			} else if( target.charAt(0)== '|') {
				target = target.substring(1);
				type = PatternType.Start;
			} else if( target.charAt(0)== '%') {
				target = target.substring(1);
				type = PatternType.End;
			}
			
			String preped = ShellCommand.prepWildCards(target,true);

			if(type == PatternType.Start) {
				preped = "^"+preped;
			} else if(type == PatternType.End) {
				preped = preped + "$";
			}
			
			Pattern rx = Pattern.compile(preped);
			Matcher m = rx.matcher(ret);
			
			
			if(m.find()) {
				if( type == PatternType.One) {
					ret = m.replaceFirst(replace);
				} else {
					ret = m.replaceAll(replace);
				}
			}
			
		}

		return ret;
	}

	private Object patternHashReplaceTail(Object val, String bodyText, PbodyContext bc) {
		if (val instanceof List<?>) {
			List<?> list = (List<?>) val;
			StringBuilder buf = new StringBuilder();
			for(int idx=0,sz=list.size(); idx < sz; idx++ ) {
				Object o = list.get(idx);
				if( !buf.isEmpty()) {
					buf.append(' ');
				}
				Object tmp = patternHashReplaceTail(o, bodyText, bc);
				buf.append(""+tmp);
			}
			return buf.toString();
		}

		String ret = ""+val;

		boolean isLong = false;
		String pat = bodyText.substring(1);
		if( pat.charAt(0)== '%') {
			pat = pat.substring(1);
			isLong = true;
		}
		String preped = ShellCommand.prepWildCards(pat,false);

		Pattern rx = Pattern.compile(preped);
		Matcher m = rx.matcher(ret);
		int start = -1;
		while(m.find()) {
			start = m.start();
			if( isLong) {
				break;
			}
		}

		if( start>=0) {
			ret = ret.substring(0,start);
		} 

		return ret;
	}

	/*
${parameter#word}
${parameter##word}
		The word is expanded to produce a pattern and matched according to the rules described 
		below (see Pattern Matching). 
		If the pattern matches the beginning of the expanded value of parameter, then the 
		result of the expansion is the expanded value of parameter with the shortest 
		matching pattern (the ‘#’ case) or the longest matching pattern (the ‘##’ case) deleted. 

		If parameter is ‘@’ or ‘*’, the pattern removal operation is applied to each positional 
		parameter in turn, and the expansion is the resultant list. 

		If parameter is an array variable subscripted with ‘@’ or ‘*’, the pattern removal 
		operation is applied to each member of the array in turn, and the expansion is the 
		resultant list.
	 */
	private Object patternHashReplaceHead(Object val,String bodyText, PbodyContext bc) {
		if (val instanceof List<?>) {
			List<?> list = (List<?>) val;
			StringBuilder buf = new StringBuilder();
			for(int idx=0,sz=list.size(); idx < sz; idx++ ) {
				Object o = list.get(idx);
				if( !buf.isEmpty()) {
					buf.append(' ');
				}
				Object tmp = patternHashReplaceHead(o, bodyText, bc);
				buf.append(""+tmp);
			}
			return buf.toString();
		}

		String ret = ""+val;

		boolean isLong = false;
		String pat = bodyText.substring(1);
		if( pat.charAt(0)== '|') {
			pat = pat.substring(1);
			isLong = true;
		}

		String preped = ShellCommand.prepWildCards(pat,false);

		Pattern rx = Pattern.compile(preped);
		Matcher m = rx.matcher(ret);
		int end = -1;
		while(m.find()) {
			end = m.end();
			if( !isLong) {
				break;
			}
		}

		if( end>=0) {
			ret = ret.substring(end);
		} 

		return ret;
	}



}
