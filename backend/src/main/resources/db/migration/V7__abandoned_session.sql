-- 迁移时关闭重复进行中会话会把会话置为 ABANDONED，此时 completed_at 保持为空。
ALTER TABLE practice_session DROP CONSTRAINT practice_session_completed_fields;
ALTER TABLE practice_session
    ADD CONSTRAINT practice_session_completed_fields CHECK (
        (status = 'OPEN' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status = 'ABANDONED' AND completed_at IS NULL)
    );
