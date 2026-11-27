import handler from "vinext/server/fetch-handler";
import { handleTransfer } from "./relay.mjs";
import { handleRooms } from "./rooms.mjs";
import { handleLive } from "./live.mjs";
import { handleImprint, handleSupport } from "./imprint.mjs";
import { runWithConnectorBinding } from "../lib/connector-context";
import type { ConnectorBinding } from "../lib/connector-contract.mjs";

const worker = {
  fetch(request: Request, env: Cloudflare.Env, ctx: ExecutionContext<{ CONNECTORS?: ConnectorBinding }>) {
    const path = new URL(request.url).pathname;
    if (path === "/support" || path === "/support/") return handleSupport(request, env);
    if (path === "/imprint" || path === "/imprint/") return handleImprint(request, env);
    if (import.meta.env.DEV && path.startsWith("/__transfer-preview")) return import("../web-test/browser-preview.mjs").then(m=>m.preview(request,env));
    if (path.startsWith("/api/transfer")) return handleTransfer(request,env);
    if (path.startsWith("/api/live")) return handleLive(request,env);
    if (path.startsWith("/api/rooms")) return handleRooms(request,env);
    let binding = ctx.props?.CONNECTORS;
    // Local preview emulates the same request-scoped capability. This branch and
    // the auxiliary service binding are absent from production builds.
    if (import.meta.env.DEV && !binding && env.CONNECTORS) {
      const preview = env.CONNECTORS;
      const expiresAt = Date.now() + 60_000;
      binding = {
        async getContext() {
          if (Date.now() >= expiresAt) return { status: "request_context_expired" };
          return preview.getContext?.() ?? { status: "binding_unavailable" };
        },
        async invoke(connectorId, actionName, args) {
          if (Date.now() >= expiresAt) {
            return { status: "request_context_expired", message: "This request has expired. Please try again." };
          }
          return preview.invoke(connectorId, actionName, args);
        },
      };
    }
    return runWithConnectorBinding(binding, () => handler.fetch(request, env, ctx));
  },
};

export default worker;
