import { Schema } from "effect"
import { HttpApi, HttpApiEndpoint, HttpApiGroup, OpenApi } from "effect/unstable/httpapi"
import { INDEXING_STATUS_STATES } from "@kilocode/kilo-indexing/status"
import { Authorization } from "@/server/routes/instance/httpapi/middleware/authorization"
import { InstanceContextMiddleware } from "@/server/routes/instance/httpapi/middleware/instance-context"
import { WorkspaceRoutingMiddleware } from "@/server/routes/instance/httpapi/middleware/workspace-routing"
import { described } from "@/server/routes/instance/httpapi/groups/metadata"
import { NonNegativeInt } from "@/util/schema"

const root = "/indexing"

export const IndexingStatusState = Schema.Literals(INDEXING_STATUS_STATES).annotate({
  identifier: "IndexingStatusState",
})

export const IndexingStatusInfo = Schema.Struct({
  state: IndexingStatusState,
  message: Schema.String,
  processedFiles: NonNegativeInt,
  totalFiles: NonNegativeInt,
  percent: NonNegativeInt.check(Schema.isLessThanOrEqualTo(100)),
}).annotate({ identifier: "IndexingStatus" })

export const IndexingPaths = {
  status: `${root}/status`,
} as const

export const IndexingApi = HttpApi.make("indexing")
  .add(
    HttpApiGroup.make("indexing")
      .add(
        HttpApiEndpoint.get("status", IndexingPaths.status, {
          success: described(IndexingStatusInfo, "Indexing status"),
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "indexing.status",
            summary: "Get indexing status",
            description: "Retrieve the current code indexing status for the active project.",
          }),
        ),
      )
      .annotateMerge(
        OpenApi.annotations({
          title: "indexing",
          description: "Kilo indexing routes.",
        }),
      )
      .middleware(InstanceContextMiddleware)
      .middleware(WorkspaceRoutingMiddleware)
      .middleware(Authorization),
  )
  .annotateMerge(
    OpenApi.annotations({
      title: "kilo HttpApi",
      version: "0.0.1",
      description: "Kilo HttpApi surface.",
    }),
  )
