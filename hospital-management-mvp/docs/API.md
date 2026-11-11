# API MedFlow

Bază `/api`, JSON și `Authorization: Bearer <token>`. Login și health sunt publice. Celelalte rute verifică și dacă acel cont este activ și versiunea JWT este curentă. Rolurile sunt ADMIN, DOCTOR, STAFF, PATIENT. Conturile cu parolă temporară trebuie să o schimbe înaintea accesului la funcțiile de business.

## Identitate și administrare

| Metodă | Rută | Date / rezultat |
|---|---|---|
| POST | `/auth/login` | `{email,password}` → `{token,user}` |
| GET | `/auth/me` | Profil cu `mustChangePassword` |
| PATCH | `/auth/profile` | `{name,email}` |
| POST | `/auth/password` | `{currentPassword,newPassword}` → sesiune nouă |
| POST | `/auth/logout` | Revocă sesiunile contului |
| GET / POST | `/accounts` | Admin: listare / creare cont |
| PATCH | `/accounts/{id}` | Admin: nume, email, rol, profil, active, specialty |
| POST | `/accounts/{id}/recover` | Admin: `{temporaryPassword}` |
| PATCH | `/doctors/{id}` | Admin: `{name,specialty,email}` |
| GET | `/staff` | Director minimal pentru organizarea departamentelor; admin |

Creare cont: `{name,email,role,profileId?,password,specialty?,birthDate?}`. Pentru DOCTOR fără profil existent este obligatorie specializarea, pentru PATIENT fără profil existent data nașterii. Rolul STAFF folosește specializarea auxiliară. Contul și profilul sunt limitate la spitalul administratorului.

## Pacienți, consultații și programări

| Metodă | Rută | Scop |
|---|---|---|
| GET | `/health` | Probează și accesul DB; nu depinde de AI |
| GET | `/dashboard` | Indicatori limitați la cont |
| GET / POST | `/patients` | Listare / înregistrare |
| GET / PATCH | `/patients/{id}` | Detalii / actualizare autorizată |
| GET | `/doctors` | Medicii spitalului |
| GET / POST | `/appointments` | Listare / creare |
| PATCH | `/appointments/{id}` | `{doctorId,startAt,reason?,version?}`; reprogramare |
| PATCH | `/appointments/{id}/status` | `{status,version?}`; pacientul poate numai CANCELLED, în condițiile documentate |
| GET / POST | `/encounters` | Listare / creare |
| PATCH | `/encounters/{id}` | Actualizare, fără reasignarea pacientului/medicului/programării |
| GET | `/audit` | Ultimele 25 de evenimente; admin |

Pacient: `{firstName,lastName,birthDate,phone?,email?}`. Consultație: `{patientId,doctorId,appointmentId?,symptoms,observations?,assessment?,patientVisible?}`. Creare programare: `{patientId,doctorId,startAt,reason?}`. `startAt` este dată/oră ISO locală a unității, de exemplu `2026-12-01T09:00:00`. Durata este 30 minute. `version` provine din răspunsul programării și este trimisă de interfață pentru detectarea datelor stale; JPA verifică separat concurența tranzacțiilor.

## Dosar extins

`GET /records/{kind}`, `POST /records/{kind}`, `PATCH /records/{kind}/{id}`.

| kind | Câmpuri de intrare |
|---|---|
| `departments` | name |
| `staff-departments` | staff_id, department_id, valid_from, valid_to? |
| `allergies` | patient_id, substance, reaction, status |
| `diagnoses` | encounter_id, code, description, coding_system, coding_version |
| `observations` | encounter_id, type, value, unit, measured_at, coding_system, coding_version |
| `lab-results` | patient_id, encounter_id?, test_code, result, unit, coding_system, coding_version |
| `prescriptions` | encounter_id, medicine, dosage, instructions |
| `ai-reviews` | analysis_id, decision, notes, revised_recommendation? |

Înregistrările clinice acceptă și `patient_visible` (implicit true la creare). La actualizare, absența câmpului păstrează valoarea existentă. PATCH trimite câmpurile formularului, nu doar diferențele. Legăturile cu pacientul/consultația nu se reasignează. Contul, spitalul și autorii sunt stabilite de server. Revizuirile AI sunt append-only, iar decizia MODIFIED cere text revizuit.

- `GET /records/medical-documents`: metadate fără cheia internă de stocare.
- `POST /medical-documents`: multipart `patientId`, `documentType`, `file`, `patientVisible` (implicit true); maximum 10 MB.
- `GET /medical-documents/{id}/download`: atașament autentificat; respectă vizibilitatea pacientului.

## Calendar, colaborare, notificări și rapoarte

| Metodă | Rută | Date |
|---|---|---|
| GET / POST | `/work-periods` | Intervale medici; creare admin `{doctorId,startAt,endAt}` |
| PATCH / DELETE | `/work-periods/{id}` | Administrarea intervalului |
| GET | `/availability?doctorId=...&date=YYYY-MM-DD` | Ore libere de 30 minute |
| GET / POST | `/staff-work-periods` | Ture auxiliare; creare admin `{staffId,startAt,endAt}` |
| PATCH / DELETE | `/staff-work-periods/{id}` | Admin; STAFF citește numai propriile ture |
| GET / POST | `/patient-access` | Admin; `{patientId,granteeId?,departmentId?,permission,purpose,expiresAt}` |
| DELETE | `/patient-access/{id}` | Revocare admin |
| GET | `/notifications` | Ultimele 200 de notificări ale contului |
| PATCH | `/notifications/{id}/read` | Marcare ca citită |
| POST | `/announcements` | Admin; `{userId?,title,message}`; lipsa userId înseamnă întregul spital |
| GET | `/reports?from=YYYY-MM-DD&to=YYYY-MM-DD` | Admin; agregate, maximum 366 zile |
| GET | `/reports.csv?from=...&to=...` | Admin; export per medic |

Acordarea are exact un beneficiar: cont sau departament. READ permite consultarea, WRITE permite și modificări în limitele rolului. Dreptul expiră automat sau este revocat. Nu există partajare inter-spital.

## AI demonstrativ și erori

`POST /ai/analyze/{encounterId}` și `GET /ai-analyses`. Serviciul intern `POST /triage` primește numai `{symptoms,observations,medicalHistory}`. Răspunsul include category, summary, evidence, modelVersion, demoOnly, insufficientData, diagnosticSuggestions, therapyOptions, priority, basis. În versiunea guidance1, răspunsul include și possibleConditions (cod, etichetă, alternative, managementOptions, investigații, direcționare), investigations, referral, confidenceLevel și clarificationQuestions. therapyOptions conține opțiunile generale de conduită/tratament ale ipotezei principale, preluate din catalogul TRAIN. Acestea sunt sugestii ale demonstrației academice, nu un model generativ de prescripții. Detaliile salvate includ sursa utilizată la momentul analizei.

Coduri principale: 400 validare, 401 autentificare/sesiune revocată, 403 drept insuficient, 404 resursă inexistentă sau necomunicabilă, 409 conflict de date/concurență, 413 fișier prea mare, 429 prea multe încercări de login prin Nginx, 503 AI/stocare indisponibilă. Majoritatea erorilor API au `{error:"mesaj"}`; erorile proxy pot avea corp text/HTML.

Actualizare opțiuni medicamentoase v1: `/ai/analyze/{encounterId}` include în cererea către AI `patientContext` fără identificatori: vârsta la consultație, alergii consemnate, diagnostice și rezultate accesibile până la data consultației, tratamente istorice. Prescripțiile istorice nu sunt presupuse active. Absența unei evaluări alergologice, a tratamentului curent, a statusului de sarcină sau a eGFR rămâne necunoscută. Răspunsul adaugă `medicationOptions`, `withheldMedications`, `medicationNote`, cu nume, indicație, status, verificări și surse pentru fiecare opțiune. Se păstrează în `details` și se afișează în consultație. Nu se generează doze sau prescripții automate. Consultați `ai-service/MEDICATION_MODEL_CARD.md` pentru dataset, metoda de antrenare și limitele evaluării.

Calendar de disponibilitate: `GET /availability/calendar?from=YYYY-MM-DD&days=7&doctorId=<optional>` returnează lista medicilor din spital și zilele cu `workingHours` și `slots`. Intervalele sunt de 30 minute, se exclud orele trecute și suprapunerile cu rezervările `SCHEDULED` ale tuturor pacienților. Niciun nume/ID de pacient nu intră în acest răspuns. Intervalul acceptat este de 1–14 zile din următoarele 365 de zile. `/availability` folosește același calcul pentru o singură zi. Rezervarea rămâne validată de backend și constrângerile PostgreSQL în cazul cererilor concurente.

V10 introduce `doctor_weekly_hours` și `doctor_schedule_exceptions`. Pentru medicii sintetici se generează ture săptămânale de 6 ore, cu zile libere și unele dimineți de sâmbătă. Ferestrele concrete sunt materializate la cererea calendarului, astfel încât regulile existente de integritate a programărilor continuă să funcționeze. Doar ferestrele largi recognoscibile ale fixture-ului sunt normalizate; toate rezervările existente rămân acoperite. Zilele configurate manual și zilele închise explicit sunt excepții de la șablon. Medicii reali fără program nu primesc ore fictive.
