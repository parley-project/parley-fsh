/**
*	Copyright 2024 Tony Bringardner
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
package us.bringardner.fsh;

import java.awt.Point;



public class DebugContext  implements DebugController {

	public enum RunState {NotRunning,Terminate,Running,AtBreakpoint,Stepping,StepInto,StepOver,StepToEnd};
	private RunState currentState;
	private RunState requestedState;
	
	
	
	
	public   boolean isBreakpoint(Point point,ShellContext ctx) {
		//  by default we're not debugging
		return false;
	}

	public synchronized RunState getRequestedState() {
		return requestedState;
	}

	public synchronized void setRequestedState(RunState requestedState) {
		this.requestedState = requestedState;
	}

	public synchronized RunState getCurrentState() {
		return currentState;
	}

	public synchronized void setCurrentState(RunState currentState) {
		this.currentState = currentState;
	}

	public synchronized void before(us.bringardner.fsh.syntax.Ast.Node node,ShellContext ctx) {
		// do nothing by default
		
	}
	
	public synchronized void after(us.bringardner.fsh.syntax.Ast.Node node,ShellContext ctx) {
		// do nothing by default
		
	}

	
	public void stepToReturn() {
		// do nothing by default
		
	}

	
	public void stepOver() {
		// do nothing by default
		
	}

	
	public void stepInto() {
		// do nothing by default
		
	}

	
	public void terminate() {
		// do nothing by default
		
	}

	
	public void suspend() {
		// do nothing by default
		
	}

	
	public void resume() {
		// do nothing by default
		
	}
	
}
