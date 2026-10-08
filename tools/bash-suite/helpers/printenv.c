/* the environment, or one variable's value (status 1 if it is not set) */
#include <stdio.h>
#include <string.h>
extern char **environ;
int main(int argc, char **argv) {
	if (argc < 2) { for (char **e = environ; *e; e++) puts(*e); return 0; }
	size_t len = strlen(argv[1]);
	for (char **e = environ; *e; e++)
		if (strncmp(*e, argv[1], len) == 0 && (*e)[len] == '=') { puts(*e+len+1); return 0; }
	return 1;
}
