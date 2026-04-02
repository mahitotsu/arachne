PROJECT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
COMPOSE_FILE := $(PROJECT_DIR)compose.yml
COMPOSE := docker compose -f $(COMPOSE_FILE)
UI_DIR := $(PROJECT_DIR)operator-console
BEDROCK_MODEL_PROVIDER ?= bedrock
AWS_EXPORT_CREDENTIALS_CMD = aws configure export-credentials --format env-no-export $(if $(AWS_PROFILE),--profile $(AWS_PROFILE),)
COMPOSE_PARALLEL_LIMIT ?= 1

.PHONY: help up up-arachne up-bedrock down reset ps logs test ui-install ui-build ui-dev check-bedrock-env

help:
	@printf "Marketplace Agent Platform commands:\n"
	@printf "  make up         Start the composed runtime in detached mode with rebuild\n"
	@printf "  make up-arachne Start the composed runtime with the deterministic Arachne workflow path enabled\n"
	@printf "  make up-bedrock Start the composed runtime with the Bedrock-backed Arachne workflow path enabled\n"
	@printf "  make down       Stop the composed runtime\n"
	@printf "  make reset      Stop the composed runtime and remove volumes\n"
	@printf "  make ps         Show compose service status\n"
	@printf "  make logs       Show compose logs, optionally pass SERVICE=<name>\n"
	@printf "  make test       Run mvn test for marketplace-agent-platform\n"
	@printf "  make ui-install Install operator-console dependencies\n"
	@printf "  make ui-build   Build the operator-console bundle\n"
	@printf "  make ui-dev     Start the operator-console Vite dev server\n"

up:
	cd $(PROJECT_DIR) && COMPOSE_PARALLEL_LIMIT=$(COMPOSE_PARALLEL_LIMIT) $(COMPOSE) up -d --build

up-arachne:
	cd $(PROJECT_DIR) && COMPOSE_PARALLEL_LIMIT=$(COMPOSE_PARALLEL_LIMIT) MARKETPLACE_WORKFLOW_ARACHNE_ENABLED=true MARKETPLACE_WORKFLOW_ARACHNE_MODEL_MODE=deterministic $(COMPOSE) up -d --build

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
		MARKETPLACE_WORKFLOW_ARACHNE_ENABLED=true \
		MARKETPLACE_WORKFLOW_ARACHNE_MODEL_MODE=bedrock \
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