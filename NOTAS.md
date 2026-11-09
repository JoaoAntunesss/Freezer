# Freezer — notas

## Chave da IA (Groq) — IMPORTANTE
A chave antiga estava escrita no código. Revoga-a em console.groq.com (API Keys) e cria uma nova.
Depois abre `local.properties` (na raiz do projeto, NÃO vai para o Git) e acrescenta uma linha:

    GROQ_API_KEY=gsk_a_tua_chave_nova

Faz Sync do Gradle e compila. Sem a chave, a app funciona, só a IA (voz/foto) mostra erro.

## O que mudou (visual)
- Paleta nova "gelo" (azul/turquesa) em vez do roxo/rosa neon; tema Material completo (campos, diálogos, seletores).
- Artigos com faixa de estado (verde/amarelo/vermelho), contador -/+ e menu Editar/Apagar.
- Resumo da arca em mosaicos; barra de ocupação com cores por nível.
- Ecrã de entrada novo, estados vazios, ícone da app e widgets com a nova paleta.
- Tema de arranque escuro (sem flash branco). Nome da app: "Freezer". Versão 1.1.

## 1.2 — correções (sem mexer na estrutura dos dados no Firebase)
- IA (voz, talão, sugestões de compras) voltou a funcionar: os modelos antigos do Groq foram desligados. A app agora pergunta ao Groq que modelos existem e escolhe o primeiro disponível de uma lista, por isso não volta a partir quando um modelo for retirado.
- Artigos fora da validade já não são apagados sozinhos do stock. Ficam a vermelho ("Expirado") e no menu do artigo há "Deitar fora (desperdício)", que os regista nos Gastos.
- Talão: a foto é tirada em resolução completa (antes era uma miniatura e a IA quase nunca lia os preços).
- Leitor de códigos de barras: lê uma vez e fecha (antes podia disparar várias vezes seguidas).
- Preços, litros e capacidades aceitam vírgula (1,50).
- Corrigidos fechos inesperados ao tocar duas vezes em Apagar/Guardar/Partilhar nos diálogos.
- Quantidades nunca ficam abaixo de 1; cancelar a edição de um artigo já não deixa a data presa no formulário seguinte.

## 1.3 — atualizar dentro da app
- Ao abrir, a app vê se há versão nova no GitHub (ramo `atualizacoes`: versao.json + freezer.apk) e oferece "Atualizar": descarrega, mostra o progresso e abre o instalador do Android. Os dados ficam, estão no Firebase.
- Em Definições há "Procurar atualização".
- Na primeira vez o Android pede para autorizar o Freezer a "instalar apps desconhecidas".
- (Substituído na 1.4: a publicação passou para o GitHub Actions, ver abaixo.)

## 1.4 — publicação automática nos Releases
- O APK passa a ser compilado pelo GitHub Actions (`.github/workflows/apk.yml`) a cada push no `main` e publicado em Releases como `vX.Y` (`freezer.apk` + `versao.json`).
- A app procura atualizações em `releases/latest/download/`, já não no ramo `atualizacoes`.
- Todos os APKs são assinados com a mesma chave (`freezer.jks`, guardada fora do Git), por isso cada versão instala por cima da anterior, seja qual for o PC.
- Segredos no GitHub (Settings → Secrets and variables → Actions): `KEYSTORE_B64`, `KEYSTORE_PASS`, `KEY_ALIAS`, `GOOGLE_SERVICES_JSON` e `GROQ_API_KEY` (este último é opcional).
- O SHA-1 da chave tem de estar registado no Firebase para o login com Google funcionar.
- Para publicar uma versão nova: subir `versionCode`/`versionName` em `app/build.gradle.kts` e fazer push. O `publicar.ps1` foi retirado.
- Para compilar neste PC com a mesma chave, acrescentar ao `local.properties`: `KEYSTORE=C:/Users/.../freezer.jks`, `KEYSTORE_PASS=...`, `KEY_ALIAS=freezer`.
- Passagem da 1.3 para a 1.4: a chave mudou, por isso é preciso desinstalar uma vez e instalar o APK dos Releases (os dados ficam, estão no Firebase).
