PROJECT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
COMPOSE_FILE := $(PROJECT_DIR)compose.yml
COMPOSE := docker compose -f $(COMPOSE_FILE)
UI_DIR := $(PROJECT_DIR)customer-ui
BEDROCK_MODEL_PROVIDER ?= bedrock
AWS_EXPORT_CREDENTIALS_CMD = aws configure export-credentials --format env-no-export $(if $(AWS_PROFILE),--profile $(AWS_PROFILE),)
COMPOSE_PARALLEL_LIMIT ?= 1

.PHONY: help up up-bedrock down reset ps logs test ui-install ui-build ui-dev smoke seed-data reinit-data

help:
	@printf "Food Delivery Agent Platform commands:\n"
	@printf "  make up           Build and start the full local demo runtime\n"
	@printf "  make up-bedrock   Build and start the runtime with Bedrock-backed agents\n"
	@printf "  make down         Stop the composed runtime\n"
	@printf "  make reset        Stop the runtime and remove volumes\n"
	@printf "  make ps           Show compose service status\n"
	@printf "  make logs         Show compose logs, optionally pass SERVICE=<name>\n"
	@printf "  make test         Run mvn test for all backend services\n"
	@printf "  make ui-install   Install Next.js dependencies\n"
	@printf "  make ui-build     Build the Next.js frontend\n"
	@printf "  make ui-dev       Start the Next.js dev server\n"
	@printf "  make smoke        Call the chat API with a sample request\n"
	@printf "  make seed-data    Insert demo order history (idempotent; safe to re-run)\n"
	@printf "  make reinit-data  Truncate order history and re-seed from db/seed-data.sql\n"

up:
	cd $(PROJECT_DIR) && COMPOSE_PARALLEL_LIMIT=$(COMPOSE_PARALLEL_LIMIT) $(COMPOSE) up -d --build

check-bedrock-env:
	@printf "Checking AWS credential resolution for Bedrock...\n"
	@set -a && \
		eval "$$($(AWS_EXPORT_CREDENTIALS_CMD))" && \
		set +a && \
		aws sts get-caller-identity --output json >/dev/null

up-bedrock: check-bedrock-env
	@cd $(PROJECT_DIR) && \
		set -a && \
		eval "$$($(AWS_EXPORT_CREDENTIALS_CMD))" && \
		set +a && \
		COMPOSE_PARALLEL_LIMIT=$(COMPOSE_PARALLEL_LIMIT) \
		DELIVERY_MODEL_MODE=bedrock \
		ARACHNE_STRANDS_MODEL_PROVIDER=$(BEDROCK_MODEL_PROVIDER) \
		ARACHNE_STRANDS_MODEL_ID=$(ARACHNE_STRANDS_MODEL_ID) \
		ARACHNE_STRANDS_MODEL_REGION=$(ARACHNE_STRANDS_MODEL_REGION) \
		AWS_REGION=$(if $(ARACHNE_STRANDS_MODEL_REGION),$(ARACHNE_STRANDS_MODEL_REGION),) \
		AWS_DEFAULT_REGION=$(if $(ARACHNE_STRANDS_MODEL_REGION),$(ARACHNE_STRANDS_MODEL_REGION),) \
		$(COMPOSE) up -d --build

down:
	cd $(PROJECT_DIR) && $(COMPOSE) down

reset:
	cd $(PROJECT_DIR) && $(COMPOSE) down -v

ps:
	cd $(PROJECT_DIR) && $(COMPOSE) ps

logs:
	cd $(PROJECT_DIR) && $(COMPOSE) logs --tail=200 --follow $(SERVICE)

test:
	cd $(PROJECT_DIR) && mvn test

ui-install:
	cd $(UI_DIR) && npm ci

ui-build:
	cd $(UI_DIR) && npm run build

ui-dev:
	cd $(UI_DIR) && npm run dev

smoke:
	curl --fail --silent --show-error -X POST http://localhost:8080/api/chat \
		-H 'Content-Type: application/json' \
		-d '{"message":"2人分でいつものチキンセットを最速で注文したい"}'

seed-data:
	$(COMPOSE) exec -T postgres psql -U delivery -d food_delivery < $(PROJECT_DIR)db/seed-data.sql

reinit-data:
	$(COMPOSE) exec -T postgres psql -U delivery -d food_delivery -c "TRUNCATE delivery_orders;"
	$(COMPOSE) exec -T postgres psql -U delivery -d food_delivery < $(PROJECT_DIR)db/seed-data.sql