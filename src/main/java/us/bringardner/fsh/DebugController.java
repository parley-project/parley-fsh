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

/**
 * Debugger controls a front end (such as fsh-ide) drives. Implemented by {@link DebugContext}.
 * Was DebugControlPanel.DebugController in the IDE package; it lives here so fsh
 * doesn't depend on the IDE.
 */
public interface DebugController {

	void stepToReturn();

	void stepOver();

	void stepInto();

	void terminate();

	void suspend();

	void resume();

}
