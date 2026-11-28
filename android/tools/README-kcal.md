# 음력·공휴일·24절기 자료 (kcal-data.js) 만든 과정

- 쓰는 파일: 런처 루트의 `kcal.js`(계산·질문 답) + `kcal-data.js`(표). 인터넷 없이 폰 안에서만 계산해요.
- 출처
  - 공휴일·24절기·음력 달: 한국천문연구원 천문우주정보 «달력자료(월력요항)» 2026~2028 (공개 페이지, 열쇠 필요 없음). `data-src/kasi_parsed.json`·`kasi_holidays_official.json` 이 그 내용이에요.
  - 음력 달 시작일은 천문 계산(`astro.py`, ephem: 한국시간 합삭 + 중기 규칙으로 달 번호·윤달)과 `korean_lunar_calendar` 표를 둘 다 돌려 2025-11 ~ 2031-02 사이 하루도 안 다름을 확인. 2026~2028은 천문연구원 표와도 일치.
  - 2029·2030 공휴일: 「관공서의 공휴일에 관한 규정」 규칙 계산(대체공휴일 포함). 월력요항 발표 전이라 `src: 'calc'` 로 표시. 선거일(2030)은 법령 외 확인이 안 돼 넣지 않았어요. 임시공휴일은 정부 발표 뒤 앱 업데이트.
  - 2030년 우수는 자정 1분 앞뒤라 날짜를 못 정해 뺐어요(`skipTerms`).
- 다시 만들기: `node android/tools/gen-kcal-data.js` (입력은 `data-src/export.json` + `data-src/kasi_holidays_official.json`). 파이썬 도구(`astro.py` `lunar_gen.py` …)는 천문 계산·대조용이고 /workspace/lunar-work 에 받은 원본이 있어야 돌아가요.
- 시험: `node android/tests/unit_kcal.js` (음력 경계·윤달·공휴일·대체공휴일·24절기·기념일·질문 답).
- 공공데이터포털 OpenAPI(특일정보·음양력정보)는 이번 판에서 쓰지 않았어요. 열쇠 파일은 이 저장소에 없고 어디에도 기록하지 않아요.
