#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <stdlib.h>
#include <errno.h>
#include <termios.h>
#include <unistd.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <signal.h>
#include <fcntl.h>
#include "NativeKeyboard.h"

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

static struct termios orig_termios;
static int rawMode = 0;
/* 1: Ctrl-C, Ctrl-Z and Ctrl-\ are read as keys (the interactive shell decides what they stop) */
static int keysNotSignals = 0;

static void disableRawMode() {
	if( rawMode ) {
		tcsetattr(STDIN_FILENO, TCSAFLUSH, &orig_termios);
		rawMode = 0;
	}
}

/*
 * Turn off echo and line buffering. The original settings are saved only once,
 * before they are changed, so they are what gets restored when the JVM exits.
 */
static void enableRawMode() {
	if( rawMode || !isatty(STDIN_FILENO) ) {
		return;
	}
	// the terminal's own settings, saved once: a program that leaves it in another mode does not change them
	static int saved = 0;
	if( !saved ) {
		if( tcgetattr(STDIN_FILENO, &orig_termios) == -1 ) {
			return;
		}
		saved = 1;
	}
	static int registered = 0;
	if( !registered ) {
		atexit(disableRawMode);
		registered = 1;
	}
	struct termios raw = orig_termios;
	raw.c_lflag &= ~(ECHO | ICANON);
	if( keysNotSignals ) {
		raw.c_lflag &= ~ISIG;
	}
	raw.c_cc[VMIN] = 1;
	raw.c_cc[VTIME] = 0;
	if( tcsetattr(STDIN_FILENO, TCSADRAIN, &raw) == 0 ) {
		rawMode = 1;
	}
}

/*
 * on: Ctrl-C, Ctrl-Z and Ctrl-\ are read as keys instead of signalling the shell's process group.
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_setSignalKeys(JNIEnv *, jobject, jboolean on) {
	keysNotSignals = on ? 1 : 0;
	struct termios t;
	if( rawMode && tcgetattr(STDIN_FILENO, &t) == 0 ) {
		// now, without dropping what was typed
		if( keysNotSignals ) {
			t.c_lflag &= ~ISIG;
		} else {
			t.c_lflag |= ISIG;
		}
		tcsetattr(STDIN_FILENO, TCSANOW, &t);
	}
}

static void takeTerminal(pid_t group);

/* the shell had the terminal in raw mode when it lent it to a program */
static int lentRaw = 0;

/*
 * on: a program gets the terminal (it reads and writes it itself), in the terminal's own mode,
 * where Ctrl-C and Ctrl-Z signal it. off: the shell has it back, as it was.
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_setProgramMode(JNIEnv *, jobject, jboolean on) {
	if( on ) {
		lentRaw = rawMode;
		if( rawMode ) {
			tcsetattr(STDIN_FILENO, TCSADRAIN, &orig_termios);
			rawMode = 0;
		}
	} else {
		// the shell's process group has the terminal again (if a job's group had it)
		takeTerminal(getpgrp());
		if( lentRaw ) {
			lentRaw = 0;
			enableRawMode();
		}
	}
}

/*
 * Make group the terminal's foreground group (the shell may be in the background for this).
 */
static void takeTerminal(pid_t group) {
	if( !isatty(STDIN_FILENO) || tcgetpgrp(STDIN_FILENO) == group ) {
		return;
	}
	sigset_t block, old;
	sigemptyset(&block);
	sigaddset(&block, SIGTTOU);
	pthread_sigmask(SIG_BLOCK, &block, &old);
	tcsetpgrp(STDIN_FILENO, group);
	pthread_sigmask(SIG_SETMASK, &old, NULL);
}

/*
 * A job's process group gets the terminal (fg).
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_giveTerminalTo(JNIEnv *, jobject, jlong group) {
	takeTerminal((pid_t) group);
}

/*
 * Put a child in a process group (the child does it too: whichever is first).
 */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_setProcessGroup(JNIEnv *, jobject, jlong pid, jlong group) {
	setpgid((pid_t) pid, (pid_t) group);
}

/*
 * @return the signal that stopped child pid since the last call, or 0. Only a stop is reported:
 * the child's exit stays for whoever waits for it.
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_stoppedBy(JNIEnv *, jobject, jlong pid) {
	siginfo_t info;
	memset(&info, 0, sizeof(info));
	if( waitid(P_PID, (id_t) pid, &info, WSTOPPED | WNOHANG) == 0 && info.si_pid == (pid_t) pid && info.si_code == CLD_STOPPED ) {
		return info.si_status;
	}
	return 0;
}

/*
 * @return the name of standard input's terminal (/dev/ttys003), or null
 */
JNIEXPORT jstring JNICALL Java_us_bringardner_fsh_NativeKeyboard_ttyName0(JNIEnv *env, jobject) {
	const char *name = ttyname(STDIN_FILENO);
	return name == NULL ? NULL : env->NewStringUTF(name);
}

/*
 * @return what standard input is: 1 a pipe or socket, 2 a file, 0 something else (a terminal, /dev/null)
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_inputKind0(JNIEnv *, jobject) {
	struct stat st;
	if( fstat(STDIN_FILENO, &st) != 0 ) {
		return 0;
	}
	if( S_ISFIFO(st.st_mode) || S_ISSOCK(st.st_mode) ) {
		return 1;
	}
	return S_ISREG(st.st_mode) ? 2 : 0;
}

/*
 * @return true if standard input is a terminal
 */
JNIEXPORT jboolean JNICALL Java_us_bringardner_fsh_NativeKeyboard_isInputTerminal0(JNIEnv *, jobject) {
	return isatty(STDIN_FILENO) ? JNI_TRUE : JNI_FALSE;
}

/*
 * @return true if standard input and output are a terminal
 */
JNIEXPORT jboolean JNICALL Java_us_bringardner_fsh_NativeKeyboard_isTerminal(JNIEnv *, jobject) {
	return isatty(STDIN_FILENO) && isatty(STDOUT_FILENO) ? JNI_TRUE : JNI_FALSE;
}

#define bufferSize 300

static unsigned char buffer[bufferSize];
static int cnt = 0;
static int idx = 0;

/*
 * @return the number of bytes that can be read without blocking.
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_ready(JNIEnv *, jobject) {
	if( idx < cnt ) {
		return cnt - idx;
	}
	enableRawMode();
	int n = 0;
	if( ioctl(STDIN_FILENO, FIONREAD, &n) == -1 ) {
		return 0;
	}
	return n;
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

	struct pollfd p = {STDIN_FILENO, POLLIN, 0};
	int r = poll(&p, 1, 1000);
	if( r == 0 || (r < 0 && errno == EINTR) ) {
		return KEY_NONE;
	}
	if( r < 0 ) {
		return KEY_EOF;
	}

	ssize_t n;
	do {
		n = read(STDIN_FILENO, buffer, bufferSize);
	} while( n < 0 && errno == EINTR );

	if( n <= 0 ) {
		// 0 is end of file
		cnt = idx = 0;
		return KEY_EOF;
	}
	cnt = (int)n;
	idx = 0;

	if( cnt == 3 && buffer[0] == 27 && buffer[1] == '[' ) {
		int key = 0;
		switch( buffer[2] ) {
		case 'A': key = KEY_UP; break;
		case 'B': key = KEY_DN; break;
		case 'C': key = KEY_RT; break;
		case 'D': key = KEY_LF; break;
		case 'H': key = KEY_HOME; break;
		case 'F': key = KEY_END; break;
		}
		if( key ) {
			cnt = 0;
			return key;
		}
	} else if( cnt == 4 && buffer[0] == 27 && buffer[1] == '[' && buffer[3] == '~' ) {
		int key = 0;
		switch( buffer[2] ) {
		case '3': key = KEY_DELETE; break;
		case '5': key = KEY_PAGE_UP; break;
		case '6': key = KEY_PAGE_DOWN; break;
		}
		if( key ) {
			cnt = 0;
			return key;
		}
	}

	return buffer[idx++];
}
