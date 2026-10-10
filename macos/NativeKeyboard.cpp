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
#include <sys/resource.h>
#include <limits.h>
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
 * @return the terminal's width in columns (standard output's), or 0
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_columns0(JNIEnv *, jobject) {
	struct winsize ws;
	if( ioctl(STDOUT_FILENO, TIOCGWINSZ, &ws) == 0 && ws.ws_col > 0 ) {
		return ws.ws_col;
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

/*
 * The CPU time of the process and of its children that have ended (and been waited for):
 * {self user, self system, children user, children system} in microseconds.
 */
JNIEXPORT jlongArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_resourceUsage0(JNIEnv *env, jobject) {
	struct rusage self, kids;
	jlong values[4] = {0, 0, 0, 0};
	if( getrusage(RUSAGE_SELF, &self) == 0 ) {
		values[0] = (jlong) self.ru_utime.tv_sec*1000000 + self.ru_utime.tv_usec;
		values[1] = (jlong) self.ru_stime.tv_sec*1000000 + self.ru_stime.tv_usec;
	}
	if( getrusage(RUSAGE_CHILDREN, &kids) == 0 ) {
		values[2] = (jlong) kids.ru_utime.tv_sec*1000000 + kids.ru_utime.tv_usec;
		values[3] = (jlong) kids.ru_stime.tv_sec*1000000 + kids.ru_stime.tv_usec;
	}
	jlongArray ret = env->NewLongArray(4);
	if( ret != NULL ) {
		env->SetLongArrayRegion(ret, 0, 4, values);
	}
	return ret;
}

/*
 * The descriptors 3..255 that are open without FD_CLOEXEC: "fd mode path" for each (mode is the
 * access mode, O_RDONLY 0, O_WRONLY 1, O_RDWR 2; path is empty if it is not known).
 */
JNIEXPORT jobjectArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_openDescriptors0(JNIEnv *env, jobject) {
	// (on the heap: 256 paths are too many for a thread's stack)
	typedef char Line[PATH_MAX+32];
	Line *lines = (Line *) malloc(256*sizeof(Line));
	if( lines == NULL ) {
		return env->NewObjectArray(0, env->FindClass("java/lang/String"), NULL);
	}
	int n = 0;
	for(int fd = 3; fd < 256; fd++) {
		int flags = fcntl(fd, F_GETFD);
		if( flags < 0 || (flags & FD_CLOEXEC) ) {
			continue;
		}
		int status = fcntl(fd, F_GETFL);
		if( status < 0 ) {
			continue;
		}
		char path[PATH_MAX];
		path[0] = 0;
#ifdef F_GETPATH
		if( fcntl(fd, F_GETPATH, path) != 0 ) {
			path[0] = 0;
		}
#endif
		snprintf(lines[n++], sizeof(lines[0]), "%d %d %s", fd, status & O_ACCMODE, path);
	}
	jobjectArray ret = env->NewObjectArray(n, env->FindClass("java/lang/String"), NULL);
	if( ret != NULL ) {
		for(int i = 0; i < n; i++) {
			env->SetObjectArrayElement(ret, i, env->NewStringUTF(lines[i]));
		}
	}
	free(lines);
	return ret;
}

/* ------------------------------------------------------------------ starting programs
 * posix_spawn straight from the shell (Java's ProcessBuilder goes through jspawnhelper, an exec
 * more for each program): see NativeProcess.java.
 */
#include <spawn.h>

// (posix_spawn_file_actions_addchdir, its new name, is only in macOS 26)
#pragma clang diagnostic ignored "-Wdeprecated-declarations"

/* a byte[] as a NUL-terminated C string (malloc'd) */
static char *cstring(JNIEnv *env, jbyteArray a) {
	if( a == NULL ) {
		return NULL;
	}
	jsize n = env->GetArrayLength(a);
	char *ret = (char *) malloc((size_t) n+1);
	env->GetByteArrayRegion(a, 0, n, (jbyte *) ret);
	ret[n] = '\0';
	return ret;
}

/* a byte[][] as a NULL-terminated array of C strings (malloc'd) */
static char **cstrings(JNIEnv *env, jobjectArray a) {
	jsize n = a == NULL ? 0 : env->GetArrayLength(a);
	char **ret = (char **) calloc((size_t) n+1, sizeof(char *));
	for(jsize i = 0; i < n; i++) {
		jbyteArray e = (jbyteArray) env->GetObjectArrayElement(a, i);
		ret[i] = cstring(env, e);
		env->DeleteLocalRef(e);
	}
	return ret;
}

static void freeStrings(char **s) {
	if( s != NULL ) {
		for(char **p = s; *p != NULL; p++) {
			free(*p);
		}
		free(s);
	}
}

/*
 * Start path with argv and envp in dir (null: here), its descriptors fds[i]=child, fds[i+1]=the
 * shell's (all others closed), in process group pgroup (-1: the shell's, 0: a new one), with no
 * signals blocked; suspended: it does not run until it gets SIGCONT.
 * @return its process id, or -errno
 */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_spawn0(JNIEnv *env, jclass, jbyteArray jpath, jobjectArray jargv,
		jobjectArray jenvp, jbyteArray jdir, jintArray jfds, jint pgroup, jboolean suspended) {
	char *path = cstring(env, jpath);
	char **argv = cstrings(env, jargv);
	char **envp = cstrings(env, jenvp);
	char *dir = cstring(env, jdir);
	jsize nfds = jfds == NULL ? 0 : env->GetArrayLength(jfds);
	jint *fds = nfds > 0 ? env->GetIntArrayElements(jfds, NULL) : NULL;

	posix_spawn_file_actions_t fa;
	posix_spawn_file_actions_init(&fa);
	posix_spawnattr_t at;
	posix_spawnattr_init(&at);
	// (handlers are reset by exec itself; those the shell ignores stay ignored, as in bash)
	short flags = POSIX_SPAWN_CLOEXEC_DEFAULT | POSIX_SPAWN_SETSIGMASK;
	sigset_t none;
	sigemptyset(&none);
	posix_spawnattr_setsigmask(&at, &none);
	if( pgroup >= 0 ) {
		flags |= POSIX_SPAWN_SETPGROUP;
		posix_spawnattr_setpgroup(&at, (pid_t) pgroup);
	}
	if( suspended ) {
		flags |= POSIX_SPAWN_START_SUSPENDED;
	}
	posix_spawnattr_setflags(&at, flags);

	// (each of the shell's descriptors copied above all the child's numbers first, so that one
	// mapping does not overwrite another's source)
	int top = 2;
	for(jsize i = 0; i+1 < nfds; i += 2) {
		if( fds[i] > top ) {
			top = fds[i];
		}
	}
	int *temps = (int *) calloc((size_t) nfds/2+1, sizeof(int));
	int err = 0;
	for(jsize i = 0; i+1 < nfds; i += 2) {
		int t = fcntl(fds[i+1], F_DUPFD_CLOEXEC, top+1);
		if( t < 0 ) {
			err = errno;
			temps[i/2] = -1;
			continue;
		}
		temps[i/2] = t;
		posix_spawn_file_actions_adddup2(&fa, t, fds[i]);
	}
	if( dir != NULL ) {
		posix_spawn_file_actions_addchdir_np(&fa, dir);
	}
	pid_t pid = -1;
	if( err == 0 ) {
		err = posix_spawn(&pid, path, &fa, &at, argv, envp);
	}
	for(jsize i = 0; i < nfds/2; i++) {
		if( temps[i] >= 0 ) {
			close(temps[i]);
		}
	}
	free(temps);
	posix_spawn_file_actions_destroy(&fa);
	posix_spawnattr_destroy(&at);
	if( fds != NULL ) {
		env->ReleaseIntArrayElements(jfds, fds, JNI_ABORT);
	}
	free(path);
	free(dir);
	freeStrings(argv);
	freeStrings(envp);
	return err == 0 ? (jint) pid : (jint) -err;
}

/* a pipe {read, write}, both close-on-exec; null on failure */
JNIEXPORT jintArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_pipe0(JNIEnv *env, jclass) {
	int p[2];
	if( pipe(p) != 0 ) {
		return NULL;
	}
	fcntl(p[0], F_SETFD, FD_CLOEXEC);
	fcntl(p[1], F_SETFD, FD_CLOEXEC);
	jintArray ret = env->NewIntArray(2);
	jint v[2] = { p[0], p[1] };
	env->SetIntArrayRegion(ret, 0, 2, v);
	return ret;
}

/* open path to read ('r'), write ('w': created or truncated) or append ('a'), close-on-exec:
 * the descriptor or -errno */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_open0(JNIEnv *env, jclass, jbyteArray jpath, jint how) {
	int flags = how == 'w' ? O_WRONLY|O_CREAT|O_TRUNC : how == 'a' ? O_WRONLY|O_CREAT|O_APPEND : O_RDONLY;
	char *path = cstring(env, jpath);
	int fd = open(path, flags | O_CLOEXEC, 0666);
	int err = errno;
	free(path);
	return fd >= 0 ? fd : -err;
}

JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_close0(JNIEnv *, jclass, jint fd) {
	close(fd);
}

/* wait for child pid to end: its wait status, or -1 (not a child) */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_waitExit0(JNIEnv *, jclass, jint pid) {
	int status = 0;
	while( waitpid((pid_t) pid, &status, 0) < 0 ) {
		if( errno != EINTR ) {
			return -1;
		}
	}
	return status;
}

/* fd as a java.io.FileDescriptor (its own: closing the stream closes it) */
JNIEXPORT jobject JNICALL Java_us_bringardner_fsh_NativeKeyboard_fd0(JNIEnv *env, jclass, jint fd) {
	jclass c = env->FindClass("java/io/FileDescriptor");
	jmethodID init = env->GetMethodID(c, "<init>", "()V");
	jobject ret = env->NewObject(c, init);
	jfieldID f = env->GetFieldID(c, "fd", "I");
	env->SetIntField(ret, f, fd);
	return ret;
}

/* send sig to pid: 0, or -errno */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_kill0(JNIEnv *, jclass, jint pid, jint sig) {
	return kill((pid_t) pid, sig) == 0 ? 0 : -errno;
}

JNIEXPORT jstring JNICALL Java_us_bringardner_fsh_NativeKeyboard_strerror0(JNIEnv *env, jclass, jint err) {
	return env->NewStringUTF(strerror(err));
}

/* ------------------------------------------------------------------ a subshell's stand-in starts its programs
 * (fshexec -s with a socket as descriptor 3: see StandIn.java)
 */
#include <sys/socket.h>
#include <arpa/inet.h>

/* a connected pair of sockets, both close-on-exec; null on failure */
JNIEXPORT jintArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_socketPair0(JNIEnv *env, jclass) {
	int p[2];
	if( socketpair(AF_UNIX, SOCK_STREAM, 0, p) != 0 ) {
		return NULL;
	}
	fcntl(p[0], F_SETFD, FD_CLOEXEC);
	fcntl(p[1], F_SETFD, FD_CLOEXEC);
	int on = 1;
	setsockopt(p[0], SOL_SOCKET, SO_NOSIGPIPE, &on, sizeof on);
	jintArray ret = env->NewIntArray(2);
	jint v[2] = { p[0], p[1] };
	env->SetIntArrayRegion(ret, 0, 2, v);
	return ret;
}

/* send payload (after its length, 4 bytes in network order) with descriptors fds: 0 or -errno */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_sendWithDescriptors0(JNIEnv *env, jclass, jint sock, jbyteArray jpayload, jintArray jfds) {
	jsize n = env->GetArrayLength(jpayload);
	jsize nfds = jfds == NULL ? 0 : env->GetArrayLength(jfds);
	if( nfds > 16 ) {
		return -EINVAL;
	}
	char *buf = (char *) malloc((size_t) n + 4);
	uint32_t len = htonl((uint32_t) n);
	memcpy(buf, &len, 4);
	env->GetByteArrayRegion(jpayload, 0, n, (jbyte *) buf+4);
	char control[CMSG_SPACE(16 * sizeof(int))];
	memset(control, 0, sizeof control);
	struct iovec iov = { buf, (size_t) n + 4 };
	struct msghdr msg;
	memset(&msg, 0, sizeof msg);
	msg.msg_iov = &iov;
	msg.msg_iovlen = 1;
	if( nfds > 0 ) {
		jint fds[16];
		env->GetIntArrayRegion(jfds, 0, nfds, fds);
		msg.msg_control = control;
		msg.msg_controllen = CMSG_SPACE(nfds * sizeof(int));
		struct cmsghdr *c = CMSG_FIRSTHDR(&msg);
		c->cmsg_level = SOL_SOCKET;
		c->cmsg_type = SCM_RIGHTS;
		c->cmsg_len = CMSG_LEN(nfds * sizeof(int));
		for(jsize i = 0; i < nfds; i++) {
			int fd = fds[i];
			memcpy(CMSG_DATA(c) + i * sizeof(int), &fd, sizeof(int));
		}
	}
	size_t off = 0;
	int err = 0;
	while( off < (size_t) n + 4 ) {
		ssize_t w = off == 0 ? sendmsg(sock, &msg, 0) : send(sock, buf+off, (size_t) n + 4 - off, 0);
		if( w < 0 && errno == EINTR ) {
			continue;
		}
		if( w <= 0 ) {
			err = w < 0 ? errno : EPIPE;
			break;
		}
		off += (size_t) w;
	}
	free(buf);
	return -err;
}

/* the terminal (/dev/tty) is process group group's (0: the shell's), as fshexec -g gives it (fg) */
JNIEXPORT void JNICALL Java_us_bringardner_fsh_NativeKeyboard_ttyTo0(JNIEnv *, jclass, jint group) {
	if( group == 0 ) {
		group = getpgrp();
	}
	int tty = open("/dev/tty", O_RDWR | O_CLOEXEC);
	if( tty < 0 ) {
		return;
	}
	sigset_t block, old;
	sigemptyset(&block);
	sigaddset(&block, SIGTTOU);
	pthread_sigmask(SIG_BLOCK, &block, &old);
	tcsetpgrp(tty, (pid_t) group);
	pthread_sigmask(SIG_SETMASK, &old, NULL);
	close(tty);
}

/* the kernel's release (uname -r), or null */
#include <sys/utsname.h>
JNIEXPORT jstring JNICALL Java_us_bringardner_fsh_NativeKeyboard_osRelease0(JNIEnv *env, jclass) {
	struct utsname u;
	if( uname(&u) != 0 ) {
		return NULL;
	}
	return env->NewStringUTF(u.release);
}

/* this process's argv[0] as it was started (KERN_PROCARGS2: the exec path, then argv), or null */
#include <sys/sysctl.h>
JNIEXPORT jstring JNICALL Java_us_bringardner_fsh_NativeKeyboard_argv0(JNIEnv *env, jclass) {
	int mib[3] = { CTL_KERN, KERN_PROCARGS2, getpid() };
	size_t size = 0;
	if( sysctl(mib, 3, NULL, &size, NULL, 0) != 0 || size < sizeof(int) ) {
		return NULL;
	}
	char *buf = (char *) malloc(size);
	if( buf == NULL || sysctl(mib, 3, buf, &size, NULL, 0) != 0 ) {
		free(buf);
		return NULL;
	}
	char *p = buf + sizeof(int);
	char *end = buf + size;
	// the exec path, then NULs up to argv[0]
	while( p < end && *p != '\0' ) {
		p++;
	}
	while( p < end && *p == '\0' ) {
		p++;
	}
	jstring ret = p < end ? env->NewStringUTF(p) : NULL;
	free(buf);
	return ret;
}

/* {uid, gid, the groups...} of this process (getgroups(2)) */
JNIEXPORT jlongArray JNICALL Java_us_bringardner_fsh_NativeKeyboard_ids0(JNIEnv *env, jclass) {
	int n = getgroups(0, NULL);
	if( n < 0 ) {
		n = 0;
	}
	gid_t *g = (gid_t *) calloc((size_t) n+1, sizeof(gid_t));
	n = getgroups(n, g);
	if( n < 0 ) {
		n = 0;
	}
	jlongArray ret = env->NewLongArray(n+2);
	jlong *v = (jlong *) calloc((size_t) n+2, sizeof(jlong));
	v[0] = (jlong) getuid();
	v[1] = (jlong) getgid();
	for(int i = 0; i < n; i++) {
		v[i+2] = (jlong) g[i];
	}
	env->SetLongArrayRegion(ret, 0, n+2, v);
	free(v);
	free(g);
	return ret;
}

/* name is in the environment this process was started with (KERN_PROCARGS2: argc, the exec
 * path, argv, then the environment), not one set since (macOS sets __CF_USER_TEXT_ENCODING in
 * the process); -1 if it cannot be told */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_startedWith0(JNIEnv *env, jclass, jstring jname) {
	int mib[3] = { CTL_KERN, KERN_PROCARGS2, getpid() };
	size_t size = 0;
	if( sysctl(mib, 3, NULL, &size, NULL, 0) != 0 || size < sizeof(int) ) {
		return -1;
	}
	char *buf = (char *) malloc(size);
	if( buf == NULL || sysctl(mib, 3, buf, &size, NULL, 0) != 0 ) {
		free(buf);
		return -1;
	}
	int argc;
	memcpy(&argc, buf, sizeof argc);
	char *p = buf + sizeof(int);
	char *end = buf + size;
	// the exec path, then NULs, then argc arguments
	while( p < end && *p != '\0' ) {
		p++;
	}
	while( p < end && *p == '\0' ) {
		p++;
	}
	for(int i = 0; i < argc && p < end; i++) {
		p += strlen(p) + 1;
	}
	const char *name = env->GetStringUTFChars(jname, NULL);
	size_t n = strlen(name);
	int found = 0;
	while( p < end && *p != '\0' ) {
		if( strncmp(p, name, n) == 0 && p[n] == '=' ) {
			found = 1;
			break;
		}
		p += strlen(p) + 1;
	}
	env->ReleaseStringUTFChars(jname, name);
	free(buf);
	return found;
}

/* the open-files soft limit (setrlimit RLIMIT_NOFILE): limit, or -1 for unlimited; 0 or -errno */
JNIEXPORT jint JNICALL Java_us_bringardner_fsh_NativeKeyboard_setOpenFilesLimit0(JNIEnv *, jclass, jlong limit) {
	struct rlimit r;
	if( getrlimit(RLIMIT_NOFILE, &r) != 0 ) {
		return -errno;
	}
	r.rlim_cur = limit < 0 ? RLIM_INFINITY : (rlim_t) limit;
	return setrlimit(RLIMIT_NOFILE, &r) == 0 ? 0 : -errno;
}

/* descriptor fd (of this process) is a terminal */
JNIEXPORT jboolean JNICALL Java_us_bringardner_fsh_NativeKeyboard_isatty0(JNIEnv *, jclass, jint fd) {
	return isatty(fd) ? JNI_TRUE : JNI_FALSE;
}
