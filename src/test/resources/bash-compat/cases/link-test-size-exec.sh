# needs: ln chmod
echo hello > f; : > e; ln -s f lf; ln -s e le; ln -s nothing dang
[ -s lf ] && echo lf-nonempty; [ -s le ] || echo le-empty; [ -s dang ] || echo dang-empty
[ -x lf ] || echo not-exec
chmod +x lf; [ -x f ] && echo target-exec; [ -x lf ] && echo link-exec
