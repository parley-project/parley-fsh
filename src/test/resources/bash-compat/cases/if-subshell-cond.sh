if (true; false); then echo t; else echo f; fi
if ( exit 0 ); then echo ok; fi
while (false); do :; done; echo loop-done
set -e; if (false; echo in); then :; fi; echo after
