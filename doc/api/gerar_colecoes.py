#!/usr/bin/env python3
"""Gera as coleções da API do PoupaMais para o Postman e para o Bruno.

Fonte única: a lista REQUISICOES abaixo. Mudou um endpoint? Ajuste aqui e rode:

    python3 doc/api/gerar_colecoes.py

Saídas (não edite à mão):
    doc/api/postman/PoupaMais.postman_collection.json
    doc/api/bruno/  (coleção nativa do Bruno: bruno.json, collection.bru, environments/, pastas .bru)
"""

import json
import re
import shutil
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parent

# Variáveis da coleção (Postman) / do ambiente "Local" (Bruno).
VARIAVEIS = {
    "baseUrl": "http://localhost:8080",
    "nome": "Usuário de Teste",
    "email": "teste.colecao@poupamais.local",
    "senha": "senha123",
    "novaSenha": "senha456",
    "mes": "11",
    "ano": "2026",
    "data": "2026-11-05",
    "token": "",
    "categoriaId": "",
    "categoriaTempId": "",
    "lancamentoId": "",
    "tokenRedefinicao": "cole-aqui-o-token-do-email",
}

# ---------------------------------------------------------------------------------------------
# Scripts pós-resposta, descritos de forma neutra e traduzidos para cada ferramenta.
#   ("salvar", variavel, campo_do_json)  -> guarda res.body[campo] numa variável
#   ("trocar_senha",)                    -> após trocar a senha, inverte senha/novaSenha
# ---------------------------------------------------------------------------------------------


def r(pasta, nome, metodo, caminho, *, auth=True, query=None, json_body=None, multipart=None,
      status=(200,), scripts=(), doc="", codigo=None):
    """`codigo`: código de erro esperado (campo `codigo` da resposta) quando o status for >= 400."""
    return {
        "pasta": pasta, "nome": nome, "metodo": metodo, "caminho": caminho, "auth": auth,
        "query": query or [], "json": json_body, "multipart": multipart, "status": status,
        "scripts": scripts, "doc": doc.strip(), "codigo": codigo,
    }


FILTROS = [
    ("descricao", "", False), ("mes", "{{mes}}", False), ("ano", "{{ano}}", False),
    ("tipo", "DESPESA", False), ("status", "ATIVO", False), ("categoriaId", "{{categoriaId}}", False),
]

LANCAMENTO = """{
  "descricao": "Supermercado",
  "tipo": "DESPESA",
  "data": "{{data}}",
  "valor": 680.00,
  "categoriaIds": [{{categoriaId}}]
}"""

REQUISICOES = [
    # 01 · Autenticação ---------------------------------------------------------------------
    r("01 Autenticação", "Cadastro", "POST", "/api/auth/cadastro", auth=False,
      json_body='{\n  "nome": "{{nome}}",\n  "email": "{{email}}",\n  "senha": "{{senha}}",\n  "aceiteTermos": true\n}',
      status=(201, 409), codigo="EMAIL_JA_CADASTRADO",
      doc="Cria o usuário das variáveis `email`/`senha`. **409** (`EMAIL_JA_CADASTRADO`) se o e-mail já existir "
          "(normal ao rodar de novo)."),
    r("01 Autenticação", "Login", "POST", "/api/auth/login", auth=False,
      json_body='{\n  "email": "{{email}}",\n  "senha": "{{senha}}"\n}',
      scripts=[("salvar", "token", "token")],
      doc="Guarda o JWT na variável `token`; as demais requisições usam `Authorization: Bearer {{token}}`."),
    r("01 Autenticação", "Login com senha errada (espera 401)", "POST", "/api/auth/login", auth=False,
      json_body='{\n  "email": "{{email}}",\n  "senha": "senha-errada"\n}', status=(401,),
      codigo="CREDENCIAIS_INVALIDAS",
      doc="Toda resposta de erro traz `codigo` (estável, o front decide por ele) além da `mensagem`: "
          "aqui, `CREDENCIAIS_INVALIDAS`."),

    # 02 · Perfil ---------------------------------------------------------------------------
    r("02 Perfil", "Meu perfil", "GET", "/api/usuarios/me"),
    r("02 Perfil", "Atualizar nome", "PUT", "/api/usuarios/me",
      json_body='{\n  "nome": "{{nome}}"\n}'),
    r("02 Perfil", "Trocar senha", "PUT", "/api/usuarios/me/senha",
      json_body='{\n  "senhaAtual": "{{senha}}",\n  "novaSenha": "{{novaSenha}}"\n}',
      scripts=[("salvar", "token", "token"), ("trocar_senha",)],
      doc="Devolve um token novo (guardado em `token`) e inverte as variáveis `senha` e `novaSenha`, "
          "para a coleção continuar funcionando. **422** se a senha atual estiver errada ou a nova for igual."),
    r("02 Perfil", "Enviar foto", "PUT", "/api/usuarios/me/foto",
      multipart=[("foto", "exemplos/foto-exemplo.png")], status=(204,),
      doc="Multipart, campo `foto`. JPG, PNG ou WEBP até 2 MB."),
    r("02 Perfil", "Baixar foto", "GET", "/api/usuarios/me/foto",
      doc="Devolve a imagem. **404** se o usuário não tiver foto."),
    r("02 Perfil", "Remover foto", "DELETE", "/api/usuarios/me/foto", status=(204,)),

    # 03 · Categorias -----------------------------------------------------------------------
    r("03 Categorias", "Criar categoria", "POST", "/api/categorias",
      json_body='{\n  "descricao": "Mercado {{$timestamp}}"\n}', status=(201,),
      scripts=[("salvar", "categoriaId", "id")],
      doc="Guarda o id em `categoriaId`. **409** se já existir uma com a mesma descrição (sem diferenciar maiúsculas)."),
    r("03 Categorias", "Listar categorias", "GET", "/api/categorias",
      query=[("ativa", "true", False), ("mes", "{{mes}}", False), ("ano", "{{ano}}", False)],
      doc="Sem `ativa` lista todas; `ativa=true` ou `false` filtra. Cada categoria traz `quantidadeLancamentos`: "
          "lançamentos ATIVO e INATIVO cuja `data` cai em `mes`/`ano` (sem eles, o mês atual; DELETADO não conta). "
          "`mes`/`ano` não filtram as categorias. Mês fora de 1–12 ou ano fora de 2000–2100 → **400**."),
    r("03 Categorias", "Editar categoria", "PUT", "/api/categorias/{{categoriaId}}",
      json_body='{\n  "descricao": "Supermercado {{$timestamp}}"\n}'),
    r("03 Categorias", "Inativar categoria", "PATCH", "/api/categorias/{{categoriaId}}/status",
      json_body='{\n  "ativa": false\n}'),
    r("03 Categorias", "Reativar categoria", "PATCH", "/api/categorias/{{categoriaId}}/status",
      json_body='{\n  "ativa": true\n}'),
    r("03 Categorias", "Criar categoria temporária", "POST", "/api/categorias",
      json_body='{\n  "descricao": "Temporária {{$timestamp}}"\n}', status=(201,),
      scripts=[("salvar", "categoriaTempId", "id")]),
    r("03 Categorias", "Excluir categoria", "DELETE", "/api/categorias/{{categoriaTempId}}", status=(204,),
      doc="Só exclui categoria sem lançamentos (**422** se estiver vinculada)."),

    # 04 · Lançamentos ----------------------------------------------------------------------
    r("04 Lançamentos", "Criar lançamento", "POST", "/api/lancamentos",
      json_body=LANCAMENTO, status=(201,), scripts=[("salvar", "lancamentoId", "id")],
      doc="Guarda o id em `lancamentoId`. `data` em ISO (`AAAA-MM-DD`, variável `data`); "
          "`categoriaIds` aceita várias categorias. A resposta traz `dataCadastro`, `dataAtualizacao` "
          "e `dataExclusao` com hora (ISO-8601, UTC)."),
    r("04 Lançamentos", "Criar lançamento inválido (espera 400)", "POST", "/api/lancamentos",
      json_body='{\n  "descricao": "",\n  "tipo": "DESPESA",\n  "data": "2026-02-31",\n  "valor": 0,\n  "categoriaIds": []\n}',
      status=(400,), codigo="VALIDACAO",
      doc="Mostra o formato de erro: `{status, codigo, mensagem, erros: [{campo, mensagem}]}` (`codigo` `VALIDACAO`). "
          "`2026-02-31` não existe: **400** com erro no campo `data`."),
    r("04 Lançamentos", "Criar lançamento já inativo", "POST", "/api/lancamentos",
      json_body=LANCAMENTO.replace('"categoriaIds"', '"status": "INATIVO",\n  "categoriaIds"'),
      status=(201,), doc="`status` opcional no corpo: `ATIVO` ou `INATIVO` (ausente = `ATIVO`), gravado na "
          "mesma chamada (sem PATCH depois)."),
    r("04 Lançamentos", "Criar lançamento com status DELETADO (espera 400)", "POST", "/api/lancamentos",
      json_body=LANCAMENTO.replace('"categoriaIds"', '"status": "DELETADO",\n  "categoriaIds"'),
      status=(400,), codigo="VALIDACAO",
      doc="`DELETADO` não vai no corpo (a lixeira é pelo DELETE): **400** (`VALIDACAO`) com erro no campo `status`."),
    r("04 Lançamentos", "Excluir categoria em uso (espera 422)", "DELETE", "/api/categorias/{{categoriaId}}",
      status=(422,), codigo="CATEGORIA_VINCULADA",
      doc="**422** com `codigo` `CATEGORIA_VINCULADA`: categoria com lançamentos só pode ser inativada."),
    r("04 Lançamentos", "Listar lançamentos", "GET", "/api/lancamentos",
      query=FILTROS + [("pagina", "0", True), ("tamanho", "12", True)],
      doc="Filtros opcionais (ative na aba Params). Sem `status` = ATIVO + INATIVO; `status=DELETADO` = lixeira. "
          "Paginado: `pagina` começa em 0 (padrão 0) e `tamanho` vai de 1 a 100 (padrão 12); fora disso, **400**. "
          "Resposta: `{itens, pagina, tamanho, total, totalPaginas}`. A exportação não pagina."),
    r("04 Lançamentos", "Buscar lançamento", "GET", "/api/lancamentos/{{lancamentoId}}"),
    r("04 Lançamentos", "Editar lançamento", "PUT", "/api/lancamentos/{{lancamentoId}}",
      json_body=LANCAMENTO.replace("Supermercado", "Supermercado do mês").replace("680.00", "720.50"),
      doc="`status` opcional (`ATIVO` ou `INATIVO`); ausente = mantém o atual. Na lixeira → **422**."),
    r("04 Lançamentos", "Inativar lançamento", "PATCH", "/api/lancamentos/{{lancamentoId}}/status",
      json_body='{\n  "status": "INATIVO"\n}'),
    r("04 Lançamentos", "Ativar lançamento", "PATCH", "/api/lancamentos/{{lancamentoId}}/status",
      json_body='{\n  "status": "ATIVO"\n}'),
    r("04 Lançamentos", "Excluir lançamento (lixeira)", "DELETE", "/api/lancamentos/{{lancamentoId}}",
      status=(204,), doc="Exclusão lógica: o status vira DELETADO e o status anterior (ATIVO/INATIVO) fica guardado."),
    r("04 Lançamentos", "Listar lixeira", "GET", "/api/lancamentos", query=[("status", "DELETADO", True)]),
    r("04 Lançamentos", "Restaurar lançamento", "POST", "/api/lancamentos/{{lancamentoId}}/restaurar",
      doc="Volta ao status que tinha antes do DELETE (aqui, ATIVO). Excluídos antes dessa regra voltam como INATIVO."),

    # 05 · Importar e exportar --------------------------------------------------------------
    r("05 Importar e exportar", "Baixar modelo CSV", "GET", "/api/lancamentos/template-csv"),
    r("05 Importar e exportar", "Importar CSV", "POST", "/api/lancamentos/importar",
      multipart=[("arquivo", "exemplos/lancamentos-exemplo.csv")],
      doc="Multipart, campo `arquivo`. O exemplo tem uma linha inválida: a resposta lista "
          "`importados`, `falhas[{linha, conteudo, motivo}]` e `categoriasCriadas`."),
    r("05 Importar e exportar", "Exportar CSV", "GET", "/api/lancamentos/exportar",
      query=[("formato", "csv", True)] + FILTROS,
      doc="Usa os mesmos filtros da listagem. No Postman, use *Send and Download* para salvar o arquivo."),
    r("05 Importar e exportar", "Exportar Excel", "GET", "/api/lancamentos/exportar",
      query=[("formato", "xlsx", True)] + FILTROS),

    # 06 · Dashboard ------------------------------------------------------------------------
    r("06 Dashboard", "Dashboard do mês", "GET", "/api/dashboard",
      query=[("mes", "{{mes}}", True), ("ano", "{{ano}}", True)],
      doc="Sem `mes`/`ano` usa o mês atual. Só considera lançamentos ATIVOS. Cada item de `maioresDespesas` "
          "traz `{id, descricao, data, valor, categorias}` (`data` em ISO `AAAA-MM-DD`)."),

    # 07 · Recuperação de senha (manual) ----------------------------------------------------
    r("07 Recuperação de senha (manual)", "Pedir link de redefinição", "POST", "/api/auth/recuperar-senha",
      auth=False, json_body='{\n  "email": "{{email}}"\n}', status=(202,),
      doc="Sempre 202, exista ou não o e-mail. O e-mail chega no Mailpit: http://localhost:8025"),
    r("07 Recuperação de senha (manual)", "Redefinir senha", "POST", "/api/auth/redefinir-senha",
      auth=False, json_body='{\n  "token": "{{tokenRedefinicao}}",\n  "novaSenha": "{{senha}}"\n}',
      status=(204, 422), codigo="TOKEN_INVALIDO",
      doc="Copie o `token` do link recebido no Mailpit para a variável `tokenRedefinicao`. "
          "Sem token válido a resposta é **422** com `codigo` `TOKEN_INVALIDO` (é o que acontece ao rodar a coleção inteira)."),

    # 08 · Privacidade (LGPD) ---------------------------------------------------------------
    r("08 Privacidade (LGPD)", "Baixar meus dados", "GET", "/api/usuarios/me/dados"),
    r("08 Privacidade (LGPD)", "Excluir minha conta", "POST", "/api/usuarios/me/excluir",
      json_body='{\n  "senha": "{{senha}}"\n}', status=(204,),
      doc="**Irreversível**: apaga o usuário e todos os dados dele. Fica por último de propósito; "
          "depois dela, rode o Cadastro de novo."),
]


# ------------------------------------------------------------------ Postman (v2.1) ----------

def _url(req):
    """URL completa com os parâmetros ativos (o Bruno CLI só envia o que está na URL)."""
    ativos = [f"{k}={v}" for k, v, ativo in req["query"] if ativo]
    return "{{baseUrl}}" + req["caminho"] + ("?" + "&".join(ativos) if ativos else "")


def _postman_script(req):
    linhas = []
    esperado = ", ".join(str(s) for s in req["status"])
    linhas.append(f'pm.test("Status esperado ({esperado})", function () {{')
    linhas.append(f"  pm.expect([{esperado}]).to.include(pm.response.code);")
    linhas.append("});")
    if req["codigo"]:
        linhas.append("if (pm.response.code >= 400) {")
        linhas.append(f'  pm.test("Código de erro ({req["codigo"]})", function () {{')
        linhas.append(f'    pm.expect(pm.response.json().codigo).to.eql("{req["codigo"]}");')
        linhas.append("  });")
        linhas.append("}")
    if req["scripts"]:
        linhas.append("if (pm.response.code < 300) {")
        for s in req["scripts"]:
            if s[0] == "salvar":
                linhas.append(f'  pm.collectionVariables.set("{s[1]}", pm.response.json().{s[2]});')
            elif s[0] == "trocar_senha":
                linhas += [
                    '  const antiga = pm.collectionVariables.get("senha");',
                    '  pm.collectionVariables.set("senha", pm.collectionVariables.get("novaSenha"));',
                    '  pm.collectionVariables.set("novaSenha", antiga);',
                ]
        linhas.append("}")
    return linhas


def postman():
    pastas = {}
    for req in REQUISICOES:
        caminho = req["caminho"]
        raw = _url(req)
        query = [{"key": k, "value": v, "disabled": not ativo} for k, v, ativo in req["query"]]
        item = {
            "name": req["nome"],
            "request": {
                "method": req["metodo"],
                "header": [],
                "url": {
                    "raw": raw,
                    "host": ["{{baseUrl}}"],
                    "path": [p for p in caminho.strip("/").split("/")],
                    **({"query": query} if query else {}),
                },
                **({"description": req["doc"]} if req["doc"] else {}),
            },
            "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": _postman_script(req)}}],
        }
        if not req["auth"]:
            item["request"]["auth"] = {"type": "noauth"}
        if req["json"] is not None:
            item["request"]["header"].append({"key": "Content-Type", "value": "application/json"})
            item["request"]["body"] = {"mode": "raw", "raw": req["json"],
                                       "options": {"raw": {"language": "json"}}}
        if req["multipart"]:
            item["request"]["body"] = {"mode": "formdata", "formdata": [
                {"key": k, "type": "file", "src": arq,
                 "description": f"Selecione o arquivo doc/api/{arq} (ou outro)"}
                for k, arq in req["multipart"]]}
        pastas.setdefault(req["pasta"], []).append(item)

    colecao = {
        "info": {
            "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "poupamais-api")),
            "name": "PoupaMais API",
            "description": "Todos os endpoints da API do PoupaMais. Gerado por doc/api/gerar_colecoes.py; "
                           "veja doc/api/README.md.",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{token}}", "type": "string"}]},
        "variable": [{"key": k, "value": v, "type": "string"} for k, v in VARIAVEIS.items()],
        "item": [{"name": nome, "item": itens} for nome, itens in pastas.items()],
    }
    destino = RAIZ / "postman" / "PoupaMais.postman_collection.json"
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text(json.dumps(colecao, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ------------------------------------------------------------------ Bruno (.bru) ------------

def _bloco(nome, linhas):
    corpo = "\n".join(f"  {l}" if l else "" for l in linhas)
    return f"{nome} {{\n{corpo}\n}}\n"


def _arquivo_seguro(nome):
    return re.sub(r"[^\w\- ()]", "", nome).strip()


def bruno():
    destino = RAIZ / "bruno"
    if destino.exists():
        shutil.rmtree(destino)
    (destino / "environments").mkdir(parents=True)

    (destino / "bruno.json").write_text(json.dumps(
        {"version": "1", "name": "PoupaMais API", "type": "collection",
         "ignore": ["node_modules", ".git"]}, indent=2) + "\n", encoding="utf-8")
    (destino / "collection.bru").write_text(
        _bloco("auth", ["mode: bearer"]) + "\n" + _bloco("auth:bearer", ["token: {{token}}"]) + "\n"
        + _bloco("docs", ["Todos os endpoints da API do PoupaMais.",
                          "Gerado por doc/api/gerar_colecoes.py; veja doc/api/README.md."]),
        encoding="utf-8")
    (destino / "environments" / "Local.bru").write_text(
        _bloco("vars", [f"{k}: {v}" for k, v in VARIAVEIS.items()]), encoding="utf-8")

    seq_pasta = {}
    seq_req = {}
    for req in REQUISICOES:
        pasta = destino / _arquivo_seguro(req["pasta"])
        if req["pasta"] not in seq_pasta:
            seq_pasta[req["pasta"]] = len(seq_pasta) + 1
            pasta.mkdir()
            (pasta / "folder.bru").write_text(
                _bloco("meta", [f"name: {req['pasta']}", f"seq: {seq_pasta[req['pasta']]}"]), encoding="utf-8")
        seq_req[req["pasta"]] = seq_req.get(req["pasta"], 0) + 1

        partes = [_bloco("meta", [f"name: {req['nome']}", "type: http", f"seq: {seq_req[req['pasta']]}"])]
        corpo = "json" if req["json"] is not None else ("multipartForm" if req["multipart"] else "none")
        partes.append(_bloco(req["metodo"].lower(), [
            f"url: {_url(req)}", f"body: {corpo}",
            f"auth: {'inherit' if req['auth'] else 'none'}"]))
        if req["query"]:
            partes.append(_bloco("params:query", [f"{'' if ativo else '~'}{k}: {v}"
                                                  for k, v, ativo in req["query"]]))
        if req["json"] is not None:
            partes.append(_bloco("body:json", req["json"].split("\n")))
        if req["multipart"]:
            # O Bruno resolve @file() a partir da pasta da coleção (doc/api/bruno)
            partes.append(_bloco("body:multipart-form", [f"{k}: @file(../{arq})" for k, arq in req["multipart"]]))
        if req["scripts"]:
            linhas = ["if (res.status < 300) {"]
            for s in req["scripts"]:
                if s[0] == "salvar":
                    linhas.append(f'  bru.setVar("{s[1]}", res.body.{s[2]});')
                elif s[0] == "trocar_senha":
                    linhas += ['  const antiga = bru.getEnvVar("senha");',
                               '  bru.setEnvVar("senha", bru.getEnvVar("novaSenha"));',
                               '  bru.setEnvVar("novaSenha", antiga);']
            linhas.append("}")
            partes.append(_bloco("script:post-response", linhas))
        esperado = ", ".join(str(s) for s in req["status"])
        testes = [f'test("Status esperado ({esperado})", function () {{',
                  f"  expect([{esperado}]).to.include(res.status);", "});"]
        if req["codigo"]:
            testes += ["if (res.status >= 400) {",
                       f'  test("Código de erro ({req["codigo"]})", function () {{',
                       f'    expect(res.body.codigo).to.eql("{req["codigo"]}");', "  });", "}"]
        partes.append(_bloco("tests", testes))
        if req["doc"]:
            partes.append(_bloco("docs", req["doc"].split("\n")))
        (pasta / f"{_arquivo_seguro(req['nome'])}.bru").write_text("\n".join(partes), encoding="utf-8")


if __name__ == "__main__":
    postman()
    bruno()
    print(f"{len(REQUISICOES)} requisições geradas em {RAIZ / 'postman'} e {RAIZ / 'bruno'}")
