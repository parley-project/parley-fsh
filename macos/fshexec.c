/*
 * fshexec [-g PGID FG] [-i FILE OFFSET REPORT] [-0 NAME] [-r [-e NAME=VALUE]...] [-f N r|w|d ARG]... [--] program [args...]
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
 * -f: the program's descriptor N (above 2: Java gives a program only 0, 1 and 2) is ARG opened
 *     for reading (r) or writing (w), or a copy of descriptor ARG (d), as N is open in the shell.
 */
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static const char *argv0 = NULL;

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

int main(int argc, char **argv) {
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
		fprintf(stderr, "usage: fshexec [-g pgid fg] [-i file offset report] [-0 name] [-r [-e name=value]...] [-f n r|w|d arg]... [--] program [args...]\n");
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
		signal(SIGINT, SIG_DFL);
		signal(SIGQUIT, SIG_DFL);
		signal(SIGHUP, SIG_DFL);
		signal(SIGTERM, SIG_DFL);
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
