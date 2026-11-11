# Trasabilitate: cerințe funcționale și nefuncționale

Referință: documentul `Cerinte_functionale_nefunctionale.docx` furnizat de utilizatoare. Aplicația rămâne un prototip academic cu date fictive. „Implementat” descrie codul și fluxul existent; nu înseamnă că toate testele de integrare sau criteriile de producție au fost validate. Rezultatele și limitele verificărilor sunt în [VERIFICARE.md](VERIFICARE.md).

## Cerințe funcționale

| ID | Stare în cod | Acoperire și limite |
|---|---|---|
| CF-01 | Implementat | Administratorul creează și administrează conturi ADMIN, DOCTOR, STAFF și PATIENT; autentificare JWT. Profilurile de medic/pacient pot fi existente sau create odată cu contul. |
| CF-02 | Implementat | Roluri fixe, verificări pe spital și resursă, relație medic–pacient, acordări READ/WRITE cu expirare. Nu există un editor arbitrar de permisiuni. |
| CF-03 | Implementat | Contul meu: nume, email și parolă. Recuperarea este asistată de administrator după verificarea identității; parola temporară trebuie schimbată. Dezactivarea, recuperarea și schimbarea parolei revocă sesiunile vechi. Nu există recuperare automată prin email. |
| CF-04 | Implementat | Înregistrare, căutare în interfață și actualizare a pacienților autorizați. Modificările sunt auditate și păstrează valorile anterioare. |
| CF-05 | Implementat | Dosar: consultații, diagnostice, observații, alergii, rețete, investigații și documente. Rezultatul AI este separat. |
| CF-06 | Implementat | Personalul autorizat actualizează dosarul în limitele rolului și acordărilor de acces. STAFF nu confirmă diagnostice/rețete sau revizuiri AI. |
| CF-07 | Implementat | Fișiere de maximum 10 MB, metadate în PostgreSQL și conținut în volum separat; descărcare autentificată. |
| CF-08 | Implementat | Pacientul creează, reprogramează și anulează propriile programări viitoare active. Înregistrările finalizate sau anulate nu se reprogramează. |
| CF-09 | Implementat | Calendar pe zi, filtre pe medic și administrarea programărilor accesibile. Medicul își gestionează propriile programări; STAFF are acces pe pacienți autorizați. |
| CF-10 | Implementat | Intervalele disponibile provin din programul medicului. PostgreSQL împiedică suprapunerile și rezervările în afara programului. Versiunea JPA a programării previne suprascrierea modificărilor concurente. |
| CF-11 | Implementat | Consultații create și actualizate, asociate pacientului și medicului; programarea este opțională. |
| CF-12 | Implementat intern | Confirmarea, modificarea, transferul și anularea produc notificări persistente pentru conturile asociate și autorul operației. Nu sunt trimise emailuri, SMS sau notificări push. |
| CF-13 | Implementat | Conturi, profiluri și specializări ale medicilor, specializări STAFF, departamente și asocieri cu perioadă de valabilitate. |
| CF-14 | Implementat | Administratorul creează/actualizează/șterge intervale concrete pentru medici și ture STAFF. STAFF își consultă propriile ture; pacienții consultă intervalele libere ale medicilor. Programul săptămânal al medicilor sintetici este materializat la cererea calendarului; excepțiile și închiderile manuale sunt păstrate. Turele STAFF se configurează explicit. |
| CF-15 | Implementat | Dashboard filtrat după drepturile contului, cu pacienți, medici, programări și analize AI accesibile. |
| CF-16 | Implementat | Rapoarte administrative pe maximum 366 de zile: pacienți noi, programări, consultații, AI, distribuție pe medic și status; export CSV. |
| CF-17 | Implementat demonstrativ | Serviciul AI primește simptome, observații și maximum 10 note istorice accesibile. Nu sunt transmise identificatoare structurate ale pacientului. Textul liber poate conține ceea ce a introdus utilizatorul; în acest prototip sunt admise numai date fictive. |
| CF-18 | Implementat consultativ; fără validare clinică | Ipoteze, conduită și investigații din TRAIN, plus un catalog de 7 substanțe cu surse și un model separat de rutare. Clasificator suplimentar pentru simptome scurte. Sunt afișate explicit motivele absenței/excluderii medicamentelor. Nu se generează doze sau rețete automat; eficacitatea clinică nu este validată. |
| CF-19 | Parțial / demonstrație | Clasificare de prioritate exclusiv didactică, marcată DEMO. Nu este triaj medical și nu schimbă automat programările. |
| CF-20 | Implementat demonstrativ | Sunt afișate explicația, versiunea, semnalul de date insuficiente și textul sursă păstrat la momentul analizei. |
| CF-21 | Implementat în flux | ACCEPTED, MODIFIED, REJECTED, NEEDS_REVIEW, autor și moment. MODIFIED cere textul revizuit. Acceptarea nu creează automat diagnostic sau rețetă. În prototip, ADMIN păstrează și drepturile clinice demonstrative din arhitectura inițială; în producție trebuie separate de administrarea tehnică. |
| CF-22 | Implementat | Administratorul acordă acces temporar unui cont medical sau unui departament, cu motiv, READ/WRITE și revocare. Apartenența la departament trebuie să fie valabilă. Nu se partajează între spitale. |
| CF-23 | Implementat | Interfață web responsive pentru propriile programări și propriul dosar. Consultațiile și înregistrările cu patient_visible=false sunt ascunse pacientului, inclusiv la descărcare directă. Datele existente rămân vizibile implicit până la modificarea explicită de către personal. |
| CF-24 | Implementat intern | Inbox pentru utilizatori și anunțuri administrative către un cont sau către întregul spital; marcarea notificărilor ca citite. |

## Cerințe nefuncționale

| ID | Stare | Măsură și ce mai trebuie verificat |
|---|---|---|
| CNF-01 | Țintă nemăsurată | `ops/benchmark.py` măsoară operațiile uzuale și semnalează depășirea a 3 secunde. Rezultatul depinde de infrastructură și volumul datelor. |
| CNF-02 | Timeout configurat; țintă nemăsurată | Conectare AI 2 s și citire 7 s; aceste limite de I/O nu constituie o garanție de 10 s pentru întregul flux. Necesită măsurare. |
| CNF-03 | Controale implementate; test de sarcină restant | Aplicație stateless, conexiuni DB gestionate de Spring și garanții SQL la rezervare. Sonda suportă cereri concurente; nu există rezultate de capacitate confirmate. |
| CNF-04 | Controale implementate | Izolare pe spital, autorizare pe resursă, separarea READ/WRITE și vizibilitate pentru pacient. Necesită audit de securitate al mediului final. |
| CNF-05 | Implementat pentru prototip | BCrypt, JWT semnat, verificarea stării contului la autentificarea fiecărei cereri, revocare prin versiune, schimbarea parolelor temporare. Nginx limitează încercările de login. IAM/MFA nu sunt implementate. |
| CNF-06 | Parțial | Configurație opțională HTTPS la frontend și backupuri criptate cu age. Implicit dezvoltarea folosește HTTP; traficul intern este HTTP, iar datele DB și fișierele active nu au criptare la nivelul aplicației. Operatorul trebuie să configureze stocare criptată și transport intern securizat înainte de utilizare reală. |
| CNF-07 | Implementat | Audit al accesărilor și modificărilor clinice; revizii pentru pacienți, consultații și înregistrări editate ale dosarului extins. Nu este un jurnal criptografic imuabil. |
| CNF-08 | Necesită evaluare externă | Minimizare, control acces, audit și separare logică. Aceste măsuri nu certifică respectarea GDPR; temeiurile, informarea, retenția, contractele și procedurile operatorului trebuie stabilite și evaluate separat. |
| CNF-09 | Țintă nemăsurată | Healthcheck backend și monitor `ops/uptime.py` cu raport lunar pe eșantioane, excluzând mentenanța marcată. Nu există dovadă de disponibilitate 99,5% sau infrastructură redundantă. |
| CNF-10 | Job configurat; execuție neverificată | Backup inițial și apoi la interval de aproximativ 24 h, arhivă criptată pentru DB și documente; healthcheck dacă ultima reușită depășește 25 h. Necesită monitorizare, spațiu suficient și o probă reală de backup. |
| CNF-11 | Controale și procedură implementate; probă restantă | Tranzacții, constrângeri DB, versiuni și istoric. Restaurarea copiază fișierele înaintea restaurării DB într-o tranzacție și nu șterge fișierele existente. Procedura trebuie testată într-un mediu separat. |
| CNF-12 | Implementat în interfață; evaluare UX restantă | Navigare pe roluri, etichete, formulare și mesaje. Necesită testare cu utilizatori reprezentativi. |
| CNF-13 | Implementat în CSS; verificare dispozitive restantă | Layout desktop/tabletă/mobil, meniuri și tabele cu derulare. Nu există rezultate ale unei matrice de dispozitive. |
| CNF-14 | Implementat în fluxuri | Etichete explicite, intervale disponibile, stări și validări în API/UI; avertismente DEMO și confirmare umană. Necesită evaluare UX. |
| CNF-15 | Arhitectură extensibilă; capacitate neverificată | API stateless, monolit modular, serviciu AI separat și hospital_id. Nu există autoscaling sau rezultate pentru volume mari; unele liste necesită paginare pentru producție. |
| CNF-16 | API documentat; compatibilitate de testat | REST/JSON, Bearer și CSV, cu documentația endpointurilor. Nu există integrare FHIR/HL7 implementată sau teste confirmate pe toate browserele. |
| CNF-17 | Implementat logic și în DB | Filtre, FK compuse cu hospital_id și teste negative. RLS nu este configurat. |
| CNF-18 | Implementat | Identitate, autorizare, operațiuni, dosar extins, persistență și AI în componente separate. |
| CNF-19 | Implementat | Migrații Flyway succesive V1–V7, imagini reconstruibile și teste în buildurile backend/AI. |
| CNF-20 | Implementat în documentație | README, ghid de utilizare, API, operare, verificare și această matrice. |
| CNF-21 | Implementat | Rezultatele sunt marcate consultative/demonstrative; nu emit automat diagnostice sau rețete și nu înlocuiesc decizia umană. |
| CNF-22 | Parțial | Antrenare efectivă pe 2.250 de cazuri, validare 375 și test 375, cu verificarea separării și metrici salvate (macro-F1 1,00 pe test pentru cele două clasificatoare). Toate subseturile provin din aceleași șabloane sintetice; nu există validare clinică independentă. Vezi ai-service/MODEL_CARD.md și ai-service/MEDICATION_MODEL_CARD.md. |
| CNF-23 | Implementat demonstrativ | Semnal pentru caracteristici necunoscute / scor insuficient și prioritate NOT_ASSESSED; afișare clară în interfață. Scorul statistic nu este probabilitate clinică. |
| CNF-24 | Implementat în arhitectură | Backendul nu depinde de pornirea AI, iar funcțiile de bază nu îl apelează. Analiza răspunde cu eroare controlată la indisponibilitate. Testul de oprire a AI rămâne de executat. |

## Criterii de acceptare înainte de declararea conformității

1. Buildurile frontend/backend/AI și testele trebuie să treacă cu versiunile declarate.
2. Migrațiile și testele SQL trebuie executate pe PostgreSQL 16, atât pe o bază nouă, cât și pe copia unei instalări existente.
3. Scenariile de utilizare din ghid trebuie verificate separat pentru cele patru roluri, inclusiv accesul negativ și două modificări concurente.
4. Trebuie demonstrată restaurarea unei copii într-un mediu separat, cu cheia corectă și verificarea fișierelor.
5. Timpii, capacitatea și disponibilitatea trebuie măsurate în condiții documentate. Configurația locală nu garantează un SLA.
6. Cerințele clinice CF-18/CF-19 și validarea clinică CNF-22 rămân neîndeplinite de clasificatorul sintetic. Nu se pot transforma în funcții clinice validate prin simpla modificare a etichetelor.

Revizie portal și cerințe, 12.11.2026: vezi [raportul actualizat](REVIZIE_PORTAL_2026_11_12.md) pentru ecranul pacientului, dosarul pe pacient, notificările, filtrarea programărilor și corecția opțiunilor medicamentoase.
