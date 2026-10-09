package us.bringardner.fsh.signal;

/**
 * A signal ended the job a command runs in (Ctrl-C, kill %1): what is running stops, and the
 * job's status is 128 + the signal, as in bash.
 */
public class SignalException extends FshException {

	private static final long serialVersionUID = 1L;

	public final int signal;
	/**
	 * the job's programs got the signal themselves: they decide what it does, and the job's
	 * commands stop after the one running (not the program: it is not killed)
	 */
	public final boolean afterProgram;

	public SignalException(int signal) {
		this(signal, false);
	}

	public SignalException(int signal, boolean afterProgram) {
		this.signal = signal;
		this.afterProgram = afterProgram;
	}

	public int exitCode() {
		return 128+signal;
	}

	@Override
	public String toString() {
		return "";
	}
}
