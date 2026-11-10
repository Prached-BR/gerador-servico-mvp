# GERA 'servico' e-Carta com FTP Client

Aplicacao desktop Java para processamento de planilhas de servico e-Carta, geracao de arquivos padronizados e operacoes de envio/recebimento por FTP.

## Objetivo

Este projeto implementa um MVP voltado a pequenas operacoes que precisam:

- importar arquivos `.xls`, `.xlsx` e `.csv`;
- validar campos antes da geracao;
- gerar ZIPs de `servico` e `Resposta`;
- organizar arquivos em `ENVIAR`, `ENVIADOS` e `RECEBIDOS`;
- persistir configuracoes em arquivo criptografado local;
- apoiar o envio e a baixa de arquivos via FTP.

## Stack tecnica

- Java 21
- Maven
- Swing
- Apache POI
- Apache PDFBox
- Apache Commons Net

## Estrutura principal

```text
cdip_ecarta_ftp_client/
  pom.xml
  build_app.bat
  build_jpackage.bat
  src/main/java/br/com/cdip/ecartaftp/
    AppStateStore.java
    ConfigCrypto.java
    FtpClientService.java
    Main.java
    MainFrame.java
    MatrixType.java
    SpreadsheetProcessor.java
```

## Requisitos

- JDK 21 ou superior
- Maven no `PATH`
- `jpackage` disponivel no ambiente para gerar `app-image`

## Build

### Maven

```bash
mvn -q -DskipTests clean package
```

Artefato principal:

```text
target/cdip-ecarta-ftp-client-all.jar
```

### Script de build

```bat
build_app.bat
```

## Testes

### Executar a suite de testes

```bash
mvn -q test
```

Os testes atuais cobrem:

- regras de validacao por tipo de matriz;
- persistencia criptografada do estado local;
- processamento de CSV com geracao de ZIPs de servico e resposta.

## Execucao

### Rodando o JAR

```bash
java -jar target/cdip-ecarta-ftp-client-all.jar
```

### Gerando app-image com jpackage

```bat
build_jpackage.bat
```

Saida esperada:

```text
dist/cdip-ecarta-ftp-client/
```

## Funcionalidades implementadas

- interface Swing para operacao local;
- leitura de planilha e normalizacao de dados;
- validacao de lote, matriz, campos obrigatorios e regras de formato;
- validacao de PDFs complementares por quantidade de paginas;
- geracao de ZIP `servico` e `Resposta`;
- fila de pendencias de envio;
- download de `Recibo` e `Inconsistencia`;
- lembretes para pendencias de `Resposta`;
- persistencia local criptografada.

## Publicacao recomendada

Para, publicar este em repositorio GitHub dedicado, evitando misturar artefatos do workspace principal com o MVP Java atual.
