#!/usr/bin/env sh
# Python 3 실행 파일의 절대 경로를 개행 없이 출력한다. harness와 Git hook이 같은 규칙을 쓴다.
#
# 순서: QELLO_PYTHON → python3 → python → py -3
# Windows는 python3 이름으로 Microsoft Store 안내만 여는 실행 별칭을 둔다. command -v로는
# 걸러지지 않으므로 실제로 Python 3 코드를 실행할 수 있는 후보만 받아들인다.
# 출력 끝에 개행을 쓰지 않는 이유: Windows Python은 print 개행을 CRLF로 내보내 경로에 CR이 붙는다.

probe='import sys
if sys.version_info[0] != 3:
    sys.exit(1)
sys.stdout.write(sys.executable)'

# 실패한 후보가 표준 출력에 남긴 내용이 결과에 섞이지 않도록 후보마다 출력을 따로 받는다.
try_python() {
  found=$("$@" -c "$probe" 2>/dev/null) || return 1
  [ -n "$found" ] || return 1
  printf '%s' "$found"
}

if [ -n "${QELLO_PYTHON:-}" ]; then
  try_python "$QELLO_PYTHON" && exit 0
  echo "QELLO_PYTHON does not point to a working Python 3 interpreter." >&2
  exit 127
fi

for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1; then
    try_python "$candidate" && exit 0
  fi
done

if command -v py >/dev/null 2>&1; then
  try_python py -3 && exit 0
fi

echo "Python 3 is required. Install it or set QELLO_PYTHON to a Python 3 interpreter." >&2
exit 127
