package us.bringardner.shell;

public enum ConsoleSignal {
	UnKnown(-1,"UnKnown"), 	
	Hup(1,"HUP"),			//Hang up detected on controlling terminal or death of controlling process
	Interupt(2,"INT"), 		//Interrupt from keyboard
	Quit(3,"QUIT"),			//Quit from keyboard
	Trap(5,"TRAP"),			//Trace/breakpoint trap
	Kill(9,"KILL"),			//Kill signal
	User1(10,"USER1"),		//User-defined signal 1
	User2(12,"USER2"),		//User-defined signal 2
	Terminate(15,"TERM"),	//Termination signal	
	ChildStopped(17,"CHLD"),//Child stopped, terminated, or continued
	Continue(18,"CONT"), 	//Continue if stopped
	Suspend(20,"TSTP"), 	//Stop typed at terminal
	IllegalTerminalRead(21,"TTIN"),		//Terminal input for background process
	IllegalTerminalWrite(22,"TTOUT"),	//Terminal output for background process
	Stop(22,"STOP"),		//Stop process	
	IoPossible(29,"IO"),	//I/O now possible 
	
	
	;
	
	public final String label;
	public final int value;

	private ConsoleSignal(int value,String label) {
		this.label = label;
		this.value = value;
	}

	public static ConsoleSignal find(String name) {
		name = name.toUpperCase();
		if( name.startsWith("SIG")) {
			name = name.substring(3);
		}
		for(ConsoleSignal o : values()) {
			if( o.label.equals(name)) {
				return o;
			}
		}
		return UnKnown;
	}

	public static ConsoleSignal find(int signum) {
		for(ConsoleSignal o : values()) {
			if( o.value == signum) {
				return o;
			}
		}
		return UnKnown;
	}

}
