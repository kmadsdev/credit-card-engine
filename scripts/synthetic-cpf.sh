#!/usr/bin/env bash
# Prints a random, synthetic CPF with valid check digits (11 digits, no punctuation).
# For local tests and demos only. It is random, so it is not anyone's known number.
set -euo pipefail

digits=()
while :; do
  digits=()
  for _ in 1 2 3 4 5 6 7 8 9; do digits+=($((RANDOM % 10))); done
  # 9 equal digits give a CPF that is never issued; draw again.
  [[ "$(printf '%s' "${digits[@]}" | grep -c '^\(.\)\1*$')" == "0" ]] && break
done

check_digit() {
  local sum=0 weight=$(( $# + 1 ))
  for d in "$@"; do sum=$(( sum + d * weight )); weight=$(( weight - 1 )); done
  local r=$(( sum % 11 ))
  (( r < 2 )) && echo 0 || echo $(( 11 - r ))
}

d1=$(check_digit "${digits[@]}")
d2=$(check_digit "${digits[@]}" "$d1")
printf '%s' "${digits[@]}" "$d1" "$d2"
echo
