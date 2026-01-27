# UniMateLK (XAMPP + MySQL)

This is a **Spring Boot + HTML/CSS/JS** full‑stack project:
- Backend: Spring Boot (Maven), Spring Security (Google OAuth2), Spring Data JPA, Flyway, WebSocket (STOMP)
- Frontend: Plain HTML/CSS/JS (served by Spring Boot from `/src/main/resources/static`)
- DB: **MySQL via XAMPP + phpMyAdmin**

## Prerequisites
- **JDK 25** configured in IntelliJ
- XAMPP installed (Apache + MySQL)
- Internet access (first time only) for Maven dependency downloads and Google OAuth

## 1) Database setup (XAMPP + phpMyAdmin)
1. Open XAMPP Control Panel
2. Start **Apache** and **MySQL**
3. Open phpMyAdmin: `http://localhost/phpmyadmin`
4. Create a DB:
   - Database name: `unimatelk`
5. Create a DB user (recommended):
   - Username: `unimatelk`
   - Password: `unimatelkpass`
   - Grant **ALL** privileges on DB `unimatelk`

> If you prefer using root with no password (default XAMPP), update `application.properties` accordingly.

## 2) Configure `application.properties`
File: `src/main/resources/application.properties`
- Ensure these match your MySQL settings:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/unimatelk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=unimatelk
spring.datasource.password=unimatelkpass
```

## 3) Configure Google OAuth2
You must create Google OAuth Client credentials:
- Authorized redirect URI should include:
  - `http://localhost:8080/login/oauth2/code/google`

Set environment variables (recommended) or fill them in `application.properties`:
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

## 4) Set an admin email
In `application.properties`:
```properties
app.admin-emails=YOUR_ADMIN_GMAIL@gmail.com
```
When you login with that Google account, your role is set to **ADMIN**.

## 5) Run the app
### IntelliJ
1. Open the project folder in IntelliJ
2. Set Project SDK = JDK 25
3. Run `UnimatelkApplication`

Open:
- App: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 6) Features
- Google login (session/cookies)
- Profile + photo upload
- Preferences + validation
- Match feed with score + reasons + filters + pagination
- Match requests (mutual match gate)
- WebSocket chat (text + emojis) + image attachments
- Report/block + auto temp-block at 5 unique reports (7‑day window)
- Admin moderation dashboard: unblock / ban + resolution notes

## 7) Troubleshooting
- **Flyway checksum error**: use a fresh DB (drop and recreate) or run Flyway repair.
- **Login works but API calls fail with CSRF**: refresh the page; `api.js` fetches `/api/csrf`.
- **Chat doesn't receive messages**: open browser devtools and verify `/ws` connection.

