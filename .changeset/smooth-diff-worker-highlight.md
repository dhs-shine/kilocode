---
"kilo-code": patch
---

Fix laggy scrolling and gray flashes in the diff and Changes views. Syntax highlighting now runs in a web worker instead of blocking the main thread, and normal-sized review files render their diffs up front instead of re-rendering while scrolling. Scrolling large diffs stays smooth even when scrolling fast.
