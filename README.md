# Test tizimi

Fan bo'yicha test yechib o'rganish uchun veb-tizim. Foydalanuvchi bitta fanni, uning bir yoki bir nechta
mavzusini va savollar sonini tanlaydi, keyin testni **o'rganish** yoki **imtihon** rejimida yechadi.

| Qism     | Texnologiya                                                         |
|----------|---------------------------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.5, Spring Security (JWT), JPA, Flyway        |
| Frontend | Vue 3 (TypeScript, `<script setup>`), Vite, Naive UI, Pinia, Router |
| Baza     | PostgreSQL 16                                                       |
| Ishga tushirish | Docker Compose (postgres + backend + nginx/frontend)          |

## Ishga tushirish

```bash
cp .env.example .env      # parollar va JWT_SECRET ni o'zgartiring
docker compose up -d --build
```

- Ilova: http://localhost:3000 (port `.env` dagi `FRONTEND_PORT` bilan o'zgaradi)
- Swagger UI: http://localhost:3000/api/swagger-ui/index.html
- Default admin: `.env` dagi `ADMIN_USERNAME` / `ADMIN_PASSWORD` (birinchi ishga tushishda yaratiladi)

Backend va Postgres tashqariga port ochmaydi. Frontend konteyneridagi nginx `/api/*` so'rovlarini Docker
ichki tarmog'i orqali backendga uzatadi.

Foydali buyruqlar:

```bash
docker compose logs -f backend          # backend loglari
docker compose up -d --build backend    # faqat backendni qayta build qilish
docker compose down                     # to'xtatish (ma'lumotlar saqlanadi)
docker compose down -v                  # to'xtatish va bazani o'chirish
```

## Lokal dasturlash

```bash
docker compose -f docker-compose.dev.yml up -d           # faqat Postgres, 127.0.0.1:5433

cd backend
DB_URL=jdbc:postgresql://localhost:5433/test_system mvn spring-boot:run   # 8080-port

cd frontend
npm install
VITE_API_TARGET=http://localhost:8080 npm run dev        # http://localhost:5173
```

Testlar (integration testlar Testcontainers orqali Docker ishlatadi):

```bash
cd backend && mvn test
cd frontend && npm run typecheck
```

## Android ilova (internetsiz)

Telefonning o'zida, serversiz ishlaydigan APK versiyasi `mobile/` papkasida: `cd mobile && ./build-apk.sh`.
Batafsil: [mobile/README.md](mobile/README.md). Veb-versiyaga ta'sir qilmaydi.

## Imkoniyatlar

**Foydalanuvchi**
- Ro'yxatdan o'tish, kirish (JWT)
- Fan tanlash, keyin mavzular (har birida savollar soni ko'rsatiladi), savollar soni va rejim
- **O'rganish rejimi:** javobdan keyin darhol to'g'ri/xato, to'g'ri variant va izoh chiqadi. Javobni o'zgartirib bo'lmaydi.
- **Imtihon rejimi:** natija faqat oxirida chiqadi. Yakunlaguncha javobni o'zgartirish mumkin.
- Savollar paneli, progress, klaviatura (1–6 variant tanlash, ← → savollar orasida o'tish)
- Sahifa yangilansa test davom etadi (sessiya backendda saqlanadi)
- Natija: ball, mavzular kesimida statistika (kuchsiz mavzu ajratib ko'rsatiladi), har bir savol tahlili
- **Xatolarni qayta yechish:** xato va javobsiz savollardan yangi test
- Tarix (davom ettirish yoki natijani ko'rish) va shaxsiy statistika

**Admin** (`/admin`)
- Fanlar, mavzular, savollar CRUD (o'chirish = nofaol qilish)
- Savollar: fan, mavzu, qiyinlik, holat bo'yicha filtr, qidiruv, sahifalash
- JSON import, validatsiya xatolari hisobot bilan

## Import formati

```json
{
  "subject": "Fizika",
  "subjectDescription": "ixtiyoriy",
  "topics": [
    {
      "name": "Kinematika",
      "description": "ixtiyoriy",
      "questions": [
        {
          "text": "Tezlikning SI birligi qaysi?",
          "explanation": "Tezlik = yo'l / vaqt.",
          "difficulty": "EASY",
          "options": [
            { "text": "m/s", "correct": true },
            { "text": "km", "correct": false }
          ]
        }
      ]
    }
  ]
}
```

- Fan va mavzular nomi bo'yicha qidiriladi (katta-kichik harf farqlanmaydi), topilmasa yaratiladi.
- Har bir savolda 2–6 ta variant bo'ladi va ulardan aynan bittasida `"correct": true` turadi. Variant matnlari takrorlanmasligi kerak.
- `difficulty`: `EASY` | `MEDIUM` | `HARD` (standart `MEDIUM`).
- Xato savollar o'tkazib yuboriladi, qolganlari import qilinadi. Hisobotda xato savolning yo'li ko'rsatiladi (`topics[0].questions[3]`).

To'liq namuna: `backend/src/main/resources/seed/5-sinf-biologiya.json`.

## Fanlar va yangi fan qo'shish

Hozir tizimda **5-sinf Biologiya** fani bor. U O'. Pratov va boshqalarning «Biologiya 5» darsligi (2020) asosida tuzilgan:
18 mavzu (darslikdagi 18 ta paragraf) va 1159 ta savol. Har bir savolda darslikka asoslangan izoh bor.
Laboratoriya va amaliy mashg'ulotlar, Redi, Paster va Pristli tajribalari hamda darslik oxiridagi
atamalar izohi o'zi tegishli mavzu ichida savolga aylantirilgan.

Yangi fan (masalan, 6-sinf Biologiya) qo'shishning ikki yo'li bor:

1. **Admin panel → Import:** JSON faylni yuklaysiz, fan darhol paydo bo'ladi.
2. **Seed fayl:** JSON faylni `backend/src/main/resources/seed/` papkasiga qo'yib, `docker compose up -d --build backend` qilasiz.
   Ishga tushishda har bir seed fayl tekshiriladi: shu nomli fan bazada bo'lmasa, import qilinadi; bo'lsa, o'tkazib yuboriladi.
   Shuning uchun baza qayta yaratilsa ham fanlar avtomatik tiklanadi. Bu xatti-harakat `SEED_DATA=false` bilan o'chiriladi.

## API (qisqacha)

| Metod | Yo'l | Tavsif |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | token olish |
| GET | `/api/auth/me` | joriy foydalanuvchi |
| GET | `/api/subjects`, `/api/subjects/{id}/topics` | faol fanlar va mavzular (savollar soni bilan) |
| POST | `/api/tests` | `{subjectId, topicIds[], questionCount, mode}` |
| GET | `/api/tests/{id}` | sessiya (to'g'ri javobsiz) |
| POST | `/api/tests/{id}/answers` | `{sessionQuestionId, optionId}` |
| POST | `/api/tests/{id}/finish` | yakunlash, natija qaytadi |
| GET | `/api/tests/{id}/result` | to'liq tahlil |
| POST | `/api/tests/{id}/retry-wrong` | xatolardan yangi test |
| GET | `/api/tests/history?page&size` | tarix |
| GET | `/api/stats/me` | statistika |
| * | `/api/admin/subjects`, `/api/admin/topics`, `/api/admin/questions`, `/api/admin/import` | admin |

Xatolar har doim bir xil formatda qaytadi: `{timestamp, status, message, errors[]}`.

## Qarorlar

- **Savol tanlash:** mavzulardan navbatma-navbat tasodifiy savol olinadi, shuning uchun savollar mavzular bo'yicha teng taqsimlanadi (farq ko'pi bilan 1 ta). Biror mavzuda savol tugasa, qolgan o'rinlar boshqa mavzulardan to'ldiriladi. Savollar tartibi ham, variantlar tartibi ham aralashtiriladi. Variantlar tartibi sessiyada saqlanadi (`option_order`), shuning uchun sahifa yangilanganda o'zgarmaydi. Kod: `QuestionPicker`.
- **Xavfsizlik:** test davomida `correctOptionId`, `correct` va `explanation` yuborilmaydi. O'rganish rejimida ular faqat shu savolga javob berilgandan keyin, imtihon rejimida esa test yakunlangandan keyin yuboriladi.
- **Soft delete:** fan, mavzu, savol va variantlar o'chirilmaydi, nofaol qilinadi. Savol tahrirlanganda olib tashlangan variantlar ham nofaol bo'ladi, shuning uchun eski natijalar buzilmaydi.
- **"Xatolarni qayta yechish"** xato javob berilgan savollar bilan birga javobsiz qolganlarini ham oladi, chunki ular ham o'zlashtirilmagan. Nofaol qilingan savollar olinmaydi.
- **Javobsiz savollar** ball hisoblashda xato hisoblanadi, lekin natijada alohida ko'rsatiladi.
- **Statistikaga** yakunlangan testlar va o'rganish rejimidagi javoblar kiradi. Davom etayotgan imtihondagi javoblar kirmaydi, aks holda statistikaga qarab to'g'ri javobni bilib olish mumkin bo'lardi.
- **Fanlar** SQL migratsiya bilan emas, import formatidagi seed JSON fayllar orqali yuklanadi. Flyway faqat sxemani boshqaradi. Integration testlar alohida test ma'lumotlaridan foydalanadi (`src/test/resources/demo/`).
- **Bitta testda ko'pi bilan 500 ta savol** bo'lishi mumkin. Frontend ham shu chegarani ko'rsatadi.
- **MapStruct** ishlatilmadi: DTO'lar `record` va kichik `from()` metodlari bilan yozildi, xaritalash oddiy bo'lgani uchun bu yetarli.
- **Excel import** hozircha yo'q (ixtiyoriy edi), faqat JSON.
- **Testcontainers 1.21.4** ishlatildi: Docker 29 bilan shu versiya sinovdan o'tkazilgan.
