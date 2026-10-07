package us.bringardner.shell.antlr;

import us.bringardner.filesource.sh.FileSourceShParser.BraceBoundContext;
import org.antlr.v4.runtime.tree.ParseTree;
import us.bringardner.filesource.sh.FileSourceShParser.BraceItemContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.filesource.sh.FileSourceShParser;
import us.bringardner.filesource.sh.FileSourceShParser.Arg_command_substitutionContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentContext;
import us.bringardner.filesource.sh.FileSourceShParser.ArgumentPartContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssignStatementContext;
import us.bringardner.filesource.sh.FileSourceShParser.AssociativeArrayValueContext;
import us.bringardner.filesource.sh.FileSourceShParser.BraceExpansionContext;
import us.bringardner.filesource.sh.FileSourceShParser.BraceRangeContext;
import us.bringardner.filesource.sh.FileSourceShParser.MathExpressionContext;
import us.bringardner.filesource.sh.FileSourceShParser.ParameterContext;
import us.bringardner.filesource.sh.FileSourceShParser.PathContext;
import us.bringardner.filesource.sh.FileSourceShParser.Path_segmentContext;
import us.bringardner.filesource.sh.FileSourceShParser.StringContext;
import us.bringardner.filesource.sh.FileSourceShParser.VariableContext;
import us.bringardner.shell.Console;
import us.bringardner.shell.ShellContext;
import us.bringardner.shell.antlr.FileSourceShPreProcessorVisitorImpl.Quoting;
import us.bringardner.shell.antlr.statement.CommandSubstitutionStatement;

public class Argument {

	ArgumentContext context;
	String value;

	/** for a word made by expansion: true if an unquoted * ? or [ in it makes it a glob */
	private boolean glob;

	public Argument(String value) {
		this.value = value;
	}

	public Argument(String value, boolean glob) {
		this.value = value;
		this.glob = glob;
	}

	public Argument(ArgumentContext ctx) {
		context = ctx;
	}

	/*
argument: argumentPart+ ;

argumentPart:
      literal=(ID | NUMBER | ARG_ID | TEXT | SLASH | ... )
    | string
    | argVariable
    | parameter
    | mathExpression
    | arg_command_substitution
    | braceExpansion
    ;
	 */
	public boolean hasValue() {
		return value !=null;
	}
	
	/**
	 * @return the value of the word: the value of its only part (which may be a number or a list),
	 * or the text of all its parts joined together
	 */
	public Object getValue(ShellContext ctx)  {
		if( value !=null) {
			return value;
		}

		List<ArgumentPartContext> parts = context.argumentPart();
		if( parts.size() == 1) {
			String home = tilde(parts, 0, ctx);
			return home != null ? home : getValue(parts.get(0), ctx);
		}
		StringBuilder ret = new StringBuilder();
		for (int idx = 0; idx < parts.size(); idx++) {
			String home = tilde(parts, idx, ctx);
			ret.append(home != null ? home : getValue(parts.get(idx), ctx));
		}
		return ret.toString();
	}

	/**
	 * ~ at the start of a word, alone or before /, is the home directory, as in bash.
	 * @return $HOME, or null if parts[idx] is not such a ~
	 */
	public static String tilde(List<ArgumentPartContext> parts, int idx, ShellContext ctx) {
		if( idx != 0 || parts.get(0).literal == null || parts.get(0).literal.getType() != FileSourceShParser.TILDE ) {
			return null;
		}
		if( parts.size() > 1 && !(""+parts.get(1).getText()).startsWith("/")) {
			return null;
		}
		Object home = ctx.getVariable("HOME");
		return home == null ? null : home.toString();
	}

	public static Object getValue(ArgumentPartContext part, ShellContext ctx)  {
		Object ret;
		if( part.literal != null) {
			ret = part.literal.getText();
		} else if( part.argVariable()!= null) {			
			ret = ctx.expand(ctx.getVariable(part.argVariable()), part.argVariable().getText()); 
		} else if(part.string()!=null) {
			ret = ctx.expandString(part.string());			
		} else if(part.parameter()!=null) {
			Object val = visit(part.parameter(),ctx);
			ret = val == null ? ctx.expand(null, part.parameter().getText()) : val;
		} else if( part.mathExpression()!= null ) {
			ret = visit(part.mathExpression(),ctx);
		} else if(part.arg_command_substitution()!=null) {
			ret = visit(part.arg_command_substitution(),ctx);
		} else if( part.procSubst != null ) {
			ret = CommandSubstitutionStatement.processSubstitution(part.procSubst.getText(), ctx);
		} else if( part.braceExpansion()!=null) {
			throw new RuntimeException("brace expantion must be done before calling getValue");
		} else {
			throw new RuntimeException("Not a valid argument "+part.getText());
		}
		return ret;
	}

	/**
	 * @return true if the word has an unquoted *, ? or [, so it names files
	 */
	public boolean hasUnquotedWildcard() {
		if( context == null ) {
			return glob;
		}
		return hasUnquotedWildcard(context);
	}

	private static boolean hasUnquotedWildcard(ArgumentContext word) {
		for(ArgumentPartContext part : word.argumentPart()) {
			if( part.literal != null ) {
				int type = part.literal.getType();
				if( type == FileSourceShParser.STAR || type == FileSourceShParser.QUESTION
						|| type == FileSourceShParser.LSQUARE || type == FileSourceShParser.EXTGLOB) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The words a word becomes when a statement runs: brace expansion, then word splitting of
	 * its unquoted expansions ($x, ${x}, $(cmd), `cmd`, $((...))) on IFS, as in bash. Literal
	 * text and quoted strings are not split; they join the neighboring field. An unquoted
	 * expansion that is empty and has nothing next to it gives no word at all. "$@" gives one
	 * word per positional parameter.
	 * 
	 * @return the words, or null if the word stays as it is (no braces and no unquoted expansion)
	 */
	public static List<Argument> expandWord(ArgumentContext word, ShellContext ctx, boolean split) throws IOException {
		List<String> braces = expandBraces(word, ctx);
		if( braces != null ) {
			List<Argument> ret = new ArrayList<>();
			// {a,b}*.txt: each word is still a pattern
			boolean wildcard = hasUnquotedWildcard(word);
			for(String b : braces) {
				ret.add(new Argument(b, wildcard));
			}
			return ret;
		}
		if( !split || !hasExpansion(word)) {
			return null;
		}
		return new WordSplitter(ctx).split(word);
	}

	private static boolean hasExpansion(ArgumentContext word) {
		for(ArgumentPartContext part : word.argumentPart()) {
			if( isExpansion(part) || quotedAt(part) != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the start and end of $@ or ${@} in the text inside a double-quoted string part (not
	 * inside $( ) or ` `), or null if there is none
	 */
	private static int[] quotedAt(ArgumentPartContext part) {
		if( part.string() == null || part.string().DQ_STRING() == null ) {
			return null;
		}
		String text = ShellContext.dq(part.string().DQ_STRING().getText());
		return findAt(text.substring(1, text.length()-1));
	}

	private static final java.util.regex.Pattern QUOTED_ARRAY = java.util.regex.Pattern.compile("\\$\\{(!?)([a-zA-Z_][a-zA-Z_0-9]*)\\[@\\]\\}");

	static int[] findAt(String body) {
		int n = body.length();
		for (int idx = 0; idx < n; idx++) {
			char c = body.charAt(idx);
			if( c == '\\' ) {
				idx++;
			} else if( c == '`' ) {
				idx++;
				while( idx < n && body.charAt(idx) != '`' ) {
					if( body.charAt(idx) == '\\' ) {
						idx++;
					}
					idx++;
				}
			} else if( c == '$' && body.startsWith("(", idx+1)) {
				int depth = 0;
				for (idx++; idx < n; idx++) {
					char d = body.charAt(idx);
					if( d == '\\' ) {
						idx++;
					} else if( d == '(' ) {
						depth++;
					} else if( d == ')' && --depth == 0 ) {
						break;
					}
				}
			} else if( c == '$' && body.startsWith("@", idx+1)) {
				return new int[] {idx, idx+2};
			} else if( c == '$' && body.startsWith("{@}", idx+1)) {
				return new int[] {idx, idx+4};
			} else if( c == '$' && body.startsWith("{", idx+1)) {
				// ${name[@]}: an array's elements
				java.util.regex.Matcher m = QUOTED_ARRAY.matcher(body).region(idx, n);
				if( m.lookingAt()) {
					return new int[] {idx, m.end()};
				}
			}
		}
		return null;
	}

	private static boolean isExpansion(ArgumentPartContext part) {
		return part.argVariable() != null || part.parameter() != null
				|| part.arg_command_substitution() != null || part.mathExpression() != null;
	}

	private static boolean hasWildcard(String text) {
		return text.indexOf('*') >= 0 || text.indexOf('?') >= 0 || text.indexOf('[') >= 0;
	}

	/**
	 * Builds the fields of one word.
	 */
	private static class WordSplitter {
		final ShellContext ctx;
		final String ifs;
		final List<Argument> fields = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean currentIsField = false;
		boolean currentGlob = false;

		WordSplitter(ShellContext ctx) {
			this.ctx = ctx;
			Object tmp = ctx.getVariable(Console.IFS);
			// unset IFS splits on space, tab and newline; an empty IFS does not split
			this.ifs = tmp == null ? " \t\n" : tmp.toString();
		}

		List<Argument> split(ArgumentContext word) {
			List<ArgumentPartContext> parts = word.argumentPart();
			for(ArgumentPartContext part : parts) {
				String home = part == parts.get(0) ? tilde(parts, 0, ctx) : null;
				if( home != null ) {
					current.append(home);
					currentIsField = true;
				} else if( isExpansion(part)) {
					Object val = getValue(part, ctx);
					String text = val instanceof List<?> ? join((List<?>) val) : ""+val;
					addSplit(text);
				} else if( quotedAt(part) != null ) {
					addQuotedAt(part);
				} else {
					String text = ""+getValue(part, ctx);
					current.append(text);
					currentIsField = true;
					if( part.literal != null && hasWildcard(text)) {
						currentGlob = true;
					}
				}
			}
			finish();
			return fields;
		}

		private static String join(List<?> list) {
			StringBuilder ret = new StringBuilder();
			for(Object o : list) {
				if( ret.length() > 0 ) {
					ret.append(' ');
				}
				ret.append(o);
			}
			return ret.toString();
		}

		/**
		 * "pre$@post": the text before $@ joins the first parameter, the text after joins the
		 * last, and each parameter is its own field. No parameters give no field (unless there is
		 * text before or after).
		 */
		private void addQuotedAt(ArgumentPartContext part) {
			int [] at = quotedAt(part);
			String text = ShellContext.dq(part.string().DQ_STRING().getText());
			String body = text.substring(1, text.length()-1);
			String prefix = FileSourceShPreProcessorVisitorImpl.processString(body.substring(0, at[0]), ctx, Quoting.DOUBLE_QUOTED);
			String suffix = FileSourceShPreProcessorVisitorImpl.processString(body.substring(at[1]), ctx, Quoting.DOUBLE_QUOTED);
			String atText = body.substring(at[0], at[1]);
			java.util.regex.Matcher am = QUOTED_ARRAY.matcher(atText);
			List<Object> params = !am.matches() ? ctx.getPositionalParameterValues()
					: am.group(1).equals("!") ? Parameter.keys(ctx.getVariable(am.group(2)))
					: Parameter.values(ctx.getVariable(am.group(2)));
			if( params.isEmpty()) {
				if( !prefix.isEmpty() || !suffix.isEmpty()) {
					appendQuoted(prefix+suffix);
				}
				return;
			}
			for (int idx = 0, sz = params.size(); idx < sz; idx++) {
				if( idx > 0 ) {
					finish();
				}
				appendQuoted((idx == 0 ? prefix : "")+params.get(idx)+(idx == sz-1 ? suffix : ""));
			}
		}

		private void appendQuoted(String text) {
			current.append(text);
			currentIsField = true;
		}

		private void finish() {
			if( currentIsField ) {
				fields.add(new Argument(current.toString(), currentGlob));
			}
			current = new StringBuilder();
			currentIsField = false;
			currentGlob = false;
		}

		private void append(String text) {
			current.append(text);
			currentIsField = true;
			if( hasWildcard(text)) {
				currentGlob = true;
			}
		}

		private boolean isIfsSpace(char c) {
			return ifs.indexOf(c) >= 0 && Character.isWhitespace(c);
		}

		private boolean isIfsOther(char c) {
			return ifs.indexOf(c) >= 0 && !Character.isWhitespace(c);
		}

		/**
		 * Split expanded text on IFS: runs of IFS whitespace separate fields and are trimmed at
		 * both ends; each other IFS character ends a field (so a::b has an empty field).
		 */
		private void addSplit(String text) {
			if( ifs.isEmpty()) {
				if( !text.isEmpty()) {
					append(text);
				}
				return;
			}
			int n = text.length();
			int idx = 0;
			// leading IFS whitespace ends the field before it
			while( idx < n && isIfsSpace(text.charAt(idx))) {
				idx++;
			}
			if( idx > 0 ) {
				finish();
			}
			boolean first = true;
			while( idx < n ) {
				if( !first ) {
					finish();
				}
				first = false;
				int start = idx;
				while( idx < n && ifs.indexOf(text.charAt(idx)) < 0 ) {
					idx++;
				}
				append(text.substring(start, idx));
				if( idx >= n ) {
					break;
				}
				// the delimiter: IFS whitespace, at most one other IFS character, IFS whitespace
				while( idx < n && isIfsSpace(text.charAt(idx))) {
					idx++;
				}
				if( idx < n && isIfsOther(text.charAt(idx))) {
					idx++;
					while( idx < n && isIfsSpace(text.charAt(idx))) {
						idx++;
					}
				}
				if( idx >= n ) {
					// a trailing delimiter ends the field
					finish();
				}
			}
		}
	}

	/**
	 * Brace expansion of a word: prefix{a,b}suffix gives prefixasuffix and prefixbsuffix.
	 * 
	 * @return the words, or null if the word has no braces
	 */
	public static List<String> expandBraces(ArgumentContext word, ShellContext ctx) throws IOException {
		List<ArgumentPartContext> parts = word.argumentPart();
		boolean any = false;
		for(ArgumentPartContext part : parts) {
			any |= part.braceExpansion() != null;
		}
		if( !any ) {
			return null;
		}
		// every combination, left to right: {x,y}{1,2} is x1 x2 y1 y2
		List<String> ret = new ArrayList<>();
		ret.add("");
		for (int idx = 0; idx < parts.size(); idx++) {
			ArgumentPartContext part = parts.get(idx);
			List<String> alternatives;
			if( part.braceExpansion() != null ) {
				alternatives = expandBraces(part.braceExpansion(), ctx);
			} else {
				String home = tilde(parts, idx, ctx);
				alternatives = List.of(home != null ? home : ""+getValue(part, ctx));
			}
			ret = product(ret, alternatives);
		}
		return ret;
	}

	private static List<String> product(List<String> left, List<String> right) {
		List<String> ret = new ArrayList<>();
		for(String l : left) {
			for(String r : right) {
				ret.add(l+r);
			}
		}
		return ret;
	}

	//	braceExpansion: LCURLY (braceRange|braceArgList) RCURLY
	private static List<String> expandBraces(BraceExpansionContext exp, ShellContext ctx) throws IOException {
		if( exp.braceRange()!=null) {
			return expandBraces(exp.braceRange(),ctx);
		} else if(exp.braceArgList()!=null) {
			// the items are between the commas; an item may be empty ({a,} is a and an empty word)
			List<String> ret = new ArrayList<>();
			BraceItemContext item = null;
			for(ParseTree kid : exp.braceArgList().children) {
				if( kid instanceof BraceItemContext ) {
					item = (BraceItemContext) kid;
				} else {
					ret.addAll(item == null ? List.of("") : expandItem(item, ctx));
					item = null;
				}
			}
			ret.addAll(item == null ? List.of("") : expandItem(item, ctx));
			return ret;
		} else {
			// {a}: text, as written
			return List.of(exp.getText());
		}
	}

	/** an item of a list: text and nested braces (b{1,2} is b1 b2) */
	private static List<String> expandItem(BraceItemContext item, ShellContext ctx) throws IOException {
		List<String> ret = new ArrayList<>();
		ret.add("");
		for(ParseTree kid : item.children) {
			if( kid instanceof BraceExpansionContext ) {
				ret = product(ret, expandBraces((BraceExpansionContext) kid, ctx));
			} else {
				ret = product(ret, List.of(braceItem((AssociativeArrayValueContext) kid, ctx)));
			}
		}
		return ret;
	}

	/**
	 * A bare name in braces is text, as in bash ({a,b} and {a..z} do not read variables a and b).
	 */
	private static String braceItem(AssociativeArrayValueContext item, ShellContext ctx) throws IOException {
		if( item.variable() != null && item.variable().idOnly != null ) {
			return item.getText();
		}
		return visit(item, ctx);
	}

	//	braceRange: start=associativeArrayValue DOT_DOT end=associativeArrayValue (DOT_DOT incr=associativeArrayValue);
	private static List<String> expandBraces(BraceRangeContext range, ShellContext ctx) throws IOException {
		List<String>  ret = new ArrayList<>();
		String startStr = bound(range.start, ctx);
		String endStr   = bound(range.end, ctx);
		boolean isChar = Character.isLetter(startStr.charAt(0));
		int start = isChar?startStr.charAt(0): Integer.parseInt( startStr);
		int end = isChar?endStr.charAt(0): Integer.parseInt( endStr);

		int inc = 1;
		if( range.incr !=null) {
			// a step for letters too: {a..e..2} is a c e
			inc = Math.abs(Integer.parseInt(bound(range.incr, ctx)));
			if( inc == 0 ) {
				// bash treats an increment of 0 as 1
				inc = 1;
			}
		}
		// {01..10}: numbers as wide as the wider end, with zeros
		int width = 0;
		if( !isChar && (startStr.matches("-?0\\d+") || endStr.matches("-?0\\d+"))) {
			width = Math.max(startStr.length(), endStr.length());
		}

		if(start <= end) {
			for(int idx=start; idx <=end; idx += inc) {
				ret.add(isChar ? ""+((char)idx) : pad(idx, width));
			}
		} else {
			for(int idx=start; idx >=end; idx -= inc) {
				ret.add(isChar ? ""+((char)idx) : pad(idx, width));
			}
		}

		return ret;
	}

	private static String bound(BraceBoundContext b, ShellContext ctx) throws IOException {
		return (b.MINUS() != null ? "-" : "")+braceItem(b.associativeArrayValue(), ctx);
	}

	private static String pad(int n, int width) {
		String digits = ""+Math.abs(n);
		int size = width-(n < 0 ? 1 : 0);
		while( digits.length() < size ) {
			digits = "0"+digits;
		}
		return (n < 0 ? "-" : "")+digits;
	}


	public static Object visit(AssignStatementContext assignStatement, ShellContext ctx)  {
		String ret = assignStatement.getText();
		if( ret.indexOf('$')>=0) {
			ret = FileSourceShPreProcessorVisitorImpl.processString(ret, ctx);
		}
		return ret;
	}

	public static Object visit(Arg_command_substitutionContext arg_command_substitution, ShellContext ctx)  {
		CommandSubstitutionStatement cs = new CommandSubstitutionStatement(arg_command_substitution);
		try {
			cs.process(ctx);
		} catch (IOException e) {
			ctx.stderr.println(e.getMessage());
		}
		// the output, even if the command failed (its errors went to stderr)
		return cs.getStdout() == null ? "" : cs.getStdout();
	}

	public static Object visit(ParameterContext parameter, ShellContext ctx)  {
		Parameter p = new Parameter(parameter);
		return p.evaluate(ctx);
	}

	public static Object visit(MathExpressionContext mathExpression, ShellContext ctx)  {
		return Arithmetic.expansion(mathExpression.getText(), ctx);
	}

	//path:  (path_segment| SLASH)+
	public static String visit(PathContext path, ShellContext ctx) {
		return path.getText();
	}

	/*
	 * path_segment: 
		  TILDE 
		| ID
		| variable
        | DOT_DOT
        | DOT
        | STAR
        | QUESTION
        | string
        | MINUS
        | MINUS_MINUS
		;

	 */
	public static String visit(Path_segmentContext path_segment, ShellContext ctx) throws IOException {
		if( path_segment.string() != null ) {
			return visit(path_segment.string(), ctx);
		} //else if( path_segment.variable()!=null ) {
			//return visit(path_segment.variable(),ctx);
		//} 
		return path_segment.getText();
	}

	/*
	 * variable:
        idOnly=ID ( associative_index | array_index)?
        |VARIABLE (associative_index | array_index)?

	 */
	public static String visit(VariableContext variable, ShellContext ctx)  {
		Object obj = ctx.getVariable(variable);
		String ret = ""+obj;
		return ret;
	}

	//string : DQ_STRING | SQ_STRING | ESC;
	public static String visit(StringContext string, ShellContext ctx)  {
		String ret = ctx.expandString(string);
		
		return ret;
	}

	public ArgumentContext getContext() {
		return context;
	}

	public void setContext(ArgumentContext Context) {
		this.context = Context;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Argument) {
			Argument other = (Argument) obj;
			return context.getText().equals(other.context.getText());
		}

		return false;
	}

	@Override
	public String toString() {
		if( value !=null) {
			return value;
		}
		return context.getText();
	}

	/*

associativeArrayValue
    : string
    | NUMBER
    | boolean
    | variable
    | mathExpression
    | parameter
    ;
	 */
	public static String visit(AssociativeArrayValueContext context, ShellContext ctx) throws IOException {
		String ret = context.getText();
		
	
		if( context.variable()!= null) {
			ret = visit(context.variable(), ctx);
		} else if( context.NUMBER()!=null) {
			ret = context.getText();
		} else if( context.boolean_()!=null) {
			ret = context.getText();
		} else if( context.mathExpression()!=null) {
			ret = ""+visit(context.mathExpression(),ctx);
		} else if( context.parameter()!=null) {
			ret = ""+visit(context.parameter(),ctx);
		} else {
			throw new IOException("Invalid AssociativeArrayValueContext "+context);
		}
		
			
		return ret;
	}



}
