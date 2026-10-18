# SistemaFinanceiro — SoftDarf

Aplicativo Windows para acompanhar patrimônio, operações brasileiras e americanas, opções, criptomoedas, proventos e apuração de DARF.

## Requisitos

- Node.js 22.12 ou superior (recomendado: Node.js 24).
- JDK 17 ou superior, com `JAVA_HOME` configurado.
- Windows para executar e empacotar o aplicativo desktop.

## Executar

```powershell
npm ci
npm run jar
npm run desktop
```

O aplicativo inicia o backend em uma porta local disponível e abre a janela após confirmar que ele está pronto. Cada conta precisa fazer login; sessões expiram em 12 horas e são encerradas ao sair ou reiniciar o backend. Senhas antigas continuam aceitas e têm o hash atualizado após o login.

Para executar apenas o backend:

```powershell
cd wheel
.\mvnw.cmd spring-boot:run
```

## Testar

```powershell
npm test
npm run test:dependencies
cd wheel
.\mvnw.cmd test
cd ..
npm run jar
npm run test:system
```

Os testes de backend usam H2 em memória. O teste de sistema inicia outro banco em memória e uma janela oculta do Electron com perfil separado, cria contas fictícias e verifica cadastro, login, aporte, cripto, navegação, privacidade e isolamento entre contas. As respostas de cotação são simuladas nesse teste; as carteiras e a autenticação usam o backend real.

O teste de dependências usa um proxy local fictício para verificar downloads, exceções `NO_PROXY`, validação SHA-256 e rejeição de arquivos alterados. Ele não acessa a internet.

## Dependências de empacotamento

O projeto fixa `global-agent` em 4.1.3 somente para versões de `@electron/get` anteriores à 5. Essa versão remove a cadeia vulnerável `roarr`/`sprintf-js`, mantendo a API de proxy usada pelo electron-builder. A regra fica registrada em `overrides` e no arquivo de dependências, sem editar manualmente `node_modules`.

O GitHub Actions verifica a auditoria com `npm audit --audit-level=low`, bloqueando qualquer vulnerabilidade conhecida que a ferramenta classifique como baixa ou superior.

## Gerar o aplicativo Windows

```powershell
npm run dist
npm run dist:portable
```

Os resultados são criados em `dist/`. O Java também precisa estar instalado no computador que executará o aplicativo.

## Dados e backups

O banco de desenvolvimento fica em `wheel/data/`. No aplicativo empacotado, instalações anteriores e o banco original são reconhecidos antes de usar a pasta de dados do aplicativo. Faça backup antes de substituir uma instalação; consulte [SALVAR_E_RESTAURAR.md](SALVAR_E_RESTAURAR.md).

Bancos, comprovantes, notas de corretagem, backups e instaladores ficam fora do versionamento. Este repositório começa com uma versão limpa do código; o histórico local anterior não faz parte da publicação.

Consulte [RELATORIO_TESTES.md](RELATORIO_TESTES.md) para o escopo e os limites da revisão.
