-- Time of the last upgrade request of the account. The cooldown reads it, and every
-- upgrade request writes it, whatever the policy outcome. So two requests for one
-- account always write the same row, and the version check lets only one commit.

alter table card_account add column last_upgrade_request_at timestamptz;

update card_account a
set last_upgrade_request_at = (select max(r.created_at) from upgrade_request r where r.account_id = a.id);
