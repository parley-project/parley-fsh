/*
 * fshexec PGID FG program [args...]
 *
 * Run program in a process group of its own, as bash runs a job: PGID 0 makes a new group, any
 * other joins that job's group (a new one if it is gone). FG 1 makes the group the terminal's
 * foreground group, so the terminal's Ctrl-C and Ctrl-Z reach the job and not the shell.
 */
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv) {
	if( argc < 4 ) {
		fprintf(stderr, "usage: fshexec pgid fg program [args...]\n");
		return 2;
	}
	pid_t pgid = (pid_t) atol(argv[1]);
	int fg = atoi(argv[2]);
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
	execvp(argv[3], argv+3);
	int err = errno;
	fprintf(stderr, "fsh: %s: %s\n", argv[3], strerror(err));
	return err == ENOENT ? 127 : 126;
}
