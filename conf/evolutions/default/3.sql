-- !Ups
ALTER TABLE SUMMARY ADD ADDRESS TEXT NOT NULL DEFAULT '';

-- !Downs
ALTER TABLE SUMMARY DROP COLUMN ADDRESS;
