# Import MedFlow 100plus în schema curentă

Datele provin din pachetul indicat de utilizatoare, `MedFlow_Populare_BD_100plus`. Originalele nu sunt modificate. `manifest.json` păstrează SHA-256 al SQL-ului sursă și numerele planificate. Scriptul adaptat este complet și nu depinde de păstrarea directorului original în Downloads.

## Rulare

Din rădăcina proiectului, cu Docker Desktop pornit și backendul actualizat:

```sh
sh ops/import-medflow.sh
```

Echivalent:

```sh
docker compose --profile seed run --rm populate
```

Dacă schema este veche, actualizează întâi backendul:

```sh
docker compose up -d --build backend
```

Importul cere schema V7. Nu șterge volumele și nu rula scriptul V1 original direct în baza actuală: programările SCHEDULED necesită intervale de lucru.

## Ce se importă

| Tabel | Înregistrări planificate din pachet |
|---|---:|
| hospitals | 100 |
| doctors | 400 |
| patients | 1.450 |
| app_users | 420 |
| appointments | 2.400 |
| encounters | 1.080 |
| ai_analyses | 720 |
| audit_events | 1.040 |
| **Total** | **7.610** |

În spitalul contului `admin@demo.ro` sunt planificate 100 de medici, 250 de pacienți, 400 de programări, 180 de consultații, 120 de etichete AI, 120 de conturi și 240 de evenimente. Celelalte date aparțin celor 100 de spitale fictive separate și nu sunt vizibile în acel cont.

Se adaugă și intervalele de lucru necesare noilor programări active. Dacă ziua nu are intervale existente care s-ar suprapune, se folosește programul demonstrativ 08:00–18:00. Dacă există alte intervale, se păstrează și se adaugă numai un interval liber compatibil; o incompatibilitate determină rollback. Programările, conturile și intervalele deja existente nu sunt rescrise.

Pachetul nu conține date structurate pentru toate tabelele dosarului extins. Simptomele, observațiile și evaluările se păstrează în consultații. Nu sunt transformate automat în diagnostice confirmate, rețete sau rezultate clinice validate.

## Garanții ale scriptului și limite

- O singură tranzacție pentru import și verificările obligatorii; la eroare, înregistrările inserate de acea rulare se anulează.
- Nu există ștergeri, actualizări ale înregistrărilor existente, eliminarea constrângerilor sau dezactivarea triggerelor.
- Identificatorii logici MFSEED și emailurile sunt folosite la rerulare pentru evitarea duplicatelor. Un lock tranzacțional serializează rulările acestui import.
- Verificările de completitudine și de disponibilitate sunt înainte de COMMIT; verificările suplimentare afișează numerele și relațiile după import.
- O coliziune cu date modificate anterior poate opri importul. Nu este rezolvată prin suprascriere sau anulare automată a datelor existente.
- Secvențele BIGSERIAL pot avansa chiar dacă tranzacția este anulată; asta nu reprezintă înregistrări importate parțial.

Datele sunt fictive. Noile conturi din pachet folosesc parola demonstrativă indicată de sursă, `Demo1234!`; conturile existente își păstrează parolele și starea. Conturile API create normal au separat politica de parolă temporară a aplicației.

Etichetele din `ai_analyses` au versiunea `seed-fixture-synthetic-1.0`: sunt etichete de referință ale setului sintetic, **nu predicții calculate de serviciul AI**. Nu au fost folosite la antrenarea sau evaluarea modelului aplicației. Butonul de analiză poate crea ulterior un rezultat separat.

După import reîncarcă pagina aplicației. Nu este necesară ștergerea datelor sau repornirea bazei pentru afișarea lor.

## Starea execuției în sesiune

Au fost validate offline cele 1.080 de cazuri distincte, distribuția de 360/categorie, adaptarea celor două fluxuri de programări și structura tranzacțională. Accesul la API-ul Docker a fost refuzat de mediul de execuție; **importul efectiv și numerele finale din DB nu sunt confirmate**. Rulează comanda de mai sus în terminalul local pentru executare.

Actualizare V8: cei 100 de medici și 250 de pacienți din spitalul demo au combinații distincte de nume. Cele 1080 de contexte clinice sunt distincte prin simptome, durată, intensitate, observații și valori din TRAIN. Simptome individuale se pot repeta între pacienți. Migrarea actualizează numai consultațiile importate care păstrează exact textele originale; ID-urile și relațiile sunt păstrate. Nu se șterg înregistrări. Indicii unici protejează e-mailurile sintetice și identificatorii programărilor importate. Numele persoanelor reale nu au restricție de unicitate.

Pentru date deja importate, reconstruirea backendului aplică V8 automat. Pentru un import nou sau repetat, serviciul `populate` aplică și corecțiile sintetice. Verificarea include grupuri duplicate după identificatorii logici și conținutul consultațiilor.

## Dosare longitudinale V9

Migrarea `V9__synthetic_longitudinal_records.sql` și scriptul rerulabil `longitudinal.sql` adaugă 2–4 consultații istorice fiecărei identități sintetice din import și celor 5 pacienți ai demo-ului inițial, dacă există. Se folosesc 1500 de scenarii ambulatorii din TRAIN. Primele două vizite formează o evaluare și un control ulterior; cazurile suplimentare variază. Antecedentele personale/familiale și alergiile sunt coerente în interiorul istoricului generat. Alergiile existente nu sunt înlocuite. Nu se inventează rezultate pentru câmpurile goale din sursă.

Se adaugă diagnostice cu sistem explicit `MEDFLOW-SYNTHETIC` (nu coduri ICD pretinse), semne vitale, analize de laborator și unele planuri medicamentoase nominale. Dozele sunt declarate neprecizate în scenariu, nu fabricate. Nu fiecare pacient primește automat un medicament sau o alergie. Se evită conflictele medicamentoase acoperite de fixture. Nu se modifică înregistrările clinice existente. Marcajele `MFHIST-v1:<patient>:<episode>` împiedică dublarea la rerulare; ID-urile și toate relațiile rămân în baza existentă.

Aplicare din rădăcina proiectului:

```sh
docker compose up -d --build ai backend frontend
docker compose --profile seed run --rm populate
```

A doua comandă asigură popularea și pe instalări unde importul inițial a fost rulat după migrarea V9. Scriptul de verificare afișează numărul efectiv de dosare/analize și eventualele dubluri. În sesiunea de dezvoltare accesul la Docker și la serverul localhost nu a fost disponibil; SQL-ul nu a fost executat asupra bazei utilizatorului.
