// Intentionally empty by default.
// Add Drizzle tables here when the site actually needs a database.
// See examples/d1/db/schema.ts for an opt-in example.
import { sqliteTable, text, integer, index, uniqueIndex } from "drizzle-orm/sqlite-core";
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

export const matchRooms = sqliteTable("match_rooms", {
  id: text("id").primaryKey(),
  adminHash: text("admin_hash").notNull(), writerHash: text("writer_hash").notNull(), readerHash: text("reader_hash").notNull(),
  codeHash: text("code_hash").notNull(), pairing: text("pairing").notNull(), watchJoin: integer("watch_join").notNull(),
  expiresAt: integer("expires_at").notNull(), revision: integer("revision").notNull(),
  cipher: text("cipher").notNull(), isFinal: integer("is_final").notNull(),
}, table => [uniqueIndex("match_rooms_code").on(table.codeHash), index("match_rooms_expiry").on(table.expiresAt)]);
