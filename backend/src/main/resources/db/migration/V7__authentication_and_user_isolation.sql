-- 登录与多用户隔离：外部来源编号只在当前用户内唯一。
ALTER TABLE lots
    DROP INDEX uk_lots_source_ref,
    ADD UNIQUE KEY uk_lots_user_source_ref (user_id, source_ref);

ALTER TABLE other_cost_entries
    DROP INDEX uk_ocs_source_ref,
    ADD UNIQUE KEY uk_costs_user_source_ref (user_id, source_ref);
