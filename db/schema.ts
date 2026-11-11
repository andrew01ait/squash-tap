// Intentionally empty by default.
// Add Drizzle tables here when the site actually needs a database.
// See examples/d1/db/schema.ts for an opt-in example.
import { sqliteTable, text, integer, index } from "drizzle-orm/sqlite-core";
export const transfers = sqliteTable("transfers", {
  id: text("id").primaryKey(), ownerHash: text("owner_hash").notNull(), guestHash: text("guest_hash").notNull(),
  expiresAt: integer("expires_at").notNull(), incoming: integer("incoming").notNull().default(0),
}, table => [index("transfers_expiry").on(table.expiresAt)]);
export const transferRates = sqliteTable("transfer_rates", {
  id: text("id").primaryKey(), count: integer("count").notNull(), expiresAt: integer("expires_at").notNull(),
});
export const liveGames = sqliteTable("live_games", {
  id: text("id").primaryKey(), ownerHash: text("owner_hash").notNull(), viewerHash: text("viewer_hash").notNull(),
  expiresAt: integer("expires_at").notNull(), activeExpiresAt: integer("active_expires_at"), revision: integer("revision").notNull(),
  cipher: text("cipher").notNull(), updatedAt: integer("updated_at").notNull(),
}, table => [index("live_games_expiry").on(table.expiresAt)]);
