# Photo-to-sketch V13 — live quality controls

V13 is based on the classic V12 pipeline. It does not use a neural model and does not alter the V12 branch.

## Editor controls

After importing a photo, the quality panel starts collapsed. Tap **Кач.** to open it. Defaults are 6,500 points, 25% edge sensitivity, minimum edge strength 0, simplification tolerance 2.75, stroke width 5.0, 80% removal of weak isolated points, and point merging enabled. Settings regenerate the edge map/vector sketch automatically after a short debounce so the user can compare detail and JSON size before manual drawing.

- **Maximum points:** a target point budget. The vectorizer first increases simplification tolerance; if that is insufficient, it omits weaker/shorter paths to stay within the selected budget.
- **Edge sensitivity:** changes the classic Sobel/hysteresis detector threshold. A lower threshold retains subtler contours; a higher threshold filters them.
- **Minimum edge strength:** removes low-confidence cells after edge detection.
- **Contour simplification:** controls Ramer–Douglas–Peucker tolerance, measured in grouped preview-map cells. Zero disables simplification at the selected tolerance; the point budget can still request automatic simplification if necessary.
- **Line thickness:** sets the width of photo-derived strokes.
- **Isolated points:** selects the percentage (0–80%) of weakest isolated detections to remove. Connected contour cells are not removed by this filter.
- **Merge points into lines:** toggles between connected editable polylines and individual edge dots.

The panel shows the current serialized JSON size, stroke count and point count. The edge-map diagnostic preview reflects the selected minimum edge-strength threshold.

Quality settings are available while the imported photo is still a generated sketch. After manual drawing or undo/redo modifies the generated sketch, the quality source is released and the panel closes so reprocessing cannot overwrite the user's manual edits. The grayscale source remains transient and is not included in JSON.

## Validation

Common tests cover the preferred default profile, configurable stroke width, point-budget enforcement when dots cannot be simplified, minimum edge strength, contour simplification, and the merge-points toggle. CI/build execution must be checked separately.
