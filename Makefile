COMPOSE_DEV := docker compose -p sudv-local -f deployment/docker-compose.local.yml --env-file deployment/.env.dev

# сервер + БД: собрать образ и запустить в фоне (http://localhost:8080)
.PHONY: dev-compose
dev-compose: deployment/.env.dev
	$(COMPOSE_DEV) up -d --build --remove-orphans

# логи сервера и БД в реальном времени
.PHONY: dev-logs
dev-logs: deployment/.env.dev
	$(COMPOSE_DEV) logs -f

# остановить контейнеры, данные БД сохраняются
.PHONY: dev-stop
dev-stop: deployment/.env.dev
	$(COMPOSE_DEV) down --remove-orphans

# остановить контейнеры и удалить данные БД
.PHONY: dev-destroy
dev-destroy: deployment/.env.dev
	$(COMPOSE_DEV) down -v --remove-orphans

# при первом запуске .env.dev создаётся из примера
deployment/.env.dev:
	cp deployment/.env.example $@
	@echo "Создан $@ из .env.example — при необходимости поменяйте значения"
