# Store Abstract (Kotlin + Ktor)

Production-ready интернет-магазин: backend на Kotlin/Ktor + PostgreSQL/Redis/RabbitMQ, отдельный worker, React frontend, Docker Compose и SSH автодеплой через GitHub Actions.

## Stack

- Kotlin + Ktor 2.x
- Gradle Kotlin DSL
- PostgreSQL + Exposed ORM + Flyway
- Redis (cache)
- RabbitMQ (order events + outbox relay)
- JWT auth
- Swagger/OpenAPI
- Tests: unit, integration (Testcontainers), e2e
- Docker + Docker Compose
- GitHub Actions (appleboy/ssh-action)

## Структура

```text
/src
  /domain
  /repository
  /service
  /routes
  /dto
  /config
/frontend
```

## Local development

1. Скопируйте env:

```bash
cp .env.example .env
```

2. Сборка:

```bash
./gradlew build
```

3. Поднять весь стек:

```bash
docker compose up --build
```

## Как открыть

- Frontend: `http://localhost:18081`
- Swagger UI: `http://localhost:18080/swagger`
- Health: `http://localhost:18080/health`

## Admin bootstrap

Админ создаётся/обновляется автоматически при старте API, если заданы:

- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`

В `.env.example` уже есть стартовые значения:

- `admin@example.com`
- `admin12345`

## Деплой на сервер (Ubuntu 20.04)

1. Клонируйте репозиторий в:

```bash
/home/azirumga/store-abstract
```

2. Создайте `.env` (по примеру `.env.example`).

3. Запустите:

```bash
cd /home/azirumga/store-abstract
docker compose up -d --build
```

## CI/CD (GitHub Actions)

### CI (tests)

Workflow: `.github/workflows/ci.yml`

Trigger:
- push в `develop` и `master`
- любой PR в `develop` или `master`
- ручной запуск (`workflow_dispatch`)

Что делает:
- поднимает JDK 17
- запускает `./gradlew clean test`
- публикует артефакты отчётов (`build/reports/tests/test`, `build/test-results/test`)

### CD (deploy)

Workflow: `.github/workflows/deploy.yml` (запускается только после успешного CI)

Trigger:
- успешный `CI` на ветке `master` (event `push`)
- это покрывает: прямой push в `master` и merge PR `develop -> master`

Secrets:
- `SSH_HOST`
- `SSH_USER`
- `SSH_PRIVATE_KEY`

Команды на сервере:

```bash
cd /home/azirumga/store-abstract
git pull origin master
docker compose up -d --build
docker image prune -f
```

## Переменные окружения

- `PORT=18080`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`
- `REDIS_HOST`, `REDIS_PORT`
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`
- `FRONTEND_API_URL`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`

## API routes

- `POST /auth/register`
- `POST /auth/login`
- `GET /products`
- `GET /products/{id}`
- `POST /orders`
- `GET /orders`
- `DELETE /orders/{id}`
- `POST /products` (admin)
- `PUT /products/{id}` (admin)
- `DELETE /products/{id}` (admin)
- `GET /stats/orders` (admin)
- `GET /health`
- `GET /swagger`

## Worker

`worker` запускается отдельным контейнером (`APP_MODE=worker`):

- читает pending события из `outbox_events`
- публикует их в RabbitMQ (`order_events`) с retry/backoff
- consumes `order_events`
- пишет событие в логи и имитирует email-уведомление (log only)

## Тесты

- Unit: `PasswordServiceTest`
- Integration: `UserRepositoryIntegrationTest` (Testcontainers)
- E2E: `ApiE2ETest`

Запуск:

```bash
./gradlew test
```

## Поведение удаления товара

- `DELETE /products/{id}` делает soft-delete: товар скрывается из каталога и недоступен для новых заказов.
- Исторические заказы сохраняют ссылку на товар, поэтому отчеты и история остаются корректными.
- События заказов пишутся в DB outbox в той же транзакции, а worker публикует их в RabbitMQ с ретраями.













