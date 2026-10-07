package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.Glob;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.antlr.statement.CommandSubstitutionStatement;
import us.bringardner.fsh.expand.ExpansionError;
import us.bringardner.fsh.expand.Expander;
import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Parser;

/**
 * The new word expansion (us.bringardner.fsh.expand) against what bash does: each case in
 * src/test/resources/expand/cases.txt is a word and bash's output for printf '<%s>' with it (or
 * for an assignment, a case pattern, a =~ or a here-document), recorded with bash 5.3.
 * <p>
 * Until the new executor is in, the setup lines run in the old one, and so do $( ).
 */
public class TestExpander {

	@Test
	public void testCases() throws Exception {
		Console.exitJvm = false;
		File globDir = new File("target/expand-glob").getAbsoluteFile();
		new File(globDir, "sub").mkdirs();
		for(String name : new String[] {"a.txt", "b.txt", "c.log", "d e.txt", ".hidden", "sub/x.c", "sub/y.c"}) {
			new File(globDir, name).createNewFile();
		}
		List<String> lines = Files.readAllLines(new File("src/test/resources/expand/cases.txt").toPath(), StandardCharsets.UTF_8);
		List<String> failed = new ArrayList<>();
		int count = 0;
		for (int i = 0; i < lines.size(); ) {
			if( !lines.get(i).startsWith("### setup")) {
				i++;
				continue;
			}
			i++;
			StringBuilder setup = new StringBuilder();
			while( i < lines.size() && !lines.get(i).startsWith("### words")) {
				setup.append(lines.get(i++).replace("@GLOBDIR@", globDir.getPath())).append('\n');
			}
			i++;
			Console c = new Console();
			ShellContext sc = new ShellContext(c);
			c.executeUsingAntlr(sc, setup.toString());
			while( i < lines.size() && !lines.get(i).startsWith("### setup")) {
				String line = lines.get(i++);
				int tab = line.lastIndexOf('\t');
				if( tab < 0 ) {
					continue;
				}
				count++;
				String word = line.substring(0, tab);
				String expected = line.substring(tab+1).replace("\\t", "\t");
				String actual = run(sc.isolatedSubShell(), word);
				if( !expected.equals(actual)) {
					failed.add(word+"\n   expected "+expected+"\n   actual   "+actual);
				}
			}
		}
		assertTrue(count > 150, "only "+count+" cases");
		assertEquals("", String.join("\n", failed), failed.size()+" of "+count+" cases differ from bash");
	}

	/** the word line expanded as the case says, printed the way the recorded output is */
	private static String run(ShellContext sc, String line) {
		Expander ex = new Expander(sc, new Expander.Host() {
			@Override
			public String commandOutput(Ast.Sequence body, String text, boolean backquote) {
				CommandSubstitutionStatement s = new CommandSubstitutionStatement(new ParserRuleContext());
				s.execute(backquote ? CommandSubstitutionStatement.backtickCode(text) : text, sc);
				return s.getStdout();
			}

			@Override
			public String functionOutput(Ast.Sequence body, String text, boolean reply) {
				return commandOutput(body, text, false);
			}

			@Override
			public String processSubstitution(char direction, Ast.Sequence body, String text) {
				return "/dev/fd/63";
			}
		});
		try {
			if( line.startsWith("A:")) {
				Ast.SimpleCommand cmd = (Ast.SimpleCommand) first(Parser.parse("v="+line.substring(2)));
				return "<"+ex.assignment(cmd.assignments.get(0).value)+">";
			}
			if( line.startsWith("C:")) {
				int colon = line.indexOf(':', 2);
				Ast.Case cs = (Ast.Case) first(Parser.parse("case "+line.substring(2, colon)+" in "+line.substring(colon+1)+") ;; esac"));
				String pattern = ex.pattern(cs.clauses.get(0).patterns.get(0));
				return Glob.toRegex(pattern).matcher(ex.string(cs.subject)).matches() ? "<y>" : "<n>";
			}
			if( line.startsWith("R:")) {
				int colon = line.indexOf(':', 2);
				Ast.Cond cond = (Ast.Cond) first(Parser.parse("[[ "+line.substring(2, colon)+" =~ "+line.substring(colon+1)+" ]]"));
				Ast.CondBinary b = (Ast.CondBinary) cond.expression;
				return java.util.regex.Pattern.compile(ex.regex(b.right())).matcher(ex.string(b.left())).find() ? "<y>" : "<n>";
			}
			if( line.startsWith("H:")) {
				String ret = ex.hereDocument(line.substring(2)+"\n");
				return ret.endsWith("\n") ? ret.substring(0, ret.length()-1) : ret;
			}
			Ast.SimpleCommand cmd = (Ast.SimpleCommand) first(Parser.parse("printf '<%s>' "+line));
			List<String> words = ex.words(cmd.words.subList(2, cmd.words.size()));
			StringBuilder ret = new StringBuilder();
			if( words.isEmpty()) {
				ret.append("<>");
			}
			for(String w : words) {
				ret.append('<').append(w).append('>');
			}
			return ret.toString();
		} catch (ExpansionError e) {
			return "ERR "+e.getMessage();
		}
	}

	private static Ast.Command first(Ast.Sequence seq) {
		return seq.items.get(0).command.pipelines.get(0).commands.get(0);
	}
}
