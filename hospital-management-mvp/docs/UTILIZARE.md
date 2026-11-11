# Ghid de utilizare MedFlow

Folosește numai date fictive. Pagina aplicației este `http://localhost:3000`; fișierul `MedFlow_PREVIEW.html` este o previzualizare mai veche și nu execută funcțiile backend.

## Administrator

1. Autentifică-te cu `admin@demo.ro` / `Demo1234!` în instalarea demonstrativă.
2. În **Conturi și drepturi**, creează contul cu rolul potrivit. Pentru medic/pacient poți selecta un profil existent; dacă îl lași necompletat, introdu și specializarea medicului sau data nașterii pacientului.
3. Comunică parola temporară printr-un canal verificat. Contul nou trebuie să o schimbe înainte să utilizeze aplicația.
4. Pentru personal auxiliar, selectează rolul **Personal medical** și completează specializarea, dacă este cunoscută.
5. În **Dosar medical extins → Departamente / Personal în departamente**, organizează structura unității. Aceste asocieri nu acordă singure acces la pacienți.
6. În **Calendar și disponibilitate**, adaugă intervalele medicilor și turele personalului auxiliar. Nu poți șterge sau scurta un interval al medicului dacă ar lăsa programări active în afara programului.
7. În **Colaborare și acces**, acordă unui cont sau unui departament acces la un pacient, cu motiv, expirare și drept READ sau WRITE. Revocarea oprește accesul bazat pe acea acordare; relațiile proprii medic–pacient continuă să se aplice.
8. În **Rapoarte**, selectează intervalul și generează raportul sau CSV-ul. În **Notificări**, poți transmite un anunț unui cont ori întregului spital.
9. Pentru recuperare, verifică identitatea utilizatorului și folosește **Conturi și drepturi → Recuperare acces**. Parola temporară nouă revocă sesiunile existente. Reactivarea unui cont dezactivat se face separat.

## Medic

- Vezi pacienții proprii/asociați și pe cei pentru care ai o acordare valabilă. Consultațiile altor medici sunt accesibile numai prin partajarea explicită a dosarului.
- Creează o programare în propriul calendar sau o consultație, cu/fără programare.
- Modifică datele clinice în limitele accesului. O acordare READ permite consultarea, nu actualizarea.
- Folosește **Vizibil pacientului** pentru a decide dacă o consultație sau o înregistrare poate fi afișată în contul pacientului. Acest marcaj nu înlocuiește procedurile de comunicare clinică.
- Analizele AI se solicită numai pentru propriile consultații. Consultă sursa și explicația; folosește **Acceptă / modifică / respinge după revizuire** pentru a deschide formularul de revizuire. Modificarea necesită text revizuit. Nu se creează automat diagnostic/rețetă.

## Personal medical auxiliar

- Consultă propriile ture în **Calendar și disponibilitate**.
- Poți înregistra pacienți și administra programările pacienților autorizați.
- Acordarea WRITE permite actualizarea informațiilor permise din dosar, de exemplu alergii, observații, laborator și documente. Diagnosticele, rețetele și revizuirile AI rămân rezervate rolurilor clinice autorizate ale prototipului.
- Pentru o consultație a altui membru al echipei este necesară o acordare valabilă asupra pacientului; pentru actualizare este necesar WRITE.

## Pacient

- Vezi numai propriul profil, propriile programări și înregistrările comunicate prin marcajul de vizibilitate.
- Creează o programare alegând medicul, ziua și un interval liber.
- **Reprogramează** sau anulează o programare viitoare activă. Programările anulate/finalizate nu se reprogramează.
- Consultă confirmările/modificările în **Notificări**. Mesajele sunt interne aplicației, nu emailuri sau SMS.

## Contul meu și erori uzuale

- Actualizează numele/emailul și schimbă parola din **Contul meu**. Parola nouă trebuie să aibă minimum 12 caractere și maximum 72 de octeți UTF-8.
- **Deconectare** șterge sesiunea locală și solicită revocarea sesiunilor contului pe server. Dacă serverul nu este disponibil, revocarea nu poate fi confirmată; tokenul local este totuși eliminat.
- La un conflict 409, reîncarcă datele înainte de a salva din nou. O altă persoană poate fi modificat programarea ori intervalul poate fi ocupat.
- Dacă nu există intervale libere, alege altă zi sau solicită administratorului configurarea programului.
- Un cont dezactivat sau o sesiune revocată necesită autentificare din nou; contul dezactivat trebuie reactivat de administrator.
- Credențialele demo și clasificatorul sintetic nu trebuie utilizate cu pacienți reali.
