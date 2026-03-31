PROJECT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
COMPOSE_FILE := $(PROJECT_DIR)compose.yml
COMPOSE := docker compose -f $(COMPOSE_FILE)
UI_DIR := $(PROJECT_DIR)operator-console

.PHONY: help up down reset ps logs test ui-install ui-build ui-dev

help:
	@printf "Marketplace Agent Platform commands:\n"
	@printf "  make up         Start the composed runtime in detached mode with rebuild\n"
	@printf "  make down       Stop the composed runtime\n"
	@printf "  make reset      Stop the composed runtime and remove volumes\n"
	@printf "  make ps         Show compose service status\n"
	@printf "  make logs       Show compose logs, optionally pass SERVICE=<name>\n"
	@printf "  make test       Run mvn test for marketplace-agent-platform\n"
	@printf "  make ui-install Install operator-console dependencies\n"
	@printf "  make ui-build   Build the operator-console bundle\n"
	@printf "  make ui-dev     Start the operator-console Vite dev server\n"

up:
	cd $(PROJECT_DIR) && $(COMPOSE) up -d --build

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