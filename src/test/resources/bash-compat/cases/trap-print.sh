# needs: SIGUSR1
trap "echo x" EXIT; trap "echo y" USR1; trap -p EXIT; trap -p; trap - EXIT; trap -p EXIT; echo done
