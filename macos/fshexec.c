/*
 * fshexec [-g PGID FG] [-i FILE OFFSET REPORT] [-0 NAME] [--] program [args...]
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

int main(int argc, char **argv) {
	int i = 1;
	int group = 0, fg = 0;
	pid_t pgid = 0;
	const char *file = NULL, *report = NULL;
	off_t offset = 0;
	while( i < argc && argv[i][0] == '-' ) {
		if( strcmp(argv[i], "--") == 0 ) {
			i++;
			break;
		} else if( strcmp(argv[i], "-g") == 0 && i+2 < argc ) {
			group = 1;
			pgid = (pid_t) atol(argv[i+1]);
			fg = atoi(argv[i+2]);
			i += 3;
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
		fprintf(stderr, "usage: fshexec [-g pgid fg] [-i file offset report] [-0 name] [--] program [args...]\n");
		return 2;
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
