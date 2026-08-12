# Open Stream Mobile

App nativo em Kotlin para Android (celular/tablet): player **Media3/ExoPlayer**,
**workers dedicados** de metadados e **cache persistente** em Room.

Este projeto é um fork **touch-first** do app irmão para Android TV — mesma
base de código e arquitetura, com a camada de interface refeita para toque em
vez de D-pad. Ele não depende do app de TV nem o modifica; são dois projetos
Gradle independentes, cada um com seu próprio `applicationId`, para poder
instalar os dois lado a lado no mesmo aparelho sem conflito.

### O que muda em relação ao app de TV

- **Navegação por toque**: o rail lateral que só aparecia com foco de D-pad foi
  substituído por uma barra de navegação inferior, sempre visível, com no
  máximo 5 ícones por vez (o restante fica a um swipe de distância).
- **Tipografia e grade de pôsteres** redimensionadas para tela de celular, em
  vez do dimensionamento "de sofá" do app de TV.
- **Retrato e paisagem** têm layouts próprios para os temas com painel de
  destaque (Netflixy/Primefly): a altura do painel, do logo e o tamanho dos
  pôsteres se ajustam à orientação para sempre sobrar pelo menos uma fileira
  de cards visível e tocável.
- **Um toque mostra a sinopse, dois toques abrem o título** nesses mesmos
  temas — o equivalente por toque ao que o foco de D-pad faz no app de TV.
- **Seleção de tema** vive em Configurações, não mais num botão de ciclo na
  navegação.

```bash
./gradlew assembleDebug     # APKs por ABI em app/build/outputs/apk/debug
./gradlew assembleRelease   # com R8; poucos MB por ABI
```

Precisa de JDK 17+ e do Android SDK. O `local.properties` (não versionado)
deve apontar para o seu SDK local.

### Firebase e login Google

O login é feito via Firebase Authentication (Google) mais uma chave pessoal da
MDBList, vinculada depois da primeira entrada. Para rodar este projeto com sua
própria conta Firebase:

1. crie um projeto Firebase e ative **Authentication > Google**, cadastrando o
   SHA-1 da chave que assina seu build (debug e/ou release);
2. gere o `google-services.json` do seu projeto e coloque em
   `app/google-services.json` (não commitado neste checkout público);
3. crie o Realtime Database e ajuste `FIREBASE_BASE` em
   `core/network/.../ApiConfig.kt` caso a instância não use a URL padrão;
4. publique regras que restrinjam cada usuário ao próprio nó — a chave MDBList
   fica em `/users/{uid}/profile` e só o próprio UID deve poder lê-la; a
   sincronização de addons usa `/users/{uid}/addons` sob a mesma regra.

---

## Por que nativo

Três coisas que uma versão empacotada em WebView não conseguia fazer, e que
motivaram o projeto (herdadas do app de TV, valem igual aqui):

1. **Reprodução de vídeo.** O Media3 lida nativamente com HLS, DASH e os
   containers/codecs suportados pelo aparelho, com buffer adaptado a links de
   addons — sem depender do comportamento irregular de um `<video>` de WebView.
2. **Cold start.** A home vem do Room no primeiro frame, e a rede nunca está
   no caminho crítico.
3. **CORS.** As escritas de watchlist precisam de um proxy no navegador porque
   o mdblist responde 405 ao preflight OPTIONS. Um cliente nativo não tem essa
   regra.

---

## Módulos

```
:core:model      Tipos de domínio. Kotlin puro, sem Android.
:core:network    Retrofit/OkHttp: mdblist, TMDB, OMDb, Stremio. Cache de disco HTTP.
:core:database   Room. É a fonte da verdade de tudo que a tela lê.
:core:data       Repositórios cache-first, sessão em DataStore, workers.
:core:ui         Design system compartilhado (Compose + androidx.tv), com
                 dimensões e tipografia recalibradas para toque.
:player          Media3/ExoPlayer, buffer, failover, áudio e legendas.
:app             Telas, navegação, grafo de objetos.
```

Sem framework de DI. O grafo é raso o bastante para caber em construtores —
veja [`DataGraph`](core/data/src/main/kotlin/com/mdblisthub/tv/core/data/DataGraph.kt).

---

## O player nunca pergunta qual fonte usar (a não ser que você peça)

Ao dar play, o app pergunta a todos os addons instalados em paralelo e entrega
as fontes verificadas ao
[`PlaybackController`](player/src/main/kotlin/com/mdblisthub/tv/player/PlaybackController.kt)
assim que cada uma chega. Ele tenta a fila automaticamente; uma fonte morta,
token expirado ou erro de demux avança para a próxima sem interromper o usuário.

Sucesso exige o Media3 chegar a `STATE_READY`, não apenas aceitar uma URL. Se
todas as opções falharem, a mesma fila aparece para escolha manual. Também é
possível pedir a seleção manual desde o início pelo botão "Selecionar Fonte"
na ficha do título, em vez de esperar a cascata automática falhar.

Tudo isso acontece atrás de um véu com o fanart do título. Nove mirrors podem
ser testados e descartados sem que nada apareça além de "Preparando a
reprodução…".

---

## Cache e workers

O Room é a fonte da verdade. `observe*` sempre emite do banco, imediatamente;
`refresh*` escreve por cima em background. Nenhuma tela espera a rede.

A tabela de metadados é dividida em duas de propósito:

- `media` — o card, que o sync de listas já traz de graça. Barato de escrever
  para mil títulos de uma vez.
- `media_detail` — a ficha: elenco, notas, artwork, temporadas. Caro, vem de três
  APIs, e só vale a pena para títulos que alguém abre.

Assim o refresh noturno reescreve todos os cards sem tocar nas fichas que um
worker levou minutos para montar, e cada uma envelhece no seu próprio ritmo
([`CachePolicy`](core/data/src/main/kotlin/com/mdblisthub/tv/core/data/CachePolicy.kt)).

| Worker | Quando | O que faz |
| --- | --- | --- |
| `ListSyncWorker` | 6/6 h + ao abrir | Listas e seus itens → Room |
| `MetadataWorker` | 4/4 h, bateria ok | Hidrata 40 fichas por passada |
| `ArtworkWorker` | após cada sync | Pré-aquece os 8 primeiros pôsteres de cada fileira |
| `ResumeSyncWorker` | 3/3 h | "Continuar assistindo" |
| `CachePruneWorker` | 24/24 h | Descarta fichas órfãs |

Prefetch por foco/toque **não** é WorkManager. É a
[`MetadataPrefetcher`](core/data/src/main/kotlin/com/mdblisthub/tv/core/data/MetadataPrefetcher.kt),
com escopo de processo: só serve nos próximos segundos, e se o usuário seguir
adiante deve ser descartado. É o que faz abrir uma ficha parecer instantâneo.

---

## Tamanho

O player usa os codecs do Android e não carrega um motor multimídia nativo de
dezenas de megabytes — o release fica na casa de poucos MB por ABI. Os splits
por ABI permanecem para as pequenas bibliotecas transitivas; o APK universal é
o caminho mais simples para sideload em aparelho desconhecido.

---

## Versões travadas

`compileSdk` está travado na plataforma instalada na máquina de build. Se os
artefatos AndroidX de uma versão mais nova do `core`/`lifecycle` se recusarem
a compilar contra ele, é sinal de que exigem um salto de `compileSdk` e AGP
juntos — ajuste `gradle/libs.versions.toml` nos dois ao mesmo tempo, não um de
cada vez.

Kotlin 2.4 exige R8 9.1.29 ou superior. O `settings.gradle.kts` fixa esse R8
compatível enquanto o projeto permanece na versão do AGP declarada em
`gradle/libs.versions.toml`.
