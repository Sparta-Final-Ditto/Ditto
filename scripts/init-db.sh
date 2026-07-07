#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
  CREATE DATABASE ditto_user;
  CREATE DATABASE ditto_feed;
  CREATE DATABASE ditto_match;
  CREATE DATABASE ditto_notification;
  CREATE DATABASE ditto_embedding;
  CREATE DATABASE ditto_assistant;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ditto_match" <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS vector;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ditto_embedding" <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS vector;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ditto_assistant" <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS vector;
EOSQL