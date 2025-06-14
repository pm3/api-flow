# API-Flow Dokumentácia

## Prečo API-Flow?

Microservice architektúra je založená na princípe **request/response**, kde je každý request spracovaný tak, aby používateľ získal jednoznačnú odpoveď – buď úspešnú, alebo chybovú. Tento proces funguje podobne ako webová transakcia, pri ktorej je cieľom dosiahnuť výsledok v primeranom čase, zvyčajne do 45 sekúnd. Kvalitný microservice by mal byť bezstavový, čo znamená, že nesmie uchovávať stav priamo v kontajneri. Namiesto toho by mal stav spravovať prostredníctvom externej databázy, Redis, alebo iného stavového zariadenia.

Bezstavový charakter microservice umožňuje jeho jednoduchosť a škálovateľnosť. Aj keď je microservice bezstavový, samotný stav aplikácie musí byť riadený v externom úložisku, aby sa zaručilo správne fungovanie systému.

---

## Rôzne prípady použitia

### Cron Job

Interný cron job predstavuje stavovú operáciu, čo je v rozpore s bezstavovým charakterom microservice. Ak beží viacero kontajnerov súčasne, je potrebné riešiť problém s paralelným prístupom pomocou globálneho zámku. Rovnako, ak nebeží žiadny kontajner, môže cron job zlyhať, a preto nie je zaznamenané, že úloha nebola vykonaná. Okrem toho sa musí brať do úvahy aj reportovanie chýb, čo znamená, že systém musí vedieť, kde a ako tieto chyby zaznamenať.

Externý cron job rieši tieto problémy tým, že je vytvorený ako webová služba, ktorú môže volať externý cron démon. Výhodou je, že všetky volania sú zaznamenané, vrátane úspešných a neúspešných pokusov. Keď je nasadených viacero inštancií, systém soft-semaforom zabezpečí, že úlohu vybaví iba jedna inštancia. Ak je vyžadované prísne zabezpečenie, že úloha bude spustená iba raz, je potrebné implementovať globálny zámok priamo v microservice.

### Asynchrónna komunikácia: Webová služba volajúca ďalšie služby

Pri asynchrónnej komunikácii môže webová služba volať ďalšie služby, ktoré fungujú paralelne, čo môže predĺžiť dobu behu. Tento proces už nie je bezstavový, pretože počas jeho behu je potrebné uchovávať stav jednotlivých krokov. V prípade reštartu kontajnera by totiž proces nemohol pokračovať tam, kde skončil. Navyše, ak služba volá ďalšie služby s limitovanou konkurenciou alebo ak sa vyžaduje paralelné volanie s neskorším spojením odpovedí, vzniká zvýšená zložitosť procesu.

### Broker s Queue/Topic

Architektúra s brokerom, ktorý využíva **queue** alebo **topic**, predstavuje ďalšiu úroveň komplexity. Tento prístup zahŕňa vlastný komunikačný protokol a používa externé knižnice, čo prináša aj odlišný spôsob autorizácie a materializácie alebo dematerializácie dát v porovnaní s tradičnými webovými službami. Pre správne fungovanie systému je potrebné špecificky riešiť logovanie, sledovanie aktivity (tracing) a metering (meranie výkonu a spotreby zdrojov).

---

## Čo je API-Flow?

API-Flow je aplikácia navrhnutá na riadenie background procesov v microservices architektúre. Jeho hlavnou úlohou je **materializovať stav spracovania jednotlivých flow a zabezpečiť jeho vizualizáciu**; každá bežiaca inštancia flow sa nazýva **case**. Tento case obsahuje vstupné parametre a má definovaný **workflow** – proces, podľa ktorého postupne spúšťa jednotlivé úlohy. Každý case má zoznam krokov (steps), ktoré sa vykonávajú sériovo jeden za druhým.

Kroky môžu byť rôzneho typu, buď **single** (jednoduchý krok vykonávaný raz) alebo **multi** (krok s iterátorom, ktorý vracia zoznam položiek, pre každú položku sa vykoná celý krok). Každý krok obsahuje zoznam workerov, pričom worker predstavuje definíciu HTTP volania webovej služby (zahŕňa method, link, headers a body). Bežiaca inštancia workera sa nazýva **task**.

Všetky workery v rámci jedného kroku sa spúšťajú paralelne. Worker môže byť nastavený s podmienkou `where`, ktorá určuje, či sa konkrétny task má vykonať alebo nie. Z každého case sa vytvára **trace mapa** všetkých volaní, čo umožňuje monitorovanie priebehu a alarmovanie chybových volaní.

---

### Typy volania Workera

API-Flow podporuje niekoľko typov volaní:

1. **Synchronné volanie webovej služby**: Ide o štandardné volanie, kde sa request posiela a čaká sa na odpoveď.
2. **Asynchrónne volanie s callback-om**: Pri tomto type volania obsahuje prvý request callback linku v hlavičke. Keď webová služba dokončí prácu, odošle POST s výsledkom na callback linku.
3. **Asynchrónne volanie cez queue**: Pri tomto type sa request uloží do queue a následne ho spracováva sidecar, ktorý volá cieľovú webovú službu. Sidecar potom pošle výsledok späť do flow ako callback.

API-Flow týmto spôsobom ponúka robustný prístup k riadeniu asynchrónnych procesov, zabezpečeniu paralelného vykonávania a monitorovaniu stavu jednotlivých úloh v rámci microservices architektúry.

# Dokumentácia konfigurácie flow

Táto dokumentácia popisuje dátové objekty používané v API-Flow, vrátane ich atribútov a účelu.

## 1. `FlowDef`

Trieda `FlowDef` definuje hlavný objekt flow, ktorý obsahuje základné informácie o flow a jeho krokoch.

### Atribúty

- **`code`**: 
  - Typ: `String`
  - Popis: Unikátny identifikátor flow.

- **`auth`**: 
  - Typ: `String`
  - Popis: Informácie o autentifikácii, ktoré sa použijú pri vykonávaní flow.

- **`steps`**: 
  - Typ: `List<FlowStepDef>`
  - Popis: Zoznam krokov (`FlowStepDef`), ktoré definujú jednotlivé časti flow.

- **`labels`**: 
  - Typ: `Map<String, String>`
  - Popis: Mapa s dodatočnými informáciami alebo metadátami o flow.

- **`response`**: 
  - Typ: `Map<String, Object>`
  - Popis: Mapa, ktorá obsahuje expression pre vyskaldanie odpovedi z workerov.

---

## 2. `FlowStepDef`

Trieda `FlowStepDef` definuje jednotlivé kroky v rámci flow. Každý krok môže obsahovať workerov, ktorí vykonávajú HTTP požiadavky.

### Atribúty

- **`code`**: 
  - Typ: `String`
  - Popis: Unikátny identifikátor kroku.

- **`itemsExpr`**: 
  - Typ: `String`
  - Popis: Výraz, ktorý generuje zoznam položiek pre tento krok (napr. iterátor).

- **`workers`**: 
  - Typ: `List<FlowWorkerDef>`
  - Popis: Zoznam workerov (`FlowWorkerDef`), ktorí sú súčasťou tohto kroku.

---

## 3. `FlowWorkerDef`

Trieda `FlowWorkerDef` definuje worker, ktorý vykonáva konkrétnu HTTP požiadavku v rámci kroku flow.

### Atribúty

- **`code`**: 
  - Typ: `String`
  - Popis: Unikátny identifikátor workera.

- **`path`**: 
  - Typ: `String`
  - Popis: Cesta, na ktorú sa worker pokúsi vykonať HTTP požiadavku.

- **`pathExpr`**: 
  - Typ: `String`
  - Popis: Výraz, ktorý určuje dynamickú cestu pre HTTP požiadavku.

- **`method`**: 
  - Typ: `String`
  - Popis: Typ HTTP metódy (napr. `GET`, `POST`, atď.) používaný pri požiadavke.

- **`headers`**: 
  - Typ: `Map<String, String>`
  - Popis: Mapa s hlavičkami HTTP požiadavky.

- **`params`**: 
  - Typ: `Map<String, Object>`
  - Popis: Mapa parametrov, ktoré sa posielajú v rámci požiadavky.

- **`where`**: 
  - Typ: `String`
  - Popis: Podmienka, ktorá určuje, či sa má worker vykonať.

- **`labels`**: 
  - Typ: `Map<String, String>`
  - Popis: Mapa s dodatočnými informáciami alebo metadátami o workerovi. Labels sa zapisu do tracing.

- **`blocked`**: 
  - Typ: `boolean`
  - Popis: Indikuje, či je worker synchrónny alebo asynchrónny.

- **`timeout`**: 
  - Typ: `Integer`
  - Popis: Časový limit (v sekundách) pre vykonanie požiadavky.

- **`whereFalseResponse`**: 
  - Typ: `Object`
  - Popis: Dáta, ktoré sa nastavia do odpovede workera, ak je podmienka `where` nepravdivá. Ak je tento parameter nastavený, response code bude 200. Ak nie je nastavený, response code bude 406.

---

## Príklad YAML konfigurácie flow

Nasleduje príklad konfigurácie flow v YAML formáte:

```yaml
code: flow
steps:
  - code: step1
    workers:
      - code: worker1
        path: POST
        headers:
          header1: header1
        params:
          a: 1 # konstantný parameter
          $a: case.params.a # výraz
      - code: workerWithWhere
        path: POST
        where: case.params.shouldRun
        whereFalseResponse:
          message: "Worker bol preskočený, pretože where podmienka bola nepravdivá."
          dovod: "Podmienka nesplnená"
        headers:
          header1: header1
        params:
          a: 2
  - code: step2
    itemsExpr: case.assets
    workers:
      - code: worker2
        path: POST
        headers:
          header1: header1
          $header2: case.created #expression
        params:
          a: 1 # konstantný parameter
          $a: case.params.a # expression
```
Táto dokumentácia poskytuje prehľad o dátových objektoch a ich atribútoch, ktoré sú základom pre spracovanie flow v API-Flow aplikácii.

---

## Používanie výrazov (Expressions)

Na manipuláciu s dátami sa používa OGNL výraz. Výraz je možné použiť pri workeroch na položkách `$path`, `where`, `headers` a `params`.

Pokiaľ ide o `headers` a `params`, ide o mapu. Ak sa názov parametra začína symbolom `$`, jeho hodnota je považovaná za výraz. V opačnom prípade ide o statickú vlastnosť.

Ak je v mape iba jedna vlastnosť s názvom `$`, výstup nie je mapa, ale hodnota vyhodnotená z výrazu z parametra value.


