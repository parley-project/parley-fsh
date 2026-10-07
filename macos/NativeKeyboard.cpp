#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <termios.h>
#include <unistd.h>
#include <poll.h>
#include <sys/ioctl.h>
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
	if( tcgetattr(STDIN_FILENO, &orig_termios) == -1 ) {
		return;
	}
	static int registered = 0;
	if( !registered ) {
		atexit(disableRawMode);
		registered = 1;
	}
	struct termios raw = orig_termios;
	raw.c_lflag &= ~(ECHO | ICANON);
	raw.c_cc[VMIN] = 1;
	raw.c_cc[VTIME] = 0;
	if( tcsetattr(STDIN_FILENO, TCSAFLUSH, &raw) == 0 ) {
		rawMode = 1;
	}
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
