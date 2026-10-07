set -u; echo ${nope:-ok}; echo ${#nope} 2>/dev/null || echo err
