# needs: ln chmod ls
echo x > f; chmod 644 f; ln -s f lf
chmod 600 lf
ls -l f | { read perm rest; echo "$perm"; }
chmod u+x lf
ls -l f | { read perm rest; echo "$perm"; }
mkdir d; chmod 755 d; ln -s d ld; chmod 700 ld
ls -ld d | { read perm rest; echo "$perm"; }
