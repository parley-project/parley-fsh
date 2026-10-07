# needs: mkdir touch
touch f; mkdir d; [ -e f -a -d d ] && echo both; [ -s f ] || echo emptyfile; echo x > f; [ -s f ] && echo nonempty
