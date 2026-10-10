/*
 * fshexec -s [-g]
 * fshexec -u | -G
 * fshexec -j program [args...]
 * fshexec [-g PGID FG] [-i FILE OFFSET REPORT] [-0 NAME] [-r [-e NAME=VALUE]...] [-f N r|w|d ARG]... [-I SIG,...] [--] program [args...]
 *
 * Runs a program for fsh as bash would run it.
 *
 * -g: in a process group of its own, as bash runs a job. PGID 0 makes a new group; any other
 *     joins that job's group (a new one if it is gone). FG 1 makes the group the terminal's
 *     foreground group, so the terminal's Ctrl-C and Ctrl-Z reach the job and not the shell.
 * -i: standard input is FILE from OFFSET, as the shell's file is shared with its programs in
 *     bash: when the program ends, where it left the file (head -n 1 reads a block and seeks
 *     back after its line) is written to REPORT, for the shell to go on reading from there.
 *     The status is the program's (a signal that ended it ends this too).
 * -0: the program's argv[0] is NAME (exec -a NAME, exec -l), not its path.
 * -r: the program and its arguments, and each -e NAME=VALUE that follows, are written with %XX
 *     for bytes (Java cannot pass bytes that are not UTF-8 itself); -e puts NAME=VALUE in the
 *     program's environment.
 * -s: stands in for one of fsh's subshells (which run in fsh's process): its process id is the
 *     subshell's ($BASHPID, $!). Each signal it gets (but KILL and STOP) is written to standard
 *     output as a byte, for fsh to give the subshell; a status read from standard input (a line)
 *     ends it with that status, as does the end of its input (0). With -g it leads a process
 *     group of its own (a job's, with job control: the job's programs join it, $! is the group).
 *     If its input is a socket (both its input and output), it also starts the subshell's
 *     programs, so that they are its children as in bash: see standIn.
 * -j: run program (java, for fsh) with -Dfsh.ignoredAtStart=SIG,... first: the signals this was
 *     started ignoring (and -Dfsh.cfAbsent=true without __CF_USER_TEXT_ENCODING, which macOS's
 *     java sets). The JVM takes some signals for itself (QUIT, USR2) before fsh can see that
 *     its caller ignored them, which bash would keep ignored (trap -p shows them).
 * -u, -G: the system's user (or group) names, one a line, in its order (getpwent, getgrent: what
 *     bash's compgen -u and -g list; Java cannot read them).
 * -I: the program ignores these signals (by number), as the shell does (trap '' INT): Java
 *     starts programs with every signal as by default, but bash's programs inherit the ones it
 *     ignores.
 * -f: the program's descriptor N (above 2: Java gives a program only 0, 1 and 2) is ARG opened
 *     for reading (r) or writing (w), or a copy of descriptor ARG (d), as N is open in the shell.
 */
#include <arpa/inet.h>
#include <errno.h>
#include <grp.h>
#include <spawn.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <pwd.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static const char *argv0 = NULL;

/* the signals -I says the program ignores */
static int ignored[NSIG];

/* s with each %XX made the byte XX, in place */
static char *decode(char *s) {
	char *out = s;
	for(char *in = s; *in != '\0'; in++) {
		if( in[0] == '%' && in[1] != '\0' && in[2] != '\0' ) {
			char hex[3] = { in[1], in[2], '\0' };
			*out++ = (char) strtol(hex, NULL, 16);
			in += 2;
		} else {
			*out++ = *in;
		}
	}
	*out = '\0';
	return s;
}

static void run(char **argv) {
	const char *path = argv[0];
	if( argv0 != NULL ) {
		argv[0] = (char *) argv0;
	}
	execvp(path, argv);
	argv[0] = (char *) path;
	int err = errno;
	fprintf(stderr, "fsh: %s: %s\n", argv[0], strerror(err));
	_exit(err == ENOENT ? 127 : 126);
}

/* descriptor n is path opened with flags, or (dup) a copy of descriptor arg */
static void descriptor(int n, const char *how, const char *arg) {
	int fd;
	if( strcmp(how, "d") == 0 ) {
		fd = atoi(arg);
		if( fd != n ) {
			dup2(fd, n);
		}
		return;
	}
	// (reading: not waiting for a writer, as the shell's may have written all and gone; what it
	// wrote is in the pipe)
	fd = open(arg, strcmp(how, "w") == 0 ? O_WRONLY : O_RDONLY|O_NONBLOCK);
	if( fd < 0 ) {
		fprintf(stderr, "fsh: %s: %s\n", arg, strerror(errno));
		return;
	}
	fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & ~O_NONBLOCK);
	if( fd != n ) {
		dup2(fd, n);
		close(fd);
	}
}

static int writeFully(int fd, const void *buf, size_t n) {
	const char *p = (const char *) buf;
	while( n > 0 ) {
		ssize_t w = write(fd, p, n);
		if( w < 0 && errno == EINTR ) {
			continue;
		}
		if( w <= 0 ) {
			return -1;
		}
		p += w;
		n -= (size_t) w;
	}
	return 0;
}

static int readFully(int fd, void *buf, size_t n) {
	char *p = (char *) buf;
	while( n > 0 ) {
		ssize_t r = read(fd, p, n);
		if( r < 0 && errno == EINTR ) {
			continue;
		}
		if( r <= 0 ) {
			return -1;
		}
		p += r;
		n -= (size_t) r;
	}
	return 0;
}

/* the stand-in talks to fsh on a socket (its descriptors 0 and 1): see standIn */
static int onSocket = 0;

/*
 * A message to fsh on the socket: kind, then a and b (4 bytes each, network order). 'G' got a
 * signal (a); 'P' started a program (a: its process id, or -errno); 'X' a program ended (a:
 * its process id, b: its wait status).
 */
static void tell(char kind, int a, int b) {
	unsigned char m[9];
	uint32_t na = htonl((uint32_t) a), nb = htonl((uint32_t) b);
	m[0] = (unsigned char) kind;
	memcpy(m+1, &na, 4);
	memcpy(m+5, &nb, 4);
	writeFully(1, m, sizeof m);
}

static void forward(int sig) {
	int saved = errno;
	if( onSocket ) {
		tell('G', sig, 0);
	} else {
		unsigned char b = (unsigned char) sig;
		(void) write(1, &b, 1);
	}
	errno = saved;
}

/* the stand-in's programs that ended: told to fsh */
static void childEnded(int sig) {
	(void) sig;
	int saved = errno;
	int status;
	pid_t pid;
	while( (pid = waitpid(-1, &status, WNOHANG)) > 0 ) {
		tell('X', (int) pid, status);
	}
	errno = saved;
}

/* the field at *p (ending in NUL), and *p past it; "" past the end */
static char *field(char **p, char *end) {
	if( *p >= end ) {
		return (char *) "";
	}
	char *ret = *p;
	*p += strlen(ret) + 1;
	return ret;
}

/*
 * Start a program as fsh asks (see standIn): fields ending in NUL: path, directory ("": this
 * one), process group (-1: this one's), the number of descriptors, the program's number for each,
 * argc, argv..., envc, envp...; fds are the descriptors that came with it, in that order. Started
 * as NativeKeyboard.spawn0 starts one, and told to fsh ('P').
 */
static void spawnFor(char *p, char *end, int *fds, int nfds) {
	char *path = field(&p, end);
	char *dir = field(&p, end);
	int pgroup = atoi(field(&p, end));
	int ntargets = atoi(field(&p, end));
	int targets[16];
	for(int k = 0; k < ntargets; k++) {
		int t = atoi(field(&p, end));
		if( k < 16 ) {
			targets[k] = t;
		}
	}
	int argc = atoi(field(&p, end));
	char **args = (char **) calloc((size_t) argc + 1, sizeof(char *));
	for(int k = 0; k < argc; k++) {
		args[k] = field(&p, end);
	}
	int envc = atoi(field(&p, end));
	char **envp = (char **) calloc((size_t) envc + 1, sizeof(char *));
	for(int k = 0; k < envc; k++) {
		envp[k] = field(&p, end);
	}

	posix_spawn_file_actions_t fa;
	posix_spawn_file_actions_init(&fa);
	posix_spawnattr_t at;
	posix_spawnattr_init(&at);
	// (the signals this catches are as by default in the program; it ignores none)
	short flags = POSIX_SPAWN_CLOEXEC_DEFAULT | POSIX_SPAWN_SETSIGMASK;
	sigset_t none;
	sigemptyset(&none);
	posix_spawnattr_setsigmask(&at, &none);
	if( pgroup >= 0 ) {
		flags |= POSIX_SPAWN_SETPGROUP;
		posix_spawnattr_setpgroup(&at, (pid_t) pgroup);
	}
	posix_spawnattr_setflags(&at, flags);
	// (each copied above all the targets first, so that one does not overwrite another's source)
	int temps[16];
	for(int k = 0; k < nfds && k < ntargets; k++) {
		temps[k] = fcntl(fds[k], F_DUPFD_CLOEXEC, 16);
		posix_spawn_file_actions_adddup2(&fa, temps[k], targets[k]);
	}
	if( *dir != '\0' ) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
		posix_spawn_file_actions_addchdir_np(&fa, dir);
#pragma clang diagnostic pop
	}
	pid_t pid = -1;
	int err = posix_spawn(&pid, path, &fa, &at, args, envp);
	posix_spawn_file_actions_destroy(&fa);
	posix_spawnattr_destroy(&at);
	for(int k = 0; k < nfds && k < ntargets; k++) {
		close(temps[k]);
	}
	free(args);
	free(envp);
	tell('P', err == 0 ? (int) pid : -err, 0);
}

/*
 * A request from fsh on the socket: a length (4 bytes, network order), then that many bytes: 'S'
 * and a program to start (see spawnFor), with its descriptors (SCM_RIGHTS); or 'E' and the status
 * that ends the stand-in. The status, or -1 to go on.
 */
static int serve(void) {
	uint32_t len = 0;
	struct iovec iov = { &len, sizeof len };
	char control[CMSG_SPACE(16 * sizeof(int))];
	struct msghdr msg;
	memset(&msg, 0, sizeof msg);
	msg.msg_iov = &iov;
	msg.msg_iovlen = 1;
	msg.msg_control = control;
	msg.msg_controllen = sizeof control;
	ssize_t got;
	while( (got = recvmsg(0, &msg, 0)) < 0 && errno == EINTR ) {
	}
	if( got <= 0 ) {
		// the end: 0, as before
		return 0;
	}
	int fds[16];
	int nfds = 0;
	for(struct cmsghdr *c = CMSG_FIRSTHDR(&msg); c != NULL; c = CMSG_NXTHDR(&msg, c)) {
		if( c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS ) {
			int n = (int) ((c->cmsg_len - CMSG_LEN(0)) / sizeof(int));
			for(int k = 0; k < n; k++) {
				int fd;
				memcpy(&fd, CMSG_DATA(c) + k * sizeof(int), sizeof(int));
				if( nfds < 16 ) {
					fds[nfds++] = fd;
				} else {
					close(fd);
				}
			}
		}
	}
	int ret = -1;
	if( got < (ssize_t) sizeof len && readFully(0, (char *) &len + got, sizeof len - (size_t) got) != 0 ) {
		ret = 0;
	} else {
		len = ntohl(len);
		char *data = (char *) malloc((size_t) len + 1);
		if( data == NULL || len == 0 || readFully(0, data, len) != 0 ) {
			ret = 0;
		} else {
			data[len] = '\0';
			if( data[0] == 'E' ) {
				ret = atoi(data+1) & 0xff;
			} else if( data[0] == 'S' ) {
				// (its end is told after its start)
				sigset_t all, old;
				sigfillset(&all);
				sigprocmask(SIG_BLOCK, &all, &old);
				spawnFor(data+1, data+len, fds, nfds);
				sigprocmask(SIG_SETMASK, &old, NULL);
			}
		}
		free(data);
	}
	for(int k = 0; k < nfds; k++) {
		close(fds[k]);
	}
	return ret;
}

/* -s: see the top */
static int standIn(void) {
	struct stat st;
	onSocket = fstat(0, &st) == 0 && S_ISSOCK(st.st_mode);
	struct sigaction sa;
	memset(&sa, 0, sizeof sa);
	sa.sa_handler = forward;
	// (a message at a time)
	sigfillset(&sa.sa_mask);
	sa.sa_flags = SA_RESTART;
	for(int sig = 1; sig < NSIG; sig++) {
		if( sig != SIGKILL && sig != SIGSTOP && sig != SIGCHLD ) {
			sigaction(sig, &sa, NULL);
		}
	}
	if( onSocket ) {
		fcntl(0, F_SETFD, FD_CLOEXEC);
		fcntl(1, F_SETFD, FD_CLOEXEC);
		sa.sa_handler = childEnded;
		sa.sa_flags = SA_RESTART | SA_NOCLDSTOP;
		sigaction(SIGCHLD, &sa, NULL);
		int ret;
		while( (ret = serve()) < 0 ) {
		}
		return ret;
	}
	char line[32];
	int n = 0;
	char c;
	while( n < (int) sizeof line - 1 ) {
		ssize_t got = read(0, &c, 1);
		if( got < 0 && errno == EINTR ) {
			continue;
		}
		if( got <= 0 || c == '\n' ) {
			break;
		}
		line[n++] = c;
	}
	line[n] = '\0';
	return n == 0 ? 0 : atoi(line) & 0xff;
}

int main(int argc, char **argv) {
	if( argc >= 2 && strcmp(argv[1], "-s") == 0 ) {
		if( argc == 3 && strcmp(argv[2], "-g") == 0 ) {
			setpgid(0, 0);
		}
		return standIn();
	}
	if( argc >= 3 && strcmp(argv[1], "-j") == 0 ) {
		char ignoring[512] = "-Dfsh.ignoredAtStart=";
		for(int sig = 1; sig < NSIG; sig++) {
			struct sigaction old;
			if( sigaction(sig, NULL, &old) == 0 && old.sa_handler == SIG_IGN ) {
				char n[8];
				snprintf(n, sizeof n, "%s%d", ignoring[strlen(ignoring)-1] == '=' ? "" : ",", sig);
				strncat(ignoring, n, sizeof ignoring - strlen(ignoring) - 1);
			}
		}
		char **args = (char **) calloc((size_t) argc+2, sizeof(char *));
		int a = 0;
		args[a++] = argv[2];
		args[a++] = ignoring;
		if( getenv("__CF_USER_TEXT_ENCODING") == NULL ) {
			// (macOS's java sets it before the JVM starts: not the shell's caller's)
			args[a++] = (char *) "-Dfsh.cfAbsent=true";
		}
		for(int k = 3; k < argc; k++) {
			args[a++] = argv[k];
		}
		execvp(argv[2], args);
		fprintf(stderr, "fshexec: %s: %s\n", argv[2], strerror(errno));
		return 127;
	}
	if( argc == 2 && strcmp(argv[1], "-u") == 0 ) {
		struct passwd *pw;
		setpwent();
		while( (pw = getpwent()) != NULL ) {
			printf("%s\n", pw->pw_name);
		}
		endpwent();
		return 0;
	}
	if( argc == 2 && strcmp(argv[1], "-G") == 0 ) {
		struct group *gr;
		setgrent();
		while( (gr = getgrent()) != NULL ) {
			printf("%s\n", gr->gr_name);
		}
		endgrent();
		return 0;
	}
	int i = 1;
	int group = 0, fg = 0;
	pid_t pgid = 0;
	const char *file = NULL, *report = NULL;
	off_t offset = 0;
	int raw = 0;
	while( i < argc && argv[i][0] == '-' ) {
		if( strcmp(argv[i], "--") == 0 ) {
			i++;
			break;
		} else if( strcmp(argv[i], "-g") == 0 && i+2 < argc ) {
			group = 1;
			pgid = (pid_t) atol(argv[i+1]);
			fg = atoi(argv[i+2]);
			i += 3;
		} else if( strcmp(argv[i], "-r") == 0 ) {
			raw = 1;
			i++;
		} else if( strcmp(argv[i], "-e") == 0 && raw && i+1 < argc ) {
			char *entry = decode(argv[i+1]);
			char *eq = strchr(entry, '=');
			if( eq != NULL ) {
				*eq = '\0';
				setenv(entry, eq+1, 1);
			}
			i += 2;
		} else if( strcmp(argv[i], "-I") == 0 && i+1 < argc ) {
			char *list = argv[i+1];
			for(char *p = strtok(list, ","); p != NULL; p = strtok(NULL, ",")) {
				int sig = atoi(p);
				if( sig > 0 && sig < NSIG ) {
					signal(sig, SIG_IGN);
					ignored[sig] = 1;
				}
			}
			i += 2;
		} else if( strcmp(argv[i], "-f") == 0 && i+3 < argc ) {
			descriptor(atoi(argv[i+1]), argv[i+2], argv[i+3]);
			i += 4;
		} else if( strcmp(argv[i], "-0") == 0 && i+1 < argc ) {
			argv0 = argv[i+1];
			i += 2;
		} else if( strcmp(argv[i], "-i") == 0 && i+3 < argc ) {
			file = argv[i+1];
			offset = (off_t) atoll(argv[i+2]);
			report = argv[i+3];
			i += 4;
		} else {
			break;
		}
	}
	if( i >= argc ) {
		fprintf(stderr, "usage: fshexec [-g pgid fg] [-i file offset report] [-0 name] [-r [-e name=value]...] [-f n r|w|d arg]... [-I sig,...] [--] program [args...]\n");
		return 2;
	}
	if( raw ) {
		for(int k = i; k < argc; k++) {
			decode(argv[k]);
		}
	}
	if( group ) {
		if( pgid == 0 || setpgid(0, pgid) != 0 ) {
			setpgid(0, 0);
		}
		if( fg ) {
			int tty = open("/dev/tty", O_RDWR);
			if( tty >= 0 ) {
				// a background group may not take the terminal without this
				signal(SIGTTOU, SIG_IGN);
				tcsetpgrp(tty, getpgrp());
				signal(SIGTTOU, SIG_DFL);
				close(tty);
			}
		}
	}
	if( file == NULL ) {
		run(argv+i);
	}

	int fd = open(file, O_RDONLY);
	if( fd < 0 ) {
		fprintf(stderr, "fsh: %s: %s\n", file, strerror(errno));
		return 1;
	}
	lseek(fd, offset, SEEK_SET);
	dup2(fd, 0);
	if( fd != 0 ) {
		close(fd);
	}
	// this waits for the program: what the terminal sends the job is for the program
	signal(SIGINT, SIG_IGN);
	signal(SIGQUIT, SIG_IGN);
	signal(SIGHUP, SIG_IGN);
	signal(SIGTERM, SIG_IGN);
	pid_t pid = fork();
	if( pid < 0 ) {
		fprintf(stderr, "fsh: fork: %s\n", strerror(errno));
		return 1;
	}
	if( pid == 0 ) {
		signal(SIGINT, ignored[SIGINT] ? SIG_IGN : SIG_DFL);
		signal(SIGQUIT, ignored[SIGQUIT] ? SIG_IGN : SIG_DFL);
		signal(SIGHUP, ignored[SIGHUP] ? SIG_IGN : SIG_DFL);
		signal(SIGTERM, ignored[SIGTERM] ? SIG_IGN : SIG_DFL);
		run(argv+i);
	}
	int status = 0;
	while( waitpid(pid, &status, 0) < 0 && errno == EINTR ) {
	}
	FILE *out = fopen(report, "w");
	if( out != NULL ) {
		fprintf(out, "%lld\n", (long long) lseek(0, 0, SEEK_CUR));
		fclose(out);
	}
	if( WIFSIGNALED(status)) {
		int sig = WTERMSIG(status);
		signal(sig, SIG_DFL);
		sigset_t set;
		sigemptyset(&set);
		sigaddset(&set, sig);
		sigprocmask(SIG_UNBLOCK, &set, NULL);
		kill(getpid(), sig);
	}
	return WIFEXITED(status) ? WEXITSTATUS(status) : 1;
}
