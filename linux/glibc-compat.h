/*
 * Included first when compile.sh builds fshexec (cc -include glibc-compat.h), so the binary runs on
 * every glibc from 2.34 on (Ubuntu 22.04, Debian 12, RHEL 9 and later), not only on the one it
 * was built with.
 *
 * fshexec.c defines _GNU_SOURCE (for posix_spawn_file_actions_addchdir_np), which on glibc 2.38
 * and later also turns on C23's strtol: the calls are linked to __isoc23_strtol, which older
 * glibcs don't have, and fshexec then doesn't start there ("version GLIBC_2.38 not found"). C23's
 * strtol differs only in also reading a 0b prefix, which fshexec never passes it, so the plain
 * strtol is kept.
 */
#ifndef FSH_GLIBC_COMPAT_H
#define FSH_GLIBC_COMPAT_H
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <features.h>
#undef __GLIBC_USE_C2X_STRTOL
#define __GLIBC_USE_C2X_STRTOL 0
#undef __GLIBC_USE_C23_STRTOL
#define __GLIBC_USE_C23_STRTOL 0
#endif
