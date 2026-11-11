# MedFlow — gestionarea unei unități medicale

Prototip academic React / Spring Boot / PostgreSQL / FastAPI. Folosește numai date fictive. Modelul AI este sintetic și nu este validat pentru diagnostic, terapie sau triaj medical.

## Pornire și actualizare

Necesită Docker Desktop sau Docker Engine cu pluginul Compose.

```sh
# Numai la prima configurare, dacă .env nu există:
cp .env.example .env

# Din rădăcina proiectului:
docker compose up -d --build
```

Deschide **http://localhost:3000**. Pentru o instalare existentă rulează numai comanda de actualizare, fără să înlocuiești `.env` și fără să ștergi volumele. Flyway aplică V1–V7. Dacă o migrare refuză date incompatibile, investighează eroarea; nu modifica migrațiile deja aplicate.

La prima pornire sunt create un spital, profiluri, consultații/programări fictive și program demonstrativ de lucru pentru medicii inițiali. Conturile demo au parola **`Demo1234!`**:

| Rol | Cont |
|---|---|
| Administrator | `admin@demo.ro` |
| Medic | `doctor@demo.ro` |
| Pacient | `pacient@demo.ro` |

Conturile de **Personal medical (STAFF)** se creează din pagina administratorului. Conturile noi primesc o parolă temporară de minimum 12 caractere, care trebuie schimbată la prima autentificare.

Porturile sunt expuse numai local. Nu publica această configurație cu secretele și conturile demonstrative.

## Populare cu pachetul MedFlow 100plus

Import opțional, adaptat schemei curente și fără ștergerea datelor existente:

```sh
sh ops/import-medflow.sh
```

Sunt planificate 7.610 înregistrări în cele opt tabele ale pachetului, plus intervalele de lucru necesare. Importul nu se execută la pornirea normală; folosește profilul `seed`. Detaliile și starea execuției sunt în [db/seed/medflow_100plus/README.md](db/seed/medflow_100plus/README.md).

## Funcții disponibile

- Conturi și roluri, profil personal, parolă, recuperare asistată de administrator, dezactivare și revocarea sesiunilor.
- Înregistrare, căutare și actualizare pacienți; dosar cu consultații, alergii, diagnostice, observații, laborator, rețete și fișiere.
- Vizibilitate explicită pentru pacient, drepturi READ/WRITE, acces temporar pe cont sau departament și audit/revizii.
- Creare, reprogramare și anulare a programărilor; calendar, intervale libere, protecție SQL la suprapuneri și detectarea modificărilor concurente.
- Administrarea medicilor/specializărilor, departamentelor și turelor personalului auxiliar.
- Notificări interne privind programările și anunțuri administrative; nu sunt trimise emailuri sau SMS.
- Dashboard, rapoarte pe interval și CSV.
- Clasificare AI didactică cu istoric accesibil, explicații, sursa analizei, date insuficiente și acceptare/modificare/respingere umană. Afișează opțiunile generale de conduită/tratament și investigațiile din dataset, fără a crea automat diagnostice/rețete.
- Job de backup zilnic criptat și script de restaurare, healthcheck, HTTPS local opțional și instrumente de măsurare.

## Arhitectură

- Frontend: React 19, TypeScript, Vite, Nginx.
- Backend: Java 21, Spring Boot 3.4, JWT/BCrypt, JPA și JDBC parametrizat.
- Persistență: PostgreSQL 16 și Flyway; cont separat pentru operațiile normale față de migrare.
- AI: Python/FastAPI, TF-IDF + regresie logistică; modele salvate pentru 3 categorii și 30 de profiluri, antrenate pe subsetul TRAIN de 2.250 din 3.000 de cazuri sintetice.
- Deployment: Docker Compose; fișierele, DB, arhivele și cheile de backup sunt în volume separate.

Backendul nu depinde de disponibilitatea AI pentru operațiile de bază. HOSPITAL_TIMEZONE este implicit Europe/Bucharest. Orele locale istorice nu se convertesc automat dacă modifici fusul unității.

## Modelul antrenat pe MedFlow AI Dataset 3000

Ponderile sunt în `ai-service/artifacts/model.json.gz`. Au fost folosite 2.250 de cazuri pentru antrenare, 375 pentru validare și 375 pentru test. Cele două clasificatoare au macro-F1 1,00 pe testul sintetic; acest rezultat reflectă șabloanele comune ale datasetului, nu validare clinică.

Serviciul încarcă modelul fără reantrenare la pornire. Pentru activarea lui în instalarea Docker: `docker compose up -d --build ai`. Detalii, rezultate și reproducere: [ai-service/MODEL_CARD.md](ai-service/MODEL_CARD.md).

## Testare

```sh
# Buildurile backend/AI rulează testele lor, frontendul verifică TypeScript și produce bundle-ul.
docker compose build backend frontend ai backup
docker compose up -d

# După migrare: verificări de integritate; fixturele sunt anulate prin ROLLBACK.
docker compose exec -T db psql -U hospital -d hospital -v ON_ERROR_STOP=1 < backend/src/test/sql/schema_guards.sql

# Evaluare pe holdout sintetic separat; nu reprezintă validare clinică.
docker compose exec ai python evaluate.py
```

Starea exactă a verificărilor și scenariile restante sunt în [docs/VERIFICARE.md](docs/VERIFICARE.md). Buildul complet și testele PostgreSQL nu au putut fi executate în sesiunea de modificare; nu sunt declarate ca trecute.

## Dezvoltare fără Docker

Necesită Java 21/Maven, PostgreSQL 16 cu suport pentru btree_gist, Node.js 22/npm și Python 3.12. Configurează DATABASE_URL/USER/PASSWORD și, dacă este cazul, FLYWAY_USER/PASSWORD. În terminale separate:

```sh
# AI
cd ai-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8000

# Backend
cd backend
mvn spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

Aceste comenzi se pornesc fiecare din rădăcina proiectului, în terminalul propriu. Vite folosește `http://localhost:5173` și proxy pentru `/api`. Fișierul `MedFlow_PREVIEW.html` este o demonstrație istorică independentă; nu reflectă toate funcțiile noi și nu utilizează API-ul.

## Limite care nu trebuie confundate cu funcții validate

CF-18/CF-19 și validarea clinică CNF-22 nu sunt îndeplinite integral de clasificatorul sintetic. Criptarea DB/fișierelor active și a tuturor conexiunilor interne, IAM/MFA, retenția, auditul imuabil, integrarea FHIR/HL7 și conformitatea juridică necesită lucrări/evaluări suplimentare. Timpii de 3/10 secunde și disponibilitatea de 99,5% sunt ținte nemăsurate, nu garanții ale configurației locale.

## Documentație

- [Cerințe CF-01–CF-24 / CNF-01–CNF-24](docs/CERINTE_CF_CNF.md): acoperire și limite.
- [Ghid utilizatori](docs/UTILIZARE.md): fluxuri pentru cele patru roluri.
- [API](docs/API.md): rute, câmpuri, drepturi și erori.
- [Operare](docs/OPERARE.md): actualizare, backup/restaurare, HTTPS și monitorizare.
- [Verificare](docs/VERIFICARE.md): rezultate executate și ce trebuie testat local.
- [Arhitectură](docs/ARHITECTURA.md) și [aliniere arhitecturală](docs/CONFORMITATE_MEDFLOW.md): model și contextul extinderii.

Dosare demo complete și opțiuni medicamentoase: popularea V9 adaugă istorice cu 2–4 vizite, alergii, diagnostice sintetice, semne vitale, rezultate de laborator și planuri de tratament. AI afișează în consultație opțiuni nominale dintr-un catalog cu surse, folosind un model separat antrenat pe 21.000 de scenarii sintetice de rutare și verificări independente de excludere. Detalii: `ai-service/MEDICATION_MODEL_CARD.md` și `db/seed/medflow_100plus/README.md`.
