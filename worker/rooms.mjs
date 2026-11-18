// Only scores encrypted by the clients and hashes of the room capabilities are stored.
export const ROOM_TTL = 14 * 86400000;
const WINDOW = 600000;
const CODE = /^[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{10}$/;
const CAP = /^[A-Za-z0-9_-]{43}$/;
const ID = /^[a-f0-9]{32}$/;
const headers = {
  'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store',
  'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY'
};
const reply = (body, status = 200) => new Response(JSON.stringify(body), {status, headers});
const fail = (status, error) => reply({error}, status);
const envelope = (s, max) => typeof s === 'string' && s.length <= max && /^1\.[A-Za-z0-9_-]{16}\.[A-Za-z0-9_-]{22,}$/.test(s);
const normalized = s => typeof s === 'string' ? s.replace(/[\s-]/g,'').toUpperCase().replace(/O/g,'0').replace(/[IL]/g,'1') : '';
async function hash(s) {
  const bytes = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)));
  return Array.from(bytes, n => n.toString(16).padStart(2, '0')).join('');
}
async function readBody(request) {
  if (!request.headers.get('Content-Type')?.startsWith('application/json')) throw Error('invalid');
  const reader = request.body?.getReader();
  if (!reader) throw Error('invalid');
  const chunks = []; let size = 0;
  try {
    for (;;) {
      const {done, value} = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 115000) throw Error('invalid');
      chunks.push(value);
    }
  } finally { await reader.cancel().catch(()=>{}); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  const data = JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(bytes));
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw Error('invalid');
  return data;
}
async function withinLimit(db, request, action, now) {
  const digest = await hash(['room',action,request.headers.get('CF-Connecting-IP') || 'local',Math.floor(now/WINDOW)].join('|'));
  const row = await db.prepare('INSERT INTO transfer_rates(id,count,expires_at) VALUES(?,1,?) ON CONFLICT(id) DO UPDATE SET count=count+1 RETURNING count').bind(digest,now+WINDOW).first();
  return row.count <= (action === 'join' ? 40 : 20);
}
const result = row => ({revision:row.revision, cipher:row.cipher, final:!!row.is_final, expiresAt:row.expires_at});
async function create(db, data, now) {
  const {id,writer,reader,admin,pairing,cipher,watchJoin} = data;
  const code = normalized(data.joinCode);
  if (!ID.test(id) || ![writer,reader,admin].every(c=>typeof c==='string' && CAP.test(c)) ||
      new Set([writer,reader,admin]).size !== 3 || !CODE.test(code) || !envelope(pairing,1500) ||
      !envelope(cipher,106000) || data.final!==false || data.revision!==0 || typeof watchJoin!=='boolean') return fail(400,'Invalid room');
  const [adminHash,writerHash,readerHash,codeHash] = await Promise.all([admin,writer,reader,'room-code:'+code].map(hash));
  const expiresAt = now + ROOM_TTL;
  const changed = await db.prepare(`INSERT OR IGNORE INTO match_rooms
    (id,admin_hash,writer_hash,reader_hash,code_hash,pairing,watch_join,expires_at,revision,cipher,is_final)
    SELECT ?,?,?,?,?,?,?,?,0,?,0 WHERE (SELECT count(*) FROM match_rooms)<500`)
    .bind(id,adminHash,writerHash,readerHash,codeHash,pairing,+watchJoin,expiresAt,cipher).run();
  if (changed.meta.changes === 1) return reply({id,revision:0,expiresAt},201);
  const saved = await db.prepare('SELECT * FROM match_rooms WHERE id=? OR code_hash=? LIMIT 1').bind(id,codeHash).first();
  if (!saved) return fail(503,'Room capacity reached');
  if (saved.id===id && saved.admin_hash===adminHash && saved.writer_hash===writerHash && saved.reader_hash===readerHash && saved.code_hash===codeHash)
    return reply({id,revision:saved.revision,expiresAt:saved.expires_at});
  return fail(409,'Room or pairing code already exists');
}
async function publish(db, row, data, now) {
  const {baseRevision,revision,cipher,final} = data;
  if (!Number.isSafeInteger(baseRevision) || baseRevision<0 || !Number.isSafeInteger(revision) ||
      revision<=baseRevision || revision>2147483647 || !envelope(cipher,106000) || typeof final!=='boolean') return fail(400,'Invalid score update');
  const expiresAt = final ? now+ROOM_TTL : row.expires_at;
  const changed = await db.prepare('UPDATE match_rooms SET cipher=?, revision=?, is_final=?, expires_at=? WHERE id=? AND revision=? AND is_final=0 AND expires_at>?')
    .bind(cipher,revision,+final,expiresAt,row.id,baseRevision,now).run();
  if (changed.meta.changes===1) return reply({revision,expiresAt});
  const saved = await db.prepare('SELECT revision,cipher,is_final,expires_at FROM match_rooms WHERE id=? AND expires_at>?').bind(row.id,now).first();
  if (saved && saved.revision===revision && saved.cipher===cipher && !!saved.is_final===final)
    return reply({revision,expiresAt:saved.expires_at});
  return fail(409,'Score changed or already confirmed');
}
export async function handleRooms(request, env) {
  if (!env.DB) return fail(503,'Service unavailable');
  const url = new URL(request.url), origin = request.headers.get('Origin'), now = Date.now(), db = env.DB;
  if (origin && origin!==url.origin) return fail(403,'Origin not allowed');
  const path = url.pathname.replace(/\/$/,'');
  if (!/^\/api\/rooms(?:\/(?:join|[a-f0-9]{32}))?$/.test(path)) return fail(404,'Not found');
  try {
    const id = path.split('/')[3];
    if (!id || id==='join') {
      if (request.method!=='POST') return fail(405,'Method not allowed');
      await db.batch([db.prepare('DELETE FROM match_rooms WHERE expires_at<=?').bind(now),db.prepare('DELETE FROM transfer_rates WHERE expires_at<=?').bind(now)]);
      if (!await withinLimit(db,request,id || 'create',now)) return fail(429,'Too many requests');
      const data = await readBody(request);
      if (!id) return await create(db,data,now);
      const code = normalized(data.code);
      if (!CODE.test(code)) return fail(400,'Invalid code');
      const row = await db.prepare('SELECT pairing FROM match_rooms WHERE code_hash=? AND watch_join=1 AND expires_at>?').bind(await hash('room-code:'+code),now).first();
      return row ? reply({pairing:row.pairing}) : fail(404,'Code unavailable');
    }
    const token = request.headers.get('Authorization')?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1];
    if (!token) return fail(401,'Authorization required');
    const row = await db.prepare('SELECT * FROM match_rooms WHERE id=? AND expires_at>?').bind(id,now).first();
    if (!row) return fail(404,'Room no longer available');
    const digest = await hash(token), owner = row.admin_hash===digest, writer = row.writer_hash===digest, reader = row.reader_hash===digest;
    if (!owner && !writer && !reader) return fail(401,'Authorization required');
    if (request.method==='GET') return reader||writer ? reply(result(row)) : fail(403,'Reader or scorer required');
    if (request.method==='PUT') return writer ? await publish(db,row,await readBody(request),now) : fail(403,'Scorer required');
    if (!owner) return fail(403,'Host required');
    if (request.method==='PATCH' || request.method==='POST') {
      const data = await readBody(request);
      if (typeof data.watchJoin!=='boolean') return fail(400,'Invalid setting');
      await db.prepare('UPDATE match_rooms SET watch_join=? WHERE id=?').bind(+data.watchJoin,id).run();
      return reply({watchJoin:data.watchJoin});
    }
    if (request.method==='DELETE') {
      await db.prepare('DELETE FROM match_rooms WHERE id=?').bind(id).run();
      return reply({ended:true});
    }
    return fail(405,'Method not allowed');
  } catch (error) {
    return error instanceof SyntaxError || error instanceof TypeError || error.message==='invalid'
      ? fail(400,'Invalid request') : fail(503,'Room service unavailable');
  }
}
export default {fetch:handleRooms};
