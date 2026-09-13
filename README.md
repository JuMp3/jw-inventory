# jw-inventory — Inventario Biblioteca

Web app per gestire l'inventario delle pubblicazioni di una biblioteca condivisa tra più
**congregazioni**: l'elenco delle pubblicazioni e le quantità sono unici e comuni a tutti.

## Funzionalità

| Area | Cosa fa | Chi |
|------|---------|-----|
| **Pubblicazioni** | Elenco con ricerca, copertina, widget `−` / `+` / numero esatto per registrare uscite ed entrate | tutti |
| | Censimento, modifica (codice, titolo, immagine), eliminazione | Admin |
| **Movimenti** | Storico di ogni variazione (entrata, uscita, rettifica, inventario) con utente e congregazione | tutti |
| **Inventario** | Sessione mensile: si contano le copie presenti, la app mostra le differenze; alla chiusura le quantità contate sostituiscono quelle a sistema (le pubblicazioni non contate restano invariate). Report storici | tutti (annullamento: Admin) |
| **Notifiche email** | Invii programmati: una volta (data), ogni giorno, ogni settimana, ogni mese (giorno X; se il mese è più corto, l'ultimo giorno). Destinatari multipli in CCN scelti con autocompletamento tra gli utenti esistenti (della propria congregazione; il Super Admin tra tutti), invio di prova | tutti |
| **Utenti** | Creazione/modifica/eliminazione utenti Admin e Utente; nessuno può disattivare, eliminare o cambiare ruolo al proprio account | Admin (propria congregazione), Super Admin (tutte) |
| **Congregazioni** | Creazione, rinomina, eliminazione (solo se senza utenti) | Super Admin |

### Ruoli

- **Super Admin** — unico, definito da configurazione (`SUPERADMIN_EMAIL` / `SUPERADMIN_PASSWORD`), senza congregazione; non può essere creato un secondo Super Admin né modificato/eliminato dalla UI. Ha tutti i permessi degli Admin.
- **Admin** — più di uno per congregazione; gestisce pubblicazioni e utenti della propria congregazione.
- **Utente** — inventario e quantità; nessun censimento pubblicazioni né gestione utenti.

### Visibilità notifiche

| Notifica creata da | Super Admin | Admin della stessa congregazione | Utenti della stessa congregazione | Altre congregazioni |
|--------------------|-------------|----------------------------------|-----------------------------------|---------------------|
| **Super Admin** | vede e modifica | — | — | — |
| **Admin** | vede e modifica | vedono e modificano | vedono in sola lettura | — |
| **Utente** (personale) | vede e modifica | — | — (solo il creatore la vede e modifica) | — |

- La congregazione di una notifica è quella del creatore al momento della creazione. Le notifiche degli Admin restano alla congregazione anche se il creatore viene eliminato; quelle personali vengono eliminate con l'utente. Eliminando una congregazione si eliminano le sue notifiche.
- **Destinatari**: solo utenti esistenti. Admin e Utenti scelgono tra gli utenti della propria congregazione, il Super Admin tra tutti. I destinatari già presenti in una notifica (es. aggiunti dal Super Admin) restano validi quando la modifica un Admin.

## Stack

Java 25 · Spring Boot 4.1 · Spring Security (form login) · Thymeleaf + Bootstrap 5 · PostgreSQL 18 + Flyway · Spring Mail · TestContainers.

## Avvio locale

```bash
docker compose up -d db mailpit     # PostgreSQL :5435, Mailpit SMTP :1027 / UI http://localhost:8027
MAIL_HOST=localhost MAIL_PORT=1027 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false mvn spring-boot:run
```

Apri http://localhost:8080 e accedi con `superadmin@jwinventory.local` / `superadmin123`.
Primo giro: crea una congregazione → crea un Admin → censisci le pubblicazioni.

Tutto in Docker: `docker compose up -d` (app su http://localhost:8081).

## Test

```bash
mvn clean test    # richiede Docker (TestContainers PostgreSQL 18)
# Windows / Rancher Desktop: DOCKER_HOST=npipe:////./pipe/docker_engine mvn clean test
```

## Configurazione

| Env var | Default | Descrizione |
|---------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5435/jwinventory` | Database |
| `DB_USERNAME` / `DB_PASSWORD` | `admin` / `password` | Credenziali DB |
| `SUPERADMIN_EMAIL` / `SUPERADMIN_PASSWORD` | `superadmin@jwinventory.local` / `superadmin123` | Super Admin (riallineato a ogni avvio) |
| `SUPERADMIN_NAME` | `Super Admin` | Nome visualizzato del Super Admin |
| `APP_TIMEZONE` | `Europe/Rome` | Fuso orario di date e programmazione notifiche |
| `EMAIL_ENABLED` | `true` | Se `false` le notifiche non vengono inviate (solo log) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | vuoti | SMTP |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | vuoti | Opzioni SMTP |
| `MAIL_FROM` / `MAIL_FROM_NAME` | `noreply@jwinventory.local` / `Inventario Biblioteca` | Mittente |
| `NOTIFICATIONS_SCHEDULER_ENABLED` | `true` | Abilita il job che invia le notifiche |
| `NOTIFICATIONS_POLL_INTERVAL_MS` | `60000` | Frequenza di controllo delle notifiche in scadenza |
| `JPA_DDL_AUTO` | `validate` | Lo schema è gestito da Flyway |
| `LOG_LEVEL` | `INFO` | Log applicativo |

## Note di comportamento

- **Concorrenza**: `+1`/`−1` e chiusura inventario usano un lock di riga sulla pubblicazione, quindi click simultanei di utenti diversi non perdono aggiornamenti.
- **Inventario**: un solo inventario aperto alla volta (vincolo DB). Alla chiusura la differenza è calcolata sulla quantità presente in quel momento.
- **Notifiche**: se l'app è spenta all'orario previsto, l'invio parte al riavvio (una sola volta). Un invio fallito viene mostrato come errore e non è ritentato: si passa alla ricorrenza successiva.
- **Eliminare una pubblicazione** cancella anche il suo storico movimenti e i conteggi d'inventario.
