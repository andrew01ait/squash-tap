// The watch wire formats are intentionally preserved, including real rally order and entered scores.
import {rowRules, finalScore} from './sports.mjs';

const squash = 'squash,0,11,advantage,0';
const invalid = () => { throw new Error('Invalid match state'); };
const partner = slot => slot ^ 2;
const defaultService = () => Array(14).fill(-1);
const numericPair = value => {
  if (!/^\d{1,5},\d{1,5}$/.test(value)) invalid();
  return value.split(',').map(Number);
};

function parseFormat(encoded) {
  const row = [];
  row[0]='3'; row[24]=encoded.split(',')[0]; row[25]=encoded.split(',')[1]==='1'?'doubles':'singles'; row[26]=encoded;
  const format=rowRules(row);
  return {...format, encoded, cap:format.sport==='badminton'?(format.target===21?30:21):Infinity};
}
function validService(first,other,format) {
  const max=format.doubles?3:1;
  return [first,other].every(n=>Number.isInteger(n)&&n>=-1&&n<=max)
    && (first<0||other<0||first%2!==other%2);
}

class Game {
  constructor(format,tie,first=-1,other=-1) {
    this.format=format; this.tie=tie; this.history=''; this.entered=false;
    this.score=[0,0]; this.points=[0,0]; this.deuces=0;
    this.first=first; this.other=other; this.resetPickle(first,other);
  }
  resetPickle(first,other) {
    this.pServer=first<0?0:first;
    this.right=[0,1];
    if(first>=0) this.right[first%2]=first;
    if(other>=0) this.right[other%2]=other;
    this.pNumber=this.format.deuce==='side-out'&&this.format.doubles?2:1;
  }
  get pickle() { return this.format.sport==='pickleball'; }
  get tieBreak() { return this.tie||this.format.nested&&this.score[0]===6&&this.score[1]===6; }
  get winner() {
    const [a,b]=this.score;
    if(this.entered) return a>b?0:1;
    if(this.format.nested&&!this.tie) {
      if(a===7||a>=6&&a-b>=2)return 0;
      if(b===7||b>=6&&b-a>=2)return 1;
      return -1;
    }
    const high=Math.max(a,b);
    if(high<(this.tie?10:this.format.target)||Math.abs(a-b)<2&&high<this.format.cap)return -1;
    return a>b?0:1;
  }
  addPickle(side) {
    const serving=this.pServer%2, f=this.format;
    let point=f.deuce!=='side-out'||side===serving;
    if(f.deuce==='rally-2025'&&side!==serving&&this.score[side]+1>=f.target&&this.score[side]+1-this.score[1-side]>=2)point=false;
    if(point) {
      this.score[side]++;
      if(f.doubles)this.right[side]=partner(this.right[side]);
    }
    if(serving===side)return;
    if(f.deuce==='side-out'&&f.doubles&&this.pNumber===1){this.pServer=partner(this.pServer);this.pNumber=2;}
    else {this.pServer=f.doubles?this.right[side]:side;this.pNumber=1;}
  }
  add(side) {
    if(![0,1].includes(side)||this.winner!==-1||this.history.length>=10000||this.pickle&&this.first<0)return false;
    this.history+=side===0?'B':'R';
    if(this.pickle){this.addPickle(side);return true;}
    if(!this.format.nested||this.tie){this.score[side]++;return true;}
    this.points[side]++;
    const [a,b]=this.points, lead=Math.abs(a-b), high=Math.max(a,b);
    if(this.tieBreak){if(high>=7&&lead>=2)this.score[side]++;return true;}
    if(a===b&&a>=3)this.deuces++;
    const deciding=this.format.deuce==='no-ad'||this.format.deuce==='star'&&this.deuces>=3;
    if(high>=4&&(lead>=2||deciding&&lead>0)){this.score[side]++;this.points=[0,0];this.deuces=0;}
    return true;
  }
  setStarters(first,other) {
    if(!validService(first,other,this.format)||this.pickle&&(first<0||other<0||this.history.length))return false;
    this.first=first; this.other=other; this.resetPickle(first,other); return true;
  }
  display(side) {
    if(!this.format.nested||this.tie||this.entered||this.winner>=0)return String(this.score[side]);
    if(this.tieBreak)return String(this.points[side]);
    const [a,b]=[this.points[side],this.points[1-side]];
    if(a>=3&&b>=3)return a>b?'AD':'40';
    return ['0','15','30','40'][Math.min(a,3)];
  }
  serviceStep() {
    if(this.tie)return Math.floor((this.score[0]+this.score[1]+1)/2);
    return this.score[0]+this.score[1]+(this.tieBreak?Math.floor((this.points[0]+this.points[1]+1)/2):0);
  }
  encode() {
    if(this.format.encoded===squash)return this.entered?'2|'+this.score.join('|'):'1|'+this.history;
    const code=this.pickle?'4|'+this.format.encoded+'|'+this.first+','+this.other:'3|'+this.format.encoded+'|'+Number(this.tie);
    return code+'|'+(this.entered?'E'+this.score.join(','):'L'+this.history);
  }
  static decode(raw,format,tie,first,other) {
    const p=raw.split('|'), game=new Game(format,tie,first,other);
    let history,entered;
    if(format.encoded===squash){
      if(p[0]==='1'&&p.length===2)history=p[1];
      else if(p[0]==='2'&&p.length===3)entered=numericPair(p.slice(1).join(','));
      else invalid();
    } else {
      const head=game.pickle?'4':'3', setting=game.pickle?first+','+other:String(Number(tie));
      if(p.length!==4||p[0]!==head||p[1]!==format.encoded||p[2]!==setting)invalid();
      if(game.pickle&&((first<0)!==(other<0)))invalid();
      if(p[3].startsWith('E'))entered=numericPair(p[3].slice(1));
      else if(p[3].startsWith('L'))history=p[3].slice(1);
      else invalid();
    }
    if(entered){
      if(!finalScore(format,tie,...entered))invalid();
      game.entered=true; game.score=entered;
    }else {
      if(history.length>10000||!/^[BR]*$/.test(history))invalid();
      for(const rally of history)if(!game.add(rally==='B'?0:1))invalid();
    }
    return game;
  }
}

export class RefereeMatch {
  static decode(raw) {
    if(typeof raw!=='string'||raw.length>75000)invalid();
    const m=/^(M[1-4])\|([a-zA-Z0-9-]{1,80})\|([1357])\|([\s\S]+)$/.exec(raw);
    if(!m)invalid();
    const [,version,id,best,tail]=m, match=new RefereeMatch();
    match.id=id; match.best=Number(best); match.retired=-1; match.service=defaultService(); match.games=[];
    let body=tail,encoded=squash;
    if(version==='M4'){
      const parts=/^(-1|0|1)\|([^|]+)\|([^|]+)\|([\s\S]+)$/.exec(tail);
      if(!parts||!/^(-1|[0-3])(,(-1|[0-3])){13}$/.test(parts[3]))invalid();
      match.retired=Number(parts[1]);encoded=parts[2];match.service=parts[3].split(',').map(Number);body=parts[4];
    }else if(version!=='M1'){
      const parts=/^(-1|0|1)\|([\s\S]+)$/.exec(tail);
      if(!parts||version==='M2'&&parts[1]==='-1')invalid();
      match.retired=Number(parts[1]);body=parts[2];
    }
    match.format=parseFormat(encoded);
    const {sport}=match.format;
    const options=sport==='pickleball'?[1,3,5]:sport==='table-tennis'?[3,5,7]:sport==='badminton'||sport==='padel'?[3]:[3,5];
    if(!options.includes(match.best))invalid();
    for(let i=0;i<14;i+=2)if(!validService(match.service[i],match.service[i+1],match.format))invalid();
    const games=body.split('~');
    if(games.length>match.best)invalid();
    for(const [index,g] of games.entries()){
      if(index&&(match.game.winner<0||match.setWinner()>=0))invalid();
      match.games.push(Game.decode(g,match.format,match.tieAt(index),match.service[index*2],match.service[index*2+1]));
    }
    if(version!=='M4'&&(version==='M3')!==match.games.some(g=>g.entered)||match.retired>=0&&match.setWinner()>=0)invalid();
    return match;
  }
  get game() { return this.games.at(-1); }
  tieAt(index) { return this.format.finalTie&&index===this.best-1; }
  get sets() { const wins=[0,0];for(const game of this.games)if(game.winner>=0)wins[game.winner]++;return wins; }
  setWinner() { return this.sets.findIndex(won=>won>this.best/2); }
  get winner() { return this.retired<0?this.setWinner():1-this.retired; }
  get nextReady() { return this.winner<0&&this.game.winner>=0; }
  get canUndo() { return this.retired>=0||this.games.length>1||this.game.entered||this.game.history.length>0; }
  get undoEntered() { return this.retired===-1&&(this.game.entered||!this.game.history.length&&this.games.length>1&&this.games.at(-2).entered); }
  score(side) { return this.winner<0&&this.game.add(side); }
  retire(side) { if(this.winner>=0||![0,1].includes(side))return false;this.retired=side;return true; }
  starters(first,other) {
    if(this.winner>=0||this.game.winner>=0||!this.game.setStarters(first,other))return false;
    const index=(this.games.length-1)*2;
    this.service[index]=first;this.service[index+1]=other;
    return true;
  }
  get server() {
    const g=this.game,f=this.format,first=g.first,other=g.other,index=this.games.length-1;
    if(this.winner>=0||g.winner>=0)return -1;
    if(g.pickle)return first<0?-1:g.pServer;
    if(f.sport==='squash'||f.sport==='badminton'){
      const last=g.history.at(-1);
      const side=last?Number(last==='R'):index?this.games[index-1].winner:-1;
      if(!f.doubles)return side>=0?side:first;
      if(first<0)return side>=0?side+4:-1;
      if(other<0)return 4+(side>=0?side:first%2);
      const right=[0,1],scores=[0,0];right[first%2]=first;right[other%2]=other;
      let server=first;
      for(const rally of g.history){
        const won=Number(rally==='R');scores[won]++;
        if(server%2===won)right[won]=partner(right[won]);
        else server=scores[won]%2===0?right[won]:partner(right[won]);
      }
      return server;
    }
    if(first<0)return -1;
    if(f.sport==='table-tennis'){
      const len=g.history.length;
      if(f.doubles&&other<0){const step=len<20?Math.floor(len/2):10+len-20;return step===0?first:4+(first%2+step)%2;}
      let server=first,receiver=f.doubles?other:1-first,count=0,flipped=false;const points=[0,0];
      for(const rally of g.history){
        points[Number(rally==='R')]++;count++;
        if(count>=20||count%2===0){const old=server;server=receiver;receiver=f.doubles?partner(old):old;}
        if(f.doubles&&index===this.best-1&&!flipped&&Math.max(...points)>=5){receiver=partner(receiver);flipped=true;}
      }
      return server;
    }
    const step=g.serviceStep();
    if(!f.doubles)return (first+step)%2;
    if(other<0)return step===0?first:4+(first%2+step)%2;
    return [first,other,partner(first),partner(other)][step%4];
  }
  next() {
    if(!this.nextReady)return false;
    const index=this.games.length,g=this.game,f=this.format;
    let [first,other]=this.service.slice(index*2,index*2+2);
    if(g.pickle){first=g.other;other=g.first;}
    else if(!f.doubles&&f.sport==='badminton')first=g.winner;
    else if(f.sport==='table-tennis'&&g.first>=0){
      if(!f.doubles)first=1-g.first;
      else if(g.other>=0){first=g.other;other=g.first;}
    }else if(f.nested&&g.first>=0){
      const step=g.serviceStep(),order=[g.first,g.other,partner(g.first),partner(g.other)];
      if(f.doubles&&g.other>=0){first=order[step%4];other=order[(step+1)%4];}
      else if(!f.doubles)first=(g.first+step)%2;
    }
    this.service[index*2]=first;this.service[index*2+1]=other;
    this.games.push(new Game(f,this.tieAt(index),first,other));return true;
  }
  undo() {
    if(this.retired>=0){this.retired=-1;return true;}
    if(!this.canUndo)return false;
    if(!this.game.history.length&&!this.game.entered&&this.games.length>1)this.games.pop();
    const old=this.game, game=new Game(this.format,old.tie,old.first,old.other);
    for(const rally of old.history.slice(0,-1))game.add(rally==='B'?0:1);
    this.games[this.games.length-1]=game;return true;
  }
  encode() {
    const body=this.games.map(g=>g.encode()).join('~');
    if(this.format.encoded!==squash||this.service.some((v,i)=>i%2===0&&v>=0))
      return ['M4',this.id,this.best,this.retired,this.format.encoded,this.service.join(','),body].join('|');
    if(this.games.some(g=>g.entered))return ['M3',this.id,this.best,this.retired,body].join('|');
    if(this.retired>=0)return ['M2',this.id,this.best,this.retired,body].join('|');
    return ['M1',this.id,this.best,body].join('|');
  }
}

export function readSnapshot(data) {
  if(!data||data.schema!==1||typeof data.final!=='boolean'||!Array.isArray(data.labels)||data.labels.length!==4
    ||!data.labels.every(s=>typeof s==='string'&&s.length<=240)
    ||![data.startedAt,data.finishedAt].every(n=>Number.isSafeInteger(n)&&n>=0)||typeof data.zone!=='string'||data.zone.length>120)invalid();
  const match=RefereeMatch.decode(data.match);
  if(data.final&&match.winner<0)invalid();
  return match;
}

export function applyAction(data,action,now=Date.now(),zone=Intl.DateTimeFormat().resolvedOptions().timeZone) {
  if(data.final)return null;
  const match=readSnapshot(data);
  const canStart=data.clockEligible??(match.games.length===1&&!match.game.entered&&!match.game.history);
  const wasComplete=match.winner>=0;
  let accepted=false;
  switch(action.type){
    case 'score': accepted=match.score(action.side); break;
    case 'undo': accepted=match.undo(); break;
    case 'next': accepted=match.next(); break;
    case 'retire': accepted=match.retire(action.side); break;
    case 'starters': accepted=match.starters(action.first,action.other); break;
    case 'finish': accepted=match.winner>=0; break;
  }
  if(!accepted)return null;
  const starts=!data.startedAt&&canStart&&action.type==='score';
  const finishedAt=match.winner<0?0:wasComplete?data.finishedAt:Math.max(now,data.startedAt);
  return {...data,match:match.encode(),final:action.type==='finish',clockEligible:canStart,
    startedAt:starts?now:data.startedAt,zone:starts||finishedAt&&!data.zone?zone:data.zone,finishedAt};
}
