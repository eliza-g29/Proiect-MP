# Aliniere la documentul de arhitectură MedFlow

Acest document descrie etapa arhitecturală V2/V3. Pentru starea curentă, inclusiv rolul STAFF, editare, colaborare, notificări și programul de lucru V4–V7, consultă [CERINTE_CF_CNF.md](CERINTE_CF_CNF.md), [API.md](API.md) și [VERIFICARE.md](VERIFICARE.md).

Referință: `Proiectarea_arhitecturii_aplicatiei_si_bazei_de_date_MedFlow.docx`, primit la 11.11.2026.

Implementarea păstrează arhitectura pe niveluri React 19 / TypeScript / Spring Boot 3.4 / Java 21 / PostgreSQL 16 / FastAPI. Serviciul AI rămâne un proces separat. Extensiile marcate „Propus” în document sunt disponibile acum ca funcții demonstrative de creare și consultare, cu excepția abonamentelor și notificărilor, pe care documentul le declară opționale.

## Trasabilitate

| Secțiune document | Implementare |
|---|---|
| 1–2: identitate, pacienți, programări, consultații, AI, audit | Endpointurile existente și navigarea pe roluri; logout explicit și carduri interactive |
| 2.1, 6: restrângerea accesului medicului | `AccessPolicy`: doar pacienți asociați; consultații și analize numai sub propriul profil |
| 2.2: consultație cu programare opțională | Selector în formular; API și FK verifică pacientul, medicul și spitalul |
| 3.2, 3.4: programare → consultații 0:N | Păstrată deliberat: `appointment_id` este opțional și nu este unic |
| 3.5: status și concurență | CHECK SQL; excludere GiST pe intervalele de 30 minute pentru SCHEDULED; răspuns 409 la conflict |
| 3.5: integritate multi-spital | FK compuse cu hospital_id, inclusiv profiluri, programări, consultații, analize și audit |
| 4: departments / staff_departments | Creare departamente și asocieri cu perioadă de valabilitate; numai admin scrie |
| 4: allergies | Substanță, reacție, stare, pacient |
| 4: diagnoses | Consultație, cod, descriere, sistem/versiune; confirmed_by derivat din contul autentificat |
| 4: observations | Consultație, parametru/cod, valoare, unitate, moment, sistem/versiune |
| 4: lab_results | Pacient, consultație opțională, cod, rezultat, unitate, sistem/versiune; FK împiedică legarea consultației altui pacient |
| 4: prescriptions | Consultație, medicament, dozaj, instrucțiuni; autorul este înregistrat automat |
| 4: medical_documents | Metadate PostgreSQL; conținut într-un volum privat separat; upload/download autentificat, maxim 10 MB |
| 4: ai_reviews | Analiză, reviewer, ACCEPTED/REJECTED/NEEDS_REVIEW, motiv, moment; nu produce diagnostic sau rețetă |
| 5: minimizarea transferului AI | Numai symptoms/observations; răspuns verificat și timeout; fără identificatori de pacient |
| 5: privilegii DB | În Compose, conexiunea aplicației folosește hospital_app (DML); Flyway are contul distinct de migrare |
| 6: auditarea citirilor | Jurnal pentru listarea/citirea pacienților, consultațiilor, dosarului extins și descărcarea documentelor |

## Regula demonstrativă medic–pacient

Un medic poate accesa un pacient din spitalul său dacă există o programare sau o consultație asociată medicului, ori dacă medicul a înregistrat acel pacient (eveniment PATIENT_CREATED). În acest fel poate crea prima programare/consultație pentru pacientul nou. Administratorul poate stabili legătura cu un pacient existent printr-o programare. Medicul vede numai propriile consultații, chiar dacă pacientul are consultații și la alți medici.

Pentru alergii și documente se aplică relația cu pacientul. Pentru diagnostice, observații, rețete și revizuiri AI se aplică și proprietatea consultației. Rezultatele de laborator legate de o consultație sunt vizibile medicului acelei consultații; rezultatele fără consultație respectă relația cu pacientul. Pacientul citește propriul dosar și propriile consultații; nu scrie în dosarul extins.

Acestea sunt reguli explicite ale demonstrației, nu un sistem complet de consimțământ și partajare inter-spital. Asocierea istorică nu expiră automat; sunt necesare politici suplimentare pentru producție.

## API nou

- `GET /api/records/{tip}`: înregistrări filtrate după cont.
- `POST /api/records/{tip}`: creare autorizată cu validare și audit în aceeași tranzacție.
- Tipuri: `departments`, `staff-departments`, `allergies`, `diagnoses`, `observations`, `lab-results`, `prescriptions`, `ai-reviews`.
- `GET /api/records/medical-documents`: metadate fără cheia de stocare.
- `POST /api/medical-documents`: multipart (`patientId`, `documentType`, `file`).
- `GET /api/medical-documents/{id}/download`: conținut returnat ca atașament după autorizare.
- `GET /api/staff`: ID, nume și rol pentru asocierea personalului cu departamente; numai admin.

Câmpurile API ale dosarului extins folosesc numele SQL (`patient_id`, `encounter_id` etc.). Contul, spitalul și autorii nu pot fi furnizați de client. SQL utilizează o listă fixă de tabele/câmpuri și parametri legați pentru valori.

## Actualizare fără ștergerea datelor

```sh
docker compose up -d --build
```

Flyway aplică V2 și V3 peste V1; V1 nu este modificată. Nu folosiți `down -v` pentru actualizare. Documentele se păstrează în volumul `medical_documents`, separat de `postgres_data`. Schimbarea parolei rolului aplicației se face prin APP_DATABASE_PASSWORD și reexecutarea serviciului db-setup.

Dacă datele existente încalcă noile constrângeri (suprapuneri sau asocieri între spitale), migrarea se oprește. Datele trebuie investigate și corectate explicit; migrarea nu șterge și nu anulează automat înregistrări.

## Verificare

```sh
# Teste unitare și de securitate; sunt rulate și în buildul imaginii backend.
cd backend
mvn test

# Din rădăcina proiectului, după pornirea PostgreSQL și migrare:
docker compose exec -T db psql -U hospital -d hospital -v ON_ERROR_STOP=1 < backend/src/test/sql/schema_guards.sql

# Frontend:
cd frontend
npm install
npm run build
```

Testul SQL verifică statusurile, suprapunerile la limită de 30 minute, FK multi-spital, legătura consultație–programare și consultație–rezultat de laborator, precum și cardinalitatea 0:N. Fixturele sunt anulate prin ROLLBACK. Testele Java acoperă autentificarea/CORS, accesul negativ și autorii/proveniența înregistrărilor. O verificare SQL într-o singură tranzacție nu înlocuiește un test de sarcină cu rezervări concurente; garanția concurenței este constrângerea PostgreSQL EXCLUDE.

## Limite păstrate din document

Nu sunt implementate IAM/MFA, HTTPS cu certificate de producție, RLS, backup automat, criptarea volumelor sau integrarea cu un serviciu extern de obiecte. Volumul privat este implementarea locală a separării fișierelor de metadate; nu este un serviciu de stocare obiecte gestionat. Codurile și versiunile sunt documentate de utilizator, fără validare într-un catalog medical oficial. Etapa inițială permitea adăugare și consultare; editarea cu revizii a fost adăugată ulterior. Documentele și revizuirile AI se păstrează prin adăugarea unei noi versiuni, fără ștergere. Nu sunt implementate identitatea longitudinală, partajarea inter-spital sau abonamentele. Notificările interne au fost adăugate în etapa CF/CNF. AI-ul rămâne un clasificator sintetic fără validare clinică.

Rezultatele istorice ale etapei arhitecturale nu certifică starea actuală. Verificările curente și limitele lor sunt documentate separat în VERIFICARE.md.

Verificările care necesită Docker și descărcarea dependențelor trebuie executate în mediul local; sesiunea de modificare nu a avut acces la socketul Docker, la rețea sau la memoria partajată necesară pornirii PostgreSQL.
