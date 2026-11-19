# newniverse-bus 중계 서버 (Cloudflare Worker, 무료 플랜)
주소: https://newniverse-bus.guy0689.workers.dev
- GET /v1/bus/stops?lat=&lng=[&r=200]  가까운 정류장 (r: 30~500m, 기본 200)
- GET /v1/bus/arrivals?city=&node=      정류장 도착 정보 (state: live | soon | nodata)
- /v1/subway/*, /v1/rail/* 는 예정(501 not_ready)
키(TAGO_KEY)는 `wrangler secret put TAGO_KEY` 로만 넣어요. 이 폴더·저장소에 키를 넣지 마세요.
배포: npm i && npx wrangler deploy
