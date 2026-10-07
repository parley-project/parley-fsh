LOG_LEVEL=${LOG_LEVEL:-info}
declare -A LEVELS=([debug]=0 [info]=1 [warn]=2 [error]=3)
log() {
  local level=$1; shift
  (( ${LEVELS[$level]} >= ${LEVELS[$LOG_LEVEL]} )) || return 0
  printf '%-5s %s\n' "${level^^}" "$*"
}
log debug "hidden"
log info "starting $((1+1)) jobs"
log warn "disk at 91%"
LOG_LEVEL=debug log debug "now shown"
log error "failed: ${MISSING:-n/a}"
