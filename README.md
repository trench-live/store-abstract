# Store Abstract (Kotlin + Ktor)

Production-ready интернет-магазин:
- backend на Kotlin/Ktor
- PostgreSQL + Exposed + Flyway
- Redis cache
- RabbitMQ + worker
- Docker Compose
- CI/CD через GitHub Actions + SSH deploy

## Stack

- Kotlin 1.9.x
- Ktor 2.x
- Gradle Kotlin DSL
- PostgreSQL
- Exposed ORM
- Flyway migrations
- Redis
- RabbitMQ
- JWT auth
- Swagger/OpenAPI
- Tests: unit, integration (Testcontainers), e2e
- React frontend

## Project Structure

```text
src/
  main/kotlin/com/storeabstract/
    domain/
    repository/
    service/
    routes/
    dto/
    config/
  main/resources/
    db/migration/
    openapi.yaml
  test/kotlin/com/storeabstract/
frontend/
.github/workflows/
```

## Environment Variables

Используются только переменные окружения (`.env`):

- `PORT=18080`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `REDIS_HOST`
- `REDIS_PORT`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USER`
- `RABBITMQ_PASSWORD`
- `FRONTEND_API_URL`
- `ADMIN_EMAIL` (optional)
- `ADMIN_PASSWORD` (optional)

## Local Development

```bash
cp .env.example .env
./gradlew build
docker compose up --build
```

## Access

- Frontend: `http://localhost:18081`
- Swagger: `http://localhost:18080/swagger`
- Health: `http://localhost:18080/health`

## Docker Compose Services

- `api` (порт `18080:18080`)
- `worker`
- `frontend` (порт `18081:80`)
- `postgres`
- `redis`
- `rabbitmq`

## API Routes

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

## Tests

```bash
./gradlew test
```

В проекте есть:
- unit tests
- integration tests (Testcontainers)
- e2e tests

Тесты автоматически прогоняются в GitHub Actions workflow при каждом `push` и `pull_request` в `master`/`develop`.

## CI/CD

Один workflow: `.github/workflows/ci.yml`

Jobs:
- `test`: запускает `./gradlew clean test` (unit + integration + e2e)
- `deploy`: запускается только после успешного `test` и только для `push` в `master`

Deploy выполняется по SSH и запускает:

```bash
git pull origin master
docker compose up -d --build
docker image prune -f
```

### Required GitHub Secrets

- `SSH_HOST`
- `SSH_USER`
- `SSH_PRIVATE_KEY`

## Server Deployment

1. Клонировать репозиторий на сервер в целевую директорию.
2. Создать `.env` (из `.env.example`).
3. Убедиться, что путь в deploy job (`cd ...`) совпадает с реальным путем проекта на сервере.
4. Запустить вручную первый раз:

```bash
docker compose up -d --build
```

Дальше деплой выполняется автоматически по `push` в `master`.

## Business Notes

- Удаление товара (`DELETE /products/{id}`) реализовано как soft-delete.
- Товар исключается из каталога и новых заказов.
- Исторические заказы остаются корректными (с сохранением имени товара в order items).
- События заказов пишутся в outbox в рамках транзакции и публикуются worker-ом в RabbitMQ.
