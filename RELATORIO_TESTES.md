# Revisão do sistema — 18/10/2026

## Resultado

34 testes de backend aprovados, além das suítes de cálculos/importação, sessão da interface, disponibilidade do backend e do teste com Electron real. O empacotamento Windows também foi concluído. Os oito alertas moderados restantes foram corrigidos em uma atualização das dependências, com teste adicional do componente de proxy.

## Correções

- Sessões autenticadas e revogáveis em todas as rotas de dados pessoais. Informar um `usuarioId` passou a ser insuficiente para ler, editar, agrupar ou excluir registros. Logout revoga a sessão; senhas e cadastros existentes continuam compatíveis.
- Bloqueio de sobrescrita por `POST` com identificador de registro existente e de transferência de operações entre contas.
- Deduplicação de operações/proventos e atualização de cripto/pagamentos protegidas por transação e bloqueio por conta, incluindo envios simultâneos.
- Compras e edições de ações americanas preservam mercado US e moeda USD.
- Símbolos cripto são normalizados antes de procurar a posição existente. Valores fora da precisão do banco são rejeitados antes de gravar; importações inválidas são revertidas por inteiro, incluindo saldos negativos e produto incompatível com o símbolo.
- Comprovantes por operação e pagamentos usam uma validação comum de tipo e tamanho. PDF e imagens raster são aceitos; HTML, SVG e anexos vazios são rejeitados.
- Datas opcionais são normalizadas antes de comparar liquidação e negociação. Limites de cadastro e valores financeiros não finitos são validados.
- Agrupamento rejeita contratos diferentes sem apagar os registros originais.
- Respostas atrasadas de histórico e carteira são descartadas quando o usuário troca de conta.
- Inicialização aguarda o backend responder com estado `UP`; erros HTTP e respostas inválidas não são tratados como disponibilidade. Navegação para outras páginas e abertura de novas janelas são bloqueadas no aplicativo.
- O arquivo de inicialização duplicado da raiz passou a encaminhar para `desktop/main.js`. Validações de cripto e comprovantes foram centralizadas.
- Correção do fundo da mensagem de sessão para acompanhar o tema claro.
- Electron e electron-builder atualizados, com arquivo de dependências travado. Verificação automatizada adicionada ao GitHub Actions.

## Validação

| Verificação | Resultado |
| --- | --- |
| Backend: login, compatibilidade de senha, CRUD, carteira, cripto, DARF, isolamento e concorrência | 34 testes aprovados |
| Interface: cálculos BR/US, opções, importação de notas, taxas, liquidação, compensações e pagamentos | Aprovada |
| Interface: token enviado somente ao backend e descarte de dados após trocar de conta | Aprovada |
| Inicialização: respostas UP/DOWN, HTTP 404/503 e conteúdo inválido | Aprovada |
| Electron real: cadastro, login, aporte, cripto, navegação, privacidade, recarga e duas contas | Aprovado |
| Geração do backend e pacote Windows com Electron 44.9.0 | Concluída |
| Downloads do empacotador: proxy, NO_PROXY, SHA-256 e rejeição de conteúdo alterado | Aprovados |
| Auditoria npm após corrigir as dependências | 0 vulnerabilidades conhecidas |

Os testes locais foram executados no Windows com Node.js 24 e JDK 26, compilando o backend para Java 17. O fluxo de integração contínua usa Java 17.

## Dependências e limites

A auditoria inicial apontava 20 alertas: 1 crítico, 15 altos e 4 moderados. Atualizar Electron e electron-builder eliminou os altos e críticos, mas deixou oito moderados derivados da cadeia `sprintf-js` → `roarr` → `global-agent`.

A correção final fixa `global-agent` em 4.1.3 por uma regra `overrides` restrita a `@electron/get@<5`. A versão 4.1 remove `roarr` e, consequentemente, `sprintf-js`, conforme as [notas oficiais de lançamento](https://github.com/gajus/global-agent/releases/tag/v4.1.0). Electron 44.9.0, electron-builder 26.17.0 e a interface de download do empacotador foram preservados. A instalação limpa reproduz a resolução corrigida pelo `package-lock.json`.

Foi acrescentado um teste com o downloader realmente resolvido pelo electron-builder. O teste usa servidores locais e verifica download por proxy, acesso direto por `NO_PROXY`, integridade SHA-256 e rejeição de um arquivo com checksum incorreto. O fluxo automático executa esse teste em Linux e Windows e passou a bloquear alertas baixos ou superiores com `npm audit --audit-level=low`.

A versão do Electron foi conferida no [histórico oficial de releases](https://releases.electronjs.org/). A consulta de vulnerabilidades foi feita pelo `npm audit` sobre o arquivo de dependências do projeto.

O teste visual usa respostas de mercado simuladas para ser reproduzível; autenticação e carteiras usam o backend real. Não depende da disponibilidade de cotações, PTAX ou proventos externos. Os cenários financeiros verificam o comportamento implementado, sem constituir validação jurídica de todas as regras tributárias. Aprovação dos testes não garante ausência de defeitos em cenários ainda não cobertos.

## Proteção dos dados na publicação

Foram usados bancos H2 em memória e perfis separados para as contas fictícias. O banco do usuário não foi usado nos testes.

O histórico antigo do Git contém arquivos locais de dados e comprovantes. Ele foi preservado localmente; a publicação em `SistemaFinanceiro/main` inicia um histórico limpo, contendo somente código, testes e documentação. Bancos, backups, notas, comprovantes, relatórios temporários e instaladores não fazem parte do envio.
