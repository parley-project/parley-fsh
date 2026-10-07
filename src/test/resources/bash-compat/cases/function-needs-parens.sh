f() { echo "x"; }; echo {1..3}
g() ( echo sub ); g; ( h() { echo h; } ); type h >/dev/null 2>&1 || echo gone
