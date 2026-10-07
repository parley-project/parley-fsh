package us.bringardner.shell.antlr;

import us.bringardner.shell.Console;
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
import us.bringardner.shell.FsshList;
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
	public static final Pattern NO_RANGE = Pattern.compile("(?<name>[a-zA-Z_]{1,}[a-zA-Z0-9_]{0,}|[0-9]+|[@*#?$!])(?<colon>[:])?(?<type>[-=?+])(?<val>.*)", Pattern.DOTALL);

	private static final Pattern ARRAY_ALL = Pattern.compile("([!#|]?)([a-zA-Z_][a-zA-Z_0-9]*)\\[([@*])\\]");
	private static final Pattern ANSI = Pattern.compile("\\$'((?:[^'\\\\]|\\\\.)*)'");
	private static final Pattern ARRAY_OP = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)\\[([@*])\\]([/#|%^,].*)", Pattern.DOTALL);
	private static final Pattern LENGTH = Pattern.compile("[#|]([a-zA-Z_][a-zA-Z_0-9]*)");
	/** a variable for one element while ${a[@]/x/y} works on it */
	private static final String ELEMENT = "__bjlshell_element";
	private static final Pattern TOGGLE_CASE = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)(~~?)");
	private static final Pattern SIMPLE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*");
	private static final Pattern MAP_ELEMENT = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)\\[(.+)\\]");
	private static final Pattern INDIRECT = Pattern.compile("!([a-zA-Z_][a-zA-Z_0-9]*)");
	private static final Pattern POSITIONAL = Pattern.compile("[0-9]+");

	/**
	 * ${a[@]} ${a[*]} (the values; [*] joined by the first character of IFS), ${!a[@]} (the indexes
	 * or keys), ${#a[@]} (how many), ${!ref} (the variable ref names) and ${10}.
	 * @return the value, or null if text is none of these
	 */
	static Object arrayForms(String text, ShellContext sc) {
		Matcher m = ARRAY_ALL.matcher(text);
		if( m.matches()) {
			Object val = sc.getVariable(m.group(2));
			List<Object> items = new ArrayList<>();
			if( m.group(1).equals("!")) {
				items.addAll(keys(val));
			} else {
				items.addAll(values(val));
			}
			// ${#a[@]} (Console.convertHash writes # as |)
			if( m.group(1).equals("#") || m.group(1).equals("|")) {
				return items.size();
			}
			String sep = " ";
			if( m.group(3).equals("*")) {
				Object ifs = sc.getVariable(Console.IFS);
				sep = ifs == null ? " " : ifs.toString().isEmpty() ? "" : ifs.toString().substring(0, 1);
			}
			StringBuilder ret = new StringBuilder();
			for(Object o : items) {
				if( ret.length() > 0 ) {
					ret.append(sep);
				}
				ret.append(o);
			}
			return ret.toString();
		}
		m = ARRAY_OP.matcher(text);
		if( m.matches()) {
			// ${a[@]/x/y}, ${a[@]#pat} ...: the operation on each element
			StringBuilder ret = new StringBuilder();
			String sep = " ";
			if( m.group(2).equals("*")) {
				Object ifs = sc.getVariable(Console.IFS);
				sep = ifs == null ? " " : ifs.toString().isEmpty() ? "" : ifs.toString().substring(0, 1);
			}
			for(Object e : values(sc.getVariable(m.group(1)))) {
				sc.setLocalVariable(ELEMENT, e);
				if( ret.length() > 0 ) {
					ret.append(sep);
				}
				ret.append(FileSourceShPreProcessorVisitorImpl.processString("${"+ELEMENT+m.group(3)+"}", sc));
			}
			sc.unSetVariable(ELEMENT);
			return ret.toString();
		}
		m = LENGTH.matcher(text);
		if( m.matches()) {
			Object val = sc.getVariable(m.group(1));
			if( val instanceof List<?> || val instanceof Map<?,?> ) {
				// ${#a} is the length of element 0
				Object first = ShellContext.firstElement(val);
				return first == null ? 0 : first.toString().length();
			}
		}
		m = TOGGLE_CASE.matcher(text);
		if( m.matches()) {
			// ${x~} toggles the case of the first letter, ${x~~} of every letter
			String val = ""+(sc.getVariable(m.group(1)) == null ? "" : sc.getVariable(m.group(1)));
			StringBuilder ret = new StringBuilder();
			for (int idx = 0; idx < val.length(); idx++) {
				char c = val.charAt(idx);
				boolean toggle = idx == 0 || m.group(2).length() == 2;
				ret.append(!toggle ? c : Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
			}
			return ret.toString();
		}
		if( SIMPLE_NAME.matcher(text).matches()) {
			// ${a} of an array is its element 0, as in bash
			Object val = sc.getVariable(text);
			if( val instanceof List<?> || val instanceof Map<?,?> ) {
				Object first = ShellContext.firstElement(val);
				return first == null ? "" : first;
			}
		}
		m = MAP_ELEMENT.matcher(text);
		if( m.matches() && sc.getVariable(m.group(1)) instanceof Map<?,?> ) {
			// ${m[any key]}: the key as written, quotes removed
			String key = m.group(2);
			if( key.length() >= 2 && (key.startsWith("\"") && key.endsWith("\"") || key.startsWith("'") && key.endsWith("'"))) {
				key = key.substring(1, key.length()-1);
			}
			Object val = ((Map<?,?>) sc.getVariable(m.group(1))).get(key);
			return val == null ? "" : val;
		}
		m = INDIRECT.matcher(text);
		if( m.matches()) {
			Object ref = sc.getVariable(m.group(1));
			if( ref == null || ref.toString().isEmpty()) {
				return "";
			}
			if( ref.toString().contains("[")) {
				// r="a[1]": ${!r} is that element
				return FileSourceShPreProcessorVisitorImpl.processString("${"+ref+"}", sc);
			}
			Object val = sc.getVariable(ref.toString());
			return val == null ? "" : val;
		}
		if( POSITIONAL.matcher(text).matches()) {
			Object val = sc.getVariable("$"+text);
			return val == null ? "" : val;
		}
		return null;
	}

	/** the indexes of an array or the keys of a map (0 for a scalar) */
	public static List<Object> keys(Object val) {
		List<Object> ret = new ArrayList<>();
		if( val instanceof FsshList ) {
			ret.addAll(((FsshList) val).getIndexes());
		} else if( val instanceof Map<?,?> ) {
			ret.addAll(((Map<?,?>) val).keySet());
		} else if( val instanceof List<?> ) {
			for (int idx = 0; idx < ((List<?>) val).size(); idx++) {
				ret.add(idx);
			}
		} else if( val != null ) {
			ret.add(0);
		}
		return ret;
	}

	/** the values of an array (in index order) or a map, or a scalar as one value */
	public static List<Object> values(Object val) {
		List<Object> ret = new ArrayList<>();
		if( val instanceof Map<?,?> ) {
			ret.addAll(((Map<?,?>) val).values());
		} else if( val instanceof List<?> ) {
			for(Object o : (List<?>) val) {
				ret.add(o);
			}
		} else if( val != null ) {
			ret.add(val);
		}
		return ret;
	}

	public Object evaluate(ShellContext sc)  {
		String fullText = ctx1.getText();
		fullText = fullText.substring(2,fullText.length()-1);
		// $'...' in a pattern (${x%$'\\n'}): its text, quoted so the pattern takes it as text
		if( fullText.contains("$'")) {
			Matcher ansi = ANSI.matcher(fullText);
			StringBuilder buf = new StringBuilder();
			while( ansi.find()) {
				String text = ShellContext.ansiC(ansi.group(1)).replace("\\", "\\\\").replace("\"", "\\\"");
				ansi.appendReplacement(buf, Matcher.quoteReplacement("\""+text+"\""));
			}
			ansi.appendTail(buf);
			fullText = buf.toString();
		}
		fullText = FileSourceShPreProcessorVisitorImpl.processString(fullText, sc);

		Object array = arrayForms(fullText, sc);
		if( array != null ) {
			return array;
		}
		
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
		} else if(ctx.AT()!=null) {
			name = "@";
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
			} else if( ctx.parameter_index().AT()!=null) {
				index =  ctx.parameter_index().AT().getText();
			
			} else if( ctx.parameter_index().TEXT()!=null) {
				index =  ctx.parameter_index().TEXT().getText();
			}

			if (ret instanceof List<?>) {	
				String tmp = index.toString();
				if( tmp.charAt(0)!='@') {

					List<?> list = (List<?>) ret;
					int idx = 0;
					try {
						idx = ((Number)Expression.toNumber(index, sc)).intValue();
					} catch (Exception e) {
					}
					// as in bash: a negative index counts from the end, and an index with no
					// element gives nothing (it gave the whole array)
					if( idx < 0 ) {
						idx += length(list);
					}
					if( list instanceof FsshList ) {
						// may have gaps; get gives null for an index with no element
						ret = idx >= 0 ? list.get(idx) : null;
					} else {
						ret = idx >= 0 && idx < list.size() ? list.get(idx) : null;
					}
				}
			} else if (ret instanceof Map<?,?>) {
				Map<?,?> map = (Map<?,?>) ret;
				ret = map.get(""+index);

			}

		}

		if( ctx.PIPE()!=null ) {
			if( ret == null ) {
				return 0;
			} else if (ret instanceof List) {
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
					if( type == FileSourceShParser.SLASH) {
						return patternSearchReplace(ret,bodyText,bc);
					} 
				}
				if( bc.children.size()>1) {
					TerminalNode tn = (TerminalNode) bc.children.get(0);
					int type = tn.getSymbol().getType();
					if( type == FileSourceShParser.PIPE || type == FileSourceShParser.OR) {
						return patternHashReplaceHead(ret,bodyText,bc);
					} else if( type == FileSourceShParser.PERC | type == FileSourceShParser.PERC_PERC) {
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
				if( !m.matches()) {
					// the offset and length are arithmetic: ${s:n:2}, ${s:i+1}
					m = RANGE1.matcher(arithmeticRange(body, sc));
				}

				if( m.matches()) {
					String n1 = m.group("number1");
					String n2 = m.group("number2");
					String n3 = m.group("number3");
					if( n3 !=null) {
						n1 = n2;
						n2 = n3;
					}
					
					//System.out.println("n1="+n1+" "+n2+" "+n3);
					// an empty field is 0: ${x::2}, ${x:1:}
					int offset = n1.isBlank() ? 0 : Integer.parseInt(n1.trim());


					if (!(ret instanceof List<?>)) {
						// a number is text here too (x=12345; ${x:1:2})
						ret = substring(""+ret, offset, n2 == null ? null : n2.isBlank() ? 0 : Integer.parseInt(n2.trim()));
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
							len = n2.isBlank() ? 0 : Integer.parseInt(n2.trim());
							if( len < 0) {
								throw new RuntimeException(""+len+": substring expression < 0");
							} else {
								len = offset+len;
							}
						}
						// past either end there is nothing
						len = Math.min(len, list.size());
						StringBuilder bufx = new StringBuilder();
						for(int idx=Math.max(offset, 0); idx < len; idx++) {
							if( !bufx.isEmpty()) {
								bufx.append(" ");
							}
							bufx.append(""+list.get(idx));
						}
						ret = bufx.toString();
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
		// ${1:-x}, ${@:-x}: positional and special parameters
		Object ret = Character.isLetter(name.charAt(0)) || name.charAt(0) == '_' ? sc.getVariable(name) : sc.getVariable("$"+name);
		if( ret != null && !Character.isLetter(name.charAt(0)) && name.charAt(0) != '_' && name.matches("[0-9]+") && sc.getPositionalParameterValues().size() < Integer.parseInt(name)) {
			ret = null;
		}
		// with the colon, an empty value counts as missing too (${e:-d} is d when e is empty)
		boolean missing = ret == null || (colon != null && (""+ret).isEmpty());
		switch(type.charAt(0)) {
		case '=':
			/*
			${parameter:=word}
			If parameter is unset or null, the expansion of word is assigned to parameter. 
			The value of parameter is then substituted. Positional parameters and special parameters may not be assigned to in this way.
			 */
			if( missing ) {
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
			if( missing ) {
				ret = val;
			}
			break;
		case '+':
			/*
			${parameter:+word}
			If parameter is null or unset, nothing is substituted, otherwise the expansion of word is substituted.
			 */		
			ret = missing ? "" : val;

			break;
		case '?':
			/*
			If parameter is null or unset, the shell writes the expansion of word (or a message to that effect if word is not present) 
				to the standard error and, if it is not interactive, exits with a non-zero status. 
				An interactive shell does not exit, but does not execute the command associated with the expansion. Otherwise, 
				the value of parameter is substituted.
			 */
			if( missing ) {
				if( val == null) {
					val = ("parameter "+name+" is null");
				} else {
					val = name+": "+val;
				}
				if( !sc.console.isInteractive) {
					// console will write val to stderr
					throw new ExitException(sc, 1,val);
				} else {
					sc.stderr.println(val);
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

		// /pat/rep (first), //pat/rep (all), /#pat/rep (start, # is written |), /%pat/rep (end);
		// with no /rep the match is deleted
		String tmp = bodyText.substring(1);
		if( tmp.startsWith("/")) {
			type = PatternType.All;
			tmp = tmp.substring(1);
		} else if( tmp.startsWith("|") || tmp.startsWith("#")) {
			type = PatternType.Start;
			tmp = tmp.substring(1);
		} else if( tmp.startsWith("%")) {
			type = PatternType.End;
			tmp = tmp.substring(1);
		}
		int idx = -1;
		for (int i = 0; i < tmp.length(); i++) {
			if( tmp.charAt(i) == '\\' ) {
				i++;
			} else if( tmp.charAt(i) == '/' ) {
				idx = i;
				break;
			}
		}
		String target = idx < 0 ? tmp : tmp.substring(0, idx);
		String replace = idx < 0 ? "" : tmp.substring(idx+1);
		if( target.isEmpty()) {
			return ret;
		}
		String preped = ShellCommand.prepWildCards(target,true);
		if(type == PatternType.Start) {
			preped = "^(?:"+preped+")";
		} else if(type == PatternType.End) {
			preped = "(?:"+preped+")$";
		}
		Matcher m = Pattern.compile(preped, Pattern.DOTALL).matcher(ret);
		if(m.find()) {
			String rep = Matcher.quoteReplacement(replace);
			ret = type == PatternType.All ? m.replaceAll(rep) : m.replaceFirst(rep);
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




	/**
	 * @return one more than the highest index (an FsshList may have gaps)
	 */
	private static int length(List<?> list) {
		if( list instanceof FsshList ) {
			List<Integer> indexes = ((FsshList) list).getIndexes();
			return indexes.isEmpty() ? 0 : indexes.get(indexes.size()-1)+1;
		}
		return list.size();
	}
	/**
	 * ${x:offset:length} as in bash: a negative offset counts from the end, a negative length is an
	 * end counted from the end, and a range past either end gives what is inside it (often nothing).
	 */
	static String substring(String text, int offset, Integer length) {
		int size = text.length();
		if( offset < 0 ) {
			offset += size;
			if( offset < 0 ) {
				return "";
			}
		}
		if( offset > size ) {
			return "";
		}
		int end = size;
		if( length != null ) {
			if( length < 0 ) {
				end = size+length;
				if( end < offset ) {
					throw new RuntimeException(length+": substring expression < 0");
				}
			} else {
				end = (int)Math.min((long)offset+length, size);
			}
		}
		return text.substring(offset, end);
	}
	/**
	 * :offset[:length] with each part evaluated as arithmetic (blank parts stay blank)
	 */
	private static String arithmeticRange(String body, ShellContext sc) {
		String rest = body.substring(1);
		int depth = 0;
		int colon = -1;
		for (int idx = 0; idx < rest.length() && colon < 0; idx++) {
			char c = rest.charAt(idx);
			if( c == '(' ) {
				depth++;
			} else if( c == ')' ) {
				depth--;
			} else if( c == ':' && depth == 0 ) {
				colon = idx;
			}
		}
		String offset = colon < 0 ? rest : rest.substring(0, colon);
		String ret = ":"+arithmetic(offset, sc);
		if( colon >= 0 ) {
			ret += ":"+arithmetic(rest.substring(colon+1), sc);
		}
		return ret;
	}

	private static String arithmetic(String text, ShellContext sc) {
		return text.isBlank() ? text : ""+Arithmetic.expandAndEvaluate(text, sc);
	}
}
