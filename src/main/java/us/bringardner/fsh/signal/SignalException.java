package us.bringardner.fsh.signal;

/**
 * A signal ended the job a command runs in (Ctrl-C, kill %1): what is running stops, and the
 * job's status is 128 + the signal, as in bash.
 */
public class SignalException extends FshException {

	private static final long serialVersionUID = 1L;

	public final int signal;

	public SignalException(int signal) {
		this.signal = signal;
	}

	public int exitCode() {
		return 128+signal;
	}

	@Override
	public String toString() {
		return "";
	}
}
