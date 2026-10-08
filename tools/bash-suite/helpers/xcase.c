/* xcase [-lnu] [file]: the input in lower (-l) or upper (-u) case */
#include <stdio.h>
#include <ctype.h>
#include <unistd.h>
int main(int argc, char **argv) {
	int c, op = 0;
	while ((c = getopt(argc, argv, "lnu")) != -1) {
		if (c == 'n') setbuf(stdout, NULL);
		else if (c == 'u') op = 'u';
		else if (c == 'l') op = 'l';
		else return 2;
	}
	FILE *in = optind < argc ? fopen(argv[optind], "r") : stdin;
	if (!in) { perror(argv[optind]); return 1; }
	while ((c = getc(in)) != EOF) putchar(op == 'u' ? toupper(c) : op == 'l' ? tolower(c) : c);
	return 0;
}
