export const SPORTS=['squash','tennis','badminton','padel','table-tennis'];
export const sportName=s=>({'squash':'Squash','tennis':'Tennis','badminton':'Badminton','padel':'Padel','table-tennis':'Table tennis'}[s]||s);
export const sportIcon=s=>({'squash':'◉','tennis':'🎾','badminton':'🏸','padel':'▦','table-tennis':'🏓'}[s]||'');
export const rowSport=r=>r[0]==='3'?r[24]:'squash';
export const rowMode=r=>r[0]==='3'?r[25]:'singles';
export const scorePairs=r=>{const indices=r[0]==='3'?[13,15,17,19,21,27,29]:[13,15,17,19,21];return indices.map(i=>r[i]===''||r[i]===undefined?null:[Number(r[i]),Number(r[i+1])]);};
export function rowRules(r){const p=r[0]==='3'?r[26].split(','):['squash','0','11','advantage','0'];if(p.length!==5||!SPORTS.includes(p[0])||!['0','1'].includes(p[1])||!['0','1'].includes(p[4]))throw Error('Invalid rules');const f={sport:p[0],doubles:p[1]==='1',target:Number(p[2]),deuce:p[3],finalTie:p[4]==='1',nested:['tennis','padel'].includes(p[0])};if(f.sport==='squash'&&f.doubles||f.sport==='padel'&&!f.doubles||f.nested&&(f.target!==6||!['advantage','no-ad',...(f.sport==='padel'?['star']:[])].includes(f.deuce)))throw Error('Invalid rules');if(!f.nested&&(f.finalTie||f.deuce!=='advantage'||!(f.sport==='badminton'?[15,21]:[11]).includes(f.target)))throw Error('Invalid rules');if(r[0]==='3'&&(r[24]!==f.sport||r[25]!== (f.doubles?'doubles':'singles')))throw Error('Inconsistent sport/mode');return f;}
export function finalScore(f,tie,a,b){const hi=Math.max(a,b),lo=Math.min(a,b);if(lo<0||a+b>10000)return false;if(tie)return lo<9?hi===10:hi===lo+2;if(f.nested)return hi===6&&lo<=4||hi===7&&(lo===5||lo===6);const cap=f.sport==='badminton'?(f.target===21?30:21):Infinity;return hi===cap&&lo===hi-1||(lo<f.target-1?hi===f.target:hi===lo+2&&hi<=cap);}
export const participants=r=>[r[4],...(r[0]==='3'?[r[32],r[34]]:[])].filter(Boolean);
export function details(r){return r[0]==='3'?r[31].split(';').map(v=>v.split(',').map(Number)):scorePairs(r).filter(Boolean).map(([a,b])=>[a,b,-1,-1]);}
export const sportRows=(rows,sport,mode='all')=>rows.filter(r=>rowSport(r)===sport&&(mode==='all'||rowMode(r)===mode));

export function sportMarkup(s){const marks={
 'squash':'<path d="M2 2v12M12 15l-4 7"/><ellipse cx="14" cy="8" rx="5" ry="7" transform="rotate(30 14 8)"/><circle cx="21" cy="19" r="1.5"/>',
 'tennis':'<path d="M10 14l-5 8M10 3l6 10M7 7l9 5"/><ellipse cx="12" cy="8" rx="5" ry="7" transform="rotate(30 12 8)"/><circle cx="20" cy="19" r="3" fill="#b4e775"/>',
 'badminton':'<path d="M5 3h14l-4 12H9zM9 3l2 12M15 3l-2 12"/><ellipse cx="12" cy="18" rx="3" ry="3"/>',
 'padel':'<rect x="5" y="2" width="13" height="14" rx="5"/><path d="M10 16v6h3v-6"/><path d="M8 6h.1M12 6h.1M16 6h.1M8 10h.1M12 10h.1M16 10h.1" stroke-width="2.5"/>',
 'table-tennis':'<ellipse cx="10" cy="8" rx="7" ry="6" fill="currentColor"/><path d="M9 14v8h3v-8"/><circle cx="20" cy="17" r="2.5" fill="#86ddf5"/>'
 };return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">'+(marks[s]||'')+'</svg>';}
