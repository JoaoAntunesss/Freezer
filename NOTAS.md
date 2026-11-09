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
