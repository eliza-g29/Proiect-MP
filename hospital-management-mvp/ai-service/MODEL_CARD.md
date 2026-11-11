# Model MedFlow — dataset sintetic de 3.000 de cazuri

Modelul a fost **antrenat efectiv**, iar ponderile sunt salvate în `artifacts/model.json.gz`. Serviciul API încarcă acest artefact; nu mai antrenează un model de 36 de exemple la fiecare pornire.

## Date și separare

Sursă: pachetul `MedFlow_AI_Dataset_3000` indicat de utilizatoare. Fișierele originale nu sunt modificate. Copiile folosite sunt în `data/medflow_3000/`.

- TRAIN: 2.250 cazuri, singurul subset folosit pentru vocabular, IDF, ponderi și catalogul profilurilor.
- VALIDATION: 375 cazuri, folosite pentru alegerea epocii după log-loss.
- TEST: 375 cazuri, evaluate după înghețarea modelelor; nu sunt folosite pentru fitting sau alegerea epocii.
- Au fost verificate ID-uri distincte de caz/pacient/consultație și lipsa duplicatelor textuale normalizate între subseturi.
- Singura caracteristică de intrare este `clinical_input_text`. Coloanele `target_*`, identificatorii, split și label_source nu intră în vocabular.

Toate cazurile provin din aceleași 30 de șabloane sintetice. Separarea pe cazuri nu echivalează cu validare externă pe alte instituții, reguli generatoare sau pacienți reali.

## Algoritm și artefacte

TF-IDF pe cuvinte și perechi de cuvinte, normalizare Unicode, TF subliniar, normalizare L2, min_df=2 și maximum 12.000 de caracteristici. Vocabularul rezultat are 3.278 de caracteristici.

Două clasificatoare multinomiale de regresie logistică, antrenate prin SGD cu seed 42, clase ponderate echilibrat, regularizare L2 și oprire pe validare:

1. 3 categorii didactice: GENERAL_REVIEW, EXTENDED_REVIEW, ATTENTION_FLAG.
2. 30 de profiluri sintetice D01–D30. Aceste coduri nu sunt ICD/SNOMED.

Nucleul de antrenare/inferență este implementat în `learning.py`, cu biblioteca standard Python. Instalarea scikit-learn din PyPI a fost încercată, dar indisponibilă în sesiune; nu au fost folosite biblioteci din alte proiecte. Nu este un fine-tuning de LLM sau un model generativ. Formatul JSON comprimat este portabil și nu execută cod la încărcare.

- `artifacts/model.json.gz`: vocabular, IDF, ponderi, intercepturi, clase și catalog din TRAIN.
- `artifacts/evaluation.json`: parametri, istoric de validare, matrice de confuzie, precision/recall/F1 și SHA-256 al modelului.
- `artifacts/dataset_integrity.json`: numărări, distribuții, verificări și hashuri CSV.
- `artifacts/test_recheck.json`: reevaluarea modelului înghețat, identică cu rezultatele salvate.
- `artifacts/inference_benchmark.json`: timp pentru funcția de inferență, fără HTTP/DB.
- `artifacts/database_fixture_overlap.json`: legătura cu datele sintetice de populare DB.

## Rezultate obținute

| Sarcină | Validare macro-F1 | Test accuracy | Test macro-F1 |
|---|---:|---:|---:|
| Categoria didactică | 1,0000 | 1,0000 | 1,0000 |
| Profil sintetic D01–D30 | 1,0000 | 1,0000 | 1,0000 |

A fost verificată suplimentar forma scurtă de input, apropiată de aplicație (simptome + observații + antecedente): accuracy 1,0000 pentru categorie și 0,9947 pentru profil. Această verificare a fost făcută după alegerea modelelor și nu a fost folosită pentru retunare.

Aceste rezultate măsoară recunoașterea șabloanelor sintetice. **Nu sunt acuratețe clinică și nu validează diagnostic, tratament sau triaj real.** Scorurile nu sunt calibrate clinic. Rezultatele aparent perfecte sunt compatibile cu generarea foarte asemănătoare a exemplelor din toate subseturile.

În această sesiune, antrenarea numerică a durat aproximativ 10,1 secunde. Inferența pe 375 de exemple a avut mediană aproximativ 0,14 ms, fără HTTP, citirea DB sau pornirea procesului. Nu reprezintă un SLA al întregii aplicații; raportul păstrează valorile efectiv măsurate.

## Integrare API și protecții

Contractul POST `/triage` rămâne compatibil cu backendul: symptoms, observations, medicalHistory. Sunt respinse câmpuri suplimentare precum patientId. Health/model-info raportează versiunea `synthetic-medflow-3000-v1` și 2.250 de exemple de antrenare.

Datele necunoscute sau scorul insuficient produc UNCLASSIFIED_DEMO / NOT_ASSESSED. Interfața afișează până la trei ipoteze ordonate, inclusiv ipoteze exploratorii pentru descrieri scurte recunoscute. Scorurile clasificatoarelor nu sunt probabilități clinice; datele complet necunoscute cer completarea descrierii. Aceste praguri nu sunt limite clinice validate.

Opțiunile generale de conduită/tratament, investigațiile și direcționarea sunt preluate din metadatele TRAIN pentru ipoteza afișată, nu generate liber. Nu se creează automat prescripții sau diagnostice. Interfața păstrează o singură etichetă Demo academic, iar decizia rămâne umană. API-ul nu stochează cererile și nu trimite datele către un model extern.

Cele 1.080 de cazuri clinice din pachetul de populare DB aparțin TRAIN (zero din VALIDATION și TEST). Rezultatele obținute prin butonul AI pe acele consultații **nu** trebuie prezentate ca evaluare independentă.

## Reproducere

Din directorul `ai-service`, cu Python 3.12+:

```sh
# Antrenare și evaluare; nu necesită pachete externe.
python train.py

# Verificarea modelului înghețat, fără fitting.
python evaluate.py
python benchmark_model.py

# Teste de antrenare/inferență fără dependențe externe.
python -m unittest discover -s tests -p test_training.py -v
```

Pentru API sunt necesare dependențele din requirements.txt. În proiect, imaginea Docker include deja artefactul și rulează și testele FastAPI în build:

```sh
docker compose up -d --build ai
```

După reconstruire, o analiză nouă va avea versiunea nouă. Înregistrările AI vechi din PostgreSQL rămân nemodificate și își păstrează versiunea originală.

Au trecut 11 teste standard-library și reevaluarea modelului salvat. Testele HTTP FastAPI și reconstruirea containerului nu au putut fi executate în sesiune din cauza indisponibilității dependențelor/rețelei și accesului Docker.

## Prezentare academică: guidance1

Versiunea API `synthetic-medflow-3000-v1-guidance1` folosește aceleași ponderi antrenate, plus `artifacts/guidance_catalog.json`, construit exclusiv din câmpurile target ale TRAIN. Backendul păstrează acum lista de ipoteze, alternativele, conduita și investigațiile în detaliile analizei. Scorurile sunt ascunse implicit în UI; ipotezele exploratorii sunt marcate scurt, fără blocuri repetitive de avertizare.

Rezultatele vechi nu sunt rescrise. Butonul Regenerează analiza salvează o analiză nouă cu informațiile actuale. Pentru această schimbare trebuie reconstruite serviciile ai, backend și frontend. Au trecut 13 teste ML/ghidaj și 9 teste frontend fără dependențe; testele HTTP și buildul complet rămân de executat local.
