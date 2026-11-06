# Trouble Ticket API

Implementacja kontraktu [`api/trouble-ticket-api.yaml`](api/trouble-ticket-api.yaml) (w treści zadania
`devtask/trouble-ticket-api.yaml`) w Javie 21 i Spring Boot 4.1. Kontrakt jest tu źródłem prawdy: leży w repo bez zmian,
interfejsy kontrolerów i DTO są z niego generowane przy każdym buildzie, a testy sprawdzają każdą odpowiedź względem
tego samego pliku.

## Uruchomienie

Wystarczy Docker:

```bash
docker compose up --build --wait
```

Startują trzy kontenery: API na `localhost:8080`, PostgreSQL 18 (port niepublikowany) i lokalny serwer OAuth2
([mock-oauth2-server](https://github.com/navikt/mock-oauth2-server)) na `localhost:8090`. Ten ostatni wydaje podpisane
tokeny trzem klientom demo, a API weryfikuje je przez JWKS tak samo, jak robiłoby to z firmowym IdP. Dzięki temu
w repozytorium nie ma żadnych kluczy ani gotowych tokenów.

| `client_id` | tenant | scope |
|---|---|---|
| `partner-a` | `tenant-a` | `trouble-ticket:read trouble-ticket:write` |
| `partner-a-readonly` | `tenant-a` | `trouble-ticket:read` |
| `partner-b` | `tenant-b` | `trouble-ticket:read trouble-ticket:write` |

Token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8090/netia/token \
  -d grant_type=client_credentials -d client_id=partner-a -d client_secret=any \
  | sed -nE 's/.*"access_token" *: *"([^"]+)".*/\1/p')
```

albo w PowerShell:

```powershell
$TOKEN = (Invoke-RestMethod -Method Post -Uri http://localhost:8090/netia/token -Body @{ grant_type = 'client_credentials'; client_id = 'partner-a'; client_secret = 'any' }).access_token
```

Token trzeba pobierać przez `localhost`, nie `127.0.0.1`: serwer wpisuje do `iss` adres, pod którym go wywołano,
a API akceptuje tylko `http://localhost:8090/netia`.

W profilu `local` (używa go tylko Compose) baza dostaje cztery usługi. `987654321` i `987654322` to identyfikatory
z przykładów w kontrakcie (tenant-a), `987654329` jest nieaktywna, a `987654331` należy do tenant-b. Przykłady żądań
z YAML-a działają więc bez żadnych zmian.

Najszybciej sprawdzić całość tak:

```bash
scripts/smoke-test.sh
```

Skrypt przechodzi przez wszystkie pięć operacji i najważniejsze błędy (401, 403, 400 i trzy przypadki 404) i kończy się
kodem różnym od zera, jeśli coś nie gra. To samo puszcza CI. Do ręcznego klikania w IntelliJ jest
`http/trouble-ticket.http`.

Statusy `acknowledged`, `inProgress`, `resolved` i `rejected` ustawia w rzeczywistości SOZ, którego tu nie ma. Żeby je
przetestować, trzeba je wpisać do bazy:

```bash
docker compose exec postgres psql -U trouble_ticket -d trouble_ticket \
  -c "update trouble_ticket set status = 'rejected' where id = 'TT-2026-000001'"
```

Porty i hasło bazy zmienia się zmiennymi `APP_PORT`, `OAUTH_PORT` i `POSTGRES_PASSWORD`. `docker compose down -v`
sprząta wszystko razem z danymi.

Pod Windows skrypt i przykłady z heredoc działają w Git Bash albo WSL. Treść żądań idzie w nich przez stdin, bo Git Bash
psuje polskie znaki w argumentach wiersza poleceń. W PowerShell `curl` jest aliasem `Invoke-WebRequest`, prawdziwy
klient to `curl.exe`.

## Testy

```bash
./mvnw verify   # JDK 21 i Docker (Testcontainers); na Windows: mvnw.cmd verify
./mvnw test     # bez Dockera, tylko testy jednostkowe i warstwy web
```

Testy integracyjne (`*IT`) podnoszą całą aplikację na losowym porcie, na prawdziwym PostgreSQL (ten sam obraz co
w Compose) i z prawdziwie podpisanymi tokenami: w teście działa mały serwer JWKS z kluczem RSA generowanym w pamięci.
H2 i zamockowany dekoder nie powiedziałyby nic ani o blokadach, ani o weryfikacji JWT. Testy warstwy web
(`@WebMvcTest`) pokrywają szczegóły: walidację każdego pola, ścisłe parsowanie JSON-a i format błędów.

Na co warto zerknąć:

- `support/ContractValidator`: interceptor klienta testowego, który sprawdza każdą odpowiedź względem YAML-a (status,
  nagłówki, schemat, `additionalProperties: false`). W praktyce każdy test integracyjny sprawdza też kontrakt.
  Pomija ją tylko kilka testów, które celowo wychodzą poza kontrakt (inna wersja API, 405, 413, 415,
  health).
- `contract/ExposedEndpointsIT`: lista endpointów aplikacji musi być równa liście operacji w kontrakcie, nic ponad.
- `contract/ContractExamplesIT`: przykłady żądań z YAML-a wysłane bez zmian przez wszystkie operacje.
- `ticket/CreateTroubleTicketIT`: 20 równoległych create z tym samym `externalId` daje dokładnie jeden 201.
- `ticket/CloseTroubleTicketIT`: wyścig zamknięcia ze zmianą statusu po stronie SOZ. Test nie zgaduje timingów, tylko
  czeka, aż PostgreSQL pokaże w `pg_stat_activity`, że żądanie stoi na blokadzie.

Raport pokrycia z JaCoCo (testy jednostkowe i integracyjne razem) jest w `target/site/jacoco/index.html`. Progu nie
ustawiałem celowo: raport ma informować, a nie blokować builda.

## Jak to jest zbudowane

```
pl.netia.troubleticket
├── ticket
│   ├── domain        agregat TroubleTicket z notatkami, reguły statusów, porty
│   ├── application   przypadki użycia i granice transakcji
│   ├── persistence   Spring Data JPA, JDBC (sekwencja numerów, inwentarz usług)
│   └── web           kontrolery implementujące interfejsy wygenerowane z kontraktu
├── security          weryfikacja JWT, tenant, scope'y, odpowiedzi 401 i 403
└── web               requestId, format błędów, ścisły JSON
```

Kontrolery implementują interfejsy wygenerowane przez openapi-generator, więc ścieżka, metoda albo typ niezgodne
z kontraktem nie przejdą kompilacji. Ceną są sztywne sygnatury: zalogowanego klienta biorę z kontekstu Spring Security
(`CurrentClient`), bo parametru do metody dodać się nie da.

Encja `TroubleTicket` jest jednocześnie modelem domeny: ma zachowanie (`open`, `close`, `addNote`) i nie ma setterów.
Przy pięciu operacjach osobny model domenowy z mapowaniem na JPA podwoiłby kod bez żadnego zysku. Jeśli serwis urośnie
albo dojdzie integracja z SOZ, to pierwsze miejsce do rozdzielenia.

## Decyzje

**Tenant scope wyłącznie z tokenu.** Token musi być podpisany RS256 kluczem z JWKS i mieć właściwe `iss`, `aud`
z `trouble-ticket-api`, `exp`, `sub` i `tenant_id`. Spring domyślnie przepuszcza token bez `exp`, dlatego doszedł
własny walidator (`TokenClaimsValidator`). Każde zapytanie do bazy jest zawężone do tenanta, a cudzy zasób zwraca ten
sam 404 co nieistniejący, żeby nie zdradzać, że istnieje.

**Uprawnienia jako scope'y w łańcuchu filtrów.** `trouble-ticket:read` dla obu GET-ów, `trouble-ticket:write` dla
reszty. Sprawdzam je przed odczytem treści, więc klient bez uprawnień zawsze dostaje 403, a nie 400 czy 404. Kontrakt
nie mówi, za co jest 403, więc to minimalny sensowny model: konto tylko do odczytu (np. monitoring) i konto pełne.

**Idempotencja create na unikalnym indeksie `(tenant_id, external_id)`.** Bez blokad i bez sprawdzania pod lockiem.
Jeśli dwa takie same żądania przyjdą naraz, indeks przepuści jedno. Drugie dostaje wyjątek, a ponieważ PostgreSQL
przerwał już jego transakcję, zwycięzcę czytam w nowej i zwracam 200. Treści powtórzonego żądania nie porównuję
z oryginałem: kontrakt nie przewiduje 409, a przykład 200 pokazuje bieżący stan zgłoszenia.

**Zamknięcie pod blokadą wiersza, a nie `@Version`.** Sprawdzenie statusu i zapis muszą być atomowe, inaczej
zamknięcie mogłoby nadpisać `rejected` ustawione chwilę wcześniej przez SOZ. Przy blokadzie optymistycznej konflikt
trzeba by zwrócić klientowi, zwykle jako 409, a tego kontrakt nie ma. Kosztem jest czekanie przy równoległych
operacjach na tym samym zgłoszeniu, co w praktyce zdarza się rzadko.

**Notatka to osobny wiersz.** Dodanie notatki nie zapisuje wiersza zgłoszenia. Hibernate aktualizuje encję wszystkimi
kolumnami, więc zapis zgłoszenia przy dodawaniu notatki mógłby nadpisać równoległe zamknięcie. Efekt uboczny:
`updated_at` zgłoszenia nie uwzględnia notatek.

**Ścisły JSON.** Jackson 3 jest domyślnie bardzo wyrozumiały: ignoruje nieznane pola, `1.5` obcina do `1`, `"123"`
czyta jako liczbę, a `0` jako pierwszą wartość enuma. To się kłóci z `additionalProperties: false` i typami ze
schematu, więc wszystko to jest wyłączone i pokryte testami. Do tego Jackson 3 domyślnie sortuje pola odpowiedzi
alfabetycznie; to też wyłączyłem, żeby kolejność była taka jak w kontrakcie.

**Identyfikatory `TT-2026-000001` i `NOTE-000001`.** Format wziąłem z przykładów. Numery idą z sekwencji w bazie, więc
są unikalne przy wielu instancjach i da się je podyktować przez telefon. Minus: kolejne numery zdradzają, ile jest
zgłoszeń. Danych nie zdradzają, bo cudzy tenant dostaje 404. Alternatywą byłyby UUID-y.

**Błędy zawsze w formacie `Error` z kontraktu,** również 401 i 403 z filtrów bezpieczeństwa oraz błędy spoza Spring
MVC (przez `/error`). `requestId` w treści to ten sam identyfikator, który wraca w nagłówku `X-Request-Id` i trafia
do każdej linii logu, więc zgłoszenie od klienta od razu da się znaleźć w logach. Poza kodami z przykładów doszły
`STATUS_TRANSITION_NOT_ALLOWED` (400), `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONTENT_TOO_LARGE`,
`UNSUPPORTED_MEDIA_TYPE` i `INTERNAL_ERROR`. 406 idzie bez treści, bo klient i tak nie przyjmuje JSON-a.

**Spring Boot 4.1.** Boot 3.5 stracił darmowe wsparcie 30.06.2026, a 4.1 ma je do 31.07.2027. W praktyce oznacza to
Spring 7 i Jacksona 3, stąd uwagi wyżej o jego ustawieniach.

**Lokalny serwer OAuth2 zamiast klucza w repo.** Rozważałem Keycloaka, ale to ciężki kontener z długim startem
i dużą konfiguracją. Klucz deweloperski z gotowymi tokenami w repo byłby prostszy, tylko że w produkcji API i tak
pobiera klucze z JWKS. mock-oauth2-server daje dokładnie tę samą ścieżkę weryfikacji przy jednym lekkim kontenerze.

**Health na osobnym porcie (8081).** Publiczny port wystawia dokładnie kontrakt. Sondy liveness i readiness są na
porcie zarządzania, którego Compose nie publikuje.

**Metadane OAuth (RFC 9728).** Spring Security 7 sam publikuje `/.well-known/oauth-protected-resource`, a konfiguracja
nie przewiduje wyłączenia. Domyślny dokument twierdził, że usługa obsługuje tokeny powiązane z certyfikatem klienta,
co nie jest prawdą. Poprawiłem go: wskazuje serwer autoryzacji i scope'y. To jedyny adres poza kontraktem dostępny
bez tokenu.

## Jak zinterpretowałem luki w kontrakcie

- **Nowe zgłoszenie ma status `new`.** Kontrakt mówi, że odpowiedź „może” zawierać `acknowledged`, ale zgłoszenie
  potwierdza SOZ. Udawanie SOZ byłoby wymyślaniem logiki biznesowej.
- **Kolejność sprawdzeń w create:** 401 → 403 → 400 → istniejące zgłoszenie (200) → usługa (404) → 201. Ponowienie
  udanego żądania zawsze dostaje 200, nawet jeśli usługę w międzyczasie wyłączono.
- **„Usługa nie istnieje”** obejmuje usługę nieistniejącą, nieaktywną i cudzą. Wszystkie trzy przypadki dają ten
  sam 404.
- **Zamykanie:** `new`, `acknowledged`, `inProgress`, `resolved` → `closed`. Ponowne zamknięcie zwraca 200 bez zmian,
  żeby ponowienie po timeoucie nie kończyło się błędem. `rejected` zostaje `rejected` (400
  `STATUS_TRANSITION_NOT_ALLOWED`): to decyzja SOZ, a bez historii statusów zamknięcie by ją zatarło.
- **Przy zamykaniu nie tworzę notatki.** Żądanie ma tylko `status`. Notatkę „Klient potwierdził…” z przykładu traktuję
  jako ilustrację.
- **Notatki są dozwolone w każdym statusie,** także po zamknięciu. Kontrakt wymaga tylko, żeby zgłoszenie istniało.
  Czy proces obsługi w ogóle czyta takie notatki, to pytanie do biznesu. Gdyby trzeba je było zablokować, reguła
  trafi do `TroubleTicket.addNote`, a dodawanie notatki będzie brało blokadę wiersza, tak jak zamykanie.
- **Lista:** wszystkie zgłoszenia tenanta od najstarszego, bez `id`, bo schemat na nie nie pozwala. Parametry zapytania
  są ignorowane: v1 nie ma paginacji ani filtrów, a ta operacja nie przewiduje 400.
- **Walidacja trzyma się litery kontraktu.** Tekst z samych spacji przechodzi, bo `minLength: 1` na to pozwala.
  Odrzucam tylko to, czego PostgreSQL nie zapisze: znak U+0000 i pojedyncze surogaty. Kontrakt nie ogranicza długości,
  więc limit jest tylko na `externalId` (255 znaków, bo siedzi w unikalnym indeksie) i na całe żądanie (1 MB, 413).
- **`1.0` w `serviceId` jest odrzucane,** chociaż według JSON Schema to liczba całkowita. Standardowy Jackson nie
  odróżnia `1.0` od `1.5`, a własny parser liczb to przesada jak na przypadek, którego żaden klient nie wyśle.
- **Kilka błędów naraz** trafia do jednego `message`, posortowane po nazwie pola, bo `Error` nie ma pola na listę.
- **`Location` jest względny** (`/api/v1/troubleTicket/{id}`). `uri-reference` na to pozwala, a adres nie zależy od
  nazw hostów za proxy.
- **Czas w UTC z dokładnością do milisekund,** żeby odpowiedź na create i późniejszy GET zwracały identyczną wartość.

## Czego świadomie nie zrobiłem

- Integracji z SOZ. Statusy poza `new` i `closed` da się ustawić tylko w bazie.
- Paginacji i filtrów listy, bo kontrakt v1 je wyklucza.
- Prawdziwego inwentarza usług. Jest tabela `service_instance` za interfejsem `ServiceInventory`; docelowo byłaby tam
  integracja z inwentarzem (np. TMF638).
- Zdarzeń i outboxa, rate limitingu, TLS (to rola bramy API i ingressu) ani manifestów Kubernetes. Usługa jest na to
  gotowa: bezstanowa, z sondami, graceful shutdown i konfiguracją ze zmiennych środowiskowych.
- Metryk, tracingu i logów strukturalnych. To następny krok, niżej.

## Ryzyka i co dalej

- Lista bez paginacji rośnie razem z tenantem. Do czasu v2 warto monitorować jej rozmiar. Nieograniczona jest też
  liczba notatek w zgłoszeniu, a GET zwraca wszystkie.
- Create i zamknięcie można bezpiecznie ponawiać, dodawania notatki już nie: ponowienie po timeoucie doda drugą
  notatkę. W następnej wersji kontraktu dodałbym nagłówek `Idempotency-Key`.
- Idempotencja nie porównuje treści, więc pomyłka klienta w `externalId` skleja dwa różne incydenty.
- Nie ma `lock_timeout` ani `statement_timeout`. Jeśli SOZ zawiesi transakcję z blokadą zgłoszenia, zamknięcie będzie
  czekać i trzymać połączenie z puli. W produkcji ustawiłbym oba limity w PostgreSQL.
- Treść zgłoszeń może zawierać dane osobowe. Nie trafia do logów, ale polityki retencji nie ma.
- Przed produkcją dodałbym metryki (Micrometer i Prometheus), tracing (OpenTelemetry), logi w formacie ECS, SBOM
  i skanowanie zależności w pipeline, a do kontraktu `maxLength` i `pattern: '\S'` dla pól tekstowych.

## Repozytorium

Historia jest prowadzona w GitHub Flow: krótkie gałęzie scalane do `main` przez `--no-ff`, commity w stylu
Conventional Commits, wydanie oznaczone tagiem `v1.0.0`. CI w GitHub Actions buduje projekt z testami i puszcza smoke
test na środowisku z Compose. Do GitLab CI przenosi się to jeden do jednego.

Stos: Java 21, Spring Boot 4.1.2 (Spring 7, Hibernate 7, Jackson 3), PostgreSQL 18.6, Flyway, openapi-generator 7.27,
Testcontainers 2, Maven 3.10 przez wrapper.
