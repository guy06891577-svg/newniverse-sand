# 뉴니버스 배경화면 추가 방법

1. 이 폴더(`wallpapers/`)에 파일을 넣어요.
   - 정지 이미지 `.jpg` `.png` `.webp` `.svg`, 움직이는 이미지 `.gif`(애니메이션 WebP도 가능) → type `image` (세로 화면 권장, 1080×2340 이상)
   - 영상 배경: `.mp4` `.webm` → type `video` (자동 재생·무음·반복·화면 꽉 채움, 10초 내외·10MB 이하 권장)
2. `wallpapers.json`에 한 줄 추가해요.

```json
[
  {"id": "ex-dunes", "name": "예시 · 사막 노을(이미지)", "type": "image", "src": "example-dunes.svg"},
  {"id": "aurora",   "name": "오로라",                 "type": "video", "src": "aurora.mp4"}
]
```

- `id`: 영문 고유 이름, `name`: 설정에 보이는 이름, `type`: `image` 또는 `video`, `src`: 이 폴더 안 파일명
- 저장(커밋)하면 설정 ⚙️ → 배경화면 목록에 자동으로 나타나요.
- 코드로 그리는 실시간 배경(기본 3D 우주 같은)은 `index.html`의 `WALLPAPERS` 배열에 `type:'live'`로 추가해요.
