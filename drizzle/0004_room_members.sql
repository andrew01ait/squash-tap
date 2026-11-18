CREATE TABLE `room_members` (
	`id` text PRIMARY KEY NOT NULL,
	`room_id` text NOT NULL,
	`token_hash` text NOT NULL,
	`role` text NOT NULL,
	`state` text NOT NULL,
	`seen_at` integer NOT NULL,
	FOREIGN KEY (`room_id`) REFERENCES `match_rooms`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `room_member_token` ON `room_members` (`room_id`,`token_hash`);--> statement-breakpoint
ALTER TABLE `match_rooms` ADD `referee_invite_hash` text;