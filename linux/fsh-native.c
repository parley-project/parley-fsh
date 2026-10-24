/*
 * The launcher of fsh's native image (linux/build-native.sh: target/native/fsh, the image beside
 * it as fsh-image). A native image raises the open-files limit as it starts (to OPEN_MAX, as the
 * JVM does): this notes the limit the shell was given in FSH_NOFILE, for fsh to put back, so its
 * programs get it as bash's do. Then it runs the image with the same arguments (argv[0] too).
 *
 * The image: fsh-image next to this (its real path), else in IMAGE_DIR (where it was built: a
 * copy of this elsewhere, cp fsh sh, still runs it).
 */
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <unistd.h>

#ifndef IMAGE_DIR
#define IMAGE_DIR "."
#endif

int main(int argc, char **argv) {
	(void) argc;
	struct rlimit r;
	if( getrlimit(RLIMIT_NOFILE, &r) == 0 ) {
		char limit[32];
		if( r.rlim_cur == RLIM_INFINITY ) {
			snprintf(limit, sizeof limit, "unlimited");
		} else {
			snprintf(limit, sizeof limit, "%llu", (unsigned long long) r.rlim_cur);
		}
		setenv("FSH_NOFILE", limit, 1);
	}
	char real[PATH_MAX], image[PATH_MAX];
	if( realpath("/proc/self/exe", real) != NULL ) {
		char *slash = strrchr(real, '/');
		if( slash != NULL ) {
			*slash = '\0';
			snprintf(image, sizeof image, "%s/fsh-image", real);
			if( access(image, X_OK) == 0 ) {
				execv(image, argv);
			}
		}
	}
	snprintf(image, sizeof image, "%s/fsh-image", IMAGE_DIR);
	execv(image, argv);
	fprintf(stderr, "fsh: %s: %s\n", image, strerror(errno));
	return 127;
}
