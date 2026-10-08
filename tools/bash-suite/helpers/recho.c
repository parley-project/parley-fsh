/* argv[n] = <arg>, control characters as ^X (as bash's test helper prints them) */
#include <stdio.h>
int main(int argc, char **argv) {
	for (int i = 1; i < argc; i++) {
		printf("argv[%d] = <", i);
		for (unsigned char *s = (unsigned char *)argv[i]; *s; s++) {
			if (*s < ' ') { putchar('^'); putchar(*s+64); }
			else if (*s == 127) { putchar('^'); putchar('?'); }
			else putchar(*s);
		}
		printf(">\n");
	}
	return 0;
}
