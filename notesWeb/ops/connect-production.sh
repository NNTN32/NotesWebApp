#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
targets=$(ssh -o BatchMode=yes nntn-vps@orb 'sudo python3 - targets' < "$script_dir/harden-production.py")
pg_target=$(printf '%s' "$targets" | python3 -c 'import ipaddress,json,sys; print(ipaddress.ip_address(json.load(sys.stdin)["pg"]))')
redis_target=$(printf '%s' "$targets" | python3 -c 'import ipaddress,json,sys; print(ipaddress.ip_address(json.load(sys.stdin)["redis"]))')
rabbit_target=$(printf '%s' "$targets" | python3 -c 'import ipaddress,json,sys; print(ipaddress.ip_address(json.load(sys.stdin)["rabbit"]))')
printf 'Keep this terminal open. PostgreSQL:15433 Redis:16379 RabbitMQ Management:15673 (localhost only).\n'
exec ssh -NT -o BatchMode=yes -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
  -L "127.0.0.1:15433:${pg_target}:5432" \
  -L "127.0.0.1:16379:${redis_target}:6379" \
  -L "127.0.0.1:15673:${rabbit_target}:15672" nntn-vps@orb
