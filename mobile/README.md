# Test tizimi – Android ilova (APK)

Internetsiz, telefonning o'zida ishlaydigan versiya. Server ham, login ham yo'q:
ilovani ochasiz, fan va mavzularni tanlaysiz, test yechasiz. Tarix va statistika telefonda saqlanadi.

Veb-versiya (`frontend/`, `backend/`) bunga bog'liq emas va o'zgarmaydi.

## Qanday tuzilgan

- Sahifalar (fanlar, test sozlash, test, natija, statistika) `frontend/src` dan **o'zgartirilmasdan** olinadi.
  `vite.config.ts` dagi alias'lar faqat server bilan ishlaydigan modullarni (`@/api`, `@/api/http`, `@/stores/auth`)
  mobil versiyalar bilan almashtiradi.
- `src/api/local/` – backend mantig'ining TypeScript nusxasi: savollarni mavzularga teng taqsimlab tanlash,
  o'rganish/imtihon rejimlari, natija, «xatolarni qayta yechish», tarix, statistika. Ma'lumotlar telefondagi
  IndexedDB'da (Dexie) saqlanadi.
- Fanlar `backend/src/main/resources/seed/*.json` dan olinadi va APK ichiga qo'shiladi. Ilova ochilganda
  hali bazada yo'q fanlar yuklanadi; mavjudlari va natijalar o'zgarmaydi.
- Mobilga xos: pastki menyu, tarix kartochkalar ko'rinishida, Android «orqaga» tugmasi
  (bosh sahifada – chiqish, test/natijada – bosh sahifaga).

## APK yig'ish

```bash
cd mobile
npm install          # bir marta
./build-apk.sh       # -> mobile/test-tizimi-1.N.apk
```

Skript versiyani avtomatik oshiradi, veb-bundle'ni yig'adi va APK'ni Docker ichida (JDK 21 + Android SDK 35)
yig'ib, `keystore/` dagi kalit bilan imzolaydi. Kompyuterga Android Studio o'rnatish shart emas.

**Yangi fan qo'shish:** JSON faylni `backend/src/main/resources/seed/` ga qo'ying (format – asosiy README'da)
va `./build-apk.sh` ni ishga tushiring. Yangi APK eski ilova ustidan o'rnatiladi, tarix saqlanib qoladi.

## Telefonga o'rnatish

1. APK faylni telefonga o'tkazing (USB, Telegram «Saved messages», va h.k.).
2. Faylni oching, «Noma'lum manbalardan o'rnatish»ga ruxsat bering.
3. Android 6.0 va undan yangi versiyalar qo'llab-quvvatlanadi.

## ⚠️ Imzolash kaliti

`keystore/release.jks` va `keystore/keystore.properties` – ilovaning imzosi. **Zaxira nusxasini saqlang.**
Kalit yo'qolsa, yangi versiyalar eski ilova ustidan o'rnatilmaydi: eskisini o'chirish kerak bo'ladi
va telefondagi tarix yo'qoladi. Kalit git'ga qo'shilmaydi.

## Sinovlar

```bash
npm test             # lokal mantiq (Vitest + fake-indexeddb)
npm run dev          # brauzerda ishlab ko'rish: http://localhost:5173
```
