CREATE TABLE `live_games` (
	`id` text PRIMARY KEY NOT NULL,
	`owner_hash` text NOT NULL,
	`viewer_hash` text NOT NULL,
	`expires_at` integer NOT NULL,
	`revision` integer NOT NULL,
	`cipher` text NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `live_games_expiry` ON `live_games` (`expires_at`);