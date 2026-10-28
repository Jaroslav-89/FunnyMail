# V14 chat and drawing improvements

Branch: `feature/chat-drawing-tools-v14`, based on V13.

## Implemented in this branch

- Drawing JSON limit is 250 KiB in `DrawingCodec`, the chat repository adapter, and Firestore rules.
- Editor displays serialized JSON size and the current cap using the codec's existing size overlay.
- Drawing document supports optional title (20 characters) and caption (80 characters); the finish dialog edits both.
- Chat cards show stroke/point counts and serialized JSON size above the drawing, title and caption below it, and the creation date/time at bottom left.
- Full-screen chat drawing viewer has a save-to-local-gallery action.
- Chat feed remembers its visible item and scroll offset when opening a drawing; the old forced-scroll-to-top effect was removed.
- Editor drafts are persisted locally on drawing changes; edited gallery drawing JSON is reloaded from Room by its saveable ID after Activity recreation, and the selected chat is restored from its saveable ID.
- Color wheels open with white selected; line and background palettes have opacity sliders. Stroke and background opacity are stored in the JSON model with backward-compatible defaults.
- Eraser deletes nearby vector strokes instead of painting with the background color.
- Brush thickness range was expanded substantially.
- Added a two-point straight-line tool; the checkmark confirms and locks the line into the drawing.
- Invite codes in chat details and the chat list copy on long press without showing a text-selection handle.
- Member-add button checks the live observed member list to avoid using a stale chat snapshot.
- Added codec tests for the 250 KiB limit, caption limits, and opacity validation.

## Not yet implemented in V14

These parts of the request need additional work before the branch can be considered complete:

- Rich text placement with arbitrary rotation and bold/italic/underline formatting.
- Emoji stickers with symbolic codes and scaling.
- Manual tracing mode that retains the original photo as a background, plus the explicit mode choice between tracing and edge conversion.
- Native share/export of the rendered drawing as an image from the viewer.
- A complete interaction for moving either endpoint of an unconfirmed straight line (currently the second endpoint can be repositioned by tapping again).
- A dedicated audit of configuration-change behavior across every screen and platform, including a full Android/iOS build.
- Any invited-user chat-description rendering adjustment if a description is added to the invited-user UI later; the current chat detail screen does not display the description.

## Verification

Source files were statically inspected and brace balance checked. Kotlin compilation, unit tests, Android/iOS runtime checks, and Firestore rules deployment have not been run from this environment. Publish the updated `firestore.rules` to Firebase separately.
