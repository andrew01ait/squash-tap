import {rowRules,scorePairs} from './sports.mjs?v=0.16.3';
import {datedFile} from './backup.mjs?v=0.16.0';
import {tx,sportText,modeText,momentText,durationText} from './transfer-copy.mjs';

function lines(ctx,text,width){const out=[];let line='';for(const char of [...text]){if(line&&ctx.measureText(line+char).width>width){out.push(line.trim());line=char;}else line+=char;}if(line)out.push(line.trim());return out;}
function label(ctx,text,x,y,width,size,maxLines=6){let rows;do{ctx.font='600 '+size+'px system-ui';rows=lines(ctx,text,width);if(rows.length<=maxLines)break;size--;}while(size>12);ctx.textAlign='center';for(let i=0;i<rows.length;i++)ctx.fillText(rows[i],x,y+i*size*1.22);}
export async function resultCardBlob(row,opponent,myName=tx('Me')){
 const f=rowRules(row),canvas=document.createElement('canvas');canvas.width=1080;canvas.height=1280;const c=canvas.getContext('2d');if(!c)throw Error('image');
 const mine=f.doubles?myName+' + '+(row[33]||tx('Partner')):myName,other=f.doubles?opponent+' + '+(row[35]||tx('Opponent 2')):opponent;
 c.fillStyle='#080d17';c.fillRect(0,0,1080,1280);c.textAlign='left';c.fillStyle='#edf4ff';c.font='700 46px system-ui';c.fillText('Keep Score',58,82);c.font='24px system-ui';c.fillStyle='#a7b6c9';c.fillText(sportText(f.sport)+' · '+modeText(f.doubles?'doubles':'singles')+' · '+momentText(row,7,false),58,128);
 for(let i=0;i<2;i++){const x=i?554:54;c.fillStyle=i?'#b82e4c':'#1668cb';c.beginPath();c.roundRect(x,180,472,510,32);c.fill();c.fillStyle='#fff';label(c,i?other:mine,x+236,236,414,32,6);c.textAlign='center';c.font='750 180px system-ui';c.fillText(row[i?12:11],x+236,605);}
 c.fillStyle='#d6e8ff';c.font='600 29px system-ui';c.textAlign='center';c.fillText(tx(f.nested?'Sets':'Games')+' · '+tx(row[23]?'Won by retirement':'Final result'),540,746);
 const scores=scorePairs(row).filter(Boolean);c.font='28px system-ui';scores.forEach(([a,b],i)=>{c.fillStyle='#142236';c.beginPath();c.roundRect(200,785+i*43,680,36,10);c.fill();c.fillStyle='#edf4ff';c.fillText(tx(f.finalTie&&i===Number(row[6])-1?'Match tie-break':f.nested?'Set':'Game')+' '+(i+1)+'     '+a+' – '+b,540,813+i*43);});
 c.fillStyle='#a7b6c9';c.font='23px system-ui';c.fillText(tx('Best of {0}',row[6])+(row[10]?' · '+durationText(Number(row[10])):''),540,1160);c.fillText(tx('Keep score. Keep playing.'),540,1218);
 return await new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(Error('image')),'image/png'));
}
function saveBlob(blob,file) {
  const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=file;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
export async function downloadResultCard(row,opponent,myName=tx('Me')){
  const blob=await resultCardBlob(row,opponent,myName);saveBlob(blob,datedFile(rowRules(row).sport+'_Result_'+row[2]+'.png'));
}
export async function shareResultCard(row,opponent,myName=tx('Me')){
  const blob=await resultCardBlob(row,opponent,myName),filename=datedFile(rowRules(row).sport+'_Result_'+row[2]+'.png');
  if(typeof File==='function'&&navigator.canShare) {
    const file=new File([blob],filename,{type:'image/png'});
    if(navigator.canShare({files:[file]})){await navigator.share({files:[file],title:'Keep Score · '+sportText(rowRules(row).sport)});return;}
  }
  saveBlob(blob,filename);
}
