-- ==================== 用户表 ====================
CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,       -- BCrypt 加密
    nickname VARCHAR(100),
    avatar_url TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',  -- USER / ADMIN
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 用户名唯一索引
CREATE UNIQUE INDEX idx_app_user_username ON app_user(username);

-- ==================== 现有表加 user_id ====================
ALTER TABLE agent ADD COLUMN user_id UUID REFERENCES app_user(id);
ALTER TABLE chat_session ADD COLUMN user_id UUID REFERENCES app_user(id);
ALTER TABLE knowledge_base ADD COLUMN user_id UUID REFERENCES app_user(id);
ALTER TABLE long_term_memory ADD COLUMN user_id UUID REFERENCES app_user(id);

-- 查询加速索引
CREATE INDEX idx_agent_user_id ON agent(user_id);
CREATE INDEX idx_chat_session_user_id ON chat_session(user_id);
CREATE INDEX idx_knowledge_base_user_id ON knowledge_base(user_id);
