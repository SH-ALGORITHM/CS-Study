#!/bin/bash
# 한 주차의 7명 멤버 폴더에 스켈레톤을 통째로 복사한다.
#
# 템플릿 우선순위:
#   1. topics/{NN}-*/_member-template/  ← 주차 전용 (1~3주차 Java + JDBC 등)
#   2. template/                         ← 기본 Spring Boot (4주차+)
#
# 운영자가 페이즈 시작 직전에 1번 실행:
#   ./scripts/scaffold-week.sh 02   # 2주차 — 주차 전용 template 사용
#   ./scripts/scaffold-week.sh 04   # 4주차 — 기본 Spring Boot template 사용
#
# 페이즈 의존성 바뀔 땐:
#   1. template/build.gradle (또는 topics/{NN}-*/_member-template/build.gradle) 의존성 갱신
#   2. ./scripts/scaffold-week.sh {주차} 실행

set -e

if [ $# -lt 1 ]; then
  echo "Usage: $0 <week_number>"
  echo "  e.g.) $0 02"
  exit 1
fi

cd "$(dirname "$0")/.."

WEEK_NUM=$1
WEEK_DIR=$(ls -d topics/${WEEK_NUM}-* 2>/dev/null | head -1)

if [ -z "$WEEK_DIR" ]; then
  echo "❌ topics/${WEEK_NUM}-* 폴더 없음"
  exit 1
fi

# 주차 전용 template 있으면 우선 사용, 없으면 기본 Spring Boot template
WEEK_TEMPLATE="$WEEK_DIR/_member-template"
if [ -d "$WEEK_TEMPLATE" ]; then
  SOURCE="$WEEK_TEMPLATE"
  echo "📌 주차 전용 template 사용: $WEEK_TEMPLATE"
elif [ -d "template" ]; then
  SOURCE="template"
  echo "📌 기본 template 사용: template/"
else
  echo "❌ template 없음 ($WEEK_TEMPLATE / template 둘 다 없음)"
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

  # template 통째 복사 (.gitkeep / README.md 제외)
  cp -R "$SOURCE"/. "$TARGET/"
  rm -f "$TARGET/.gitkeep"
  # 주차 전용 template 의 README 는 운영용 안내라 멤버 폴더엔 불필요
  if [ -f "$TARGET/README.md" ] && [ "$SOURCE" = "$WEEK_TEMPLATE" ]; then
    rm -f "$TARGET/README.md"
  fi

  echo "✅ $TARGET"
done

echo ""
echo "🎉 ${WEEK_DIR} 셋업 완료 — 각 멤버 IntelliJ에서 ▶ 클릭으로 실행"
