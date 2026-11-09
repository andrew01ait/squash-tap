CREATE TABLE `transfer_rates` (
	`id` text PRIMARY KEY NOT NULL,
	`count` integer NOT NULL,
	`expires_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `transfers` (
	`id` text PRIMARY KEY NOT NULL,
	`owner_hash` text NOT NULL,
	`guest_hash` text NOT NULL,
	`expires_at` integer NOT NULL,
	`incoming` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE INDEX `transfers_expiry` ON `transfers` (`expires_at`);