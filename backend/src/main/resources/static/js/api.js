// Conversa com o backend. Toda chamada passa por aqui, para que o login,
// o token CSRF e os erros sejam tratados num lugar só.

const MENSAGEM_SEM_CONEXAO = 'Sem conexão com o sistema. Confira a rede e tente de novo.';

/** Erro vindo da API, com o status HTTP e a mensagem que a API mandou. */
export class ErroApi extends Error {
    constructor(status, mensagem) {
        super(mensagem);
        this.status = status;
    }
}

function lerCookie(nome) {
    const par = document.cookie.split('; ').find((c) => c.startsWith(nome + '='));
    return par ? decodeURIComponent(par.substring(nome.length + 1)) : null;
}

/** Garante que o cookie do token CSRF exista antes de qualquer alteração. */
async function garantirTokenCsrf() {
    if (!lerCookie('XSRF-TOKEN')) {
        await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    }
}

async function chamar(metodo, caminho, corpo) {
    const cabecalhos = { Accept: 'application/json' };
    if (corpo !== undefined) {
        cabecalhos['Content-Type'] = 'application/json';
    }
    if (metodo !== 'GET') {
        await garantirTokenCsrf();
        cabecalhos['X-XSRF-TOKEN'] = lerCookie('XSRF-TOKEN') ?? '';
    }

    let resposta;
    try {
        resposta = await fetch(caminho, {
            method: metodo,
            headers: cabecalhos,
            credentials: 'same-origin',
            body: corpo === undefined ? undefined : JSON.stringify(corpo)
        });
    } catch {
        throw new ErroApi(0, MENSAGEM_SEM_CONEXAO);
    }

    if (resposta.status === 204) {
        return null;
    }
    const dados = await resposta.json().catch(() => null);

    if (!resposta.ok) {
        // Sessão expirou: a tela volta para o login. Login e a checagem da sessão
        // não contam como "expirou": ali o 401 é a resposta normal de quem não entrou.
        if (resposta.status === 401 && caminho !== '/api/auth/login' && caminho !== '/api/auth/me') {
            window.dispatchEvent(new Event('sessao-expirada'));
        }
        throw new ErroApi(resposta.status, dados?.mensagem ?? 'Algo deu errado. Tente de novo.');
    }
    return dados;
}

export const api = {
    get: (caminho) => chamar('GET', caminho),
    post: (caminho, corpo) => chamar('POST', caminho, corpo),
    put: (caminho, corpo) => chamar('PUT', caminho, corpo)
};
