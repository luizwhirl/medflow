# Busca por voz

O microfone fica dentro do campo de busca, na Início e em Buscar (`app-search-field`, PDS-52). Ele usa a Web Speech API do navegador por meio do `ReconhecimentoVozService` (PDS-50). O texto reconhecido vai para a busca tolerante (PDS-51). Em Buscar, vão até três alternativas do reconhecimento numa chamada só (`busca` repetido); na Início, a busca por voz leva para Buscar só com o texto principal na URL (`?busca=`), sem as alternativas.

Se o reconhecimento no aparelho não aceitar pt-BR, o app tenta de novo com o do navegador (passando pelo aviso de privacidade, se ainda não foi aceito); o microfone só some quando nem o do navegador reconhece pt-BR.

O botão só aparece onde o navegador reconhece voz e a página está em contexto seguro (HTTPS ou `localhost`). No Firefox, e em navegadores que não reconhecem pt-BR, o campo fica igual ao de antes.

## O que é enviado e para quem (LGPD)

Falar o nome de um remédio é dado de saúde, então a pessoa precisa saber para onde vai o áudio.

- **Áudio:** no Chrome e no Edge, o reconhecimento padrão manda o áudio para o serviço do navegador (Google ou Microsoft), que devolve o texto. O UBS Conecta não recebe nem grava o áudio.
- **Reconhecimento no aparelho:** quando o navegador já tem o pacote de pt-BR instalado (`SpeechRecognition.available({ langs: ['pt-BR'], processLocally: true })` responde `available`), o app liga `processLocally` e o áudio não sai do aparelho. O app não baixa o pacote sozinho (`install()`, cerca de 60 MB).
- **Aviso no primeiro uso:** sem reconhecimento no aparelho, antes da primeira escuta aparece um aviso curto com "Continuar" e "Agora não". A escolha fica no navegador (`medflow.voz.avisoAceito`). "Agora não" não liga o microfone, e o aviso volta na próxima vez.
- **Texto reconhecido:** vai para a API só como o termo de busca, como se tivesse sido digitado, e não é gravado. A contagem do "mais procurados" (PDS-49) guarda só o medicamento aberto, por dia, sem o texto.

## Erros

| Situação | O que a pessoa vê |
| --- | --- |
| Permissão negada (`not-allowed`, `service-not-allowed`) | "Permita o uso do microfone para buscar por voz", com o caminho para liberar |
| Nada foi dito (`no-speech`, `nomatch`, resultado vazio) | "Não ouvimos nada. Toque no microfone e diga o nome do remédio." |
| Sem microfone (`audio-capture`) | "Não encontramos um microfone neste aparelho." |
| Sem internet (`network`) | "A busca por voz precisa de internet. Digite o nome do remédio." |
| Idioma não suportado | O botão some |
| A própria pessoa cancelou (`aborted`) | Nada |

A mensagem fica abaixo do campo, numa região `aria-live`, e some ao digitar ou ao tentar de novo.
