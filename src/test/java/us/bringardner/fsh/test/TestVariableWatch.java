/**
 *	Copyright 2026 Tony Bringardner
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 *	the specific language governing permissions and limitations under the License.
 */
package us.bringardner.fsh.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.DebugContext;
import us.bringardner.fsh.ShellContext;

/**
 * What a debugger's watchpoints are told (DebugContext.variableRead / variableWritten): the
 * script's own reads and writes of a variable, once each, and not the lookups the shell makes to
 * carry them out.
 */
public class TestVariableWatch {

	/** The reads (R) and writes (W, with the value after it) of x, as a debugger is told them. */
	private static String accesses(String script) throws Exception {
		List<String> seen = new CopyOnWriteArrayList<>();
		Console c = new Console();
		c.setDebugContext(new DebugContext() {
			@Override
			public boolean variableRead(String name, ShellContext ctx) {
				if( name.equals("x")) {
					seen.add("R");
				}
				return false;
			}

			@Override
			public boolean variableWritten(String name, ShellContext ctx) {
				if( name.equals("x")) {
					// (looked at as a debugger would: not a read)
					seen.add("W"+ctx.peekVariable("x"));
				}
				return false;
			}
		});
		c.executeScript(script);
		return String.join(" ", seen);
	}

	@Test
	public void writes() throws Exception {
		assertEquals("W5", accesses("x=5"));
		assertEquals("W1 Wnull", accesses("x=1; unset x"));
		assertEquals("W1 2", accesses("x=(1 2)"));
		assertEquals("W3", accesses("((x=3))"));
		assertEquals("Wa", accesses("read x <<< a"));
		assertEquals("W1 W2", accesses("for x in 1 2; do :; done"));
		assertEquals("W1", accesses("declare x=1"));
		assertEquals("W1", accesses("export x=1"));
		assertEquals("W1", accesses("readonly x=1"));
		assertEquals("Wa", accesses("printf -v x %s a"));
	}

	@Test
	public void anElementIsTheArrays() throws Exception {
		assertEquals("Wa b Wa b c", accesses("x=(a b); x[2]=c"));
	}

	@Test
	public void reads() throws Exception {
		assertEquals("W1 R", accesses("x=1; echo $x >/dev/null"));
		assertEquals("W1 R", accesses("x=1; y=$(( x + 1 ))"));
		assertEquals("W1 R", accesses("x=1; [ \"$x\" = 1 ]"));
	}

	@Test
	public void readAndWrite() throws Exception {
		assertEquals("W1 R W2", accesses("x=1; ((x++))"));
		assertEquals("W1 R W12", accesses("x=1; x+=2"));
	}

	@Test
	public void aFunctionsLocal() throws Exception {
		assertEquals("W1 R", accesses("f() { local x=1; echo $x >/dev/null; }; f"));
	}

	@Test
	public void otherVariablesArentX() throws Exception {
		assertEquals("", accesses("xy=1; echo $xy >/dev/null; cd /tmp"));
	}

	@Test
	public void aWatchCanStopTheScriptUntilTheDebuggerLetsItGo() throws Exception {
		List<String> seen = new CopyOnWriteArrayList<>();
		Console c = new Console();
		c.setDebugContext(new DebugContext() {
			@Override
			public boolean variableWritten(String name, ShellContext ctx) {
				return name.equals("x");
			}

			@Override
			public synchronized void setCurrentState(RunState state) {
				super.setCurrentState(state);
				if( state == RunState.AtBreakpoint ) {
					seen.add("stopped at "+ctx().getLastNode().line+" with x="+ctx().peekVariable("x"));
					// let it go on, from another thread, as a debugger's Resume would
					new Thread(()->setCurrentState(RunState.Running)).start();
				}
			}

			private ShellContext ctx;

			@Override
			public synchronized void before(us.bringardner.fsh.syntax.Ast.Node node, String source, ShellContext ctx) {
				this.ctx = ctx;
			}

			private ShellContext ctx() {
				return ctx;
			}
		});
		c.executeScript("y=0\nx=7\necho done >/dev/null\n");
		assertEquals(List.of("stopped at 2 with x=7"), seen);
	}
}
