# Photo-to-sketch V12 — preserve edge-map details

V12 is based on the classic V10 detector. It does not use a neural model.

## Keep contours compact while preserving their shape

- Vectorization uses the same grouped preview grid and maximum edge strength per cell as the diagnostic map.
- Connected edge cells are traced into editable polylines. Long paths use iterative Ramer–Douglas–Peucker simplification with a one-preview-cell tolerance, retaining endpoints and important bends while removing raster stair-steps.
- For isolated edge cells only, the weakest 20% are removed; cells connected to a contour are not removed by that noise filter. With fewer than five isolated cells, the integer 20% budget removes none.
- Stroke width is 2.6 canvas units, increased from 2.0 to make simplified contours read more clearly.
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
