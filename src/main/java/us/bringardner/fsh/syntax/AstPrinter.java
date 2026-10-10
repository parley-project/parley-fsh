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
		if( c instanceof SimpleCommand ) {
			SimpleCommand s = (SimpleCommand) c;
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
		} else if( c instanceof BraceGroup ) {
			out.append("(group ");
			sequence(((BraceGroup) c).body);
		} else if( c instanceof Subshell ) {
			out.append("(subshell ");
			sequence(((Subshell) c).body);
		} else if( c instanceof If ) {
			If f = (If) c;
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
		} else if( c instanceof Loop ) {
			Loop l = (Loop) c;
			out.append(l.until ? "(until " : "(while ");
			sequence(l.condition);
			out.append(' ');
			sequence(l.body);
		} else if( c instanceof For ) {
			For f = (For) c;
			out.append("(for ").append(f.variable);
			words(f.words);
			out.append(' ');
			sequence(f.body);
		} else if( c instanceof Select ) {
			Select f = (Select) c;
			out.append("(select ").append(f.variable);
			words(f.words);
			out.append(' ');
			sequence(f.body);
		} else if( c instanceof ArithFor ) {
			ArithFor f = (ArithFor) c;
			out.append("(arith-for {").append(f.init.raw).append("} {").append(f.condition.raw).append("} {").append(f.step.raw).append("} ");
			sequence(f.body);
		} else if( c instanceof Case ) {
			Case k = (Case) c;
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
		} else if( c instanceof Arith ) {
			out.append("(arith {").append(((Arith) c).expression.raw).append('}');
		} else if( c instanceof Cond ) {
			out.append("(cond ");
			cond(((Cond) c).expression);
		} else if( c instanceof Ast.Coproc ) {
			Ast.Coproc k = (Ast.Coproc) c;
			out.append("(coproc ").append(k.name).append(' ');
			command(k.body);
		} else if( c instanceof FunctionDef ) {
			FunctionDef f = (FunctionDef) c;
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
		if( e instanceof Ast.CondAnd ) {
			Ast.CondAnd a = (Ast.CondAnd) e;
			out.append("(&& ");
			cond(a.left());
			out.append(' ');
			cond(a.right());
			out.append(')');
		} else if( e instanceof Ast.CondOr ) {
			Ast.CondOr o = (Ast.CondOr) e;
			out.append("(|| ");
			cond(o.left());
			out.append(' ');
			cond(o.right());
			out.append(')');
		} else if( e instanceof Ast.CondNot ) {
			Ast.CondNot n = (Ast.CondNot) e;
			out.append("(! ");
			cond(n.expression());
			out.append(')');
		} else if( e instanceof Ast.CondUnary ) {
			Ast.CondUnary u = (Ast.CondUnary) e;
			out.append('(').append(u.op()).append(' ');
			word(u.operand());
			out.append(')');
		} else if( e instanceof Ast.CondBinary ) {
			Ast.CondBinary b = (Ast.CondBinary) e;
			out.append('(').append(b.op()).append(' ');
			word(b.left());
			out.append(' ');
			word(b.right());
			out.append(')');
		} else if( e instanceof Ast.CondWord ) {
			Ast.CondWord w = (Ast.CondWord) e;
			word(w.word());
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
			if( p instanceof Word.Literal ) {
				Word.Literal l = (Word.Literal) p;
				out.append(l.text());
			} else if( p instanceof Word.Escaped ) {
				Word.Escaped e = (Word.Escaped) p;
				out.append('\\').append(e.c());
			} else if( p instanceof Word.SingleQuoted ) {
				Word.SingleQuoted s = (Word.SingleQuoted) p;
				out.append('\'').append(s.text()).append('\'');
			} else if( p instanceof Word.AnsiC ) {
				Word.AnsiC a = (Word.AnsiC) p;
				out.append("$'").append(a.text()).append('\'');
			} else if( p instanceof Word.DoubleQuoted ) {
				Word.DoubleQuoted d = (Word.DoubleQuoted) p;
				out.append(d.locale() ? "$\"" : "\"");
				parts(d.parts());
				out.append('"');
			} else if( p instanceof Word.Param ) {
				Word.Param pa = (Word.Param) p;
				out.append('$').append(pa.name());
			} else if( p instanceof Word.ParamExpansion ) {
				Word.ParamExpansion pe = (Word.ParamExpansion) p;
				out.append("${").append(pe.body()).append('}');
			} else if( p instanceof Word.CommandSub ) {
				Word.CommandSub cs = (Word.CommandSub) p;
				out.append("$");
				if( cs.body() == null ) {
					out.append('(').append(cs.text()).append(')');
				} else {
					sequence(cs.body());
				}
			} else if( p instanceof Word.FunctionSub ) {
				Word.FunctionSub fs = (Word.FunctionSub) p;
				out.append(fs.reply() ? "${|" : "${");
				sequence(fs.body());
				out.append('}');
			} else if( p instanceof Word.Backquote ) {
				Word.Backquote b = (Word.Backquote) p;
				out.append('`').append(b.text()).append('`');
			} else if( p instanceof Word.ArithSub ) {
				Word.ArithSub a = (Word.ArithSub) p;
				out.append("$((");
				parts(a.expression().parts);
				out.append("))");
			} else if( p instanceof Word.ArithSubscript ) {
				Word.ArithSubscript a = (Word.ArithSubscript) p;
				out.append('[').append(a.text()).append(']');
			} else if( p instanceof Word.ProcessSub ) {
				Word.ProcessSub ps = (Word.ProcessSub) p;
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
