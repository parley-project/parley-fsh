package us.bringardner.fsh.commands;

/**
 * [ expr ], as the shell runs it: test, with [ in its messages. (The parser takes [ and ] off.)
 */
public class BracketTest extends Test {

	public BracketTest() {
		super("__bracket_test");
		label = "[";
	}
}
