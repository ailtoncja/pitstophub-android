# Product

## Platform

android

## Users
O mesmo público do site: fãs de automobilismo, principalmente brasileiros (interface em português e inglês), que acompanham mais de uma categoria ao mesmo tempo, não só a Fórmula 1. O app é o jeito principal de usar o PitStopHub no celular Android, e quem o tem quer saber, sem esforço, a próxima largada de cada categoria no seu fuso, receber o aviso na hora certa e consultar classificação, grid, perfis e "como funciona".

## Product Purpose
Reunir as 14 categorias (F1, F2, F3, F1 Academy, IndyCar, NASCAR, WEC, IMSA, DTM, GT World Challenge Europa/América/Ásia, Endurance Brasil e WRC) num app nativo: calendário, resultados, classificação, grid, perfis de piloto e equipe, página da corrida com circuito, clima e sessões, guia de regras por categoria e avisos locais de largada e resultado. Sucesso é a pessoa saber o que vem a seguir, ser avisada a tempo e entender o que está vendo.

## Positioning
Cobre séries que os apps de F1 ignoram (Endurance Brasil, WEC, IMSA, GT World Challenge, DTM, WRC) ao lado da F1, em português, com horários no fuso do aparelho, o guia "Como funciona" de cada categoria e avisos locais que não dependem de push de servidor. A mesma conta, favoritos e dados do site (pitstophub.vercel.app).

## Operating Context
- Código: Kotlin + Jetpack Compose, minSdk 26, targetSdk 37, repositório privado `ailtoncja/pitstophub-android`. Distribuído hoje como APK (versão 1.2.0); a publicação na Play Store está arquivada para o futuro.
- Dados: F1 ao vivo pela Jolpica; GT World Challenge e Endurance Brasil pela api-motorsports; demais categorias pelo Supabase (rotina diária do repositório `pitstophub-data`); clima pela Open-Meteo; catálogo estático embutido como base.
- Conta e favoritos: Supabase Auth e `user_settings`, compartilhados com o site.
- Avisos: locais, agendados com WorkManager (largada, minutos antes, resultado).

## Capabilities and Constraints
- Idiomas português (padrão) e inglês; horários no fuso do aparelho.
- Funcionalidades: categoria escolhida com Início, Calendário, Classificação e Grid; gaveta de categorias; páginas de piloto, equipe, corrida e campeões da F1; busca; favoritos com prioridade; avisos locais; "Como funciona" por categoria; perfil e login.
- Retirado de propósito, não reintroduzir sem o dono pedir: seção de notícias; push de servidor.
- Limites dos dados: classificação de WEC, IMSA, IndyCar, NASCAR e F1 Academy vem do catálogo estático e pode estar desatualizada; o sync diário às vezes traz nome genérico de etapa ("Rodada N").
- Easter eggs de homenagem (Thata, Nay, Lucas e Brunão) são parte do produto e mantêm os mesmos gatilhos.
- Projeto independente, sem vínculo com a F1, a FIA, equipes ou pilotos.

## Brand Commitments
Nome PitStopHub. Logo em escudo com capacete e carro. Vermelho de corrida #E10600 como cor de marca. O site acabou de ganhar um visual novo ("Reel": fundo preto, Archivo, branco como cor de interface, vermelho reservado), documentado no `DESIGN.md` do repositório do site; é referência disponível, não obrigação.

## Evidence on Hand
Mesmo conteúdo do site: 14 categorias com equipes, pilotos, fotos, circuitos (mapas de pista), desenhos de carro por tipo de categoria, biografias, títulos, regras e galeria de campeões da F1. Não existem depoimentos, números de audiência ou avaliações; nada disso deve ser inventado.

## Product Principles
1. A próxima corrida vem primeiro, no fuso de quem olha, sem rolar nem tocar.
2. Toda categoria é cidadã de primeira classe.
3. Explicar o esporte: regras e formato sempre à mão.
4. Dado honesto: o que é estático ou desatualizado é dito na tela.
5. Uma conta, um conjunto de dados: site e app mostram o mesmo.
6. O aviso certo na hora certa vale mais que abrir o app.
