package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.parser.FileSourceShLexer;
import us.bringardner.fsh.parser.FileSourceShParser;

/**
 * The fast (SLL) parse must succeed on ordinary scripts. When it gives up, FileSourceShVisitorImpl
 * parses again in full LL mode, which took 10 to 30 seconds on large real scripts (man, brew.sh):
 * the grammar was ambiguous where if/while/case were reached two ways, where a command or an
 * && list could start, and where white space could go in a block. Each snippet here once made
 * the fast parse give up.
 */
public class TestParseSpeed {

	private static final String [] SCRIPTS = {
		"#!/bin/sh\n\nif [ ! -e \"/etc/x.conf\" ]; then\n\tchmod 644 \"/etc/x.conf\"\nfi\n/usr/sbin/ipconfig waitall\n",
		"if true; then\n  echo a\nfi\n\necho b\nwhile false; do :; done | cat\nfor i in 1 2; do echo $i; done > /dev/null\n",
		"f() {\n  local IFS=_ parts\n  read -ra parts <<< \"$1\"\n  [ -n \"$x\" ] && echo y || echo n\n}\nf a_b\n",
		"case $1 in\n  a|b) echo ab;;\n  c) true && echo c ;;\n  *) echo other\nesac\n",
		"x=1; y=$(echo $x) &\nwait\n{ echo a; echo b; } 2>/dev/null\n( cd / && pwd )\n",
		"if x=$(echo hi) &&\n   [ -n \"$x\" ]\nthen\n  echo \"$x\"\nelif false; then :\nelse\n  echo no\nfi\n",
		"arr=(\n  one\n  two # c\n)\nfor a in \"${arr[@]}\"; do\n  case $a in one) continue;; esac\n  echo \"$a\"\ndone\n",
		"> out.txt\n<<'X'\nignored\nX\necho done # end\n",
	};

	@Test
	public void testFastParseSucceeds() throws Exception {
		Console.exitJvm = false;
		for(String code : SCRIPTS) {
			Console c = new Console();
			String pp = c.preProcess(code.trim(), new ShellContext(c));
			FileSourceShParser parser = new FileSourceShParser(new CommonTokenStream(new FileSourceShLexer(CharStreams.fromString(pp))));
			parser.removeErrorListeners();
			parser.setErrorHandler(new BailErrorStrategy());
			parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
			try {
				parser.script();
			} catch (ParseCancellationException e) {
				fail("the fast parse gave up on:\n"+code);
			}
		}
	}
}
