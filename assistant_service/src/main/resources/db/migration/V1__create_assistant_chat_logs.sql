CREATE TABLE IF NOT EXISTS assistant_chat_logs (
    id UUID PRIMARY KEY,
    user_id UUID,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    matched_document_ids UUID[] NOT NULL,
    similarity_scores FLOAT[],
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
