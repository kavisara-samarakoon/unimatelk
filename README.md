# UniMateLK — Roommate Matching & Chat Platform

UniMateLK is a roommate matching web application built with **Spring Boot + MySQL**.  
Users log in with **Google OAuth**, complete a **Profile** + **Preferences**, view **Suggested Matches**, send/accept **Match Requests**, and then chat in real time using **WebSockets (STOMP + SockJS)**. Image sharing in chat is supported.

---

## Tech Stack

- **Backend:** Java (JDK 25), Spring Boot, Spring Security (OAuth2), JPA/Hibernate
- **Database:** MySQL (XAMPP)
- **Migrations:** Flyway (`src/main/resources/db/migration/V1__init.sql`)
- **Frontend:** HTML + CSS + Vanilla JS
- **Realtime Chat:** WebSocket `/ws` (SockJS) + STOMP

---

## Features

✅ Google Login (OAuth2)  
✅ Profile creation/update  
✅ Preferences creation/update  
✅ Match suggestions feed (based on preferences + filters)  
✅ Match requests (send / accept / reject / cancel)  
✅ Chat rooms unlocked only after acceptance  
✅ Realtime chat (text) + image upload  
✅ Basic safety tools (block/report) and admin moderation (if enabled)

---

## Prerequisites

1. **JDK 25** installed and configured in IntelliJ
2. **XAMPP** installed (MySQL running)
3. **Maven** (IntelliJ includes it by default)

---

## Database Setup (XAMPP MySQL)

1. Open **XAMPP Control Panel**
2. Start:
    - ✅ Apache (optional, for phpMyAdmin)
    - ✅ MySQL

### Option A (Recommended): Let Flyway create tables automatically
Your project uses Flyway migrations, so the tables are created on first run.

### Option B: Create DB manually (phpMyAdmin → SQL)
```sql
CREATE DATABASE IF NOT EXISTS unimatelk;
