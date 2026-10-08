package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.AstPrinter;
import us.bringardner.fsh.syntax.Parser;
import us.bringardner.fsh.syntax.SyntaxError;

/**
 * The new front end (us.bringardner.fsh.syntax): every bash-compat case parses, scripts parse to
 * the trees bash would build, and scripts bash rejects are rejected with bash's message and line.
 */
public class TestSyntaxParser {

	/** script, then its tree as AstPrinter prints it */
	private static final String [][] TREES = {
		{"echo a | wc -l && x=1 || y &",
			"(seq (and-or (pipe (cmd [echo] [a]) (cmd [wc] [-l])) && (cmd x=[1]) || (cmd [y])) &)"},
		{"a=(1 \"2 3\") b[i + 1]+=x cmd >out 2>&1 <<<\"$s\"",
			"(seq (cmd a=([1] [\"2 3\"]) b[i + 1]+=[x] [cmd] >[out] 2>&[1] <<<[\"$s\"]))"},
		{"cat <<EOF; cat <<-\"X\"\nhi $x\nEOF\n\ttab\n\tX\necho done",
			"(seq (cmd [cat] <<EOF{hi $x\\n}) (cmd [cat] <<-'X'{tab\\n}) (cmd [echo] [done]))"},
		{"f() { local a=(1 2) b=$x; } >/dev/null",
			"(seq (function f (group (seq (cmd [local] decl:a=([1] [2]) decl:b=[$x])) >[/dev/null])))"},
		{"if a; then b; elif c; then d; else e; fi",
			"(seq (if (seq (cmd [a])) (seq (cmd [b])) (seq (cmd [c])) (seq (cmd [d])) else (seq (cmd [e]))))"},
		{"while a; do b; done; until a; do b; done",
			"(seq (while (seq (cmd [a])) (seq (cmd [b]))) (until (seq (cmd [a])) (seq (cmd [b]))))"},
		{"case $x in (a|b) echo 1;; c) ;& *) echo $(case y in y) echo z;; esac) ;;& esac",
			"(seq (case [$x] ([a]|[b] (seq (cmd [echo] [1])) ;;) ([c] (seq) ;&) ([*] (seq (cmd [echo] [$(seq (case [y] ([y] (seq (cmd [echo] [z])) ;;)))])) ;;&)))"},
		{"for i; do :; done; for i in a \"b c\"; do :; done; for ((i=0; i<3; i++)) { echo $i; }; select x in a b; do break; done",
			"(seq (for i (seq (cmd [:]))) (for i in [a] [\"b c\"] (seq (cmd [:]))) (arith-for {i=0} {i<3} {i++} (seq (cmd [echo] [$i]))) (select x in [a] [b] (seq (cmd [break]))))"},
		{"time -p ! cmd |& tee >(wc) <(ls) {fd}>f 3<&-",
			"(seq (pipe time-p ! (cmd [cmd]) |& (cmd [tee] [>(seq (cmd [wc]))] [<(seq (cmd [ls]))] {fd}>[f] 3<&[-])))"},
		{"echo \"a $(echo \"b ${c:-\"d\"}\") `e` $((1+2)) \\$x\" $'\\n' $\"loc\" @(a|b)",
			"(seq (cmd [echo] [\"a $(seq (cmd [echo] [\"b ${c:-\"d\"}\"])) `e` $((1+2)) \\$x\"] [$'\\n'] [$\"loc\"] [@(a|b)]))"},
		{"echo $(( $x + $(echo 2) * \"3\" )) $[1+2]; (( a[$i] += 1 ))",
			"(seq (cmd [echo] [$(($x + $(seq (cmd [echo] [2])) * 3))] [$((1+2))]) (arith {a[$i] += 1}))"},
		{"eval a=( 1 \"2 3\" ); declare -A m=([a b]=1 [c]=2)",
			"(seq (cmd [eval] [a=( 1 \"2 3\" )]) (cmd [declare] [-A] decl:m=([[a b]=1] [[c]=2])))"},
		{"function g { :; }; function h() ( : ); i() [[ -n $x ]]",
			"(seq (function g (group (seq (cmd [:])))) (function h (subshell (seq (cmd [:])))) (function i (cond (-n [$x]))))"},
		{"[[ -f $a && ( x == y* || ! -z \"$b\" ) && $c =~ ^(a|b)+$ ]]",
			"(seq (cond (&& (&& (-f [$a]) (|| (== [x] [y*]) (! (-z [\"$b\"])))) (=~ [$c] [^(a|b)+$]))))"},
		{"[[ -n x\n && a == b\n ]]",
			"(seq (cond (&& (-n [x]) (== [a] [b]))))"},
		{"echo ${ echo a; } ${| REPLY=1; } ${a:-{b} ${x:-${y}}",
			"(seq (cmd [echo] [${(seq (cmd [echo] [a]))}] [${|(seq (cmd REPLY=[1]))}] [${a:-{b}] [${x:-${y}}]))"},
		{"x 3>&11>&2 &>all &>>app >|clob <>rw",
			"(seq (cmd [x] 3>&[11] >&[2] &>[all] &>>[app] >|[clob] <>[rw]))"},
		{"a[2 x]=y cmd; echo a[2 x]",
			"(seq (cmd a[2 x]=[y] [cmd]) (cmd [echo] [a[2] [x]]))"},
		{"{ a; b; } && ( c )",
			"(seq (and-or (group (seq (cmd [a]) (cmd [b]))) && (subshell (seq (cmd [c])))))"},
		{"echo \\\n continued # comment", "(seq (cmd [echo] [continued]))"},
		{"! true; time; !", "(seq (pipe ! (cmd [true])) (pipe time) (pipe !))"},
		{"echo <(( fi )) $((a)|b)", "(seq (cmd [echo] [<(( fi ))] [$((a)|b)]))"},
	};

	/** script, then the error bash -n reports for it */
	private static final String [][] ERRORS = {
		{"if true; then echo; fi fi", "line 1: syntax error near unexpected token `fi'"},
		{"echo a (", "line 1: syntax error near unexpected token `('"},
		{"if true\nthen", "line 3: syntax error: unexpected end of file from `if' command on line 1"},
		{"if x; then\n if y; then\n", "line 3: syntax error: unexpected end of file from `if' command on line 2"},
		{"f() {\n", "line 2: syntax error: unexpected end of file from `{' command on line 1"},
		{"echo $(x\n", "line 2: unexpected EOF while looking for matching `)'"},
		{"echo ${ fi;}", "line 1: syntax error near unexpected token `fi' while looking for matching `}'"},
		{"{ }", "line 1: syntax error near unexpected token `}'"},
		{"echo \"abc", "line 1: unexpected EOF while looking for matching `\"'"},
		{"echo $(fi)", "line 1: syntax error near unexpected token `fi' while looking for matching `)'"},
		{"echo ${x:-$(fi)}", "line 1: syntax error near unexpected token `fi' while looking for matching `)'"},
		{"a[2 x", "line 1: unexpected EOF while looking for matching `]'"},
		{"x | !", "line 1: syntax error near unexpected token `!'"},
		{"time && x", "line 1: syntax error near unexpected token `&&'"},
		{"in", "line 1: syntax error near unexpected token `in'"},
		{"echo a=(1)", "line 1: syntax error near unexpected token `('"},
		{"[[ z x ]]", "line 1: unexpected token `x', conditional binary operator expected"},
		{"[[ -n ]]", "line 1: unexpected argument `]]' to conditional unary operator"},
		{"[[ a == ]]", "line 1: unexpected argument `]]' to conditional binary operator"},
		{"[[ a =~ a<b ]]", "line 1: syntax error in conditional expression: unexpected token `<'"},
		{"[[ a\n]]", "line 1: unexpected token `newline', conditional binary operator expected"},
		{"[[ $w = (|x) ]]", "line 1: unexpected argument `(' to conditional binary operator"},
		{"f >{x}>a", "line 1: syntax error near unexpected token `{x}'"},
	};

	@Test
	public void testCaseScriptsParse() throws Exception {
		File dir = new File("src/test/resources/bash-compat/cases");
		File [] scripts = dir.listFiles((d, n) -> n.endsWith(".sh"));
		assertTrue(scripts != null && scripts.length > 0, "no cases in "+dir);
		Arrays.sort(scripts);
		List<String> failed = new ArrayList<>();
		for(File f : scripts) {
			String code = Files.readString(f.toPath(), StandardCharsets.ISO_8859_1);
			if( code.matches("(?s).*(^|\\n)alias .*")) {
				// its aliases are used as the shell reads it, a line at a time (not all at once)
				continue;
			}
			try {
				Parser.parse(code);
			} catch (SyntaxError e) {
				failed.add(f.getName()+": "+e.describe());
			}
		}
		if( !failed.isEmpty()) {
			fail(failed.size()+" case scripts do not parse:\n"+String.join("\n", failed));
		}
	}

	@Test
	public void testTrees() {
		for(String [] t : TREES) {
			String tree;
			try {
				tree = AstPrinter.print(Parser.parse(t[0]));
			} catch (SyntaxError e) {
				tree = "error "+e.describe();
			}
			assertEquals(t[1], tree, t[0]);
		}
	}

	@Test
	public void testErrors() {
		for(String [] t : ERRORS) {
			try {
				Parser.parse(t[0]);
				fail("parsed, bash does not: "+t[0]);
			} catch (SyntaxError e) {
				assertEquals(t[1], e.describe(), t[0]);
			}
		}
	}

	@Test
	public void testPositions() {
		String code = "echo one\n\nif true\nthen\n  echo \"two\nlines\" three\nfi\n";
		Ast.Sequence seq = Parser.parse(code);
		assertEquals(2, seq.items.size());
		Ast.Command echo = seq.items.get(0).command.pipelines.get(0).commands.get(0);
		assertEquals(1, echo.line);
		assertEquals("echo one", echo.text(code));
		Ast.If f = (Ast.If) seq.items.get(1).command.pipelines.get(0).commands.get(0);
		assertEquals(3, f.line);
		assertTrue(f.text(code).startsWith("if true") && f.text(code).endsWith("fi"), f.text(code));
		Ast.SimpleCommand inner = (Ast.SimpleCommand) f.bodies.get(0).items.get(0).command.pipelines.get(0).commands.get(0);
		assertEquals(5, inner.line);
		assertEquals(6, inner.words.get(2).line);
		assertEquals("three", inner.words.get(2).raw);
	}
}
