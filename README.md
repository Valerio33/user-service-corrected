Вызовы проходят через `Main → UserService → UserDao → UserDaoImpl → PostgreSQL`.

## Запуск приложения

Создайте базу в локальном PostgreSQL:

```sql
CREATE DATABASE user_db;
```

В `src/main/resources/hibernate.cfg.xml` укажите свои параметры подключения: сервер, базу, пользователя и пароль. В примере используется `localhost:5432/user_db`, пользователь `postgres` и учебный пароль `postgres`.

Из каталога с `pom.xml`:

```bash
mvn clean test
mvn exec:java
```

Hibernate создаёт таблицу `users` через `hbm2ddl.auto=update`. Меню поддерживает создание, поиск по id, список, изменение и удаление. Enter при изменении поля сохраняет его прежнее значение.

## Тесты

| `UserTest` | Юнит, JUnit 5 | Проверка значений полей сущности |
| `UserServiceTest` | Юнит, JUnit 5 + Mockito | Создание, чтение, обновление, удаление, проверка ввода, отсутствие записи, передача ошибок DAO |
| `UserDaoImplIT` | Интеграционный, JUnit 5 + Testcontainers | Реальный Hibernate и PostgreSQL: CRUD, уникальность email, откат и работа после ошибки |

### Только юнит-тесты — Docker не нужен

```bash
mvn clean test
```

### Все тесты — нужен работающий Docker

```bash
mvn clean verify
```

### Отчёты

- `target/surefire-reports` — юнит-тесты;
- `target/failsafe-reports` — интеграционные тесты;
- `target/test-logs/tests.log` — журнал последнего тестового процесса, включая ожидаемые нарушения уникальности.

Каталог `target` исключён из Git.