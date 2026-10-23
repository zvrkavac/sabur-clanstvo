# Sabur Članstvo — Android aplikacija

Prava Android aplikacija (Kotlin) za članski portal Džemata Sabur Randers: **https://dzemat.sabur.dk/clanstvo/**

- Početni ekran s logom, cijeli ekran bez adresne trake, povuci dolje za osvježavanje, ekran „Nema interneta“
- **Obavijesti o članarini bez Firebase-a:** aplikacija svaka 3 sata (i kad je otvorite) pita server ima li novih poruka (`app_api.php`)
- MobilePay, Viber, telefon, SMS i e-mail linkovi otvaraju odgovarajuću aplikaciju
- Potvrda o članstvu i pristupnica → **Ispis / Spremi kao PDF**
- Preuzimanja (Excel izvještaj u adminu) rade i s prijavom
- **Automatska nadogradnja** s GitHub Releases, kao „Moje Kamere“: aplikacija sama ponudi novu verziju

## Postavljanje (jednom)
1. Napravite **javni** repozitorij `zvrkavac/sabur-clanstvo` i uploadujte sadržaj ovog foldera: `.github/`, `android/`, `.gitignore` i `README.md`. Repozitorij mora biti javni da bi aplikacija mogla sama provjeriti nadogradnje. Ključ nije u repozitoriju, nego u Secrets.
2. **Settings → Secrets and variables → Actions → New repository secret**:
   - `KEYSTORE_BASE64` = sadržaj `sabur.jks.base64.txt` (iz `KLJUC_android_TAJNO.zip`)
   - `KEYSTORE_PASSWORD` = sadržaj `keystore_password.txt`
3. **Actions → Android APK → Run workflow**. Ili samo pushajte promjenu u `android/`.

Svaki build napravi **Release** `v2.0.N` s fajlovima `sabur-clanstvo.apk` i `version.json`.

## Link za članove (uvijek najnovija verzija)
```
https://github.com/zvrkavac/sabur-clanstvo/releases/latest/download/sabur-clanstvo.apk
```
Ovaj link se na početnoj stranici članstva prikazuje samo na Android telefonima, kao „Android aplikacija“.

## Nova verzija
Promijenite kod → commit/push na `main` → GitHub izgradi APK i objavi Release. Članovima aplikacija pri sljedećem otvaranju ponudi „Nova verzija… Instaliraj“.

## Promjene
| Šta | Gdje |
|---|---|
| Adresa portala | `android/app/build.gradle` → `BASE_URL` |
| Ime aplikacije, tekstovi | `android/app/src/main/res/values/strings.xml` |
| Boje | `android/app/src/main/res/values/colors.xml` |
| Ikone | prave se automatski iz loga na sajtu (`android/tools/make_icons.py`) |
| Koliko često provjerava obavijesti | `InboxWorker.kt` → `PeriodicWorkRequestBuilder(3, HOURS)` |

⚠️ `sabur.jks` i lozinku čuvajte na sigurnom. S drugim ključem telefon odbija nadogradnju, pa bi aplikaciju trebalo deinstalirati i instalirati ponovo.
