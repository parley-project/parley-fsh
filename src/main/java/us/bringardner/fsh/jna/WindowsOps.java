package us.bringardner.fsh.jna;

import java.util.Collections;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * The process on Windows, as in windows/NativeKeyboard.cpp; the console (the keyboard and the
 * terminal) is parley-term's (us.bringardner.parley.term.tty.jna).
 *
 * Windows has no process groups, job-control signals or controlling terminal: giveTerminalTo and
 * setProcessGroup do nothing and stoppedBy is always 0. The Unix process details (pgrp, ids, argv0 ...)
 * are not here, as they are not in the JNI library: the Java side has its fallbacks.
 */
final class WindowsOps extends NativeOps {

	/**
	 * The options to load a Win32 library with. 32-bit Windows calls its functions as stdcall, which
	 * JNA calls ALT_CONVENTION; on 64-bit Windows there is one calling convention, and JNA refuses
	 * ALT_CONVENTION there ("Invalid calling convention 63"), so the default is the one to use.
	 */
	static java.util.Map<String, Object> win32Options() {
		if( Native.POINTER_SIZE == 4 ) {
			return Collections.<String, Object>singletonMap(Library.OPTION_CALLING_CONVENTION, Function.ALT_CONVENTION);
		}
		return Collections.<String, Object>emptyMap();
	}
	static final class K {
		static {
			Native.register(NativeLibrary.getInstance("kernel32", win32Options()));
		}
		static native int GetProcessTimes(Pointer process, Pointer created, Pointer exited, Pointer kernel, Pointer user);
		static native Pointer GetCurrentProcess();
		static native int GetCurrentProcessId();
		static native Pointer GetCommandLineW();
	}

	/** ntdll's RtlGetVersion (a class of its own: JNA looks up all of a class's functions as it loads it) */
	static final class N {
		static {
			Native.register(NativeLibrary.getInstance("ntdll", win32Options()));
		}
		static native int RtlGetVersion(Pointer info);
	}

	/** ENOSYS in the C runtime's numbering (errno.h) */
	private static final int ENOSYS = 40;

	WindowsOps() {
		// (loads kernel32 and fails here, not later, if it cannot)
		K.GetCurrentProcess();
	}

	@Override
	public void giveTerminalTo(long group) {
		// (no process groups on a console)
	}

	@Override
	public void setProcessGroup(long pid, long group) {
	}

	@Override
	public int stoppedBy(long pid) {
		return 0;
	}

	private static long micros(Pointer p, int offset) {
		// a FILETIME: 100 ns units
		long ticks = (p.getInt(offset) & 0xffffffffL) | ((long) p.getInt(offset+4) << 32);
		return ticks / 10;
	}

	@Override
	public long [] resourceUsage() {
		// (Windows keeps no total for the programs that have ended: theirs are 0)
		long [] values = new long[4];
		Memory times = new Memory(32);
		times.clear();
		if( K.GetProcessTimes(K.GetCurrentProcess(), times, times.share(8), times.share(16), times.share(24)) != 0 ) {
			values[0] = micros(times, 24);
			values[1] = micros(times, 16);
		}
		return values;
	}

	// the process: the contract's answers where Windows has no such thing (NATIVE-CONTRACT.md),
	// the same as windows/NativeKeyboard.cpp gives

	@Override
	public void ttyTo(int group) {
		// (no process groups on a console)
	}

	@Override
	public long pgrp() {
		// (a process leads a group of its own)
		return K.GetCurrentProcessId() & 0xffffffffL;
	}

	@Override
	public long [] ids() {
		// (no numeric ids: not known)
		return null;
	}

	@Override
	public int startedWith(String name) {
		// (the environment at start is not kept: cannot be told)
		return -1;
	}

	@Override
	public int setOpenFilesLimit(long limit) {
		return -ENOSYS;
	}

	@Override
	public String argv0() {
		Pointer cmd = K.GetCommandLineW();
		if( cmd == null ) {
			return null;
		}
		String line = cmd.getWideString(0);
		int p = 0;
		while( p < line.length() && (line.charAt(p) == ' ' || line.charAt(p) == '\t') ) {
			p++;
		}
		int end = p;
		if( p < line.length() && line.charAt(p) == '"' ) {
			// (the C runtime's rule: up to the next quote)
			p++;
			end = p;
			while( end < line.length() && line.charAt(end) != '"' ) {
				end++;
			}
		} else {
			while( end < line.length() && line.charAt(end) != ' ' && line.charAt(end) != '\t' ) {
				end++;
			}
		}
		return end > p ? line.substring(p, end) : null;
	}

	@Override
	public String osRelease() {
		// OSVERSIONINFOW: DWORD size (276), major, minor, build, ...
		Memory v = new Memory(276);
		v.clear();
		v.setInt(0, 276);
		if( N.RtlGetVersion(v) != 0 ) {
			return null;
		}
		return (v.getInt(4) & 0xffffffffL)+"."+(v.getInt(8) & 0xffffffffL)+"."+(v.getInt(12) & 0xffffffffL);
	}

	@Override
	public String [] openDescriptors() {
		// (a program is given only standard input, output and error)
		return new String[0];
	}
}
