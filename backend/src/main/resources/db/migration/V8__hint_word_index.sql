-- 记录提示命中的当前词索引，刷新后仍能显示同一提示。
ALTER TABLE practice_item
    ADD COLUMN hint_word_index INT CHECK (hint_word_index >= 0);
