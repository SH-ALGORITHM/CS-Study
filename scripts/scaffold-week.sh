#!/bin/bash
# 한 주차의 7명 멤버 폴더에 Spring Boot 스켈레톤(template)을 통째로 복사한다.
# 모든 멤버가 동일한 application.yml 사용 (각자 로컬 DB, 충돌 없음).
#
# 운영자가 페이즈 시작 직전에 1번 실행:
#   ./scripts/scaffold-week.sh 04   # 4주차 7명 폴더 다 채움
#   ./scripts/scaffold-week.sh 05
#   ...
#
# 페이즈 의존성 바뀔 땐 (3주→7주, 9주→10주):
#   1. template/build.gradle 의존성 갱신
#   2. ./scripts/scaffold-week.sh {주차} 실행

set -e

if [ $# -lt 1 ]; then
  echo "Usage: $0 <week_number>"
  echo "  e.g.) $0 04"
  exit 1
fi

cd "$(dirname "$0")/.."

WEEK_NUM=$1
WEEK_DIR=$(ls -d topics/${WEEK_NUM}-* 2>/dev/null | head -1)

if [ -z "$WEEK_DIR" ]; then
  echo "❌ topics/${WEEK_NUM}-* 폴더 없음"
  exit 1
fi

if [ ! -d template ]; then
  echo "❌ template/ 폴더 없음"
  exit 1
fi

MEMBERS=(chanhyeok gabin minseo huimin jaehoon gaeun sujin)

echo "📦 ${WEEK_DIR} 셋업 시작"
echo ""

for M in "${MEMBERS[@]}"; do
  TARGET=$WEEK_DIR/members/$M

  if [ ! -d "$TARGET" ]; then
    echo "⚠️  $TARGET 없음 — 스킵"
    continue
  fi

  # 이미 build.gradle 있으면 덮어쓰기 방지 (멤버 작업 보호)
  if [ -f "$TARGET/build.gradle" ]; then
    echo "⏭  $TARGET 이미 셋업됨 — 스킵"
    continue
  fi

  # template 통째 복사 (.gitkeep 제외)
  cp -R template/. "$TARGET/"
  rm -f "$TARGET/.gitkeep"

  echo "✅ $TARGET"
done

echo ""
echo "🎉 ${WEEK_DIR} 셋업 완료 — 각 멤버 IntelliJ에서 ▶ 클릭으로 실행"
