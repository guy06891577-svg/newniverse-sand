# Play 스토어 판에서 전원 창 접근성 서비스를 매니페스트에서 제거
import re, sys
from pathlib import Path
p = Path(sys.argv[1])
t = p.read_text(encoding='utf-8')
t2 = re.sub(r'\s*<!-- v44b[\s\S]*?</service>', '', t, count=1)
if 'PowerService' in t2:
    sys.exit('failed to strip PowerService from ' + str(p))
p.write_text(t2, encoding='utf-8')
