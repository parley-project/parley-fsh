package us.bringardner.fsh;

/**
 * Input the shell and the programs it runs share, as they share a file in bash: a program is
 * given only what it reads, and what was sent to it that it did not read is put back for the
 * next reader (while read l; do prog; done < file reads every line).
 */
public interface SharedInput {

	/** put data back: the next reads return it first */
	void unread(byte [] data, int off, int len);

	/** a read returns data or the end now, without waiting */
	boolean readyOrEnded();
}
