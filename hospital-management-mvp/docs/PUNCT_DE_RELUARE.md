# Punct de reluare — lucru întrerupt la cererea utilizatoarei

Stare: RELUAT la cererea utilizatoarei. Acest fișier păstrează istoricul punctului de pauză; pentru starea finală consultă CERINTE_CF_CNF.md și VERIFICARE.md.

## Limita de acces obligatorie

Accesează exclusiv `/Users/elisa/Downloads/hospital-management-mvp` și fișierele indicate explicit de utilizatoare. Nu căuta în alte proiecte, în directoarele personale sau în cache-uri externe și nu folosi dependențe din alte proiecte fără acord explicit. Utilizatoarea a reiterat această limită după o căutare neautorizată a definițiilor TypeScript. Celelalte proiecte nu au fost modificate.

## Obiectivul curent

Adaptarea aplicației la documentul `Cerinte_functionale_nefunctionale.docx`, indicat explicit de utilizatoare. Conținutul documentului a fost deja citit. Cuprinde CF-01–CF-24 și CNF-01–CNF-24. Lucrul anterior, bazat pe documentul arhitectural MedFlow, rămâne parte a proiectului.

## Modificări efectuate în etapa curentă

- Migrare V4: rol STAFF, conturi active/inactive, versiune pentru revocarea JWT, schimbarea obligatorie a parolelor temporare, notificări, program de lucru, acces temporar la dosare, revizuiri AI MODIFIED și istoricul modificărilor.
- Migrare V5: controlul vizibilității consultațiilor și înregistrărilor pentru pacient.
- Administrarea conturilor, recuperare asistată de administrator, editarea profilului, schimbarea parolei și logout cu revocarea sesiunilor.
- Servicii și API-uri noi în `backend/src/main/java/ro/hospital/operations`: conturi, notificări/anunțuri, program de lucru/disponibilitate, acordări/revocări de acces și rapoarte JSON/CSV.
- Reprogramarea și anularea de către pacient a propriilor programări viitoare; verificarea programului medicului și notificări interne.
- Actualizarea pacienților, consultațiilor și dosarului extins; revizii ale valorilor anterioare.
- AccessPolicy diferențiază consultarea de scriere și limitează deciziile clinice pentru STAFF.
- Interfețe noi `AccountSettings.tsx`, `OperationsWorkspace.tsx`; navigare, calendar, disponibilitate, notificări, rapoarte și formulare de editare.
- AI: istoric accesibil al consultațiilor, rezultate structurate, proveniența datelor, date insuficiente, revizuire umană și set sintetic de evaluare separat. Modelul a fost separat în `ai-service/model.py`.
- AI-ul rămâne demonstrativ. NU generează terapii și NU îndeplinește validarea clinică pentru diagnostice/triaj real. Nu declara CF-18/CF-19 complet implementate clinic.
- `ops/`: backup zilnic criptat cu age, restaurare explicită, TLS local opțional, probe de performanță și monitorizare disponibilitate.
- Dockerfile backend include healthcheck Java și execută testele la build. Dockerfile AI include testele. Compose include backup/restore și volumele lor. `compose.https.yml` este configurația HTTPS opțională.

## Verificări efectuate și limite

- 23 teste unitare Java au trecut folosind dependențe externe disponibile înainte de impunerea limitei stricte. Au existat modificări ULTERIOARE acestui rezultat: teste de conturi/JWT, proveniența AI și vizibilitatea înregistrărilor. Nu prezenta acel rezultat ca verificare a stării finale.
- Modulele de business compilaseră înaintea ultimelor modificări; buildul Maven complet cu versiunile declarate nu a fost executat.
- Sintaxa TypeScript și configurațiile Compose au fost verificate înaintea ultimelor modificări, nu după toate schimbările recente.
- Testele FastAPI nu au putut rula deoarece mediul încercat nu avea FastAPI. Nu mai folosi acel mediu extern fără acord.
- Nu există rezultate confirmate pentru evaluarea sintetică, performanță, disponibilitate sau restaurare.
- Nu s-a putut accesa Docker în sesiune; PostgreSQL local nu a putut porni din cauza restricțiilor de memorie partajată.
- Nu mai accesa cache-uri externe, directoare temporare externe sau alte proiecte pentru a recupera verificările anterioare.

## De continuat după reluare

1. Revizuiește modificările recente în interiorul proiectului și corectează eventualele erori. În special verifică împreună RecordService, RecordsController, AccessPolicy, DTO-urile și formularele după introducerea patient_visible.
2. Verifică fluxurile conturilor/JWT și recuperarea accesului; testele noi sunt în auth și operations. Verifică recuperarea parolei și dezactivarea în condiții concurente.
3. Programul de lucru implementat este pentru MEDICI. Cerința CF-14 se referă și la personalul medical auxiliar: extinde fluxul pentru STAFF sau marchează explicit diferența; nu declara acoperire integrală fără implementare.
4. Verifică autorizarea READ/WRITE pentru revizuirile AI și editarea dosarului; accesul READ nu trebuie să permită scrierea.
5. Revizuiește scripturile backup/restore, configurația TLS și migrațiile. Nu executa restaurări sau operații distructive.
6. Finalizează matricea `docs/CERINTE_CF_CNF.md` (încă NEcreată), cu toate cele 24 CF și 24 CNF, separând funcționalitățile implementate, verificările neexecutate și limitele de producție.
7. Actualizează README și documentația veche: unele limitări sunt depășite de etapa curentă. Documentează conturile/rolul STAFF, notificările interne (fără email/SMS), recuperarea asistată, backup/restaurare, TLS și actualizarea prin migrare.
8. CNF-06 nu este complet îndeplinit: HTTPS este opțional la marginea aplicației și backupurile sunt criptate; baza de date și fișierele active necesită stocare criptată configurată de operator, iar traficul intern este HTTP. Nu afirma criptare integrală sau conformitate GDPR certificată.
9. CNF privind 3 secunde, 10 secunde și disponibilitate 99,5% sunt ținte care trebuie măsurate. Evaluarea sintetică nu reprezintă validare clinică.
10. Rulează verificările numai cu resurse din proiect și instrumente autorizate. Dacă lipsesc dependențele ori accesul Docker, explică exact limita; nu căuta soluții în alte directoare fără acord.
11. Comanda de actualizare prevăzută este `docker compose up -d --build`, fără `down -v`. Migrațiile păstrează datele existente, dar pot refuza date care încalcă noile constrângeri.

Nu s-a emis încă un raport final pentru adaptarea la documentul CF/CNF. Utilizatoarea a cerut explicit salvarea și întreruperea lucrului până revine acasă.

## Stare după reluare

Implementarea din proiect a fost completată cu V6 (specializări/ture STAFF), V7 (versiuni pentru programări), normalizarea orelor SQL, corecții de concurență la conturi, teste suplimentare și documentația CF/CNF, API, utilizare, operare și verificare. Instrucțiunile vechi „de continuat” de mai sus sunt istoricul pauzei; pentru starea actuală folosește CERINTE_CF_CNF.md și VERIFICARE.md.

Verificările fără dependențe externe au trecut. Buildurile complete, testele de integrare/PostgreSQL, browserul și backup/restaurare rămân de executat în mediul local autorizat. Descărcarea npm este blocată de rețea, iar pluginul Compose nu este disponibil cu configurația izolată în proiect. Nu căuta alte dependențe, configurații sau cache-uri în afara proiectului fără acord explicit.
