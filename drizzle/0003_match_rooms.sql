CREATE TABLE `match_rooms` (
	`id` text PRIMARY KEY NOT NULL,
	`admin_hash` text NOT NULL,
	`writer_hash` text NOT NULL,
	`reader_hash` text NOT NULL,
	`code_hash` text NOT NULL,
	`pairing` text NOT NULL,
	`watch_join` integer NOT NULL,
	`expires_at` integer NOT NULL,
	`revision` integer NOT NULL,
	`cipher` text NOT NULL,
	`is_final` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `match_rooms_code` ON `match_rooms` (`code_hash`);--> statement-breakpoint
CREATE INDEX `match_rooms_expiry` ON `match_rooms` (`expires_at`);