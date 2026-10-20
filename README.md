# PitStopHub Android

App Android nativo (Kotlin + Jetpack Compose) do PitStopHub: calendário, classificação, equipes e pilotos do
automobilismo. O app nasceu a partir do site (PWA) do PitStopHub, que vive em [`ailtoncja/pitstophub`](https://github.com/ailtoncja/pitstophub)
(privado) e está em <https://pitstophub.vercel.app>. Os dados das categorias sem API própria vêm do Supabase, alimentado pela
rotina diária do repositório [`ailtoncja/pitstophub-data`](https://github.com/ailtoncja/pitstophub-data).

A interface segue o design **Velocity Control** (exportado do Stitch): tema escuro, vermelho de corrida
`#E10600`, cortes diagonais, itálico e dados em fonte monoespaçada.

## Telas e recursos

| Tela | O que mostra |
| --- | --- |
| **Início** | Próxima corrida (ou todas do dia) com mapa do circuito e contagem regressiva, seletor das 14 categorias com ícones, líderes do campeonato |
| **Calendário** | Temporada completa: concluídas (vencedor + resultados), a próxima em destaque, as futuras, com horários |
| **Classificação** | Pilotos / construtores / equipes, cartão do líder, setas ▲/▼ de posições ganhas e a diferença da última corrida |
| **Grid** | Pilotos e equipes da categoria em cartões com foto, número, bandeira e pontos |
| **Piloto** | Foto, posição, pontos, vitórias, pódios, duelo interno com o companheiro, últimas etapas, telemetria da temporada, biografia e títulos |
| **Piloto campeão** | Perfil de carreira de campeões da F1 que já saíram do grid (carta, títulos, equipes, biografia) |
| **Equipe** | Carro, posição, pontos, diferença para a líder, pilotos, gráfico de evolução e biografia |
| **Como funciona** | Guia de cada categoria (formato do fim de semana, pontuação, classes, carros e regras), com atalhos no topo. Aparece como cartão na Início e na página da categoria, e o link "Como funcionam os pontos?" da Classificação abre direto na seção de pontuação |
| **Corrida** | Contagem regressiva, previsão do tempo (Open-Meteo), traçado oficial, curvas, recorde, zonas de frenagem/DRS, pneus, programação e resultado |
| **Campeões da F1** | Galeria com as cartas dos campeões mundiais, ordenável por títulos, recentes ou vitórias |
| **Busca** | Pilotos, equipes, corridas e categorias em um só lugar |
| **Conta** | Login e cadastro (Supabase Auth), perfil, favoritos com prioridade, onboarding, idioma |
| **Alertas** | Escolha o que notificar, notificação de teste e estado das permissões |
| **Menu lateral** | Todas as categorias, agrupadas como no PWA, com os mesmos ícones do site |

Idiomas: inglês e português do Brasil. O padrão segue o aparelho, e dá para trocar no perfil (a escolha também é
sincronizada com a conta).

### Conta, favoritos e sincronização com o site

O app fala com o **mesmo Supabase do PWA** (`/auth/v1` e a tabela `user_settings`), então os favoritos, a
prioridade, o idioma e as preferências de alerta são os mesmos no site e no app. As chaves seguem o mesmo
formato: `categoria::id` para o que se segue e `time::categoria::id` / `piloto::categoria::id` para a ordem de
prioridade. Sem login o app funciona normalmente (modo visitante); com login ele sincroniza em segundo plano e
guarda a sessão localmente.

### Notificações

O PWA usa Web Push, que precisa de um servidor. No app as notificações são **locais** (sem Firebase): um
`WorkManager` a cada 30 minutos atualiza o calendário e agenda alarmes (`AlarmManager`) para as corridas
próximas. As regras são as dos scripts `send-race-notifications` e `send-result-notifications` do PWA:

- **Dia da corrida**: aviso a partir das 08:00 locais (ou 2 horas antes, nas largadas cedo).
- **1 hora antes** e **na largada**.
- **Resultado**: na F1, as posições dos pilotos seguidos (ordem de prioridade); nas demais categorias, o vencedor.

Só avisa as categorias que você segue, e cada tipo (dia da corrida, 1 hora antes, largada, resultado) pode ser ligado ou desligado em Alertas. Como são alarmes
locais, o horário pode atrasar alguns minutos (economia de bateria do Android), e os resultados só chegam quando o
app consegue consultar a rede. Tocar na notificação abre direto a página da corrida.

### Extras do site

Os "easter eggs" do PWA também vieram: o tributo na página do Hamilton (toque 5 vezes rápido na foto) e o segundo,
de segurar a foto por 5 segundos.

## De onde vêm os dados

| Categoria | Fonte |
| --- | --- |
| F1 | Jolpica (`api.jolpi.ca`): calendário, vencedores, classificação, resultados e sessões |
| GT World Challenge (3 séries) e Endurance Brasil | `api-motorsports` (repositório `ailtoncja/api-motorsports`) |
| F2, F3, F1 Academy, WEC, IMSA, DTM, IndyCar, NASCAR, WRC | Tabelas `synced_races` / `synced_standings` do Supabase, **se a anon key estiver configurada**; senão, o catálogo estático |
| Todas | Catálogo estático (`assets/motorsport_data.json`) como base: nomes, fotos, cores e biografias |

Se uma busca ao vivo falhar, o app cai no catálogo estático e mostra um aviso, nunca uma tela vazia. Corridas com
data já passada viram "concluídas" mesmo que o catálogo as marque como futuras.

> **Atenção:** sem a `SUPABASE_ANON_KEY`, as categorias da terceira linha usam só o catálogo estático (os
> vencedores e a classificação delas ficam como na última atualização do PWA) e **o login, os favoritos
> sincronizados e a conta ficam desligados** (o app abre em modo visitante e o botão de entrar avisa).

## Como abrir e rodar

1. Abra a pasta no Android Studio e aguarde o Gradle Sync.
2. Edite o `local.properties` (está no `.gitignore`):

   ```properties
   SUPABASE_URL=https\://SEU-PROJETO.supabase.co
   SUPABASE_ANON_KEY=sua-anon-key
   VAPID_PUBLIC_KEY=
   ```

   São os mesmos valores do `.env` do PWA (`VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`). A anon key é pública
   por design (protegida por RLS) e chega ao código via `BuildConfig`.
3. Rode no emulador `Medium_Phone_API_37.0` ou em um aparelho com depuração USB.

Linha de comando (Windows):

```bash
./gradlew.bat :app:testDebugUnitTest   # testes unitários (clientes HTTP, regras de alerta, favoritos, estatísticas)
./gradlew.bat :app:lintDebug           # lint
./gradlew.bat :app:assembleDebug       # APK de debug
./gradlew.bat :app:assembleRelease     # APK minificado com R8 (assinado se houver keystore.properties)
```

### Testar login e sincronização sem o Supabase real

O `tools/mock-supabase.mjs` é um servidor local que imita `/auth/v1` e `user_settings`. Com o emulador:

```bash
node tools/mock-supabase.mjs                 # sobe em http://localhost:54321
adb reverse tcp:54321 tcp:54321              # o emulador enxerga o localhost do PC
./gradlew.bat :app:installDebug -PSUPABASE_URL=http://localhost:54321 -PSUPABASE_ANON_KEY=teste
```

O `src/debug` libera tráfego HTTP em texto claro só para `localhost`/`10.0.2.2`; a versão release exige HTTPS.

### Atualizar o catálogo exportado do PWA

Os catálogos (pilotos, biografias, circuitos, campeões) foram exportados do PWA e já estão em `app/src/main/assets`. Os
scripts `tools/export-web-data.mjs` e `tools/convert-champion-cards.mjs` leem o código do site na pasta irmã `..\pitstophub`
(`npm install` antes). Para mudar um texto ou foto, o mais simples é editar o JSON em `assets/` direto.

### Emulador no Windows

O emulador precisa do recurso **Plataforma do Hipervisor do Windows** (WHPX). Confira com
`emulator -accel-check`; deve aparecer "WHPX is installed and usable".

### Problema conhecido: "Unable to establish loopback connection"

Observado ao rodar o Gradle pelo shell do Claude Code nesta máquina: o JDK 25 (o JBR do Android Studio) falha em
`Selector.open()` ("Invalid argument: connect") quando o diretório temporário fica dentro de `AppData` (o padrão
do Windows). Com o tmpdir em `Documents` ou na raiz do usuário funciona. Não ocorreu no Android Studio aberto
normalmente. Se o Gradle Sync mostrar esse erro, aponte o tmpdir para fora de `AppData`:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=C:\Users\SEU_USUARIO\jt -Djdk.net.unixdomain.tmpdir=C:\Users\SEU_USUARIO\jt"
```

No Android Studio, as mesmas duas opções vão em `Help > Edit Custom VM Options`.

## Stack

| Item | Versão |
| --- | --- |
| Android Gradle Plugin | 9.4.1 (Kotlin embutido) |
| Gradle | 9.8.0 |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 (Material 3) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| applicationId | `com.pitstophub.app` |

Também: Navigation Compose (rotas tipadas), ViewModel, kotlinx-serialization, coroutines, OkHttp e Coil 3.
Versões centralizadas em `gradle/libs.versions.toml`.

## Estrutura

```
app/src/main/java/com/pitstophub/app/
  PitStopHubApp.kt, AppContainer.kt   # Application e injeção manual de dependências
  data/
    model/                            # modelos (espelham src/types.ts do PWA)
    remote/                           # Jolpica, api-motorsports, Supabase REST, Open-Meteo
    repo/                             # CategoryRepository, AuthRepository, UserSettingsRepository, mesclas
  domain/                             # tempo de corrida, estatísticas de temporada, configurações do usuário
  notifications/                      # regras (AlertPlanner/ResultPlanner), agendamento e notificações
  i18n/                               # idioma escolhido no app
  ui/
    theme/                            # cores, tipografia e formas do design system
    components/                       # barras, botões, chips, formulários, gráfico de linha
    home/ calendar/ standings/ grid/ drivers/ teams/ race/ champions/ account/ tributes/   # uma pasta por tela
tools/                                # exportação dos dados do PWA e servidor Supabase de teste
app/src/main/assets/                          # catálogos exportados do PWA (motorsport_data, driver_extras, f1_champions, circuits)
app/src/test/                                 # testes unitários
```

## Decisões e diferenças em relação ao design

- O design chama a marca de "APEX PRO"; o app usa **PITSTOPHUB**.
- O chip de categoria selecionado e o alternador usam texto escuro/claro de maior contraste (o design usa branco
  sobre salmão, que fica ilegível).
- O Compose não tem blur de fundo: os painéis de "vidro" são translúcidos com borda fina.
- Todas as telas usam o fundo `#000000` (nível 0 do DESIGN.md); o design alterna com `#121414`.
- O avatar do topo mostra a inicial da conta (ou um ícone de pessoa no modo visitante) e abre o perfil.
- Mapas de circuito só existem para a F1 (copiados do PWA). As outras categorias mostram o nome da série como
  marca d'água.
- `allowBackup=false`: o app não guarda nada que valha backup.

## Fora do escopo (por enquanto)

Web Push/FCM (as notificações são locais, veja acima), a intro 3D do PWA e as páginas de circuito para categorias
que não são a F1 (o site também só tem os dados detalhados da F1).

## Distribuir o APK

O app é distribuído como APK direto (sem Play Store). O material da Play Store (ficha, capturas, `.aab`, política de
privacidade, exclusão de conta) foi guardado em `..\pitstophub-playstore-arquivo`, com um `LEIA-ME.md` de como retomar.

- **Gerar:** `./gradlew.bat :app:assembleRelease` e pegar `app/build/outputs/apk/release/app-release.apk`. Precisa do
  `keystore.properties` (aponta para a chave em `..\pitstophub-keys`) e do `local.properties` com a anon key.
- **Assinatura:** use **sempre a mesma chave**. Se um dia o APK sair assinado por outra chave, quem já instalou precisa
  desinstalar antes de atualizar.
- **Backup da chave:** guarde uma cópia **offline** do keystore (`.jks`) e do `keystore.properties` (pendrive ou disco
  externo guardado fora do computador, ou cofre de senhas com anexo cifrado). Sem a chave não há como publicar
  atualizações para quem já instalou. Nunca coloque a chave ou as senhas no git, no Release ou em nuvem sem cifra.
- **Publicar:** cada APK compartilhado vira um **GitHub Release** (tag `vX.Y.Z` igual ao `versionName`) com o APK anexado e,
  nas notas, o SHA-256 do arquivo e o SHA-256 do certificado de assinatura:

  ```powershell
  # SHA-256 do APK
  Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
  # Assinatura valida + SHA-256 do certificado (apksigner fica em <Android SDK>\build-tools\<versao>)
  apksigner verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
  ```

  O certificado tem de ser **o mesmo em todos os Releases**; se mudar, algo está errado (chave trocada ou APK
  adulterado). Quem baixar pode conferir o hash do arquivo (`Get-FileHash` / `sha256sum`) e o certificado com o mesmo
  `apksigner verify --print-certs` antes de instalar.
- **Atualizar quem já instalou:** aumente o `versionCode` em `app/build.gradle.kts` a cada APK novo que for compartilhar.
- **Atualização dentro do app (desde a 1.3.2):** ao abrir, no máximo a cada 6 horas e sem travar a tela, o app consulta
  `releases/latest` deste repositório (`update/AppUpdater.kt`). Se a tag `vX.Y.Z` for maior que o `versionName`
  instalado, pergunta "Nova versão X.Y.Z disponível. Atualizar?". O APK só é baixado de
  `github.com/ailtoncja/pitstophub-android/releases/download/...` (e dos servidores de arquivos do GitHub para onde
  esse link redireciona), até 100 MB, para o cache privado; antes de instalar, o app confere o SHA-256 (campo `digest`
  do asset no GitHub e/ou a linha "SHA-256 do APK" das notas: se nenhum existir ou não bater, recusa) e se o certificado
  de assinatura é o mesmo do app instalado. A instalação passa pelo instalador do Android (com a confirmação de sempre);
  na primeira vez ele pede para liberar "instalar apps desconhecidos" para o PitStopHub. Por isso, cada Release precisa
  de **um** asset `pitstophub-X.Y.Z.apk` e do SHA-256 do APK nas notas, como acima.
- **Instalar:** baixar o APK do Release no celular, abrir e permitir "instalar apps de fontes desconhecidas" quando o
  Android pedir.

## Licenças

Fontes Anybody, Inter e JetBrains Mono (SIL Open Font License) em `res/font`; as licenças estão em
`assets/OFL-*.txt`. Dados da F1: Jolpica/Ergast.
