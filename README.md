# UniMateLK

**University roommate matching and real-time communication platform**

UniMateLK helps university students discover compatible roommates, manage accommodation preferences, connect through match requests, and communicate in one place.

Originally developed as a university group project for the DEA module, UniMateLK is now being actively revived with a focus on backend reliability, privacy, security, and a modern user experience.

![Java 25](https://img.shields.io/badge/Java-25-E76F00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot 4.0.1](https://img.shields.io/badge/Spring%20Boot-4.0.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Development status](https://img.shields.io/badge/Status-Active%20Development-629DC7?style=flat-square)

## Project status

> **Active development — not production-ready.** The original application is being stabilized and extended. Existing features require further integration and acceptance testing. There is no verified public deployment.

- [Main branch](https://github.com/kavisara-samarakoon/unimatelk/tree/main): original application baseline and project overview.
- [Revival branch](https://github.com/kavisara-samarakoon/unimatelk/tree/feature/unimatelk-revival): active backend development, security improvements and regression tests.
- [Issues](https://github.com/kavisara-samarakoon/unimatelk/issues): development and supporting tasks.

## Application features

The existing codebase contains the following feature areas. Their presence does not imply that every workflow is fully functional or production-ready.

| Area | Functionality |
| --- | --- |
| Authentication | Local registration and sign-in; optional Google OAuth2 |
| Student profiles | Profile information and accommodation preferences |
| Roommate discovery | Preference-based match suggestions and filters |
| Connections | Match requests and friend-related workflows |
| Messaging | Real-time chat using WebSocket/STOMP, with image attachments |
| Safety | Blocking, reporting and administrative moderation |

Secure password recovery is being redesigned; the previous recovery endpoints are disabled on the revival branch.

## Technology stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 25, Spring Boot 4.0.1, Spring MVC, Spring Security |
| Persistence | MySQL, Spring Data JPA, Hibernate |
| Database migrations | Flyway |
| Current frontend | HTML, CSS, vanilla JavaScript |
| Real-time communication | WebSocket, STOMP, SockJS |
| Build and testing | Maven Wrapper, JUnit, Spring test libraries; Node.js for JavaScript regression tests on the revival branch |
| Planned frontend | React, TypeScript and a responsive light/dark design |

## Project structure

```text
src/
├── main/
│   ├── java/com/unimatelk/
│   │   ├── api/          # HTTP API controllers and DTOs
│   │   ├── config/       # Application and security configuration
│   │   ├── domain/       # Domain entities
│   │   ├── repo/         # Persistence repositories
│   │   └── service/      # Business logic
│   └── resources/
│       ├── db/migration/ # Flyway migrations
│       ├── static/       # Existing HTML, CSS and JavaScript
│       └── application.properties
└── test/                # Automated tests
pom.xml
mvnw
mvnw.cmd
```

## Getting started

Use the **revival branch** for current development. The instructions below target that branch, not the older application on main.

### Prerequisites

- JDK 25
- MySQL Server (8.4 used for local revival development)
- Git
- Node.js, if running JavaScript regression tests

The Maven Wrapper is included; a separate Maven installation is not required.

### 1. Clone the repository

Create a fresh checkout in a new directory.

```bash
git clone --branch feature/unimatelk-revival https://github.com/kavisara-samarakoon/unimatelk.git
cd unimatelk
java -version
```

### 2. Configure a local development database

Create a dedicated local MySQL database named `unimatelk` and a dedicated application user named `unimatelk_app`, with the permissions needed for the application's schema and Flyway migrations. Use a unique local password, not a shared or production credential.

| Environment variable | Revival branch value |
| --- | --- |
| `DB_URL` | Defaults to `jdbc:mysql://localhost:3306/unimatelk` |
| `DB_USERNAME` | Defaults to `unimatelk_app` |
| `DB_PASSWORD` | Required; supply privately in the environment |

In a Bash or Zsh shell, enter your password without placing it in shell history:

```bash
read -r -s DB_PASSWORD
export DB_PASSWORD
```

Enter the password when the first command waits for input. Flyway manages database schema migrations. Google OAuth2 is optional on the revival branch and requires separate private configuration if enabled.

### 3. Start the application

With your local MySQL server running:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.address=127.0.0.1
```

On Windows, use `mvnw.cmd` with equivalent environment configuration. Open [http://127.0.0.1:8080](http://127.0.0.1:8080) locally.

### 4. Run tests

From the revival branch, review test configuration first and use an isolated development environment with no real user data.

```bash
./mvnw test
node --test src/test/js/chat-security.test.mjs
```

The application is not intended for public exposure before security and deployment acceptance testing is complete.

## Development roadmap

| Phase | Focus | Status |
| --- | --- | --- |
| 1 | Project recovery and baseline | Completed |
| 2 | Core security foundation | Implemented; verification follow-ups remain |
| 3 | Backend security and functionality | In progress |
| 4 | Coastal Aurora UI/UX redesign with React and TypeScript | Planned |
| 5 | Full integration and system testing | Planned |
| 6 | Production preparation and deployment | Planned |

The planned **Coastal Aurora** interface will emphasize clean layouts, responsive navigation, soft peach and blue accents, accessible interactions, and light and dark modes. It is not yet implemented.

## Security and privacy

The revival branch includes security improvements for CSRF handling, protected media access, image validation and storage, WebSocket authorization, and session lifecycle handling. Further authentication, authorization and end-to-end verification is in progress.

Never commit credentials, private OAuth configuration, environment files, database dumps, or runtime user uploads. Use synthetic accounts and images for local testing. Report potentially sensitive security findings privately to the maintainer rather than posting sensitive details in public issues.

## Contributing

- Use a fresh checkout of the current repository. Do not reuse old history or merge stale local branches.
- Follow the base branch and scope agreed with the maintainer for your assigned issue.
- Keep changes narrow and submit a pull request for review.
- Include validation results and known limitations; do not claim tests passed without evidence.
- Do not merge your own pull request without maintainer approval.

## Acknowledgments

UniMateLK originated as a collaborative university group project. The original contributors' work forms the foundation of the ongoing revival.

See the [contributors](https://github.com/kavisara-samarakoon/unimatelk/graphs/contributors) and [development issues](https://github.com/kavisara-samarakoon/unimatelk/issues) for project activity.
