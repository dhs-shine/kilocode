import { beforeEach, describe, it, expect } from "bun:test"
import { deleteDraftsForSession, drafts, imageDrafts, reviewDrafts } from "../../webview-ui/src/utils/draft-store"
import { pendingDraftKey, scopeDraftKey, sessionDraftKey } from "../../webview-ui/src/utils/prompt-drafts"

beforeEach(() => {
  drafts.clear()
  reviewDrafts.clear()
  imageDrafts.clear()
})

describe("deleteDraftsForSession", () => {
  it("clears deleted-session drafts without touching other sessions", () => {
    drafts.set("prompt:default:session:a", "draft a")
    drafts.set("prompt:default:pending:a", "pending a")
    drafts.set("prompt:default:session:b", "draft b")
    reviewDrafts.set("prompt:default:session:a", [])
    imageDrafts.set("prompt:default:session:a", [])

    deleteDraftsForSession("a")

    expect(drafts.has("prompt:default:session:a")).toBe(false)
    expect(drafts.has("prompt:default:pending:a")).toBe(false)
    expect(drafts.get("prompt:default:session:b")).toBe("draft b")
    expect(reviewDrafts.has("prompt:default:session:a")).toBe(false)
    expect(imageDrafts.has("prompt:default:session:a")).toBe(false)
  })

  it("is a no-op when given an empty id", () => {
    drafts.set("prompt:default:session:a", "draft a")
    deleteDraftsForSession("")
    expect(drafts.get("prompt:default:session:a")).toBe("draft a")
  })

  it("clears drafts that PromptInput's draftKey effect recreates after the batch", () => {
    // Production race:
    //   1. handleSessionDeleted batches setCurrentSessionID(undefined) +
    //      setDraftSessionID(undefined) and (in the original layout) the draft-cleanup helper.
    //   2. PromptInput's createEffect(on(draftKey, ...)) runs after the batch and sees the
    //      key transition, then calls saveDraft(prev, currentText, currentImages) which
    //      writes the live prompt and any attached image data URLs back into the
    //      ":session:<id>" key.
    //   3. deleteDraftsForSession runs after the effect and clears the re-added entry.
    //
    // The helper is called twice here to model that exact order: once representing the
    // call that runs alongside the batch, and once representing the call that runs after
    // the Solid effect. If the post-batch call is removed (regression to a single in-batch
    // call), the re-add between the two calls would survive and the final assertions fail —
    // the test therefore does not pass under a single-cleanup implementation.
    const img = {
      id: "i1",
      filename: "x.png",
      mime: "image/png",
      dataUrl: "data:image/png;base64,AAAA",
    }
    drafts.set("prompt:default:session:a", "draft a")
    imageDrafts.set("prompt:default:session:a", [img])
    deleteDraftsForSession("a")
    // The createEffect's saveDraft re-writes the live prompt into the just-cleared entry.
    drafts.set("prompt:default:session:a", "draft a")
    imageDrafts.set("prompt:default:session:a", [img])

    deleteDraftsForSession("a")

    expect(drafts.has("prompt:default:session:a")).toBe(false)
    expect(imageDrafts.has("prompt:default:session:a")).toBe(false)
  })
})

describe("sessionDraftKey", () => {
  it("prefixes session ids", () => {
    expect(sessionDraftKey("abc")).toBe("session:abc")
  })

  it("returns undefined when no id is present", () => {
    expect(sessionDraftKey()).toBeUndefined()
  })
})

describe("pendingDraftKey", () => {
  it("prefixes pending ids", () => {
    expect(pendingDraftKey("pending:1")).toBe("pending:1")
  })

  it("returns undefined when no id is present", () => {
    expect(pendingDraftKey()).toBeUndefined()
  })
})

describe("scopeDraftKey", () => {
  it("scopes raw keys to a prompt box", () => {
    expect(scopeDraftKey("prompt:1", "session:abc")).toBe("prompt:1:session:abc")
  })

  it("falls back to an empty key when raw key is missing", () => {
    expect(scopeDraftKey("prompt:1")).toBe("prompt:1:empty")
  })
})
