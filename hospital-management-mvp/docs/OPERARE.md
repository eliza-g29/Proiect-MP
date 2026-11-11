# Operare locală și verificarea infrastructurii

## Actualizare și pornire

Din rădăcina proiectului:

```sh
docker compose up -d --build
```

Migrațiile V1–V7 se aplică succesiv. Nu modifica migrațiile deja aplicate și nu șterge volumele pentru actualizare. V4 generează intervale de lucru pentru zilele programărilor deja existente; administratorul configurează apoi programul viitor. O instalare nouă primește intervale demonstrative pentru medicii inițiali.

Conturile demo își păstrează parola. JWT-urile vechi fără versiune sunt respinse după actualizare: autentifică-te din nou. Noile conturi și conturile recuperate au schimbarea parolei obligatorie.

```sh
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 ai
docker compose logs --tail=100 backup
```

Frontendul și API-ul expun porturile locale 3000/8080 numai pe 127.0.0.1. Healthcheck-ul API probează DB, iar backendul poate porni fără AI. Pentru a porni numai funcțiile de bază:

```sh
docker compose up -d --build db db-setup backend frontend
```

HOSPITAL_TIMEZONE este implicit Europe/Bucharest și se aplică JVM-ului backend și sesiunilor PostgreSQL. Orele programărilor sunt locale unității, fără offset în schemă/API. Înregistrările istorice nu sunt convertite automat când schimbi fusul; o asemenea schimbare necesită plan de migrare separat.

## Parole și acces DB

POSTGRES_PASSWORD aparține contului de migrare. APP_DATABASE_PASSWORD este pentru rolul `hospital_app`, utilizat la operațiile normale. `db-setup` acordă DML și utilizarea secvențelor, fără dreptul de a modifica istoricul Flyway. Scriptul poate fi reexecutat după restaurare sau schimbarea parolei aplicației.

JWT_SECRET trebuie să aibă minimum 32 de octeți; schimbarea lui invalidează toate tokenurile. Nu utiliza secretele sau conturile demo în producție. Fișierul `.env` nu este copiat în imaginile aplicației.

## Backup zilnic

Serviciul `backup` pornește după DB și backend sănătos. Face un dump consistent PostgreSQL, copiază documentele și publică atomic arhiva criptată numai după succes. Documentele aplicației sunt append-only; un fișier nou primește altă cheie de stocare.

Volume:

- `postgres_data`: baza de date;
- `medical_documents`: fișierele active;
- `backup_archives`: arhive `.tar.gz.age` și momentul ultimei reușite;
- `backup_keys`: cheia privată age, necesară restaurării.

Păstrează o copie securizată separată a cheii de restaurare. Pierderea volumului cu chei poate face arhivele irecuperabile. Aceeași gazdă nu constituie protecție împotriva pierderii întregii gazde; este necesară și o politică de copiere separată, stabilită de operator. Nu este configurată ștergerea automată a arhivelor: monitorizează spațiul și stabilește retenția înainte de utilizarea reală.

```sh
docker compose exec backup ls -lh /backups
docker compose logs --tail=100 backup
```

Healthcheck-ul semnalează dacă ultima reușită depășește 25 de ore; eșecul jobului determină repornirea containerului. Frecvența și recuperarea efectivă trebuie probate; configurația nu este un rezultat de test.

## Restaurare — numai într-un mediu de test sau mentenanță aprobată

Această procedură **înlocuiește tabelele și datele DB** cu arhiva selectată. Nu este executată automat. Testeaz-o întâi pe o instalare separată care are volumele/copii și cheia potrivite. Nu încerca restaurarea în timp ce backendul sau jobul de backup scriu.

```sh
# La un mediu nou, creează mai întâi DB și rolul aplicației.
docker compose up -d db db-setup

# Oprește scrierile, păstrând DB pornită.
docker compose stop frontend backend backup

# Numele trebuie să fie al unei arhive existente; comanda cere explicit înlocuirea.
docker compose --profile maintenance run --rm --no-deps restore medflow-TIMESTAMP.tar.gz.age --replace-existing-data

# Reaplică drepturile rolului aplicației, apoi repornește.
docker compose run --rm --no-deps db-setup
docker compose up -d backend frontend backup
```

Scriptul decriptează/verifică arhiva, copiază întâi fișierele și restaurează DB într-o singură tranzacție. Un eșec al copierii nu modifică DB; un eșec al pg_restore anulează tranzacția. Nu șterge fișierele deja existente; după succes pot rămâne fișiere fără referință în snapshot, care trebuie investigate separat. Verifică autentificarea, numărul înregistrărilor și descărcarea unor documente după restaurare. Procedura nu a fost testată end-to-end în sesiunea de modificare.

## HTTPS local opțional

```sh
sh ops/generate-local-tls.sh
docker compose -f docker-compose.yml -f compose.https.yml up -d --build
```

Pagina este `https://localhost:3443`. Certificatul local este autosemnat; browserul nu îl consideră automat de încredere. Portul HTTP 3000 redirecționează spre HTTPS în această configurație. În producție sunt necesare certificate și terminare TLS gestionate corespunzător; această configurație nu securizează automat traficul dintre toate serviciile.

## Verificarea obiectivelor nefuncționale

- Performanță: `ops/benchmark.py` folosește MEDFLOW_TOKEN furnizat prin mediu și verifică operații uzuale concurente. Nu salvează parola/tokenul în surse.
- Disponibilitate: `ops/uptime.py` este o sondă care trebuie rulată din exteriorul serviciului supravegheat. Raportul este pe eșantioanele colectate, nu pe intervale lipsă.
- AI: `ai-service/evaluate.py` evaluează modelul înghețat pe cele 375 de cazuri TEST sintetice și marchează explicit lipsa validării clinice.
- Reziliență AI: oprește numai serviciul AI într-un mediu de test; paginile de bază trebuie să funcționeze, iar solicitarea analizei să afișeze o eroare controlată. Repornește apoi AI.

Nu există rezultate confirmate pentru SLA, sarcină, criptarea stocării active, restaurare sau conformitate juridică. Vezi matricea [CERINTE_CF_CNF.md](CERINTE_CF_CNF.md).
