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
  if (data.refereeInvite!==undefined && (!CAP.test(data.refereeInvite) || [admin,reader,writer].includes(data.refereeInvite))) return fail(400,'Invalid invitation');
  const inviteHash = data.refereeInvite ? await hash(data.refereeInvite) : null;
  const [adminHash,writerHash,readerHash,codeHash] = await Promise.all([admin,writer,reader,'room-code:'+code].map(hash));
  const expiresAt = now + ROOM_TTL;
  const changed = await db.prepare(`INSERT OR IGNORE INTO match_rooms
    (id,admin_hash,writer_hash,reader_hash,code_hash,pairing,watch_join,expires_at,revision,cipher,is_final,referee_invite_hash)
    SELECT ?,?,?,?,?,?,?,?,0,?,0,? WHERE (SELECT count(*) FROM match_rooms)<500`)
    .bind(id,adminHash,writerHash,readerHash,codeHash,pairing,+watchJoin,expiresAt,cipher,inviteHash).run();
  if (changed.meta.changes === 1) return reply({id,revision:0,expiresAt},201);
  const saved = await db.prepare('SELECT * FROM match_rooms WHERE id=? OR code_hash=? LIMIT 1').bind(id,codeHash).first();
  if (!saved) return fail(503,'Room capacity reached');
  if (saved.id===id && saved.admin_hash===adminHash && saved.writer_hash===writerHash && saved.reader_hash===readerHash && saved.code_hash===codeHash && saved.referee_invite_hash===inviteHash)
    return reply({id,revision:saved.revision,expiresAt:saved.expires_at});
  return fail(409,'Room or pairing code already exists');
}
async function publish(db, row, data, now, memberHash) {
  const {baseRevision,revision,cipher,final} = data;
  if (!Number.isSafeInteger(baseRevision) || baseRevision<0 || !Number.isSafeInteger(revision) ||
      revision<=baseRevision || revision>2147483647 || !envelope(cipher,106000) || typeof final!=='boolean') return fail(400,'Invalid score update');
  const expiresAt = final ? now+ROOM_TTL : row.expires_at;
  const guard = memberHash ? " AND EXISTS (SELECT 1 FROM room_members WHERE room_id=match_rooms.id AND token_hash=? AND role='referee' AND state='active')" : '';
  const changed = await db.prepare('UPDATE match_rooms SET cipher=?, revision=?, is_final=?, expires_at=? WHERE id=? AND revision=? AND is_final=0 AND expires_at>?'+guard)
    .bind(cipher,revision,+final,expiresAt,row.id,baseRevision,now,...(memberHash?[memberHash]:[])).run();
  if (changed.meta.changes===1) return reply({revision,expiresAt});
  const saved = await db.prepare('SELECT revision,cipher,is_final,expires_at FROM match_rooms WHERE id=? AND expires_at>?').bind(row.id,now).first();
  if (saved && saved.revision===revision && saved.cipher===cipher && !!saved.is_final===final)
    return reply({revision,expiresAt:saved.expires_at});
  return fail(409,'Score changed or already confirmed');
}
// The shared invitation admits devices. Only a device credential can read or score.
async function enroll(db,row,token,role,proof,now) {
  if(typeof token!=='string'||!CAP.test(token))return fail(400,'Device credential required');
  const digest=await hash(token), id=(await hash('member|'+row.id+'|'+digest)).slice(0,24);
  if([row.reader_hash,row.writer_hash,row.admin_hash,row.referee_invite_hash].includes(digest))return fail(400,'Separate device credential required');
  const watch=role==='watch';
  const grant=watch?'watch_join=1 AND code_hash=?':'referee_invite_hash=?';
  const room=await db.prepare(`INSERT INTO room_members (id,room_id,token_hash,role,state,seen_at)
    SELECT ?,id,?,?,'active',0 FROM match_rooms WHERE id=? AND expires_at>? AND ${grant}
    AND (EXISTS(SELECT 1 FROM room_members WHERE id=?) OR (SELECT count(*) FROM room_members WHERE room_id=?)<128)
    ON CONFLICT(id) DO UPDATE SET state='active' WHERE room_members.state!='removed' AND room_members.role=excluded.role
    RETURNING id`).bind(id,digest,role,row.id,now,proof,id,row.id).first();
  if(!room){
    const old=await db.prepare('SELECT state FROM room_members WHERE id=? AND room_id=?').bind(id,row.id).first();
    return old?.state==='removed'?fail(410,'Device removed'):fail(403,'Invitation unavailable or room full');
  }
  return reply(watch?{memberId:id,pairing:row.pairing,score:result(row)}:{memberId:id});
}
async function manageMember(db,room,member,method,now) {
  if(!member && method==='GET'){
    const list=await db.prepare("SELECT id,role,seen_at AS seenAt FROM room_members WHERE room_id=? AND state='active' AND seen_at>0 ORDER BY role,seen_at DESC,id")
      .bind(room).all();
    return reply({members:list.results,now});
  }
  if(member && method==='DELETE'){
    const changed=await db.prepare("UPDATE room_members SET state='removed' WHERE room_id=? AND id=? RETURNING id").bind(room,member).first();
    return changed?reply({removed:true}):fail(404,'Device unavailable');
  }
  return fail(405,'Method not allowed');
}
export async function handleRooms(request, env) {
  if (!env.DB) return fail(503,'Service unavailable');
  const url = new URL(request.url), origin = request.headers.get('Origin'), now = Date.now(), db = env.DB;
  if (origin && origin!==url.origin) return fail(403,'Origin not allowed');
  const path = url.pathname.replace(/\/$/,'');
  const route=path.match(/^\/api\/rooms(?:\/(join|[a-f0-9]{32})(?:\/(referees|participants)(?:\/(me|[a-f0-9]{24}))?)?)?$/);
  if(!route)return fail(404,'Not found');
  try {
    const [,id,section,memberId]=route;
    if(section && (!id || id==='join' || section==='referees' && memberId))return fail(404,'Not found');
    if (!id || id==='join') {
      if (request.method!=='POST') return fail(405,'Method not allowed');
      await db.batch([db.prepare('DELETE FROM match_rooms WHERE expires_at<=?').bind(now),db.prepare('DELETE FROM transfer_rates WHERE expires_at<=?').bind(now)]);
      if (!await withinLimit(db,request,id || 'create',now)) return fail(429,'Too many requests');
      const data = await readBody(request);
      if (!id) return await create(db,data,now);
      const code = normalized(data.code);
      if (!CODE.test(code)) return fail(400,'Invalid code');
      const proof=await hash('room-code:'+code);
      const row=await db.prepare('SELECT * FROM match_rooms WHERE code_hash=? AND watch_join=1 AND expires_at>?').bind(proof,now).first();
      if(!row)return fail(404,'Code unavailable');
      if(!row.referee_invite_hash)return reply({pairing:row.pairing});
      if(!data.deviceToken)return fail(426,'Update the joining watch');
      return await enroll(db,row,data.deviceToken,'watch',proof,now);
    }
    const token = request.headers.get('Authorization')?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1];
    if (!token) return fail(401,'Authorization required');
    const row = await db.prepare('SELECT * FROM match_rooms WHERE id=? AND expires_at>?').bind(id,now).first();
    if (!row) return fail(404,'Room no longer available');
    const digest = await hash(token), owner = row.admin_hash===digest, writer = row.writer_hash===digest, reader = row.reader_hash===digest;
    if(section==='referees'){
      if(request.method!=='POST')return fail(405,'Method not allowed');
      if(digest!==row.referee_invite_hash)return fail(403,'Invitation required');
      if(!await withinLimit(db,request,'join',now))return fail(429,'Too many requests');
      return await enroll(db,row,(await readBody(request)).deviceToken,'referee',digest,now);
    }
    if(section==='participants' && memberId!=='me'){
      if(!owner)return fail(403,'Host required');
      if(!row.referee_invite_hash)return fail(409,'Legacy shared match');
      return await manageMember(db,id,memberId,request.method,now);
    }
    let device=null;
    if(!owner && !writer && !reader){
      device=await db.prepare('SELECT id,role,state,seen_at FROM room_members WHERE room_id=? AND token_hash=?').bind(id,digest).first();
      if(!device)return fail(401,'Authorization required');
      if(device.state!=='active')return fail(410,'Device access ended');
    }
    if(section==='participants'){
      if(!device || request.method!=='DELETE')return fail(403,'Device required');
      await db.prepare("UPDATE room_members SET state='left',seen_at=0 WHERE id=? AND state='active'").bind(device.id).run();
      return reply({left:true});
    }
    if(device && device.seen_at<now-30000 && (request.method==='GET' || request.method==='PUT' && device.role==='referee')){
      const seen=await db.prepare("UPDATE room_members SET seen_at=? WHERE id=? AND state='active'").bind(now,device.id).run();
      if(!seen.meta.changes)return fail(410,'Device access ended');
    }
    if (request.method==='GET') return reader||writer||device ? reply(result(row)) : fail(403,'Reader or scorer required');
    if (request.method==='PUT') return writer||device?.role==='referee' ? await publish(db,row,await readBody(request),now,device?digest:null) : fail(403,'Scorer required');
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
