// Servidor de teste que imita o pedaco do Supabase que o app usa (login por senha, cadastro e a tabela
// user_settings), para testar o fluxo de conta no emulador SEM a chave real.
//
//   node tools/mock-supabase.mjs            # escuta em http://localhost:54321
//   gradlew installDebug -PSUPABASE_URL=http://10.0.2.2:54321 -PSUPABASE_ANON_KEY=teste
//
// Senhas com menos de 10 caracteres sao recusadas; "confirmar@..." simula projeto que exige confirmar o e-mail.
// Tudo fica em memoria. NAO e seguro nem completo: serve so para desenvolvimento.
import { createServer } from 'node:http';

const port = Number(process.env.PORT ?? 54321);
const users = new Map(); // email -> { id, password, name }
const settings = new Map(); // user_id -> linha de user_settings
const tokens = new Map(); // access_token -> user_id
let counter = 0;

const json = (res, status, body) => {
  res.writeHead(status, { 'content-type': 'application/json', 'access-control-allow-origin': '*' });
  res.end(body === undefined ? '' : JSON.stringify(body));
};

const session = (user) => {
  const access = `access-${user.id}-${++counter}`;
  tokens.set(access, user.id);
  return {
    access_token: access,
    token_type: 'bearer',
    expires_in: 3600,
    expires_at: Math.floor(Date.now() / 1000) + 3600,
    refresh_token: `refresh-${user.id}`,
    user: { id: user.id, email: user.email, created_at: new Date().toISOString(), user_metadata: { display_name: user.name } },
  };
};

const readBody = (req) =>
  new Promise((resolve) => {
    let data = '';
    req.on('data', (chunk) => (data += chunk));
    req.on('end', () => resolve(data ? JSON.parse(data) : {}));
  });

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  const path = url.pathname;
  const body = req.method === 'POST' || req.method === 'PATCH' ? await readBody(req) : {};
  console.log(req.method, path + url.search);

  if (path === '/auth/v1/signup') {
    const email = String(body.email ?? '').toLowerCase();
    if (users.has(email)) return json(res, 422, { code: 422, error_code: 'user_already_exists', msg: 'User already registered' });
    if (String(body.password ?? '').length < 10) return json(res, 422, { code: 422, error_code: 'weak_password', msg: 'Password should be at least 10 characters' });
    const user = { id: `user-${users.size + 1}`, email, password: body.password, name: body.data?.display_name ?? '' };
    users.set(email, user);
    if (email.startsWith('confirmar@')) return json(res, 200, { id: user.id, email, confirmation_sent_at: new Date().toISOString() });
    return json(res, 200, session(user));
  }

  if (path === '/auth/v1/token') {
    const grant = url.searchParams.get('grant_type');
    if (grant === 'refresh_token') {
      const user = [...users.values()].find((u) => `refresh-${u.id}` === body.refresh_token);
      return user ? json(res, 200, session(user)) : json(res, 400, { error: 'invalid_grant', error_description: 'Invalid Refresh Token' });
    }
    const user = users.get(String(body.email ?? '').toLowerCase());
    if (!user || user.password !== body.password) return json(res, 400, { error: 'invalid_grant', error_description: 'Invalid login credentials' });
    return json(res, 200, session(user));
  }

  if (path === '/auth/v1/logout') return json(res, 204);

  if (path === '/rest/v1/user_settings') {
    const userId = tokens.get((req.headers.authorization ?? '').replace('Bearer ', ''));
    if (!userId) return json(res, 401, { message: 'JWT expired' });
    if (req.method === 'GET') {
      const row = settings.get(userId);
      return json(res, 200, row ? [row] : []);
    }
    if (req.method === 'POST') {
      const ignore = String(req.headers.prefer ?? '').includes('ignore-duplicates');
      if (!(ignore && settings.has(userId))) settings.set(userId, { ...(settings.get(userId) ?? {}), ...body });
      console.log('  user_settings =', JSON.stringify(settings.get(userId)));
      return json(res, 201);
    }
  }

  // tabelas publicas de calendario/classificacao: vazias, o app usa o catalogo estatico
  if (path.startsWith('/rest/v1/')) return json(res, 200, []);
  json(res, 404, { message: 'not found' });
}).listen(port, () => console.log(`mock-supabase em http://localhost:${port}`));
