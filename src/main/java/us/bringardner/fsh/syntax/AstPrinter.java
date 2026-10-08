package us.bringardner.fsh.syntax;

import java.util.List;

import us.bringardner.fsh.syntax.Ast.AndOr;
import us.bringardner.fsh.syntax.Ast.Arith;
import us.bringardner.fsh.syntax.Ast.ArithFor;
import us.bringardner.fsh.syntax.Ast.Assignment;
import us.bringardner.fsh.syntax.Ast.BraceGroup;
import us.bringardner.fsh.syntax.Ast.Case;
import us.bringardner.fsh.syntax.Ast.CaseClause;
import us.bringardner.fsh.syntax.Ast.Command;
import us.bringardner.fsh.syntax.Ast.Cond;
import us.bringardner.fsh.syntax.Ast.For;
import us.bringardner.fsh.syntax.Ast.FunctionDef;
import us.bringardner.fsh.syntax.Ast.If;
import us.bringardner.fsh.syntax.Ast.Item;
import us.bringardner.fsh.syntax.Ast.Loop;
import us.bringardner.fsh.syntax.Ast.Pipeline;
import us.bringardner.fsh.syntax.Ast.Redirect;
import us.bringardner.fsh.syntax.Ast.Select;
import us.bringardner.fsh.syntax.Ast.Sequence;
import us.bringardner.fsh.syntax.Ast.SimpleCommand;
import us.bringardner.fsh.syntax.Ast.Subshell;

/**
 * The syntax tree as one line of nested (kind ...) forms, for tests and debugging:
 * {@code echo a | wc -l && x=1} is {@code (seq (and-or (pipe (cmd [echo] [a]) (cmd [wc] [-l])) && (cmd x=[1])))}.
 * A word is [parts], each part as written but for "..." which is shown as "parts".
 */
public final class AstPrinter {

	private final StringBuilder out = new StringBuilder();

	private AstPrinter() {
	}

	public static String print(Sequence seq) {
		AstPrinter p = new AstPrinter();
		p.sequence(seq);
		return p.out.toString();
	}

	public static String print(Word w) {
		AstPrinter p = new AstPrinter();
		p.word(w);
		return p.out.toString();
	}

	private void sequence(Sequence seq) {
		out.append("(seq");
		for(Item i : seq.items) {
			out.append(' ');
			andOr(i.command);
			if( i.background ) {
				out.append(" &");
			}
		}
		out.append(')');
	}

	private void andOr(AndOr ao) {
		if( ao.pipelines.size() == 1 ) {
			pipeline(ao.pipelines.get(0));
			return;
		}
		out.append("(and-or ");
		for (int i = 0; i < ao.pipelines.size(); i++) {
			if( i > 0 ) {
				out.append(' ').append(ao.ops.get(i-1)).append(' ');
			}
			pipeline(ao.pipelines.get(i));
		}
		out.append(')');
	}

	private void pipeline(Pipeline p) {
		boolean plain = p.commands.size() == 1 && !p.negated && !p.timed;
		if( plain ) {
			command(p.commands.get(0));
			return;
		}
		out.append("(pipe");
		if( p.timed ) {
			out.append(p.timePosix ? " time-p" : " time");
		}
		if( p.negated ) {
			out.append(" !");
		}
		for (int i = 0; i < p.commands.size(); i++) {
			if( i > 0 && p.stderrToo.get(i-1)) {
				out.append(" |&");
			}
			out.append(' ');
			command(p.commands.get(i));
		}
		out.append(')');
	}

	private void command(Command c) {
		if( c instanceof SimpleCommand s ) {
			out.append("(cmd");
			for(Assignment a : s.assignments) {
				out.append(' ');
				assignment(a);
			}
			for(Word w : s.words) {
				out.append(' ');
				if( w.assignment != null ) {
					out.append("decl:");
					assignment(w.assignment);
				} else {
					word(w);
				}
			}
		} else if( c instanceof BraceGroup g ) {
			out.append("(group ");
			sequence(g.body);
		} else if( c instanceof Subshell s ) {
			out.append("(subshell ");
			sequence(s.body);
		} else if( c instanceof If f ) {
			out.append("(if");
			for (int i = 0; i < f.conditions.size(); i++) {
				out.append(' ');
				sequence(f.conditions.get(i));
				out.append(' ');
				sequence(f.bodies.get(i));
			}
			if( f.elseBody != null ) {
				out.append(" else ");
				sequence(f.elseBody);
			}
		} else if( c instanceof Loop l ) {
			out.append(l.until ? "(until " : "(while ");
			sequence(l.condition);
			out.append(' ');
			sequence(l.body);
		} else if( c instanceof For f ) {
			out.append("(for ").append(f.variable);
			words(f.words);
			out.append(' ');
			sequence(f.body);
		} else if( c instanceof Select f ) {
			out.append("(select ").append(f.variable);
			words(f.words);
			out.append(' ');
			sequence(f.body);
		} else if( c instanceof ArithFor f ) {
			out.append("(arith-for {").append(f.init.raw).append("} {").append(f.condition.raw).append("} {").append(f.step.raw).append("} ");
			sequence(f.body);
		} else if( c instanceof Case k ) {
			out.append("(case ");
			word(k.subject);
			for(CaseClause cl : k.clauses) {
				out.append(" (");
				for (int i = 0; i < cl.patterns.size(); i++) {
					if( i > 0 ) {
						out.append('|');
					}
					word(cl.patterns.get(i));
				}
				out.append(' ');
				sequence(cl.body);
				if( cl.terminator != null ) {
					out.append(' ').append(cl.terminator);
				}
				out.append(')');
			}
		} else if( c instanceof Arith a ) {
			out.append("(arith {").append(a.expression.raw).append('}');
		} else if( c instanceof Cond k ) {
			out.append("(cond ");
			cond(k.expression);
		} else if( c instanceof Ast.Coproc k ) {
			out.append("(coproc ").append(k.name).append(' ');
			command(k.body);
		} else if( c instanceof FunctionDef f ) {
			out.append("(function ").append(f.name).append(' ');
			command(f.body);
		}
		for(Redirect r : c.redirects) {
			out.append(' ');
			redirect(r);
		}
		out.append(')');
	}

	private void cond(Ast.CondExpr e) {
		switch (e) {
		case Ast.CondAnd a -> {
			out.append("(&& ");
			cond(a.left());
			out.append(' ');
			cond(a.right());
			out.append(')');
		}
		case Ast.CondOr o -> {
			out.append("(|| ");
			cond(o.left());
			out.append(' ');
			cond(o.right());
			out.append(')');
		}
		case Ast.CondNot n -> {
			out.append("(! ");
			cond(n.expression());
			out.append(')');
		}
		case Ast.CondUnary u -> {
			out.append('(').append(u.op()).append(' ');
			word(u.operand());
			out.append(')');
		}
		case Ast.CondBinary b -> {
			out.append('(').append(b.op()).append(' ');
			word(b.left());
			out.append(' ');
			word(b.right());
			out.append(')');
		}
		case Ast.CondWord w -> word(w.word());
		}
	}

	private void words(List<Word> words) {
		if( words == null ) {
			return;
		}
		out.append(" in");
		for(Word w : words) {
			out.append(' ');
			word(w);
		}
	}

	private void redirect(Redirect r) {
		if( r.fd != null ) {
			out.append(r.fd);
		}
		if( r.fdVariable != null ) {
			out.append('{').append(r.fdVariable).append('}');
		}
		out.append(r.op);
		if( r.hereDoc != null ) {
			out.append(r.hereDoc.quoted ? "'" : "").append(r.hereDoc.delimiter).append(r.hereDoc.quoted ? "'" : "")
			.append('{').append(r.hereDoc.body.replace("\n", "\\n")).append('}');
		} else {
			word(r.target);
		}
	}

	private void assignment(Assignment a) {
		out.append(a.name);
		if( a.index != null ) {
			out.append('[').append(a.index).append(']');
		}
		out.append(a.append ? "+=" : "=");
		if( a.array != null ) {
			out.append('(');
			for (int i = 0; i < a.array.size(); i++) {
				if( i > 0 ) {
					out.append(' ');
				}
				word(a.array.get(i));
			}
			out.append(')');
		} else {
			word(a.value);
		}
	}

	private void word(Word w) {
		out.append('[');
		parts(w.parts);
		out.append(']');
	}

	private void parts(List<Word.Part> parts) {
		for(Word.Part p : parts) {
			switch (p) {
			case Word.Literal l -> out.append(l.text());
			case Word.Escaped e -> out.append('\\').append(e.c());
			case Word.SingleQuoted s -> out.append('\'').append(s.text()).append('\'');
			case Word.AnsiC a -> out.append("$'").append(a.text()).append('\'');
			case Word.DoubleQuoted d -> {
				out.append(d.locale() ? "$\"" : "\"");
				parts(d.parts());
				out.append('"');
			}
			case Word.Param pa -> out.append('$').append(pa.name());
			case Word.ParamExpansion pe -> out.append("${").append(pe.body()).append('}');
			case Word.CommandSub cs -> {
				out.append("$");
				if( cs.body() == null ) {
					out.append('(').append(cs.text()).append(')');
				} else {
					sequence(cs.body());
				}
			}
			case Word.FunctionSub fs -> {
				out.append(fs.reply() ? "${|" : "${");
				sequence(fs.body());
				out.append('}');
			}
			case Word.Backquote b -> out.append('`').append(b.text()).append('`');
			case Word.ArithSub a -> {
				out.append("$((");
				parts(a.expression().parts);
				out.append("))");
			}
			case Word.ProcessSub ps -> {
				out.append(ps.direction());
				if( ps.body() == null ) {
					out.append('(').append(ps.text()).append(')');
				} else {
					sequence(ps.body());
				}
			}
			}
		}
	}
}
