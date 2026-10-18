# Salvar e restaurar o SoftDarf

Este projeto agora tem dois caminhos de retorno:

- Git para salvar o codigo e as configuracoes versionadas.
- Backup ZIP para salvar tambem o banco local em `wheel/data`.

## Criar um novo backup no futuro

Feche o SoftDarf e rode na pasta do projeto:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backup-softdarf.ps1
```

O arquivo sera criado em `backups/`.

## Restaurar um backup ZIP

Primeiro faca uma simulacao:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/restore-softdarf-checkpoint.ps1 -BackupZip .\backups\NOME_DO_BACKUP.zip
```

Para aplicar de verdade:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/restore-softdarf-checkpoint.ps1 -BackupZip .\backups\NOME_DO_BACKUP.zip -Apply
```

Antes de substituir os arquivos, o script cria outro backup em `backups/antes-de-restaurar-*.zip`.

## Restaurar so o codigo pelo Git

Quando existir uma tag de checkpoint, o codigo pode voltar para ela com:

```powershell
git reset --hard NOME_DA_TAG
```

Esse caminho nao restaura o banco local em `wheel/data`; para isso use o backup ZIP.
