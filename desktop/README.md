# SoftDarf Desktop

Este empacotamento transforma o sistema em um aplicativo Windows.

## Primeira vez

Instale o Node.js LTS no computador. Depois, na pasta `SoftDarf`, rode:

```powershell
npm install
```

## Testar como aplicativo

```powershell
npm run desktop
```

## Gerar o .exe

```powershell
npm run dist
```

O arquivo final aparece na pasta `dist`.

## Observacao

O app desktop ainda precisa do Java instalado, porque ele inicia o backend Spring Boot localmente. O banco fica salvo na pasta de dados do usuario do Windows.
