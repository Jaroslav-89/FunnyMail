# Photo-to-sketch V12 — preserve edge-map details

V12 is based on the classic V10 detector. It does not use a neural model.

## Match the “Карта найденных границ” preview

- Vectorization uses the same grouped preview grid and maximum edge strength per cell as the diagnostic map.
- No minimum contour-length filtering, path simplification, or point downsampling is applied.
- Short contours and isolated edge cells remain in the drawing.
- The document retains the source photo's aspect ratio and uses editable vector strokes.

## Drawing size and limits

The editor displays the current serialized JSON size in bytes and KiB, plus stroke and point counts. If the payload cannot be saved, the size panel and save/send action show the reason rather than silently trimming details.

- Maximum encoded JSON payload: 900 KiB.
- Maximum strokes: 120,000.
- Maximum points: 500,000.

If a detailed drawing exceeds the JSON payload limit, it remains visible and editable in the editor so its size can be inspected, but persistence and sending are blocked. These limits are intentionally checked separately: a high point budget does not mean an arbitrarily large JSON payload can be stored safely.

## Tests

Common tests cover:
- preservation of short contours and isolated detected cells;
- map tone/contrast and line thickness;
- reporting actual JSON size when a payload exceeds its configured size limit;
- accepting drawings with more than the old 30,000-point budget.
