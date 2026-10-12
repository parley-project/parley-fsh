/*
 * NativeKeyboard for Windows: the Win32 console stands in for the terminal.
 *
 * Raw mode is the console's input without ENABLE_LINE_INPUT and ENABLE_ECHO_INPUT; signal keys
 * are ENABLE_PROCESSED_INPUT (Ctrl-C is CTRL_C_EVENT with it, the byte 3 without it). Keys are
 * returned as a terminal sends them: Enter is '\n', Backspace 127, Alt-x ESC x, text as UTF-8.
 *
 * Windows has no process groups, job-control signals or controlling terminal: giveTerminalTo and
 * setProcessGroup do nothing and stoppedBy is always 0.
 *
 * Provided with the answers the contract allows where Windows has no such thing (see
 * NATIVE-CONTRACT.md): ttyTo0 does nothing; pgrp0 is the process id (a process is the leader of its
 * own group); ids0 is null (no numeric ids: "not known"); startedWith0 is -1 (the environment at
 * start is not kept: "cannot be told"); setOpenFilesLimit0 is -ENOSYS (no such limit).
 *
 * Not here, on purpose: the methods that start programs and pass descriptors (spawn0, pipe0,
 * open0, close0, waitExit0, fd0, kill0, strerror0, socketPair0, sendWithDescriptors0). Windows has
 * no descriptors above 2 to give a program, no process groups, and no signals; the Java side
 * (exec.NativeProcess, StandIn) is written for Unix paths and descriptors, and does not start
 * programs through them there (NativeProcess.usable() is false): ProcessBuilder does.
 */
/* GetTickCount64 is Windows Vista and later: older MinGW headers default to Windows XP and omit it */
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0601
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <wchar.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "NativeKeyboard.h"

#ifndef ENABLE_VIRTUAL_TERMINAL_PROCESSING
#define ENABLE_VIRTUAL_TERMINAL_PROCESSING 0x0004
#endif
#ifndef ENABLE_VIRTUAL_TERMINAL_INPUT
#define ENABLE_VIRTUAL_TERMINAL_INPUT 0x0200
#endif

// getChar() results that are not keys (must match NativeKeyboard.java)
#define KEY_EOF  -1
#define KEY_NONE -2

// special keys (must match NativeKeyboard.java)
#define KEY_UP        500
#define KEY_DN        501
#define KEY_RT        502
#define KEY_LF        503
#define KEY_HOME      504
#define KEY_PAGE_UP   505
#define KEY_PAGE_DOWN 506
#define KEY_DELETE    507
#define KEY_END       508

static HANDLE in() {
	return GetStdHandle(STD_INPUT_HANDLE);
}

static HANDLE out() {
	return GetStdHandle(STD_OUTPUT_HANDLE);
}

/* milliseconds since the system started, that do not wrap */
static ULONGLONG tickCount() {
#if defined(__MINGW32__) && !defined(__MINGW64_VERSION_MAJOR)
	/* MinGW.org's headers have no GetTickCount64: widen GetTickCount, noticing its wrap (one reader at a time) */
	static DWORD last = 0;
	static ULONGLONG wraps = 0;
	DWORD now = GetTickCount();
	if( now < last ) {
		wraps += 0x100000000ULL;
	}
	last = now;
	return wraps + now;
#else
	return GetTickCount64();
#endif
}

static int isConsole(HANDLE h) {
	DWORD mode;
	return h != NULL && h != INVALID_HANDLE_VALUE && GetConsoleMode(h, &mode);
}

static DWORD origInMode;
static int rawMode = 0;
/* 1: Ctrl-C, Ctrl-Z and Ctrl-\ are read as keys (the interactive shell decides what they stop) */
static int keysNotSignals = 0;

static void disableRawMode() {
	if( rawMode ) {
		SetConsoleMode(in(), origInMode);
		rawMode = 0;
	}
}

/*
 * Turn off echo and line buffering. The original settings are saved only once,
 * before they are changed, so they are what gets restored when the JVM exits.
 */
static void enableRawMode() {
	HANDLE h = in();
	if( rawMode || !isConsole(h) ) {
		return;
	}
	// the console's own settings, saved once: a program that leaves it in another mode does not change them
	static int saved = 0;
	if( !saved ) {
		if( !GetConsoleMode(h, &origInMode) ) {
			return;
		}
		saved = 1;
	}
	static int registered = 0;
	if( !registered ) {
		atexit(disableRawMode);
		registered = 1;
	}
	// key events, not escape sequences: the virtual key codes say which key it was
	DWORD raw = origInMode & ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT | ENABLE_VIRTUAL_TERMINAL_INPUT);
	if( keysNotSignals ) {
		raw &= ~ENABLE_PROCESSED_INPUT;
	} else {
		raw |= ENABLE_PROCESSED_INPUT;
	}
	if( SetConsoleMode(h, raw) ) {
		rawMode = 1;
	}
}

static DWORD origOutMode, origErrMode;
static int outSaved = 0, errSaved = 0;
/* the console's output code page when the shell started (0: not changed) */
static UINT origOutputCp = 0;

static void restoreOutput() {
	if( outSaved ) {
		SetConsoleMode(out(), origOutMode);
	}
	if( errSaved ) {
		SetConsoleMode(GetStdHandle(STD_ERROR_HANDLE), origErrMode);
	}
	if( origOutputCp != 0 ) {
		SetConsoleOutputCP(origOutputCp);
		origOutputCp = 0;
	}
}

/*
 * The shell writes VT100 escape sequences (cursor movement, clearing the line): the console
 * acts on them only with ENABLE_VIRTUAL_TERMINAL_PROCESSING (Windows 10 and later).
 */
static void enableEscapes() {
	DWORD mode;
	HANDLE h = out();
	if( isConsole(h) && GetConsoleMode(h, &mode) ) {
		if( !outSaved ) {
			origOutMode = mode;
			outSaved = 1;
		}
		SetConsoleMode(h, mode | ENABLE_PROCESSED_OUTPUT | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
	}
	h = GetStdHandle(STD_ERROR_HANDLE);
	if( isConsole(h) && GetConsoleMode(h, &mode) ) {
		if( !errSaved ) {
			origErrMode = mode;
			errSaved = 1;
		}
		SetConsoleMode(h, mode | ENABLE_PROCESSED_OUTPUT | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
	}
}

/*
 * The shell writes UTF-8 bytes. A console decodes what is written with its output code page (437
 * or 850 in a new window), so any text outside ASCII came out as the wrong characters. The code
 * page is made UTF-8 for as long as the shell runs and put back when it ends. FSH_CONSOLE_UTF8=0
 * leaves it alone.
 */
static void utf8Output() {
	const char *optOut = getenv("FSH_CONSOLE_UTF8");
	if( origOutputCp != 0 || (optOut != NULL && strcmp(optOut, "0") == 0) ) {
		return;
	}
	if( !isConsole(out()) && !isConsole(GetStdHandle(STD_ERROR_HANDLE)) ) {
		return;
	}
	UINT cp = GetConsoleOutputCP();
	if( cp != 0 && cp != CP_UTF8 && SetConsoleOutputCP(CP_UTF8) ) {
		origOutputCp = cp;
	}
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
	enableEscapes();
	utf8Output();
	atexit(restoreOutput);
	return JNI_VERSION_1_8;
}

/*
 * on: Ctrl-C, Ctrl-Z and Ctrl-\ are read as keys instead of signalling the shell.
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_setSignalKeys(JNIEnv *, jobject, jboolean on) {
	keysNotSignals = on ? 1 : 0;
	DWORD mode;
	HANDLE h = in();
	if( rawMode && GetConsoleMode(h, &mode) ) {
		// now, without dropping what was typed
		if( keysNotSignals ) {
			mode &= ~ENABLE_PROCESSED_INPUT;
		} else {
			mode |= ENABLE_PROCESSED_INPUT;
		}
		SetConsoleMode(h, mode);
	}
}

/* the shell had the console in raw mode when it lent it to a program */
static int lentRaw = 0;

/*
 * on: a program gets the console (it reads and writes it itself), in the console's own mode,
 * where Ctrl-C signals it. off: the shell has it back, as it was.
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_setProgramMode(JNIEnv *, jobject, jboolean on) {
	if( on ) {
		lentRaw = rawMode;
		if( rawMode ) {
			SetConsoleMode(in(), origInMode);
			rawMode = 0;
		}
	} else {
		// the program may have changed the output mode too
		enableEscapes();
		if( lentRaw ) {
			lentRaw = 0;
			enableRawMode();
		}
	}
}

/*
 * A job's process group gets the terminal (fg): Windows has no process groups on a console.
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_giveTerminalTo(JNIEnv *, jobject, jlong) {
}

/*
 * Put a child in a process group: Windows has none.
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_setProcessGroup(JNIEnv *, jobject, jlong, jlong) {
}

/*
 * @return 0: a Windows process cannot be stopped by a signal.
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_stoppedBy(JNIEnv *, jobject, jlong) {
	return 0;
}

/*
 * @return the console window's width in columns (standard output's), or 0
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_columns0(JNIEnv *, jobject) {
	CONSOLE_SCREEN_BUFFER_INFO info;
	if( GetConsoleScreenBufferInfo(out(), &info) ) {
		int cols = info.srWindow.Right - info.srWindow.Left + 1;
		if( cols > 0 ) {
			return cols;
		}
	}
	return 0;
}

/*
 * @return the name of standard input's console (CON), or null
 */
JNIEXPORT jstring JNICALL Java_us_bringardner_fsh_NativeKeyboard_ttyName0(JNIEnv *env, jobject) {
	return isConsole(in()) ? env->NewStringUTF("CON") : NULL;
}

/*
 * @return what standard input is: 1 a pipe or socket, 2 a file, 0 something else (a console, NUL)
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_inputKind0(JNIEnv *, jobject) {
	switch( GetFileType(in()) ) {
	case FILE_TYPE_PIPE: return 1;
	case FILE_TYPE_DISK: return 2;
	default: return 0;
	}
}

/*
 * @return true if standard input is a console
 */
JNIEXPORT jboolean JNICALL Java_us_bringardner_fsh_NativeKeyboard_isInputTerminal0(JNIEnv *, jobject) {
	return isConsole(in()) ? JNI_TRUE : JNI_FALSE;
}

/*
 * @return true if standard input and output are a console
 */
JNIEXPORT jboolean JNICALL Java_us_bringardner_fsh_NativeKeyboard_isTerminal(JNIEnv *, jobject) {
	return isConsole(in()) && isConsole(out()) ? JNI_TRUE : JNI_FALSE;
}

/*
 * descriptor fd is a console: 0, 1 and 2 are standard input, output and error; Windows has no
 * other numbered descriptors for the shell.
 */
JNIEXPORT jboolean JNICALL Java_us_bringardner_fsh_NativeKeyboard_isatty0(JNIEnv *, jclass, jint fd) {
	DWORD which;
	switch( fd ) {
	case 0: which = STD_INPUT_HANDLE; break;
	case 1: which = STD_OUTPUT_HANDLE; break;
	case 2: which = STD_ERROR_HANDLE; break;
	default: return JNI_FALSE;
	}
	return isConsole(GetStdHandle(which)) ? JNI_TRUE : JNI_FALSE;
}

/* a FILETIME (100 ns units) in microseconds */
static jlong micros(const FILETIME &t) {
	ULARGE_INTEGER u;
	u.LowPart = t.dwLowDateTime;
	u.HighPart = t.dwHighDateTime;
	return (jlong) (u.QuadPart / 10);
}

/*
 * The CPU time of the process: {self user, self system, children user, children system} in
 * microseconds. Windows keeps no total for the programs that have ended: theirs are 0.
 */
JNIEXPORT jlongArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_resourceUsage0(JNIEnv *env, jobject) {
	jlong values[4] = {0, 0, 0, 0};
	FILETIME created, exited, kernel, user;
	if( GetProcessTimes(GetCurrentProcess(), &created, &exited, &kernel, &user) ) {
		values[0] = micros(user);
		values[1] = micros(kernel);
	}
	jlongArray ret = env->NewLongArray(4);
	if( ret != NULL ) {
		env->SetLongArrayRegion(ret, 0, 4, values);
	}
	return ret;
}

/*
 * The descriptors above 2 that a program would get: none on Windows (a program is given only
 * standard input, output and error).
 */
JNIEXPORT jobjectArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_openDescriptors0(JNIEnv *env, jobject) {
	return env->NewObjectArray(0, env->FindClass("java/lang/String"), NULL);
}

#define bufferSize 300

static unsigned char buffer[bufferSize];
static int cnt = 0;
static int idx = 0;

/* the high half of a character outside the BMP, waiting for its low half */
static WCHAR highSurrogate = 0;

/* the special key for virtual key vk, or 0 */
static int specialKey(WORD vk) {
	switch( vk ) {
	case VK_UP: return KEY_UP;
	case VK_DOWN: return KEY_DN;
	case VK_RIGHT: return KEY_RT;
	case VK_LEFT: return KEY_LF;
	case VK_HOME: return KEY_HOME;
	case VK_END: return KEY_END;
	case VK_PRIOR: return KEY_PAGE_UP;
	case VK_NEXT: return KEY_PAGE_DOWN;
	case VK_DELETE: return KEY_DELETE;
	}
	return 0;
}

/* the record is a key that getChar returns something for */
static int isKey(const INPUT_RECORD &r) {
	if( r.EventType != KEY_EVENT ) {
		return 0;
	}
	const KEY_EVENT_RECORD &k = r.Event.KeyEvent;
	if( k.bKeyDown ) {
		return k.uChar.UnicodeChar != 0 || specialKey(k.wVirtualKeyCode) != 0;
	}
	// a character typed with Alt and the numeric keypad arrives when Alt is released
	return k.wVirtualKeyCode == VK_MENU && k.uChar.UnicodeChar != 0;
}

/* append c to the buffer as UTF-8 */
static void putUtf8(unsigned long c) {
	if( cnt+4 > bufferSize ) {
		return;
	}
	if( c < 0x80 ) {
		buffer[cnt++] = (unsigned char) c;
	} else if( c < 0x800 ) {
		buffer[cnt++] = (unsigned char) (0xC0 | (c >> 6));
		buffer[cnt++] = (unsigned char) (0x80 | (c & 0x3F));
	} else if( c < 0x10000 ) {
		buffer[cnt++] = (unsigned char) (0xE0 | (c >> 12));
		buffer[cnt++] = (unsigned char) (0x80 | ((c >> 6) & 0x3F));
		buffer[cnt++] = (unsigned char) (0x80 | (c & 0x3F));
	} else {
		buffer[cnt++] = (unsigned char) (0xF0 | (c >> 18));
		buffer[cnt++] = (unsigned char) (0x80 | ((c >> 12) & 0x3F));
		buffer[cnt++] = (unsigned char) (0x80 | ((c >> 6) & 0x3F));
		buffer[cnt++] = (unsigned char) (0x80 | (c & 0x3F));
	}
}

/*
 * Put what key record r types in the buffer, as a terminal would send it.
 * @return a special key (KEY_UP ...), or 0
 */
static int translate(const INPUT_RECORD &r) {
	if( !isKey(r) ) {
		return 0;
	}
	const KEY_EVENT_RECORD &k = r.Event.KeyEvent;
	int key = specialKey(k.wVirtualKeyCode);
	if( key && k.uChar.UnicodeChar == 0 ) {
		return key;
	}
	WCHAR w = k.uChar.UnicodeChar;
	unsigned long c;
	if( w >= 0xD800 && w <= 0xDBFF ) {
		highSurrogate = w;
		return 0;
	} else if( w >= 0xDC00 && w <= 0xDFFF ) {
		if( !highSurrogate ) {
			return 0;
		}
		c = 0x10000 + (((unsigned long) (highSurrogate - 0xD800)) << 10) + (w - 0xDC00);
		highSurrogate = 0;
	} else {
		c = w;
	}
	if( c == '\r' ) {
		// as the terminal's ICRNL does
		c = '\n';
	} else if( c == 8 && k.wVirtualKeyCode == VK_BACK ) {
		// Backspace, as a terminal sends it
		c = 127;
	}
	// Alt-x is ESC x (AltGr, which is Ctrl and Alt, types a character of its own)
	DWORD state = k.dwControlKeyState;
	int meta = k.bKeyDown && (state & (LEFT_ALT_PRESSED | RIGHT_ALT_PRESSED))
			&& !(state & (LEFT_CTRL_PRESSED | RIGHT_CTRL_PRESSED));
	int repeat = k.wRepeatCount > 0 ? k.wRepeatCount : 1;
	for(int i = 0; i < repeat; i++ ) {
		if( meta ) {
			putUtf8(27);
		}
		putUtf8(c);
	}
	return 0;
}

/* a special key in a terminal's escape sequence in the buffer (from a pipe), or 0 */
static int escapeKey() {
	if( cnt == 3 && buffer[0] == 27 && buffer[1] == '[' ) {
		switch( buffer[2] ) {
		case 'A': return KEY_UP;
		case 'B': return KEY_DN;
		case 'C': return KEY_RT;
		case 'D': return KEY_LF;
		case 'H': return KEY_HOME;
		case 'F': return KEY_END;
		}
	} else if( cnt == 4 && buffer[0] == 27 && buffer[1] == '[' && buffer[3] == '~' ) {
		switch( buffer[2] ) {
		case '3': return KEY_DELETE;
		case '5': return KEY_PAGE_UP;
		case '6': return KEY_PAGE_DOWN;
		}
	}
	return 0;
}

/* the bytes left in file h from where it is now */
static jint fileRemaining(HANDLE h) {
	LARGE_INTEGER size, pos, zero;
	zero.QuadPart = 0;
	if( !GetFileSizeEx(h, &size) || !SetFilePointerEx(h, zero, &pos, FILE_CURRENT) ) {
		return 0;
	}
	LONGLONG n = size.QuadPart - pos.QuadPart;
	return n <= 0 ? 0 : (n > 0x7fffffff ? 0x7fffffff : (jint) n);
}

/*
 * @return the number of bytes that can be read without blocking.
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_ready(JNIEnv *, jobject) {
	if( idx < cnt ) {
		return cnt - idx;
	}
	enableRawMode();
	HANDLE h = in();
	if( isConsole(h) ) {
		// the keys waiting (each is at least a byte); other events (mouse, focus, key up) are skipped
		INPUT_RECORD recs[64];
		DWORD n = 0;
		if( !PeekConsoleInputW(h, recs, 64, &n) ) {
			return 0;
		}
		jint keys = 0;
		for(DWORD i = 0; i < n; i++ ) {
			if( isKey(recs[i]) ) {
				keys++;
			}
		}
		return keys;
	}
	switch( GetFileType(h) ) {
	case FILE_TYPE_PIPE: {
		DWORD avail = 0;
		if( !PeekNamedPipe(h, NULL, 0, NULL, &avail, NULL) ) {
			return 0;
		}
		return (jint) avail;
	}
	case FILE_TYPE_DISK:
		return fileRemaining(h);
	}
	return 0;
}

/*
 * @return the next key from the console, KEY_NONE if nothing was typed within timeout
 * milliseconds, or KEY_EOF if the console cannot be read.
 */
static int readConsoleKey(HANDLE h, DWORD timeout) {
	ULONGLONG until = tickCount() + timeout;
	while( true ) {
		ULONGLONG now = tickCount();
		DWORD left = now >= until ? 0 : (DWORD) (until - now);
		DWORD w = WaitForSingleObject(h, left);
		if( w == WAIT_TIMEOUT ) {
			return KEY_NONE;
		}
		if( w != WAIT_OBJECT_0 ) {
			return KEY_EOF;
		}
		INPUT_RECORD r;
		DWORD n = 0;
		if( !ReadConsoleInputW(h, &r, 1, &n) ) {
			return KEY_EOF;
		}
		if( n == 0 ) {
			continue;
		}
		cnt = idx = 0;
		int key = translate(r);
		if( key ) {
			return key;
		}
		if( cnt > 0 ) {
			return buffer[idx++];
		}
		if( left == 0 ) {
			return KEY_NONE;
		}
	}
}

/*
 * @return the next byte (0-255), a special key (KEY_UP ...), KEY_NONE if nothing was typed
 * within a second, or KEY_EOF at the end of input.
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_getChar(JNIEnv *, jobject) {

	if( idx < cnt ) {
		return buffer[idx++];
	}

	enableRawMode();

	HANDLE h = in();
	if( isConsole(h) ) {
		return readConsoleKey(h, 1000);
	}

	if( GetFileType(h) == FILE_TYPE_PIPE ) {
		// wait up to a second for something to read, as poll() does on a terminal
		ULONGLONG until = tickCount() + 1000;
		while( true ) {
			DWORD avail = 0;
			if( !PeekNamedPipe(h, NULL, 0, NULL, &avail, NULL) ) {
				// the writer closed the pipe
				cnt = idx = 0;
				return KEY_EOF;
			}
			if( avail > 0 ) {
				break;
			}
			if( tickCount() >= until ) {
				return KEY_NONE;
			}
			Sleep(10);
		}
	}

	DWORD n = 0;
	if( !ReadFile(h, buffer, bufferSize, &n, NULL) || n == 0 ) {
		// 0 is end of file
		cnt = idx = 0;
		return KEY_EOF;
	}
	cnt = (int) n;
	idx = 0;

	int key = escapeKey();
	if( key ) {
		cnt = 0;
		return key;
	}

	return buffer[idx++];
}

/* ------------------------------------------------------------------ the process
 * (the contract's answers where there is no Unix equivalent: see the top of this file)
 */

/* the terminal (/dev/tty) is process group group's: Windows has no process groups on a console */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_ttyTo0(JNIEnv *, jclass, jint) {
}

/* this process's process group: the process is the leader of its own */
JNIEXPORT jlong JNICALL Java_us_bringardner_fsh_NativeKeyboard_pgrp0(JNIEnv *, jclass) {
	return (jlong) GetCurrentProcessId();
}

/* {uid, gid, groups...}: null, there are no numeric ids (the caller has its fallbacks) */
JNIEXPORT jlongArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_ids0(JNIEnv *, jclass) {
	return NULL;
}

/* -1: what this process was started with is not kept, so it cannot be told */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_startedWith0(JNIEnv *, jclass, jstring) {
	return -1;
}

/* the open-files limit: Windows has none to set (-ENOSYS) */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_setOpenFilesLimit0(JNIEnv *, jclass, jlong) {
	return -ENOSYS;
}

/* this process's argv[0] as it was started: the first word of its command line, or null */
JNIEXPORT jstring JNICALL Java_us_bringardner_fsh_NativeKeyboard_argv0(JNIEnv *env, jclass) {
	const wchar_t *p = GetCommandLineW();
	if( p == NULL ) {
		return NULL;
	}
	while( *p == L' ' || *p == L'\t' ) {
		p++;
	}
	const wchar_t *end;
	if( *p == L'"' ) {
		// (the CRT's rule: up to the next quote, no escapes in the program's name)
		p++;
		end = p;
		while( *end != 0 && *end != L'"' ) {
			end++;
		}
	} else {
		end = p;
		while( *end != 0 && *end != L' ' && *end != L'\t' ) {
			end++;
		}
	}
	if( end == p ) {
		return NULL;
	}
	return env->NewString((const jchar *) p, (jsize) (end - p));
}

/* the system's release, as 10.0.19045 (RtlGetVersion: GetVersionEx reports what the manifest says), or null */
JNIEXPORT jstring JNICALL Java_us_bringardner_fsh_NativeKeyboard_osRelease0(JNIEnv *env, jclass) {
	typedef LONG (WINAPI *RtlGetVersionFn)(OSVERSIONINFOW *);
	HMODULE ntdll = GetModuleHandleW(L"ntdll.dll");
	if( ntdll == NULL ) {
		return NULL;
	}
	RtlGetVersionFn get = (RtlGetVersionFn) GetProcAddress(ntdll, "RtlGetVersion");
	if( get == NULL ) {
		return NULL;
	}
	OSVERSIONINFOW v;
	memset(&v, 0, sizeof v);
	v.dwOSVersionInfoSize = sizeof v;
	if( get(&v) != 0 ) {
		return NULL;
	}
	char release[64];
	snprintf(release, sizeof release, "%lu.%lu.%lu", (unsigned long) v.dwMajorVersion,
			(unsigned long) v.dwMinorVersion, (unsigned long) v.dwBuildNumber);
	return env->NewStringUTF(release);
}
