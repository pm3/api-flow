# API-Flow Dokumentácia

## Prečo API-Flow?

### Základy Microservice Architektúry: Request/Response
- **Microservice** je založený na princípe **request/response**.
- ![Request/Service/Response Diagram](#) - ilustrácia request -> service -> response.
- Request funguje ako webová transakcia – môže skončiť úspešne alebo chybne.
- Používateľ vidí, či request skončil úspešne alebo chybne.
- **Časové obmedzenie**: request by mal byť dokončený v rozumnom čase, zvyčajne do 45 sekúnd.
- **Bezstavový Microservice**: dobrý microservice by mal byť bezstavový.
  - Bezstavový znamená, že žiadny stav nie je priamo uložený v microservice.
  - Stav je spravovaný v externej databáze, Redise alebo inom stavovom úložisku.

---

## Prípadové scenáre

### Cron Job
- **Interný cron job** je stavová operácia, čím narúša bezstavovú definíciu.
  - **Problémy s paralelným behom**: Ak beží viac kontajnerov, je potrebné riešiť **globálny zámok**.
  - **Problémy s absenciou behu**: Ak žiadny kontajner nebeží, cron job sa nevykoná a neexistuje žiaden záznam o zlyhaní.
  - **Chybové stavy**: Ako a kde sú chybové stavy reportované?

- **Externý cron job**:
  - Cron job by mal byť vytvorený ako webová služba a spustený externým cron deamonom.
  - ![Cron/Service Diagram](#) - ilustrácia cron -> service.
  - **Výhody**:
    - Zalogovanie každého volania, úspešného aj neúspešného.
    - Soft-semafor: ak je viac inštancií, volanie spracuje iba jedna.
    - Ak je striktne potrebné spustenie iba raz, je nutné riešiť globálny zámok v rámci microservice.

### Asynchrónna komunikácia: Webservice, ktorý volá ďalšie služby
- ![Asynchronous Service Flow Diagram](#) - ilustrácia request -> service -> async service 1,2,3 -> response.
- **Časovo náročný proces**:
  - Musí udržiavať stav jednotlivých krokov (už nejde o bezstavový proces).
  - Po reštarte kontajnera nie je schopný pokračovať.
  - **Zložitosť pri konkurenčnom volaní**: problém s limitovanou konkurenciou a paralelným volaním služieb, pričom treba zabezpečiť správne spojenie odpovedí (joinovanie).

### Broker s Queue/Topic
- ![Broker/Consumer/Event Diagram](#) - ilustrácia broker <- consumer -> event.
- **Špecifické vlastnosti**:
  - Používa vlastný protokol a externé knižnice.
  - Rieši odlišný spôsob autorizácie.
  - Vyžaduje samostatné riešenie pre logovanie, tracing a metering.
  - Odlišný spôsob materializácie a dematerializácie dát v porovnaní s webovými službami.

---

## Čo je API-Flow?

API-Flow je aplikácia na riadenie background procesov v microservices architektúre. 

- **Materializácia spracovania jednotlivých flow**: Bežiaca inštancia flow sa nazýva **case**.
  - Case má vstupné parametre a definovaný workflow, podľa ktorého spúšťa jednotlivé úlohy.
  - **Case obsahuje zoznam krokov (steps)**, ktoré sa vykonávajú sériovo.

### Krok (Step) v rámci API-Flow
- **Typy krokov**:
  - **Single Step**: vykonáva sa raz.
  - **Multi Step**: iterátor, ktorý vracia zoznam položiek, pre každú položku spustí celý krok.
- **Krok obsahuje zoznam workerov**, kde každý worker predstavuje definíciu HTTP volania webovej služby (method, link, headers, body).
- **Beh workera** sa nazýva **task**.
- **Paralelný beh**: všetky workery v rámci kroku sa spúšťajú paralelne.
- **Podmienky pre worker**:
  - Worker môže mať definovanú `where` podmienku, ktorá určuje, či sa task má spustiť.
- **Trace mapy**:
  - Z každého case sa vytvára trace mapa volania všetkých taskov.
  - Trace umožňuje monitorovanie behu a alarmovanie chybových volaní v rámci case.

### Typy volania Workera
- **Synchronné volanie webovej služby**:
  - ![Synchronous Request/Response Diagram](#) - request -> service -> response.
- **Asynchrónne volanie s callback-om**:
  - ![Asynchronous Request/Callback Diagram](#) - request+callback -> service, service -> callback.
  - Prvé volanie obsahuje callback linku v hlavičke. Keď webová služba skončí, zavolá POST na callback linku s odpoveďou.
- **Asynchrónne volanie cez queue**:
  - ![Queue/Sidecar/Service Diagram](#) - request -> queue -> sidecar -> service.
  - Request sa zapíše do queue.
  - Sidecar vyberie requesty z queue a volá koncovú webovú službu.
  - Sidecar následne callback volaním odošle výsledok späť do flow.
