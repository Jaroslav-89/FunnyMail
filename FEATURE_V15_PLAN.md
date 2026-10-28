# V15 implementation plan

Target branch: feature/chat-drawing-tools-v15
Base branch: feature/chat-drawing-tools-v14

## Small subtasks

1. **Audit existing functionality** — review README, feature status, serialized drawing model, editor, chat feed, gallery, viewer, invitation flows and screen state restoration.
2. **Chat drawing metadata** — retain date/time at bottom-left, show serialized JSON byte/KiB size alongside stroke/point counts above the drawing, keep the optional title (20 characters) and caption (80 characters) below it.
3. **Drawing schema** — add backward-compatible serialized rich text and sticker collections; validate coordinates, font-size/scale, rotation, opacity, supported sticker codes and annotation counts; keep the existing 250 KiB JSON limit.
4. **Editor rich text and stickers** — add placement, font-size and angle controls, bold/italic/underline settings, six code-based emoji stickers and sticker scale.
5. **Photo workflows** — separate tracing from contour conversion; show conversion settings and edge map only in contour mode.
6. **Line tool** — support moving either pending endpoint; keep the checkmark as the commit/finalization action.
7. **Gallery and sharing** — render annotations consistently in previews and the full-screen viewer; keep saving in the viewer non-navigating; export an SVG for the native share sheet.
8. **Invitations and membership state** — long-press copy in all invite-code surfaces; refresh member snapshots and use the current membership list before deciding whether a user can be added.
9. **Lifecycle regression** — use the saveable selected-chat ID when the in-memory chat object is recreated; keep draft JSON persisted while drawing.
10. **Verification and handoff** — add codec and SVG-export tests, inspect the complete branch snapshot and diff, open a draft PR for review, and run platform builds/tests in a configured environment.

## Status

- Completed in source: subtasks 1–9, including codec tests for text/stickers and an SVG exporter test suite.
- Review needed: confirm layout and touch behavior on devices, especially line-endpoint dragging and photo tracing.
- Environment limitation: this repository has no GitHub Actions workflow files on the target branch, and direct Git clone/build execution is unavailable in this session. Android/iOS compilation, all unit tests, device rotation regression tests and Firestore rule deployment therefore remain unverified and must be run before release.
- Compatibility: new model fields have empty-list defaults; old drawing JSON remains valid. Tracing pixels are transient and are not persisted in the final drawing JSON.
