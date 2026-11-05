COMPOSE_DEV := docker compose -p sudv-local -f deployment/docker-compose.local.yml --env-file deployment/.env.dev

.PHONY: dev-compose
dev-compose: deployment/.env.dev
	$(COMPOSE_DEV) up -d --build --remove-orphans

.PHONY: dev-logs
dev-logs: deployment/.env.dev
	$(COMPOSE_DEV) logs -f

# keeps DB data
.PHONY: dev-stop
dev-stop: deployment/.env.dev
	$(COMPOSE_DEV) down --remove-orphans

# also removes DB data
.PHONY: dev-destroy
dev-destroy: deployment/.env.dev
	$(COMPOSE_DEV) down -v --remove-orphans

deployment/.env.dev:
	cp deployment/.env.example $@
	@echo "Создан $@ из .env.example — при необходимости поменяйте значения"
