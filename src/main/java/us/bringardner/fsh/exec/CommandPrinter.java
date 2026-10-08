package us.bringardner.fsh.exec;

import java.util.ArrayList;
import java.util.List;

import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Word;

/**
 * Commands as bash prints them (declare -f, type, the body of $( ) in a word): a port of bash's
 * print_cmd.c, so a function comes back in bash's layout, four spaces deep, with ; between
 * commands and here-document bodies after their line.
 */
final class CommandPrinter {

	private final Executor executor;
	private final StringBuilder out = new StringBuilder();
	private int indentation;
	/** how much deeper each level is (0 in the environment's one-line form) */
	private int amount = 4;
	private int skipThisIndent;
	private boolean wasHeredoc;
	private List<Ast.Redirect> deferredHeredocs;
	private int printingConnection;
	private int insideFunctionDef;
	private final boolean printingComsub;

	private CommandPrinter(Executor executor, boolean comsub) {
		this.executor = executor;
		this.printingComsub = comsub;
	}

	/** name () { body } as declare -f prints it (function name () for a name that is not one) */
	static String function(Executor executor, String name, Ast.Command body) {
		CommandPrinter p = new CommandPrinter(executor, false);
		return p.namedFunction(Executor.isName(name) || name.matches("[^\\s$`'\"|&;()<>=]+") ? name : "function "+name, body);
	}

	/** () {  body\n}: a function as bash puts it in the environment (export -f) */
	static String exported(Executor executor, Ast.Command body) {
		CommandPrinter p = new CommandPrinter(executor, false);
		p.out.append("() ");
		p.indentation = 1;
		p.amount = 0;
		p.insideFunctionDef++;
		p.out.append("{ ");
		p.functionBody(body);
		p.indentation = 0;
		p.amount = 4;
		p.insideFunctionDef--;
		p.functionEnd(body);
		return p.out.toString();
	}

	/** the commands of $( ) as bash keeps them in a word: $( echo  a ;echo b ) is $(echo a; echo b) */
	static String comsub(Executor executor, Ast.Sequence body) {
		CommandPrinter p = new CommandPrinter(executor, true);
		p.make(p.tree(body));
		return p.out.toString();
	}

	// ------------------------------------------------------------------ bash's command tree

	/**
	 * A command as bash holds it: a connection (first, connector, second: ; newline & | && ||),
	 * built left-heavy as bash's parser builds it, or one command; with ! and time.
	 */
	private static final class Node {
		Node first;
		String connector;
		Node second;
		Ast.Command command;
		boolean invert;
		boolean time;
		boolean timePosix;
		/** 2>&1 of |& (bash adds it to the command before the |) */
		boolean stderrToo;
	}

	private Node tree(Ast.Sequence seq) {
		if( seq == null || seq.items.isEmpty()) {
			return null;
		}
		Node ret = null;
		for (int i = 0; i < seq.items.size(); i++) {
			Ast.Item item = seq.items.get(i);
			Node n = tree(item.command);
			boolean last = i == seq.items.size()-1;
			if( ret == null ) {
				ret = n;
			} else {
				Node c = new Node();
				c.first = ret;
				Ast.Item before = seq.items.get(i-1);
				c.connector = before.background ? "&" : separator(before, item);
				c.second = n;
				ret = c;
			}
			if( last && item.background ) {
				Node c = new Node();
				c.first = ret;
				c.connector = "&";
				ret = c;
			}
		}
		return ret;
	}

	/** ; or a newline between two commands, as they were written */
	private String separator(Ast.Item a, Ast.Item b) {
		String between = executor.text(a.command.end, b.command.start);
		return between.indexOf('\n') >= 0 ? "\n" : ";";
	}

	private Node tree(Ast.AndOr ao) {
		Node ret = tree(ao.pipelines.get(0));
		for (int i = 1; i < ao.pipelines.size(); i++) {
			Node c = new Node();
			c.first = ret;
			c.connector = ao.ops.get(i-1);
			c.second = tree(ao.pipelines.get(i));
			ret = c;
		}
		return ret;
	}

	private Node tree(Ast.Pipeline p) {
		Node ret = null;
		for (int i = 0; i < p.commands.size(); i++) {
			Node n = new Node();
			n.command = p.commands.get(i);
			n.stderrToo = i < p.stderrToo.size() && p.stderrToo.get(i);
			if( ret == null ) {
				ret = n;
			} else {
				Node c = new Node();
				c.first = ret;
				c.connector = "|";
				c.second = n;
				ret = c;
			}
		}
		ret.invert = p.negated;
		ret.time = p.timed;
		ret.timePosix = p.timePosix;
		return ret;
	}

	// ------------------------------------------------------------------ printing

	private String namedFunction(String name, Ast.Command body) {
		out.append(name).append(" () \n");
		indentation += amount;
		insideFunctionDef++;
		out.append("{ \n");
		functionBody(body);
		indentation -= amount;
		insideFunctionDef--;
		functionEnd(body);
		return out.toString();
	}

	/** the commands of a function: a { } group's own, or the one command */
	private void functionBody(Ast.Command body) {
		if( body instanceof Ast.BraceGroup g ) {
			make(tree(g.body));
		} else {
			Node n = new Node();
			n.command = body;
			make(n);
		}
		printDeferredHeredocs("");
	}

	private void functionEnd(Ast.Command body) {
		if( body instanceof Ast.BraceGroup && !body.redirects.isEmpty()) {
			newline("} ");
			redirectionList(body.redirects);
		} else {
			newline("}");
			wasHeredoc = false;
		}
	}

	private void make(Node n) {
		if( n == null ) {
			return;
		}
		if( skipThisIndent > 0 ) {
			skipThisIndent--;
		} else {
			indent(indentation);
		}
		if( n.time ) {
			out.append("time ");
			if( n.timePosix ) {
				out.append("-p ");
			}
		}
		if( n.invert ) {
			out.append("! ");
		}
		if( n.command == null ) {
			connection(n);
			return;
		}
		Ast.Command c = n.command;
		switch (c) {
		case Ast.SimpleCommand s -> simple(s, n.stderrToo);
		case Ast.For f -> forCommand(f);
		case Ast.ArithFor f -> arithFor(f);
		case Ast.Select s -> select(s);
		case Ast.Case k -> caseCommand(k);
		case Ast.Loop l -> loop(l);
		case Ast.If i -> ifCommand(i, 0);
		case Ast.Arith a -> out.append(arithText(a));
		case Ast.Cond k -> {
			out.append("[[ ");
			cond(k.expression);
			out.append(" ]]");
		}
		case Ast.FunctionDef f -> functionDef(f);
		case Ast.BraceGroup g -> group(g);
		case Ast.Subshell s -> {
			out.append("( ");
			skipThisIndent++;
			make(tree(s.body));
			printDeferredHeredocs("");
			out.append(" )");
			wasHeredoc = false;
		}
		case Ast.Coproc k -> {
			out.append("coproc ");
			if( !(k.body instanceof Ast.SimpleCommand)) {
				out.append(k.name == null ? "COPROC" : k.name).append(' ');
			}
			skipThisIndent++;
			Node b = new Node();
			b.command = k.body;
			make(b);
		}
		default -> out.append(executor.text(c).trim());
		}
		if( !(c instanceof Ast.SimpleCommand) && !(c instanceof Ast.FunctionDef) && !c.redirects.isEmpty()) {
			out.append(' ');
			redirectionList(c.redirects);
		}
		if( !(c instanceof Ast.SimpleCommand) && n.stderrToo ) {
			out.append(" 2>&1");
		}
	}

	private void connection(Node n) {
		skipThisIndent++;
		printingConnection++;
		make(n.first);
		switch (n.connector) {
		case "&":
		case "|": {
			printDeferredHeredocs(" "+n.connector);
			if( !n.connector.equals("&") || n.second != null ) {
				out.append(' ');
				skipThisIndent++;
			}
			break;
		}
		case "&&":
		case "||":
			printDeferredHeredocs(" "+n.connector+" ");
			if( n.second != null ) {
				skipThisIndent++;
			}
			break;
		default: {
			// ; and newline
			String c = n.connector;
			String s = printingComsub ? c : ";";
			boolean wasNewline = deferredHeredocs == null && !wasHeredoc && c.equals("\n");
			if( deferredHeredocs == null ) {
				if( !wasHeredoc ) {
					out.append(s);
				} else {
					wasHeredoc = false;
				}
			} else {
				printDeferredHeredocs(insideFunctionDef > 0 ? "" : ";");
			}
			if( insideFunctionDef > 0 ) {
				out.append('\n');
			} else if( printingComsub && c.equals("\n") && !wasNewline ) {
				out.append('\n');
			} else {
				if( c.equals(";")) {
					out.append(' ');
				}
				if( n.second != null ) {
					skipThisIndent++;
				}
			}
		}
		}
		make(n.second);
		if( printingConnection == 1 ) {
			printDeferredHeredocs("");
		}
		printingConnection--;
	}

	private void simple(Ast.SimpleCommand s, boolean stderrToo) {
		List<String> words = new ArrayList<>();
		for(Ast.Assignment a : s.assignments) {
			words.add(assignment(a));
		}
		for(Word w : s.words) {
			words.add(w.assignment != null ? assignment(w.assignment) : word(w));
		}
		out.append(String.join(" ", words));
		List<Ast.Redirect> redirects = new ArrayList<>(s.redirects);
		if( !redirects.isEmpty() || stderrToo ) {
			if( !words.isEmpty()) {
				out.append(' ');
			}
			if( stderrToo ) {
				// |&: bash prints it as 2>&1 |
				out.append("2>&1");
				if( !redirects.isEmpty()) {
					out.append(' ');
				}
			}
			if( !redirects.isEmpty()) {
				redirectionList(redirects);
			}
		}
	}

	/** x=v as written (its $( ) as bash keeps them) */
	private String assignment(Ast.Assignment a) {
		List<Word.CommandSub> subs = new ArrayList<>();
		if( a.value != null ) {
			commandSubs(a.value.parts, subs);
		}
		if( a.array != null ) {
			for(Word w : a.array) {
				commandSubs(w.parts, subs);
			}
		}
		String ret = normalized(executor.text(a).trim(), subs);
		return a.value == null ? ret : ansiC(ret, a.value.parts);
	}

	/** a word as written, with the commands of a $( ) in it printed as bash keeps them */
	private String word(Word w) {
		List<Word.CommandSub> subs = new ArrayList<>();
		commandSubs(w.parts, subs);
		return ansiC(normalized(w.raw == null ? "" : w.raw, subs), w.parts);
	}

	/** a word as bash keeps it: $'..' as the characters in '..' (set -x shows array words so) */
	static String asKept(Word w) {
		return ansiC(w.raw == null ? "" : w.raw, w.parts);
	}

	/** $'..' as bash keeps it: the characters, in '..' */
	private static String ansiC(String ret, List<Word.Part> parts) {
		int from = 0;
		for(Word.Part p : parts) {
			if( p instanceof Word.AnsiC a ) {
				String written = "$'"+a.text()+"'";
				int at = ret.indexOf(written, from);
				if( at >= 0 ) {
					String kept = "'"+us.bringardner.fsh.ShellContext.ansiC(a.text()).replace("'", "'\\''")+"'";
					ret = ret.substring(0, at)+kept+ret.substring(at+written.length());
					from = at+kept.length();
				}
			}
		}
		return ret;
	}

	private String normalized(String raw, List<Word.CommandSub> subs) {
		int from = 0;
		for(Word.CommandSub c : subs) {
			if( c.body() == null ) {
				continue;
			}
			String written = "$("+c.text()+")";
			int at = raw.indexOf(written, from);
			if( at < 0 ) {
				continue;
			}
			String printed = "$("+comsub(executor, c.body())+")";
			raw = raw.substring(0, at)+printed+raw.substring(at+written.length());
			from = at+printed.length();
		}
		return raw;
	}

	private static void commandSubs(List<Word.Part> parts, List<Word.CommandSub> out) {
		for(Word.Part p : parts) {
			if( p instanceof Word.CommandSub c ) {
				out.add(c);
			} else if( p instanceof Word.DoubleQuoted d ) {
				commandSubs(d.parts(), out);
			}
		}
	}

	private String words(List<Word> words) {
		List<String> ret = new ArrayList<>();
		for(Word w : words) {
			ret.add(word(w));
		}
		return String.join(" ", ret);
	}

	private void forCommand(Ast.For f) {
		out.append("for ").append(f.variable).append(" in ").append(f.words == null ? "\"$@\"" : words(f.words));
		out.append(';');
		newline("do\n");
		body(f.body);
		newline("done");
	}

	private void select(Ast.Select f) {
		out.append("select ").append(f.variable).append(" in ").append(f.words == null ? "\"$@\"" : words(f.words));
		out.append(';');
		newline("do\n");
		body(f.body);
		newline("done");
	}

	/** a loop's body, one deeper, with the ; after its last command */
	private void body(Ast.Sequence seq) {
		indentation += amount;
		make(tree(seq));
		printDeferredHeredocs("");
		semicolon();
		indentation -= amount;
	}

	private void arithFor(Ast.ArithFor f) {
		out.append("for ((").append(arith(f.init)).append("; ").append(arith(f.condition)).append("; ").append(arith(f.step)).append("))");
		newline("do\n");
		body(f.body);
		newline("done");
	}

	private static String arith(Word w) {
		return w == null || w.raw == null ? "" : w.raw;
	}

	/** (( expression )) as written */
	private String arithText(Ast.Arith a) {
		String t = executor.text(a.start, a.end).trim();
		return t.startsWith("((") ? t : "(("+arith(a.expression)+"))";
	}

	private void caseCommand(Ast.Case k) {
		out.append("case ").append(word(k.subject)).append(" in ");
		boolean first = true;
		indentation += amount;
		for(Ast.CaseClause clause : k.clauses) {
			if( !printingComsub || !first ) {
				newline("");
			}
			first = false;
			if( !clause.patterns.isEmpty() && "esac".equals(clause.patterns.get(0).raw)) {
				out.append('(');
			}
			out.append(String.join(" | ", clause.patterns.stream().map(this::word).toList()));
			out.append(")\n");
			indentation += amount;
			make(tree(clause.body));
			indentation -= amount;
			printDeferredHeredocs("");
			newline(clause.terminator == null || clause.terminator.equals(";;") ? ";;" : clause.terminator);
		}
		indentation -= amount;
		newline("esac");
	}

	private void loop(Ast.Loop l) {
		out.append(l.until ? "until " : "while ");
		skipThisIndent++;
		make(tree(l.condition));
		printDeferredHeredocs("");
		semicolon();
		if( wasHeredoc ) {
			indent(indentation);
			out.append("do\n");
			wasHeredoc = false;
		} else {
			out.append(" do\n");
		}
		indentation += amount;
		make(tree(l.body));
		printDeferredHeredocs("");
		indentation -= amount;
		semicolon();
		newline("done");
	}

	/** if, with elif as an if in the else (as bash holds it) */
	private void ifCommand(Ast.If i, int from) {
		out.append("if ");
		skipThisIndent++;
		make(tree(i.conditions.get(from)));
		printDeferredHeredocs("");
		semicolon();
		if( wasHeredoc ) {
			indent(amount);
			out.append("then\n");
			wasHeredoc = false;
		} else {
			out.append(" then\n");
		}
		indentation += amount;
		make(tree(i.bodies.get(from)));
		printDeferredHeredocs("");
		indentation -= amount;
		if( from+1 < i.conditions.size() || i.elseBody != null ) {
			semicolon();
			newline("else\n");
			indentation += amount;
			if( from+1 < i.conditions.size()) {
				indent(indentation);
				ifCommand(i, from+1);
			} else {
				make(tree(i.elseBody));
			}
			printDeferredHeredocs("");
			indentation -= amount;
		}
		semicolon();
		newline("fi");
	}

	private void cond(Ast.CondExpr e) {
		switch (e) {
		case Ast.CondAnd a -> {
			condOperand(a.left(), a.left() instanceof Ast.CondOr);
			out.append(" && ");
			condOperand(a.right(), a.right() instanceof Ast.CondOr);
		}
		case Ast.CondOr o -> {
			cond(o.left());
			out.append(" || ");
			cond(o.right());
		}
		case Ast.CondNot n -> {
			out.append("! ");
			condOperand(n.expression(), n.expression() instanceof Ast.CondAnd || n.expression() instanceof Ast.CondOr);
		}
		case Ast.CondUnary u -> out.append(u.op()).append(' ').append(word(u.operand()));
		case Ast.CondBinary b -> out.append(word(b.left())).append(' ').append(b.op()).append(' ').append(word(b.right()));
		case Ast.CondWord w -> out.append(word(w.word()));
		}
	}

	/** ( ... ) where the grouping needs it (the parser does not keep the parentheses) */
	private void condOperand(Ast.CondExpr e, boolean parens) {
		if( parens ) {
			out.append("( ");
		}
		cond(e);
		if( parens ) {
			out.append(" )");
		}
	}

	/** function name () { ... } inside a function: as bash prints it, with the keyword */
	private void functionDef(Ast.FunctionDef f) {
		out.append("function ").append(f.name).append(" () \n");
		indent(indentation);
		out.append("{ \n");
		insideFunctionDef++;
		indentation += amount;
		functionBody(f.body);
		indentation -= amount;
		insideFunctionDef--;
		functionEnd(f.body);
	}

	private void group(Ast.BraceGroup g) {
		out.append("{ ");
		if( insideFunctionDef == 0 ) {
			skipThisIndent++;
		} else {
			out.append('\n');
			indentation += amount;
		}
		make(tree(g.body));
		printDeferredHeredocs("");
		if( insideFunctionDef > 0 ) {
			out.append('\n');
			indentation -= amount;
			indent(indentation);
		} else {
			semicolon();
			out.append(' ');
		}
		out.append('}');
		wasHeredoc = false;
	}

	// ------------------------------------------------------------------ redirections

	private void redirectionList(List<Ast.Redirect> redirects) {
		List<Ast.Redirect> heredocs = new ArrayList<>();
		wasHeredoc = false;
		for (int i = 0; i < redirects.size(); i++) {
			Ast.Redirect r = redirects.get(i);
			if( r.hereDoc != null ) {
				heredocHeader(r);
				heredocs.add(r);
			} else {
				redirection(r);
			}
			if( i+1 < redirects.size()) {
				out.append(' ');
			}
		}
		if( !heredocs.isEmpty() && printingConnection > 0 ) {
			if( deferredHeredocs == null ) {
				deferredHeredocs = new ArrayList<>();
			}
			deferredHeredocs.addAll(heredocs);
		} else if( !heredocs.isEmpty()) {
			heredocBodies(heredocs);
		}
	}

	private void heredocHeader(Ast.Redirect r) {
		if( r.fdVariable != null ) {
			out.append('{').append(r.fdVariable).append('}');
		} else if( r.fd != null && r.fd != 0 ) {
			out.append(r.fd);
		}
		out.append(r.op);
		if( r.hereDoc.quoted ) {
			out.append('\'').append(r.hereDoc.delimiter.replace("'", "'\\''")).append('\'');
		} else {
			out.append(r.hereDoc.delimiter);
		}
	}

	private void heredocBodies(List<Ast.Redirect> heredocs) {
		out.append('\n');
		for(Ast.Redirect r : heredocs) {
			out.append(r.hereDoc.body == null ? "" : r.hereDoc.body).append(r.hereDoc.delimiter);
			out.append('\n');
		}
		wasHeredoc = true;
	}

	/** the bodies of the here-documents of the commands before a connector, after the connector */
	private void printDeferredHeredocs(String connector) {
		boolean show = !connector.isEmpty() && !connector.equals(";");
		if( show ) {
			out.append(connector);
		}
		if( deferredHeredocs != null ) {
			heredocBodies(deferredHeredocs);
			if( show ) {
				out.append(' ');
			}
			wasHeredoc = true;
		}
		deferredHeredocs = null;
	}

	private void redirection(Ast.Redirect r) {
		String target = r.target == null ? "" : word(r.target);
		String var = r.fdVariable != null ? "{"+r.fdVariable+"}" : null;
		switch (r.op) {
		case "<", "<>", "<<<" -> {
			out.append(var != null ? var : r.fd != null && r.fd != 0 ? String.valueOf(r.fd) : "");
			out.append(r.op).append(' ').append(target);
		}
		case ">", ">>", ">|" -> {
			out.append(var != null ? var : r.fd != null && r.fd != 1 ? String.valueOf(r.fd) : "");
			out.append(r.op).append(' ').append(target);
		}
		case "&>", "&>>" -> out.append(r.op).append(' ').append(target);
		case "<&", ">&" -> {
			int usual = r.op.equals("<&") ? 0 : 1;
			int fd = r.fd == null ? usual : r.fd;
			String head = var != null ? var : null;
			if( target.equals("-")) {
				// closed: bash says >&- for both
				out.append(head != null ? head : String.valueOf(fd)).append(">&-");
			} else if( target.matches("[0-9]+-?")) {
				// a descriptor (moved, with -)
				out.append(head != null ? head : String.valueOf(fd)).append(r.op).append(target);
			} else {
				out.append(head != null ? head : fd == usual ? "" : String.valueOf(fd)).append(r.op).append(target);
			}
		}
		default -> out.append(r.fd == null ? "" : r.fd).append(r.op).append(' ').append(target);
		}
	}

	// ------------------------------------------------------------------ layout

	private void newline(String s) {
		out.append('\n');
		indent(indentation);
		out.append(s);
	}

	private void indent(int n) {
		out.append(" ".repeat(Math.max(0, n)));
	}

	/** a ; unless the line just ended, or after " &" */
	private void semicolon() {
		int n = out.length();
		if( (n > 0 && out.charAt(n-1) == '\n') || (n > 1 && out.charAt(n-1) == '&' && out.charAt(n-2) == ' ')) {
			return;
		}
		out.append(';');
	}
}
