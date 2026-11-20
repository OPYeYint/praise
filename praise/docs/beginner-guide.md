# Praise backend, a beginner guide

This file explains the gratitude API that lives in this project: what it does, how the database is shaped, where each Java file sits, and the rules the code follows. Read it from top to bottom the first time. Later, use the headings to jump back to one idea.

The website and the phone app are not in this repository. This is the server they will call.

## What the app does

A person registers with a username and a password. The server stores the username and a hash of the password, then gives that account a private journal. Entries in the journal have a day, a title, the gratitude text, and an optional mood.

There is one token after login. `JwtService` creates it. Every gratitude call sends it as `Authorization: Bearer <token>`. It expires after 12 hours (`app.jwt.expiration` in `src/main/resources/application.yml`). The password is never that token, and the token is never stored as the password.

The gratitude JSON never contains a user id. `CurrentUser` reads the id from the token. A request cannot ask to open someone else's journal by putting their id in the body.

| Call | Who can use it | Result |
| --- | --- | --- |
| `POST /api/auth/register` | Anyone | 201, our JWT, and the new user |
| `POST /api/auth/login` | Anyone with an account | 200, our JWT, and the user |
| `GET /api/auth/me` | Signed-in user | id and username |
| `POST /api/gratitudes` | Signed-in user | 201 and the new entry |
| `GET /api/gratitudes` | Signed-in user | That user's entries, newest day first |
| `GET /api/gratitudes/random` | Signed-in user | One of their entries, or 204 if the journal is empty |
| `GET /api/gratitudes/{id}` | Owner of that entry | The entry, or 404 |
| `PUT /api/gratitudes/{id}` | Owner | Updated entry, or 404 |
| `DELETE /api/gratitudes/{id}` | Owner | 204, or 404 |

`GET /api/gratitudes/random` is declared before `GET /api/gratitudes/{id}` in `GratitudeController` so the word `random` is not treated as an id.

Status codes you will see:

- **401** means no token, a bad token, or the username and password do not match. A wrong username and a wrong password use the same message.
- **404** means the entry is missing or it belongs to someone else. Both cases use the same answer so people cannot probe ids.
- **409** means that username is already registered.
- **400** means the JSON failed validation, such as a blank title. The body includes an `errors` list of field and message.

## Database structure

PostgreSQL database name: `praise`. The tables are created by Flyway, not by typing SQL by hand each time you start the app.

File: `src/main/resources/db/migration/V1__create_users_and_gratitude_entries.sql`

The `V1` is version 1. The words after `__` are a label for humans. On the first startup Flyway runs this file, then writes a row in a table it owns, `flyway_schema_history`. The next startup sees version 1 already recorded and skips the file. When the shape of the data changes later, add `V2__something.sql`. Do not edit V1 after it has run against a real database.

`ddl-auto` is `validate`. Hibernate checks that the Java classes match the tables. It does not create or alter them.

### `users`

| Column | Meaning |
| --- | --- |
| `id` | Number the database assigns. This is the id inside our JWT. |
| `username` | Login name, stored in lowercase. Letters, numbers, and `_` only. 3 to 50 characters. Unique. |
| `password_hash` | BCrypt hash of the password. The typed password is not stored. |
| `created_at` | UTC moment the row was first saved. |

`V1` created the first tables with Google columns. `V2__replace_google_with_username_password.sql` drops those columns and adds `username` and `password_hash`. Flyway runs V2 once, because V1 is already recorded.

### `gratitude_entries`

| Column | Meaning |
| --- | --- |
| `id` | Number the database assigns. |
| `user_id` | Points at `users.id`. Required. This is how a journal stays private. |
| `entry_date` | The calendar day the gratitude is about. If the request omits it, the server uses today's date in UTC. |
| `title` | Required, at most 200 characters. |
| `content` | Required text, at most 4000 characters in the API. |
| `mood_tag` | Optional, at most 50 characters. A blank mood is stored as NULL. |
| `created_at` | UTC moment the row was first saved. `PUT` does not change it. |

One person can have several entries on the same day. The index `idx_gratitude_entries_user_date` is on `(user_id, entry_date, created_at)` so the list query can find one user's rows in newest-first order without scanning every row in the table.

```text
users.id  <----  gratitude_entries.user_id
```

Java side of the link: `GratitudeEntry.user` is a `@ManyToOne` to `UserAccount`, column `user_id`. Many entries, one account.

`entry_date` is a `LocalDate` (a day, with no clock time). `created_at` is an `Instant` (a UTC timestamp). `java.util.Date` is not used.

## Folder map

All code is under `src/main/java/com/praise`.

| File | One job |
| --- | --- |
| `PraiseApplication` | Starts Spring Boot. |
| `config/TimeConfig` | Provides the UTC `Clock` so "now" can be frozen in tests. |
| `user/domain/UserAccount` | The `users` row. `create` stores the username and the password hash. |
| `user/persistence/UserAccountRepository` | Finds a user by username or id. |
| `user/service/UserAccountService` | `register` creates the account. `login` checks the password. Both issue our JWT. |
| `auth/jwt/JwtService` | Creates and reads our JWT. Claims are `sub` (our user id) and `exp`. |
| `auth/jwt/JwtAuthenticationFilter` | Reads `Authorization: Bearer`. A valid token becomes `AuthenticatedUser`. A bad token is 401. |
| `auth/security/SecurityConfig` | Public: register and login. Everything else needs our JWT. Also provides the BCrypt `PasswordEncoder`. |
| `auth/security/CurrentUser` | The user id for this request, taken from the token. |
| `auth/web/AuthController` | `POST /api/auth/register`, `POST /api/auth/login`, and `GET /api/auth/me`. |
| `gratitude/domain/GratitudeEntry` | One journal row. `create` for new rows. `change` for edits. |
| `gratitude/persistence/GratitudeEntryRepository` | Queries that always include `userId`. |
| `gratitude/service/GratitudeEntryService` | Create, list, get, random, update, delete. |
| `gratitude/service/GratitudeMapper` | Turns an entity into the JSON record. Not a Spring bean. |
| `gratitude/web/GratitudeController` | HTTP only. It does not touch the database. |

`open-in-view` is false. A database transaction finishes in the service. The controller receives a `GratitudeResponse` record, which has no database connection inside it.

## Request stories

### Register and login

1. `AuthController.register` receives `RegisterRequest`. The username is trimmed and lowercased. `Ada` and `ada` are the same account.
2. `UserAccountService.register` hashes the password with BCrypt and saves `UserAccount.create`.
3. If that username already exists, the response is 409.
4. `AuthController.login` calls `UserAccountService.login`. BCrypt checks the typed password against `password_hash`.
5. A miss returns 401 with `Invalid username or password`.
6. A hit calls `JwtService.issue`. The response is `AuthResponse`: token, `Bearer`, `expiresAt`, and `UserResponse` (id and username).

The password must be 8 to 72 characters. BCrypt only uses the first 72 bytes, so the API rejects anything longer.

`GET /api/auth/me` calls `UserAccountService.current`, which loads the user id from `CurrentUser`.

### Create a gratitude

1. The filter accepts our JWT and stores `AuthenticatedUser`.
2. `GratitudeController.create` validates `GratitudeRequest`.
3. `GratitudeEntryService.create` loads that user and calls `GratitudeEntry.create`.
4. A missing `entryDate` becomes `LocalDate.now(clock)`.
5. The repository saves the row. The response is 201 with a `Location` header such as `/api/gratitudes/1`.

### Random memory

`GratitudeEntryService.findRandom` counts this user's rows. Zero rows → the controller returns 204. Otherwise it picks a random index and loads one row with `findByUserId`, sorted by `id` so the offset stays stable. The choice is only among that user's entries.

### Update and delete

`update` and `delete` call `findOwned`, which runs `findByIdAndUserId(id, currentUser.id())`. An empty result throws `GratitudeNotFoundException`. The handler turns that into 404.

`GratitudeEntry.change` writes the day, title, content, and mood. There is no setter for `id`, `createdAt`, or `user`, so an update cannot move the row to another person or rewrite the creation time.

## Code style in this project

New classes do not use Lombok. Constructors, getters, and factories are written out so you can see every field.

**Entities** (`UserAccount`, `GratitudeEntry`) are the database rows. They expose getters. New rows go through `create`. A gratitude edit goes through `change`. They do not implement `equals` or `hashCode`. Before insert, `id` is still null, so equality based on id is misleading. The database id is the identity.

**Records** (`GratitudeRequest`, `GratitudeResponse`, `AuthResponse`, `RegisterRequest`, `LoginRequest`) are the JSON. `GratitudeRequest` trims strings in its compact constructor. A blank `moodTag` becomes null. `RegisterRequest` lowercases the username. `@NotBlank`, `@Size`, and `@Pattern` reject a bad body before the service runs.

**Constructor injection.** Services and controllers receive dependencies in the constructor. There is no field `@Autowired`. Spring calls that constructor.

**`@Transactional(readOnly = true)`** is on the service class. Read methods stay read-only. `create`, `update`, `delete`, and `register` add `@Transactional`, which is a write transaction. `login` stays read-only. The class is not `final`, and those methods are public, so Spring can wrap them.

**Logs** include ids only. Journal text, passwords, and JWTs are not logged.

**Secrets** are not in `application.yml`. The database password and `app.jwt.secret` are in `application-local.yml` next to `pom.xml`. That file is listed in `.gitignore`. The JWT secret must be at least 32 bytes. `JwtService` refuses to start if it is shorter. Account passwords are stored only as BCrypt hashes.

## How to run it

You need a JDK (this project targets Java 17; Java 21 is fine) and PostgreSQL on `localhost:5432` as user `postgres`.

1. Create the database once, in psql or pgAdmin:

   ```sql
   CREATE DATABASE praise;
   ```

2. Open `application-local.yml`. It holds the database password and a local JWT secret. There is no Google client id.

3. `JAVA_HOME` must point at the JDK. From the folder that contains `pom.xml`:

   ```text
   mvnw spring-boot:run
   ```

   The API listens on port **8082**. Flyway applies any new migration, including V2. The console prints SQL because `show-sql` and `format_sql` are on.

4. Register, then call the API. A gratitude call without a token is 401:

   ```text
   curl.exe -i -X POST http://localhost:8082/api/auth/register ^
     -H "Content-Type: application/json" ^
     -d "{\"username\":\"ada\",\"password\":\"password123\"}"
   ```

   Login later with the same body at `POST /api/auth/login`. Copy `token` from the JSON. Then:

   ```text
   curl -i http://localhost:8082/api/gratitudes ^
     -H "Authorization: Bearer PASTE_OUR_TOKEN"

   curl -i -X POST http://localhost:8082/api/gratitudes ^
     -H "Authorization: Bearer PASTE_OUR_TOKEN" ^
     -H "Content-Type: application/json" ^
     -d "{\"title\":\"Morning\",\"content\":\"Quiet coffee\",\"moodTag\":\"calm\"}"
   ```

   On PowerShell, `curl` is an alias of `Invoke-WebRequest`. Use `curl.exe` if you want the commands above.

Tests do not need PostgreSQL. They use an in-memory H2 database:

```text
mvnw test
```

Keep the running API on your own machine until it is served over HTTPS. A bearer token sent over plain `http://` on a shared network can be copied.

## Glossary

Annotations that appear in this code:

| Annotation | Meaning here |
| --- | --- |
| `@SpringBootApplication` | Starts the app and scans `com.praise` for components. |
| `@Configuration` | A class that declares beans. |
| `@Bean` | A method whose return value Spring stores and reuses. `Clock` and the security filter chain are beans. |
| `@Component` | A class Spring creates, such as `CurrentUser`. |
| `@Service` | A `@Component` for business rules. |
| `@RestController` | A class whose methods return JSON, not an HTML page. |
| `@RequestMapping` | Prefix for the paths on that class. |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | One HTTP method and path. |
| `@RequestBody` | Read the JSON body into a Java object. |
| `@PathVariable` | Read `{id}` from the path. |
| `@Valid` | Run the validation annotations on that object. |
| `@NotBlank` | The string must contain something other than spaces. |
| `@Size` | Length limit. |
| `@Pattern` | The username may contain only lowercase letters, numbers, and `_` after it is normalized. |
| `@Entity` | This class is a database table. |
| `@Table` | The table name, when it is not the class name. |
| `@Id` | Primary key. |
| `@GeneratedValue` | The database assigns the id. |
| `@Column` | Column name, required, and length. |
| `@ManyToOne` | Many gratitude rows point at one user. |
| `@JoinColumn` | The foreign-key column, `user_id`. |
| `@PrePersist` | Runs just before the first insert. Here it fills `createdAt` if the service did not. |
| `@Query` | The JPQL written on a repository method. |
| `@Param` | Names a method argument inside that query. |
| `@Transactional` | This method runs in a database transaction. `readOnly = true` means it should not write. |
| `@RestControllerAdvice` | Handles exceptions for the listed controller. |
| `@ExceptionHandler` | The method that runs for that exception type. |
| `@Value` | Inserts one property, such as `app.jwt.secret`, into a constructor argument. |
