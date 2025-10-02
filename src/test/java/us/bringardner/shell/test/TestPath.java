package us.bringardner.shell.test;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.filesource.sh.FileSourceShLexer;


@TestMethodOrder(OrderAnnotation.class)
public class TestPath {

		/*
		 * 
ID                   'ANIMAL'
EQ                   '='
ID                   'dog'
EOF                  '<EOF>'

ID                   'ANIMAL'
EQ                   '='
ID                   'dog'
EOF                  '<EOF>'
		 */
	//@Test
	public void testPath01() throws Exception{
		String path = "/1111/aa-bb/*/?/./../one/\"a b\"/'two' -ww";
		//path = "/";
		//path = "sh ~/test02.sh dude";
		//path = "function f () { echo s}";
		//path = "f () { echo s}";
		path = "export a=x";
		//System.out.println("path="+path);
		FileSourceShLexer lexer = new FileSourceShLexer(CharStreams.fromString(path));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		tokens.fill();

		for (Token t : tokens.getTokens()) {
		    System.out.printf("%-20s '%s'\n",
		    		FileSourceShLexer.VOCABULARY.getSymbolicName(t.getType()),
		            t.getText().replace("\n", "\\n"));
		}
		//Object obj = FileSourceShVisitorImpl.parsePath("/one/\"a b\"/two\n");
		//System.out.println(obj);
	}
	
}
