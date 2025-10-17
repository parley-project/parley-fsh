package us.bringardner.shell;

import us.bringardner.core.BaseThread;


public abstract class SignalEnabledThread extends BaseThread {
	public abstract void handleSignal(ConsoleSignal signal)  ;
}
