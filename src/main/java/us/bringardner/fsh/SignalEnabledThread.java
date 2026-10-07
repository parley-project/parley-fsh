package us.bringardner.fsh;

import java.io.IOException;

import us.bringardner.parley.core.BaseThread;


public abstract class SignalEnabledThread extends BaseThread {
	public abstract void handleSignal(ConsoleSignal signal) throws IOException  ;
}
