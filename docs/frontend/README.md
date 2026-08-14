# User Flow Storyboards

Real-looking screen storyboards inferred from `specs/frontend/` — every frame is a rendered, realistically-populated HTML mockup (not a drawn diagram), captured with `_scripts/render.mjs`. No live app required. Regenerate a flow with `/doc-fronend <group>` after its spec changes.

Each entry below embeds a flat PNG filmstrip. For a wide flow, the PNG can feel cramped at a glance — every group also has a `storyboard.html` (open in a browser, pan/zoom natively) and `storyboard.pdf` (crisp at any zoom) linked from its own page.

- [brand](brand.md) — 品牌列表切換啟用/停用
- [currency-pair](currency-pair.md) — 幣種對主檔建立、套用至品牌、修改送審、核准
- [spread](spread.md) — 新增客制點差群組並送審核准
- [audit](audit.md) — 審核作業：開啟差異比對並核准申請
