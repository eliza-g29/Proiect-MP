# Revizie funcțională a portalului MedFlow

Document de referință analizat integral: `Cerinte_functionale_nefunctionale.docx`, furnizat explicit de utilizatoare la 12.11.2026. Acest raport descrie implementarea și verificările disponibile, nu o certificare de conformitate clinică, securitate, GDPR sau disponibilitate în producție.

## Referințe de experiență

Au fost consultate paginile publice [Regina Maria — noul ecran principal](https://www.reginamaria.ro/noutati-aplicatia-regina-maria), [Regina Maria — programare online](https://www.reginamaria.ro/programare-online-aplicatie) și [MedLife — funcțiile aplicației](https://www.medlife.ro/noutati/medlife-medlife-se-extinde-media-digitala-prin-lansarea-aplicatiei-pentru-iphone-ipod-touch). Din acestea au fost preluate tiparele funcționale: acțiuni rapide pentru pacient, următoarea vizită, acces la rezultate și istoric, alegerea medicului/datei/orei, confirmarea anulării. Nu s-a accesat portalul privat al vreuneia dintre rețele și nu se pretinde o reproducere exactă a ecranelor private.

## Modificări implementate

| Cerințe | Rezultat |
|---|---|
| CF-01–03 | Autentificare cu câmpuri goale și manager de parole, fără conturi/parole publicate în pagină. Administrarea conturilor, rolurile, actualizarea profilului și recuperarea asistată de administrator rămân disponibile. Nu s-a inventat un serviciu automat de email. |
| CF-04–07 | Dosar centrat pe pacient, cronologie a consultațiilor, diagnostice asociate, rezultate, tratamente, alergii și documente. Filtrare SQL explicită pe pacient, cumulată cu drepturile și izolarea existente. Căutare în secțiune, export CSV al rezultatelor și descărcarea documentelor autentificate. Actualizarea deschide editorul în contextul pacientului selectat. |
| CF-08–12 | Programări separate în viitoare/istoric/anulate/toate; filtre pe medic și dată, confirmare explicită înaintea anulării. Calendarul săptămânal și selecția orelor libere continuă să folosească rezervările existente și validările concurente. |
| CF-13–16 | Departamentele, conturile, programul personalului, dashboardul și rapoartele existente rămân accesibile. Indicatorul numărului total de medici este numit „Medici în echipă”, evitând confundarea cu disponibilitatea efectivă. |
| CF-17–20 | Model suplimentar pentru simptome scurte, antrenat efectiv pe TRAIN 2250 / validare 375 / test 375, cu normalizarea unor formulări uzuale românești la termeni deja existenți în sursă. Catalogul nominal de medicamente și excluderile rămân independente de scorul clasificării. Secțiunea medicamentoasă apare și când este goală, cu motivul concret; rezultatele din versiunile vechi cer regenerare. Backendul detectează explicit un serviciu AI vechi care nu trimite câmpurile medicamentoase. |
| CF-21 | Revizuirea AI deschide direct formularul pentru analiza aleasă. Opțiunile rămân consultative; nu se scriu automat diagnostice ori prescripții. |
| CF-22–24 | Colaborarea și controlul accesului existente sunt păstrate. Pacientul are ecran principal propriu cu următoarea programare și rezultate recente. Clopoțelul afișează notificările necitite. Salvarea unui rezultat/document vizibil pacientului notifică doar contul asociat, fără valori medicale în mesaj; rezultatele interne sau legate de consultații interne nu produc acest anunț. |

Referințele „proiect de facultate”, „academic”, „demo” din brand, subsol, ecranul AI și autentificare au fost eliminate din textul interfeței. Metadatele și rapoartele modelelor continuă să documenteze proveniența sintetică și lipsa validării clinice. Textele fixturilor au o prezentare curățată în dosar, fără modificarea înregistrărilor originale din DB.

## Limite și verificare

Au fost verificate prin teste locale logica de prezentare, izolarea rezultatelor pe pacient la nivel de helper, exportul CSV, integritatea artefactelor și generarea opțiunilor pentru formulări uzuale. Testele Java suplimentare verifică autorizarea filtrării și notificările pentru rezultate vizibile/interne. Verificarea sintactică Java nu înlocuiește Maven. În această sesiune nu a fost disponibilă compilarea completă React/Java, rularea PostgreSQL/Docker sau testarea în browser. Testele HTTP FastAPI sunt în imaginea AI și rulează la construire.

Obiectivele CNF privind 3 secunde, 10 secunde, sarcină concurentă, disponibilitate 99,5%, criptare completă, restaurare, GDPR și validare clinică nu sunt declarate îndeplinite prin schimbarea designului. Matricea `CERINTE_CF_CNF.md` și `VERIFICARE.md` descriu măsurile existente și ce rămâne de verificat.

## Aplicare

```sh
docker compose up -d --build ai backend frontend
```

Este necesară reconstruirea serviciului **ai**, pe lângă frontend/backend. Analizele deja salvate nu sunt rescrise; se folosește „Regenerează analiza”. Versiunea rezultatelor noi se termină în `medications2`.
