# needs: SIGUSR1
trap "echo caught" INT; kill -INT $$; echo after
trap "echo usr" USR1; kill -USR1 $$; kill -0 $$ && echo alive
trap "echo term" TERM; kill $$; echo after2
