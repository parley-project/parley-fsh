shopt -s extglob
t() { [[ $1 = $2 ]] && echo "0: $1 = $2" || echo "1: $1 = $2"; }
t foo '!(foo)*'
t foobar '!(foo)*'
t foo '*(!(foo))'
t foobb '!(foo)b*'
t foob '!(foo)b*'
t moo.cow '!(*.*).!(*.*)'
t mad.moo.cow '!(*.*).!(*.*)'
t bar '@(|foo)*'
t foo '@(|foo)'
t '' '@(|foo)'
v=foobar; echo "${v#!(foo)}" "${v##!(x)}" "${v/!(o)/X}"
case foo in !(foo)*) echo case-yes;; *) echo case-no;; esac
