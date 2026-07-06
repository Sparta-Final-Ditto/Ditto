CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS matching_history (
                                                id UUID PRIMARY KEY,
                                                user_id UUID NOT NULL,
                                                matched_user_id UUID NOT NULL,
                                                similarity_score FLOAT NOT NULL,
                                                final_score FLOAT NOT NULL,
                                                matched_at TIMESTAMP NOT NULL,
                                                gender_filter VARCHAR(10) NOT NULL,
    location_filter_on BOOLEAN NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID
    );

CREATE TABLE IF NOT EXISTS matching_explanations (
                                                     id UUID PRIMARY KEY,
                                                     user_id UUID NOT NULL,
                                                     matched_user_id UUID NOT NULL,
                                                     explanation_text TEXT NOT NULL,
                                                     created_at TIMESTAMP NOT NULL,
                                                     updated_at TIMESTAMP NOT NULL,
                                                     created_by UUID,
                                                     updated_by UUID,
                                                     deleted_at TIMESTAMP,
                                                     deleted_by UUID
);