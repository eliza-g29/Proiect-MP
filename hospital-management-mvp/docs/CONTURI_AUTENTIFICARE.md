# Conturi de autentificare MedFlow

Adresă aplicație: **http://localhost:3000**

Datele de mai jos sunt cele definite în inițializarea și importul proiectului. Parolele curente din baza care rulează nu au putut fi verificate; dacă au fost schimbate sau conturile dezactivate, datele inițiale nu mai sunt valabile.

## Conturi inițiale

Aceste conturi sunt create la prima inițializare a bazei, când este activată popularea inițială.

| Rol | Email | Parolă inițială | Utilizare |
|---|---|---|---|
| Administrator | `admin@demo.ro` | `Demo1234!` | Conturi și drepturi, pacienți, programări, rapoarte, organizarea unității. |
| Medic | `doctor@demo.ro` | `Demo1234!` | Consultații și dosare autorizate, analiza AI și revizuirea recomandărilor. |
| Pacient | `pacient@demo.ro` | `Demo1234!` | Programări proprii, rezultate, tratamente și documente vizibile în dosarul personal. |

Contul de medic este asociat cu Dr. Andrei Popescu; cel de pacient cu Ana Marinescu. Emailul unui profil de medic sau pacient nu reprezintă automat un cont de autentificare: folosește emailurile din tabel.

## Conturi suplimentare din popularea bazei

Sunt disponibile după executarea importului `populate`, în aceeași unitate cu `admin@demo.ro`.

| Rol | Email | Parolă inițială |
|---|---|---|
| Administrator | `demo.admin0001@medflow.example.test` | `Demo1234!` |
| Medic | `demo.doctor0001@medflow.example.test` | `Demo1234!` |
| Medic | `demo.doctor0002@medflow.example.test` | `Demo1234!` |
| Pacient | `demo.patient0001@medflow.example.test` | `Demo1234!` |
| Pacient | `demo.patient0002@medflow.example.test` | `Demo1234!` |

Dacă aceste conturi lipsesc, importul poate fi executat din rădăcina proiectului:

```sh
docker compose --profile seed run --rm populate
```

Importul nu resetează parolele conturilor existente.

## Personal medical — STAFF

Inițializarea și importul existente **nu definesc un cont STAFF cu parolă prestabilită**. Pentru acest rol:

1. Autentifică-te cu `admin@demo.ro`.
2. Deschide **Conturi și drepturi → Adaugă cont**.
3. Alege rolul **Personal medical**, introdu emailul și o parolă temporară de minimum 12 caractere.
4. La prima autentificare, utilizatorul trebuie să schimbe parola.
5. Configurează accesul la pacienți/departamente și programul de lucru. Rolul STAFF nu confirmă diagnostice, rețete sau recomandări AI.

Emailul și parola acestui cont se stabilesc la creare; documentul nu prezintă un cont inexistent drept unul activ.

## Dacă autentificarea eșuează

Verifică emailul exact, existența și starea activă a contului. Pentru parole schimbate, administratorul poate folosi recuperarea accesului din **Conturi și drepturi**. Nu este necesară ștergerea bazei de date pentru recuperarea unui cont.

Surse în proiect: `backend/src/main/java/ro/hospital/service/DemoSeeder.java`, `db/seed/medflow_100plus/seed_medflow.sql` și `db/seed/medflow_100plus/manifest.json`.
