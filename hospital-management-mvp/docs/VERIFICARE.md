# Verificare și limitele rezultatelor

## Executat după reluare

- Parsarea sintactică Java cu JDK, fără rezolvarea dependențelor externe: sursele aplicației, testele JUnit și healthcheck-ul. Aceasta nu este un build Maven și nu verifică tipurile externe.
- Compilarea cu Java 21 a healthcheck-ului și utilitarului SQL, fără dependențe externe.
- Test independent al conversiei java.sql.Timestamp / java.sql.Date: păstrează orele locale și valorile normale în UTC și Europe/Bucharest.
- Parsare sintactică Python pentru serviciul AI, evaluare, teste și scripturile operaționale.
- Verificarea sintaxei scripturilor shell.
- Parsare YAML pentru Compose, override HTTPS și application.yml; JSON pentru configurațiile frontend și XML pentru pom.xml.

Nu au fost accesate alte proiecte sau cache-uri externe după reluare. Artefactele verificărilor și încercarea de cache npm sunt în directoare `.cache` din proiect, ignorate de Git și de buildurile Docker.

## Neexecutat pentru starea finală

- Buildul Vite/TypeScript și verificarea completă de tipuri: descărcarea dependențelor din registrul npm este blocată de rețea.
- Buildul Maven complet și suitele finale JUnit, inclusiv noile teste pentru conturi, vizibilitate și programul STAFF.
- Testele HTTP FastAPI cu versiunile declarate. Evaluarea noului model sintetic a fost executată ulterior, conform secțiunii de mai jos.
- Migrațiile pe PostgreSQL 16 și scriptul schema_guards.sql.
- Validarea semantică Docker Compose și pornirea containerelor. Cu configurația Docker izolată în proiect nu este disponibil pluginul Compose; nu a fost accesată configurația personală externă.
- Teste browser/dispozitive, scenarii complete pe roluri, concurență reală, backup/restaurare, timpi și disponibilitate.

Înainte de pauză au trecut 23 teste de business cu alte dependențe disponibile atunci; au urmat modificări suplimentare. **Acest rezultat nu certifică versiunea finală.**

## Comenzi pentru verificarea completă în mediul local autorizat

Din proiect:

```sh
# Configurație și build; imaginile backend și AI execută testele lor la construire.
docker compose config --quiet
docker compose build backend frontend ai backup

# Pornire și verificări DB după migrare.
docker compose up -d
docker compose exec -T db psql -U hospital -d hospital -v ON_ERROR_STOP=1 < backend/src/test/sql/schema_guards.sql

# Evaluare sintetică; rezultatele nu sunt validare clinică.
docker compose exec ai python evaluate.py
```

Scriptul SQL anulează fixturele prin ROLLBACK; secvențele pot avansa. Verifică separat baza nouă și o copie a volumului existent. Nu modifica migrațiile aplicate și nu șterge volumele pentru a ocoli o eroare de integritate.

Pentru mediile fără Docker:

```sh
# Necesită dependențele proiectului instalate în mediul ales.
cd backend
mvn test
cd ../frontend
npm install
npm run build
cd ../ai-service
python -m pytest -q
python evaluate.py
```

Verificări care nu necesită dependențe Maven:

```sh
mkdir -p ops/.cache backend/.cache/checks
javac --release 21 -d ops/.cache ops/JavaSyntaxCheck.java
java -cp ops/.cache JavaSyntaxCheck backend/src/main/java backend/src/test/java backend/Healthcheck.java
javac --release 21 -d backend/.cache/checks backend/src/main/java/ro/hospital/util/SqlRows.java backend/src/test/standalone/SqlRowsCheck.java
java -cp backend/.cache/checks SqlRowsCheck
```

## Scenarii manuale necesare

1. Login pentru cele patru roluri; cont nou → schimbare obligatorie a parolei → acces normal.
2. Cont dezactivat / parolă recuperată → tokenul vechi respins; profilurile altui spital refuzate.
3. Acces READ → dosar consultabil, scriere respinsă; WRITE în limitele rolului; expirare și revocare aplicate.
4. Consultație/document intern → ascuns în contul pacientului, inclusiv la acces direct.
5. Programare în programul medicului → salvată și notificată; interval ocupat sau în afara programului → conflict.
6. Două sesiuni modifică aceeași programare → cea cu versiune veche primește conflict și trebuie să reîncarce.
7. Pacientul reprogramează/anulează numai propriile programări viitoare active; medicul nu editează calendarul altui medic.
8. STAFF vede numai propriile ture; administratorul organizează și modifică ture, fără suprapuneri pentru același membru al personalului.
9. CSV-ul include numai unitatea autorizată; formulele din celule sunt neutralizate.
10. AI indisponibil → funcțiile de bază continuă; date necunoscute → semnal de insuficiență; MODIFIED cere text nou și nu creează diagnostic.
11. Backup + restaurare pe mediu separat → DB și documentele referite sunt accesibile cu cheia corectă.

## Remedierea ecranului alb la navigare

Cauza identificată în cod: paginile administrative refoloseau aceeași instanță OperationsWorkspace. La schimbarea mode, înaintea efectului de golire a listei, rândurile paginii precedente puteau fi randate ca notificări. Lipsa created_at ajungea la Intl.DateTimeFormat și producea RangeError: Invalid time value. Excepția a fost reprodusă independent cu un rând fictiv de cont.

Remediere: identitate React distinctă pentru fiecare pagină administrativă, formatare sigură a datelor lipsă/invalide și o limită de eroare pentru conținut, astfel încât meniul să rămână utilizabil în cazul unei alte erori de randare.

`node --test frontend/tests/dateFormat.test.mjs`: 5 teste trecute, inclusiv rânduri fără dată și zile calendaristice în trei fusuri orare. Buildul imaginii frontend execută acum și aceste teste. Navigarea într-un browser și buildul React/TypeScript complet trebuie încă verificate în mediul local; dependențele frontend nu sunt disponibile în sesiune.

## Pachetul de populare MedFlow 100plus

SQL-ul sursă a fost adaptat pentru schema V7 în `db/seed/medflow_100plus`. Au fost validate offline 1.080 de cazuri unice, câte 360/categorie, manifestul celor 7.610 de înregistrări planificate, verificările dinaintea COMMIT și lipsa operațiilor destructive asupra tabelelor existente. Sintaxa YAML a serviciului opțional `populate` și sintaxa scripturilor de import au trecut.

Încercarea de executare a importului a fost refuzată la accesul Docker (`permission denied` pentru socket). Nu există rezultate confirmate ale inserării sau ale numărătorilor din baza efectivă. Comanda locală este `sh ops/import-medflow.sh`; scriptul rulează importul și verificările.

## Antrenare MedFlow AI Dataset 3000

Antrenare efectivă finalizată, folosind numai datele indicate și fișiere din proiect. TRAIN=2.250, VALIDATION=375, TEST=375. Vocabular de 3.278 de caracteristici; două modele TF-IDF + regresie logistică, pentru 3 categorii și 30 de profiluri. Ponderile sunt în `ai-service/artifacts/model.json.gz`, iar raportul complet în `ai-service/artifacts/evaluation.json`. Ambele au macro-F1 1,00 pe testul sintetic; nu este validare clinică.

Au trecut 11 teste de algoritm, integritate, separare, artefact și inferență, executate cu biblioteca standard în mediul Python creat în proiect. Reevaluarea artefactului înghețat reproduce metricile salvate. Sonda de inferență a măsurat circa 0,14 ms mediană pentru funcția modelului, fără HTTP/DB; nu certifică timpul întregii aplicații.

API-ul a fost adaptat să încarce modelul, însă testele HTTP și reconstruirea containerului rămân neexecutate. Instalarea bibliotecilor din PyPI nu a reușit; antrenarea a folosit nucleul local portabil, fără biblioteci din alte proiecte.

## Afișarea sugestiilor academice (guidance1)

Catalogul de prezentare a fost construit exclusiv din TRAIN, cu 30 de profiluri consistente. API-ul și backendul transmit/păstrează ipoteze, alternative, opțiuni de conduită/tratament și investigații. Ponderile modelului rămân nemodificate. Au trecut 13 teste ML, inclusiv ipoteze cu scor exploratoriu și verificarea sursei opțiunilor, și 9 teste frontend (date, parsare rezultate vechi/noi, evitarea randării obiectelor JSON). Sintaxa Java și Python a trecut.

Interfața are o singură etichetă Demo academic; detaliile tehnice și rezultatele istorice sunt pliate. Analizele vechi pot fi regenerate fără rescrierea istoricului. Pentru activare: `docker compose up -d --build ai backend frontend`. Buildul complet/HTTP/browser nu au putut fi rulate în sesiune; descărcarea dependențelor npm este blocată de DNS.

Actualizare consultații / date distincte (12.11.2026): analiza AI este afișată direct în consultație, cu generare/regenerare și istoric. Migrarea V8 corectează numele sintetice și adaugă context clinic din aceleași cazuri TRAIN, numai dacă textele originale nu au fost editate. Testele statice verifică 1080 contexte distincte, numele demo și cheia stabilă a importului repetat (3 teste). Cele 9 teste frontend existente trec. Docker refuză accesul la socket; migrarea PostgreSQL, compilarea frontend și interfața în browser nu au fost executate în această sesiune.

Dosare longitudinale și medicamente (12.11.2026): V9 + scriptul rerulabil de import pregătesc istorice pentru identitățile sintetice; cel puțin primele două vizite folosesc scenarii cu rezultate de laborator disponibile. Catalogul de 7 substanțe are surse NHS/NICE/SmPC; modelul separat de rutare a fost efectiv antrenat pe 14700 scenarii, selectat pe 3150 de validare și evaluat pe 3150 de test. Raportul și hash-urile sunt în `ai-service/artifacts/medication_evaluation.json`. Acestea sunt etichete sintetice după reguli, nu rezultate clinice. 13 teste de model/dataset, 10 teste de medicamente, 10 teste frontend și 6 teste de fixture trec (39 total); verificarea sintactică Java acoperă 45 surse. Testele HTTP FastAPI, compilarea completă Java/TypeScript, execuția PostgreSQL și browserul nu au fost disponibile. Accesul Docker este refuzat; localhost:3000 nu este accesibil din această sesiune. Nu s-a pretins popularea bazei aflate în execuție.

Calendar săptămânal (12.11.2026): pagina Disponibilitate afișează medicii cu locuri libere în ziua/săptămâna selectată, căutare/specialitate, bandă de zile derulabilă și navigare între săptămâni. Alegerea efectivă a orei este în fereastra de programare, cu programul zilei și butoane pentru intervalele libere. Actualizare la 30 secunde, la revenirea în fereastră și după modificări. Administrarea brută a intervalelor este într-o secțiune separată, încărcată la deschidere.

Verificat: 14 teste Node (inclusiv navigare calendaristică/DST, eliminarea orelor trecute, filtre și ordonare), 8 verificări executate pe calculatorul Java de intervale (suprapuneri parțiale, limite, intervale scurte, miezul nopții, dubluri), sintaxă Java în 46 de fișiere. Testele Mockito ale serviciului au fost adaptate pentru interogarea calendarului. Compilarea React/TypeScript și Maven nu au putut fi rulate: dependențele nu sunt instalate, iar registrul npm răspunde ENOTFOUND. PostgreSQL local a fost încercat într-un cluster izolat din `.cache` al proiectului; inițializarea a fost refuzată de restricția de memorie partajată. Migrarea V10 și interfața în browser rămân de verificat la pornirea Docker.

Referință funcțională publică: https://www.reginamaria.ro/programare-online-aplicatie și lista publică de medici cu „Vezi program”. Interfața păstrează stilul MedFlow; nu s-a pretins accesul la portalul privat Regina Maria.

Revizie funcțională/portal, 12.11.2026: documentul original a fost citit integral din calea furnizată explicit. Vezi `REVIZIE_PORTAL_2026_11_12.md`. Au trecut 52 de teste locale: 18 Node, 8 de fixture/migrare, 13 pentru modelele existente și 13 pentru medicamente/simptome scurte. Sintaxa a 46 de surse Java este validă. Patru teste Java au fost adăugate pentru autorizarea filtrării și notificările rezultatelor; acestea rămân de executat prin Maven în build. Nu s-au rulat buildurile complete, testele HTTP FastAPI sau browserul. Modelul suplimentar a fost efectiv antrenat în această sesiune; raportul păstrează spliturile și hash-urile. Nu s-a executat o migrare nouă și nu s-a declarat conformitate de producție.
